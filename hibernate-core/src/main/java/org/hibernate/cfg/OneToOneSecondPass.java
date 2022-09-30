/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg;

import java.util.Map;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;

import org.hibernate.AnnotationException;
import org.hibernate.MappingException;
import org.hibernate.annotations.LazyGroup;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.annotations.PropertyBinder;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.Join;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToOne;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.SortableValue;
import org.hibernate.type.ForeignKeyDirection;

import static org.hibernate.cfg.BinderHelper.findPropertyByName;
import static org.hibernate.cfg.BinderHelper.isEmptyAnnotationValue;
import static org.hibernate.internal.util.StringHelper.qualify;

/**
 * We have to handle OneToOne in a second pass because:
 * -
 */
public class OneToOneSecondPass implements SecondPass {
	private final MetadataBuildingContext buildingContext;
	private final String mappedBy;
	private final String ownerEntity;
	private final String ownerProperty;
	private final PropertyHolder propertyHolder;
	private final NotFoundAction notFoundAction;
	private final PropertyData inferredData;
	private final XClass targetEntity;
	private final boolean cascadeOnDelete;
	private final boolean optional;
	private final String cascadeStrategy;
	private final AnnotatedJoinColumn[] joinColumns;

	//that sucks, we should read that from the property mainly
	public OneToOneSecondPass(
			String mappedBy,
			String ownerEntity,
			String ownerProperty,
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			XClass targetEntity,
			NotFoundAction notFoundAction,
			boolean cascadeOnDelete,
			boolean optional,
			String cascadeStrategy,
			AnnotatedJoinColumn[] columns,
			MetadataBuildingContext buildingContext) {
		this.ownerEntity = ownerEntity;
		this.ownerProperty = ownerProperty;
		this.mappedBy = mappedBy;
		this.propertyHolder = propertyHolder;
		this.buildingContext = buildingContext;
		this.notFoundAction = notFoundAction;
		this.inferredData = inferredData;
		this.targetEntity = targetEntity;
		this.cascadeOnDelete = cascadeOnDelete;
		this.optional = optional;
		this.cascadeStrategy = cascadeStrategy;
		this.joinColumns = columns;
	}

