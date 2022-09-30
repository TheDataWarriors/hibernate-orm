/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import org.hibernate.AnnotationException;
import org.hibernate.annotations.Columns;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JoinColumnOrFormula;
import org.hibernate.annotations.JoinColumnsOrFormulas;
import org.hibernate.annotations.JoinFormula;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.annotations.EntityBinder;
import org.hibernate.cfg.annotations.Nullability;
import org.hibernate.internal.util.StringHelper;

import static org.hibernate.cfg.AnnotatedColumn.buildColumnFromAnnotation;
import static org.hibernate.cfg.AnnotatedColumn.buildColumnFromNoAnnotation;
import static org.hibernate.cfg.AnnotatedColumn.buildColumnsFromAnnotations;
import static org.hibernate.cfg.AnnotatedColumn.buildFormulaFromAnnotation;
import static org.hibernate.cfg.AnnotationBinder.getOverridableAnnotation;
import static org.hibernate.cfg.BinderHelper.getPath;
import static org.hibernate.cfg.BinderHelper.getPropertyOverriddenByMapperOrMapsId;

/**
 * Do the initial discovery of columns metadata and apply defaults.
 * Also hosts some convenient methods related to column processing
 *
 * @author Emmanuel Bernard
 * @author Brett Meyer
 */
class ColumnsBuilder {

	private final PropertyHolder propertyHolder;
	private final Nullability nullability;
	private final XProperty property;
	private final PropertyData inferredData;
	private final EntityBinder entityBinder;
	private final MetadataBuildingContext buildingContext;
	private AnnotatedColumn[] columns;
	private AnnotatedJoinColumn[] joinColumns;

	public ColumnsBuilder(
			PropertyHolder propertyHolder,
			Nullability nullability,
			XProperty property,
			PropertyData inferredData,
			EntityBinder entityBinder,
			MetadataBuildingContext buildingContext) {
		this.propertyHolder = propertyHolder;
		this.nullability = nullability;
		this.property = property;
		this.inferredData = inferredData;
		this.entityBinder = entityBinder;
		this.buildingContext = buildingContext;
	}

	public AnnotatedColumn[] getColumns() {
		return columns;
	}

	public AnnotatedJoinColumn[] getJoinColumns() {
		return joinColumns;
	}

	public ColumnsBuilder extractMetadata() {
		columns = null;
		joinColumns = buildExplicitJoinColumns(property, inferredData);


		Comment comment = property.getAnnotation(Comment.class);
		if ( property.isAnnotationPresent( Column.class ) ) {
			columns = buildColumnFromAnnotation(
					property.getAnnotation( Column.class ),
					comment,
					nullability,
					propertyHolder,
					inferredData,
					entityBinder.getSecondaryTables(),
					buildingContext
			);
		}
		else if ( property.isAnnotationPresent( Formula.class ) ) {
			columns = buildFormulaFromAnnotation(
					getOverridableAnnotation( property, Formula.class, buildingContext ),
					comment,
					nullability,
					propertyHolder,
					inferredData,
					entityBinder.getSecondaryTables(),
					buildingContext
			);
		}
		else if ( property.isAnnotationPresent( Columns.class ) ) {
			columns = buildColumnsFromAnnotations(
					property.getAnnotation( Columns.class ).columns(),
					comment,
					nullability,
					propertyHolder,
					inferredData,
					entityBinder.getSecondaryTables(),
					buildingContext
			);
		}

		//set default values if needed
		if ( joinColumns == null &&
				( property.isAnnotationPresent( ManyToOne.class )
						|| property.isAnnotationPresent( OneToOne.class ) )
				) {
			joinColumns = buildDefaultJoinColumnsForXToOne( property, inferredData );
		}
		else if ( joinColumns == null &&
				( property.isAnnotationPresent( OneToMany.class )
						|| property.isAnnotationPresent( ElementCollection.class )
				) ) {
			OneToMany oneToMany = property.getAnnotation( OneToMany.class );
			joinColumns = AnnotatedJoinColumn.buildJoinColumns(
					null,
					comment,
					oneToMany != null ? oneToMany.mappedBy() : "",
					entityBinder.getSecondaryTables(),
					propertyHolder,
					inferredData.getPropertyName(),
					buildingContext
			);
		}
		else if ( joinColumns == null && property.isAnnotationPresent( org.hibernate.annotations.Any.class ) ) {
			throw new AnnotationException( "@Any requires an explicit @JoinColumn(s): "
					+ getPath( propertyHolder, inferredData ) );
		}
		if ( columns == null && !property.isAnnotationPresent( ManyToMany.class ) ) {
			//useful for collection of embedded elements
			columns = buildColumnFromNoAnnotation(
					comment,
					nullability,
					propertyHolder,
					inferredData,
					entityBinder.getSecondaryTables(),
					buildingContext
			);
		}

		if ( nullability == Nullability.FORCED_NOT_NULL ) {
			//force columns to not null
			for (AnnotatedColumn col : columns ) {
				col.forceNotNull();
			}
		}
		return this;
	}

