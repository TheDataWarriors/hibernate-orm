/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg;

import java.util.Map;

import jakarta.persistence.ForeignKey;

import org.hibernate.AnnotationException;
import org.hibernate.MappingException;
import org.hibernate.annotations.LazyGroup;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.annotations.PropertyBinder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.Join;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToOne;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.SortableValue;
import org.hibernate.type.ForeignKeyDirection;

import static org.hibernate.cfg.BinderHelper.findPropertyByName;
import static org.hibernate.cfg.BinderHelper.getPath;
import static org.hibernate.cfg.BinderHelper.isEmptyAnnotationValue;
import static org.hibernate.cfg.ToOneBinder.bindForeignKeyNameAndDefinition;
import static org.hibernate.cfg.ToOneBinder.getReferenceEntityName;
import static org.hibernate.internal.util.StringHelper.qualify;

/**
 * We have to handle {@link jakarta.persistence.OneToOne} associations
 * in a second pass.
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
	private final AnnotatedJoinColumns joinColumns;

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
			AnnotatedJoinColumns columns,
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
		final OneToOne value = new OneToOne(
				buildingContext,
				propertyHolder.getTable(),
				propertyHolder.getPersistentClass()
		);
		final String propertyName = inferredData.getPropertyName();
		value.setPropertyName( propertyName );
		final String referencedEntityName = getReferenceEntityName( inferredData, targetEntity, buildingContext );
		value.setReferencedEntityName( referencedEntityName );
		XProperty property = inferredData.getProperty();
		ToOneBinder.defineFetchingStrategy( value, property, inferredData, propertyHolder );
		//value.setFetchMode( fetchMode );
		value.setCascadeDeleteEnabled( cascadeOnDelete );
		//value.setLazy( fetchMode != FetchMode.JOIN );

		value.setConstrained( !optional );
		value.setForeignKeyType( getForeignKeyDirection() );
		bindForeignKeyNameAndDefinition( value, property, property.getAnnotation( ForeignKey.class ), buildingContext );

		final PropertyBinder binder = new PropertyBinder();
		binder.setName( propertyName );
		binder.setProperty( property );
		binder.setValue( value );
		binder.setCascade( cascadeStrategy );
		binder.setAccessType( inferredData.getDefaultAccess() );

		final LazyGroup lazyGroupAnnotation = property.getAnnotation( LazyGroup.class );
		if ( lazyGroupAnnotation != null ) {
			binder.setLazyGroup( lazyGroupAnnotation.value() );
		}

		final Property result = binder.makeProperty();
		result.setOptional( optional );
		if ( isEmptyAnnotationValue( mappedBy ) ) {
			bindUnowned( persistentClasses, value, propertyName, result );
		}
		else {
			bindOwned( persistentClasses, value, result );
		}
		value.sortProperties();
	}

	private ForeignKeyDirection getForeignKeyDirection() {
		return !isEmptyAnnotationValue( mappedBy ) ? ForeignKeyDirection.TO_PARENT : ForeignKeyDirection.FROM_PARENT;
	}

	private void bindOwned(Map<String, PersistentClass> persistentClasses, OneToOne oneToOne, Property property) {
		oneToOne.setMappedByProperty( mappedBy );
		final PersistentClass targetEntity = persistentClasses.get( oneToOne.getReferencedEntityName() );
		if ( targetEntity == null ) {
			throw new MappingException( "Association '" + getPath( propertyHolder, inferredData )
					+ "' targets unknown entity type '" + oneToOne.getReferencedEntityName() + "'" );
		}
		final Property targetProperty = targetProperty( oneToOne, targetEntity );
		if ( targetProperty.getValue() instanceof OneToOne ) {
			propertyHolder.addProperty( property, inferredData.getDeclaringClass() );
		}
		else if ( targetProperty.getValue() instanceof ManyToOne ) {
			bindTargetManyToOne( persistentClasses, oneToOne, property, targetEntity, targetProperty );
		}
		else {
			throw new AnnotationException( "Association '" + getPath( propertyHolder, inferredData )
					+ "' is 'mappedBy' a property named '" + mappedBy
					+ "' of the target entity type '" + oneToOne.getReferencedEntityName()
					+ "' which is not a '@OneToOne' or '@ManyToOne' association" );
		}
	}

	private void bindTargetManyToOne(
			Map<String, PersistentClass> persistentClasses,
			OneToOne oneToOne,
			Property property,
			PersistentClass targetEntity,
			Property targetProperty) {
		Join otherSideJoin = null;
		for ( Join otherSideJoinValue : targetEntity.getJoins() ) {
			if ( otherSideJoinValue.containsProperty(targetProperty) ) {
				otherSideJoin = otherSideJoinValue;
				break;
			}
		}
		if ( otherSideJoin != null ) {
			//@OneToOne @JoinTable
			final Join mappedByJoin = buildJoinFromMappedBySide(
					persistentClasses.get( ownerEntity ), targetProperty, otherSideJoin
			);
			final ManyToOne manyToOne = new ManyToOne( buildingContext, mappedByJoin.getTable() );
			//FIXME use ignore not found here
			manyToOne.setNotFoundAction( notFoundAction );
			manyToOne.setCascadeDeleteEnabled( oneToOne.isCascadeDeleteEnabled() );
			manyToOne.setFetchMode( oneToOne.getFetchMode() );
			manyToOne.setLazy( oneToOne.isLazy() );
			manyToOne.setReferencedEntityName( oneToOne.getReferencedEntityName() );
			manyToOne.setUnwrapProxy( oneToOne.isUnwrapProxy() );
			manyToOne.markAsLogicalOneToOne();
			property.setValue( manyToOne );
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
				copy.setGeneratedAs( column.getGeneratedAs() );
				manyToOne.addColumn( copy );
			}
			mappedByJoin.addProperty( property );
		}
		else {
			propertyHolder.addProperty( property, inferredData.getDeclaringClass() );
		}

		oneToOne.setReferencedPropertyName( mappedBy );

		// HHH-6813
		// Foo: @Id long id, @OneToOne(mappedBy="foo") Bar bar
		// Bar: @Id @OneToOne Foo foo
		final KeyValue targetEntityIdentifier = targetEntity.getIdentifier();
		boolean referenceToPrimaryKey = mappedBy == null
				|| targetEntityIdentifier instanceof Component
						&& !( (Component) targetEntityIdentifier ).hasProperty( mappedBy );
		oneToOne.setReferenceToPrimaryKey( referenceToPrimaryKey );

		final String propertyRef = oneToOne.getReferencedPropertyName();
		if ( propertyRef != null ) {
			buildingContext.getMetadataCollector()
					.addUniquePropertyReference( oneToOne.getReferencedEntityName(), propertyRef );
		}
	}

	private Property targetProperty(OneToOne oneToOne, PersistentClass targetEntity) {
		try {
			Property targetProperty = findPropertyByName( targetEntity, mappedBy );
			if ( targetProperty != null ) {
				return targetProperty;
			}
		}
		catch (MappingException e) {
			// swallow it
		}
		throw new AnnotationException( "Association '" + getPath( propertyHolder, inferredData )
				+ "' is 'mappedBy' a property named '" + mappedBy
				+ "' which does not exist in the target entity type '" + oneToOne.getReferencedEntityName() + "'" );
	}

	private void bindUnowned(Map<String, PersistentClass> persistentClasses, OneToOne oneToOne, String propertyName, Property property) {
		// we need to check if the columns are in the right order
		// if not, then we need to create a many to one and formula
		// but actually, since entities linked by a one to one need
		// to share the same composite id class, this cannot happen
//		boolean rightOrder = true;
//
//		if ( rightOrder ) {
		final ToOneFkSecondPass secondPass = new ToOneFkSecondPass(
				oneToOne,
				joinColumns,
				true,
				propertyHolder.getPersistentClass(),
				qualify( propertyHolder.getPath(), propertyName),
				buildingContext
		);
		secondPass.doSecondPass(persistentClasses);
		//no column associated since it's a one to one
		propertyHolder.addProperty( property, inferredData.getDeclaringClass() );
//		}
//		else {
			// this is a @ManyToOne with Formula
//		}
	}

	/**
	 * Builds the {@link Join} instance for the unowned side
	 * of a {@code OneToOne} association using a join table.
	 * From the {@code mappedBy} side we should not create
	 * neither the PK, nor the FK, this is all handled from
	 * the owning side.
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