	//TODO refactor this code, there is a lot of duplication in this method
	public void doSecondPass(Map<String, PersistentClass> persistentClasses) throws MappingException {
		OneToOne value = new OneToOne(
				buildingContext,
				propertyHolder.getTable(),
				propertyHolder.getPersistentClass()
		);
		final String propertyName = inferredData.getPropertyName();
		value.setPropertyName( propertyName );
		String referencedEntityName = ToOneBinder.getReferenceEntityName( inferredData, targetEntity, buildingContext );
		value.setReferencedEntityName( referencedEntityName );
		AnnotationBinder.defineFetchingStrategy( value, inferredData.getProperty() );
		//value.setFetchMode( fetchMode );
		value.setCascadeDeleteEnabled( cascadeOnDelete );
		//value.setLazy( fetchMode != FetchMode.JOIN );

		value.setConstrained( !optional );
		final ForeignKeyDirection foreignKeyDirection = !isEmptyAnnotationValue( mappedBy )
				? ForeignKeyDirection.TO_PARENT
				: ForeignKeyDirection.FROM_PARENT;
		value.setForeignKeyType(foreignKeyDirection);
		AnnotationBinder.bindForeignKeyNameAndDefinition(
				value,
				inferredData.getProperty(),
				inferredData.getProperty().getAnnotation( jakarta.persistence.ForeignKey.class ),
				inferredData.getProperty().getAnnotation( JoinColumn.class ),
				inferredData.getProperty().getAnnotation( JoinColumns.class),
				buildingContext
		);

		PropertyBinder binder = new PropertyBinder();
		binder.setName( propertyName );
		binder.setProperty( inferredData.getProperty() );
		binder.setValue( value );
		binder.setCascade( cascadeStrategy );
		binder.setAccessType( inferredData.getDefaultAccess() );

		final LazyGroup lazyGroupAnnotation = inferredData.getProperty().getAnnotation( LazyGroup.class );
		if ( lazyGroupAnnotation != null ) {
			binder.setLazyGroup( lazyGroupAnnotation.value() );
		}

		Property prop = binder.makeProperty();
		prop.setOptional( optional );
		if ( isEmptyAnnotationValue( mappedBy ) ) {
			/*
			 * we need to check if the columns are in the right order
			 * if not, then we need to create a many to one and formula
			 * but actually, since entities linked by a one to one need
			 * to share the same composite id class, this cannot happen in hibernate
			 */
			boolean rightOrder = true;

			if ( rightOrder ) {
				String path = qualify( propertyHolder.getPath(), propertyName );
				final ToOneFkSecondPass secondPass = new ToOneFkSecondPass(
						value,
						joinColumns,
						!optional, //cannot have nullabe and unique on certain DBs
						propertyHolder.getEntityOwnerClassName(),
						path,
						buildingContext
				);
				secondPass.doSecondPass( persistentClasses );
				//no column associated since its a one to one
				propertyHolder.addProperty( prop, inferredData.getDeclaringClass() );
			}
//			else {
				//this is a many to one with Formula
//			}
		}
		else {
			value.setMappedByProperty( mappedBy );
			PersistentClass otherSide = persistentClasses.get( value.getReferencedEntityName() );
			Property otherSideProperty;
			try {
				if ( otherSide == null ) {
					throw new MappingException( "Unable to find entity: " + value.getReferencedEntityName() );
				}
				otherSideProperty = findPropertyByName( otherSide, mappedBy );
			}
			catch (MappingException e) {
				throw new AnnotationException(
						"Unknown mappedBy in: " + qualify( ownerEntity, ownerProperty )
								+ ", referenced property unknown: "
								+ qualify( value.getReferencedEntityName(), mappedBy )
				);
			}
			if ( otherSideProperty == null ) {
				throw new AnnotationException(
						"Unknown mappedBy in: " + qualify( ownerEntity, ownerProperty )
								+ ", referenced property unknown: "
								+ qualify( value.getReferencedEntityName(), mappedBy )
				);
			}
			if ( otherSideProperty.getValue() instanceof OneToOne ) {
				propertyHolder.addProperty( prop, inferredData.getDeclaringClass() );
			}
			else if ( otherSideProperty.getValue() instanceof ManyToOne ) {
				Join otherSideJoin = null;
				for ( Join otherSideJoinValue : otherSide.getJoins() ) {
					if ( otherSideJoinValue.containsProperty( otherSideProperty ) ) {
						otherSideJoin = otherSideJoinValue;
						break;
					}
				}
				if ( otherSideJoin != null ) {
					//@OneToOne @JoinTable
					Join mappedByJoin = buildJoinFromMappedBySide(
							persistentClasses.get( ownerEntity ), otherSideProperty, otherSideJoin
					);
					ManyToOne manyToOne = new ManyToOne( buildingContext, mappedByJoin.getTable() );
					//FIXME use ignore not found here
					manyToOne.setNotFoundAction( notFoundAction );
					manyToOne.setCascadeDeleteEnabled( value.isCascadeDeleteEnabled() );
					manyToOne.setFetchMode( value.getFetchMode() );
					manyToOne.setLazy( value.isLazy() );
					manyToOne.setReferencedEntityName( value.getReferencedEntityName() );
					manyToOne.setUnwrapProxy( value.isUnwrapProxy() );
					manyToOne.markAsLogicalOneToOne();
					prop.setValue( manyToOne );
					for ( Column column: otherSideJoin.getKey().getColumns() ) {
						Column copy = new Column();
						copy.setLength( column.getLength() );
						copy.setScale( column.getScale() );
						copy.setValue( manyToOne );
						copy.setName( column.getQuotedName() );
						copy.setNullable( column.isNullable() );
						copy.setPrecision( column.getPrecision() );
						copy.setUnique( column.isUnique() );
						copy.setSqlType( column.getSqlType() );
						copy.setCheckConstraint( column.getCheckConstraint() );
						copy.setComment( column.getComment() );
						copy.setDefaultValue( column.getDefaultValue() );
						copy.setGeneratedAs(column.getGeneratedAs() );
						manyToOne.addColumn( copy );
					}
					mappedByJoin.addProperty( prop );
				}
				else {
					propertyHolder.addProperty( prop, inferredData.getDeclaringClass() );
				}

				value.setReferencedPropertyName( mappedBy );

				// HHH-6813
				// Foo: @Id long id, @OneToOne(mappedBy="foo") Bar bar
				// Bar: @Id @OneToOne Foo foo
				boolean referenceToPrimaryKey  = ( mappedBy == null ) || otherSide.getIdentifier() instanceof Component && ! ( (Component) otherSide.getIdentifier() ).hasProperty( mappedBy ) ;
				value.setReferenceToPrimaryKey( referenceToPrimaryKey );

				String propertyRef = value.getReferencedPropertyName();
				if ( propertyRef != null ) {
					buildingContext.getMetadataCollector().addUniquePropertyReference(
							value.getReferencedEntityName(),
							propertyRef
					);
				}
			}
			else {
				throw new AnnotationException(
						"Referenced property not a (One|Many)ToOne: "
								+ qualify(
								otherSide.getEntityName(), mappedBy
						)
								+ " in mappedBy of "
								+ qualify( ownerEntity, ownerProperty )
				);
			}
		}
		value.sortProperties();
	}