	AnnotatedJoinColumn[] buildDefaultJoinColumnsForXToOne(XProperty property, PropertyData inferredData) {
		AnnotatedJoinColumn[] joinColumns;
		JoinTable joinTableAnn = propertyHolder.getJoinTable( property );
		Comment comment = property.getAnnotation(Comment.class);
		if ( joinTableAnn != null ) {
			joinColumns = AnnotatedJoinColumn.buildJoinColumns(
					joinTableAnn.inverseJoinColumns(),
					comment,
					null,
					entityBinder.getSecondaryTables(),
					propertyHolder,
					inferredData.getPropertyName(),
					buildingContext
			);
			if ( StringHelper.isEmpty( joinTableAnn.name() ) ) {
				throw new AnnotationException(
						"JoinTable.name() on a @ToOne association has to be explicit: "
								+ getPath( propertyHolder, inferredData )
				);
			}
		}
		else {
			OneToOne oneToOneAnn = property.getAnnotation( OneToOne.class );
			String mappedBy = oneToOneAnn != null
					? oneToOneAnn.mappedBy()
					: null;
			joinColumns = AnnotatedJoinColumn.buildJoinColumns(
					null,
					comment,
					mappedBy,
					entityBinder.getSecondaryTables(),
					propertyHolder,
					inferredData.getPropertyName(),
					buildingContext
			);
		}
		return joinColumns;
	}

	AnnotatedJoinColumn[] buildExplicitJoinColumns(XProperty property, PropertyData inferredData) {
		//process @JoinColumn(s) before @Column(s) to handle collection of entities properly
		JoinColumn[] joinColumnAnnotations = null;

		if ( property.isAnnotationPresent( JoinColumn.class ) ) {
			joinColumnAnnotations = new JoinColumn[] { property.getAnnotation( JoinColumn.class ) };
		}
		else if ( property.isAnnotationPresent( JoinColumns.class ) ) {
			JoinColumns joinColumnAnnotation = property.getAnnotation( JoinColumns.class );
			joinColumnAnnotations = joinColumnAnnotation.value();
			int length = joinColumnAnnotations.length;
			if ( length == 0 ) {
				throw new AnnotationException( "Cannot bind an empty @JoinColumns" );
			}
		}

		if ( joinColumnAnnotations != null ) {
			return AnnotatedJoinColumn.buildJoinColumns(
					joinColumnAnnotations,
					property.getAnnotation( Comment.class ),
					null,
					entityBinder.getSecondaryTables(),
					propertyHolder,
					inferredData.getPropertyName(),
					buildingContext
			);
		}

		JoinColumnOrFormula[] joinColumnOrFormulaAnnotations = null;

		if ( property.isAnnotationPresent( JoinColumnOrFormula.class ) ) {
			joinColumnOrFormulaAnnotations = new JoinColumnOrFormula[] {
					property.getAnnotation( JoinColumnOrFormula.class ) };
		}
		else if ( property.isAnnotationPresent( JoinColumnsOrFormulas.class ) ) {
			JoinColumnsOrFormulas joinColumnsOrFormulasAnnotations = property.getAnnotation(
					JoinColumnsOrFormulas.class );
			joinColumnOrFormulaAnnotations = joinColumnsOrFormulasAnnotations.value();
			int length = joinColumnOrFormulaAnnotations.length;
			if ( length == 0 ) {
				throw new AnnotationException( "Cannot bind an empty @JoinColumnsOrFormulas" );
			}
		}

		if (joinColumnOrFormulaAnnotations != null) {
			return AnnotatedJoinColumn.buildJoinColumnsOrFormulas(
					joinColumnOrFormulaAnnotations,
					null,
					entityBinder.getSecondaryTables(),
					propertyHolder,
					inferredData.getPropertyName(),
					buildingContext
			);
		}

		if (property.isAnnotationPresent( JoinFormula.class)) {
			JoinFormula ann = getOverridableAnnotation( property, JoinFormula.class, buildingContext );
			AnnotatedJoinColumn[] annotatedJoinColumns = new AnnotatedJoinColumn[1];
			annotatedJoinColumns[0] = AnnotatedJoinColumn.buildJoinFormula(
					ann,
					null,
					entityBinder.getSecondaryTables(),
					propertyHolder,
					inferredData.getPropertyName(),
					buildingContext
			);
			return annotatedJoinColumns;
		}

		return null;
	}

	AnnotatedColumn[] overrideColumnFromMapperOrMapsIdProperty(boolean isId) {
		AnnotatedColumn[] result = columns;
		final PropertyData overridingProperty = getPropertyOverriddenByMapperOrMapsId(
				isId,
				propertyHolder,
				property.getName(),
				buildingContext
		);
		if ( overridingProperty != null ) {
			result = buildExcplicitOrDefaultJoinColumn( overridingProperty );
		}
		return result;
	}

	/**
	 * useful to override a column either by @MapsId or by @IdClass
	 */
	AnnotatedColumn[] buildExcplicitOrDefaultJoinColumn(PropertyData overridingProperty) {
		AnnotatedColumn[] result;
		result = buildExplicitJoinColumns( overridingProperty.getProperty(), overridingProperty );
		if (result == null) {
			result = buildDefaultJoinColumnsForXToOne( overridingProperty.getProperty(), overridingProperty);
		}
		return result;
	}
}