	/**
	 * Builds the <code>Join</code> instance for the mapped by side of a <i>OneToOne</i> association using
	 * a join table.
	 * <p>
	 * Note:<br/>
	 * <ul>
	 * <li>From the mappedBy side we should not create the PK nor the FK, this is handled from the other side.</li>
	 * <li>This method is a dirty dupe of EntityBinder.bindSecondaryTable</li>.
	 * </ul>
	 * </p>
	 */
	private Join buildJoinFromMappedBySide(PersistentClass persistentClass, Property otherSideProperty, Join originalJoin) {
		Join join = new Join();
		join.setPersistentClass( persistentClass );

		//no check constraints available on joins
		join.setTable( originalJoin.getTable() );
		join.setInverse( true );
		DependantValue key = new DependantValue( buildingContext, join.getTable(), persistentClass.getIdentifier() );

		if ( notFoundAction != null ) {
			join.disableForeignKeyCreation();
		}

		//TODO support @ForeignKey
		join.setKey( key );
		join.setSequentialSelect( false );
		//TODO support for inverse and optional
		join.setOptional( true ); //perhaps not quite per-spec, but a Good Thing anyway
		key.setCascadeDeleteEnabled( false );
		for ( Column column: otherSideProperty.getValue().getColumns() ) {
			Column copy = new Column();
			copy.setLength( column.getLength() );
			copy.setScale( column.getScale() );
			copy.setValue( key );
			copy.setName( column.getQuotedName() );
			copy.setNullable( column.isNullable() );
			copy.setPrecision( column.getPrecision() );
			copy.setUnique( column.isUnique() );
			copy.setSqlType( column.getSqlType() );
			copy.setCheckConstraint( column.getCheckConstraint() );
			copy.setComment( column.getComment() );
			copy.setDefaultValue( column.getDefaultValue() );
			column.setGeneratedAs( column.getGeneratedAs() );
			key.addColumn( copy );
		}
		if ( otherSideProperty.getValue() instanceof SortableValue
				&& !( (SortableValue) otherSideProperty.getValue() ).isSorted() ) {
			key.sortProperties();
		}
		persistentClass.addJoin( join );
		return join;
	}
}
