/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg.annotations;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.hibernate.AnnotationException;
import org.hibernate.AssertionFailure;
import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.annotations.MapKeyCompositeType;
import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.AccessType;
import org.hibernate.cfg.AnnotatedClassType;
import org.hibernate.cfg.AnnotatedColumns;
import org.hibernate.cfg.AnnotatedJoinColumns;
import org.hibernate.cfg.AnnotationBinder;
import org.hibernate.cfg.CollectionPropertyHolder;
import org.hibernate.cfg.CollectionSecondPass;
import org.hibernate.cfg.AnnotatedJoinColumn;
import org.hibernate.cfg.InheritanceState;
import org.hibernate.cfg.PropertyData;
import org.hibernate.cfg.PropertyPreloadedData;
import org.hibernate.cfg.SecondPass;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.DependantBasicValue;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.hibernate.resource.beans.spi.ManagedBean;
import org.hibernate.usertype.CompositeUserType;
import org.hibernate.usertype.UserCollectionType;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.MapKeyClass;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyJoinColumn;
import jakarta.persistence.MapKeyJoinColumns;

import static org.hibernate.cfg.BinderHelper.PRIMITIVE_NAMES;
import static org.hibernate.cfg.BinderHelper.findPropertyByName;
import static org.hibernate.cfg.PropertyHolderBuilder.buildPropertyHolder;
import static org.hibernate.internal.util.StringHelper.nullIfEmpty;
import static org.hibernate.internal.util.StringHelper.qualify;

/**
 * Implementation to bind a Map
 *
 * @author Emmanuel Bernard
 */
public class MapBinder extends CollectionBinder {

	public MapBinder(
			Supplier<ManagedBean<? extends UserCollectionType>> customTypeBeanResolver,
			boolean sorted,
			MetadataBuildingContext buildingContext) {
		super( customTypeBeanResolver, sorted, buildingContext );
	}

	public boolean isMap() {
		return true;
	}

	protected Collection createCollection(PersistentClass owner) {
		return new org.hibernate.mapping.Map( getCustomTypeBeanResolver(), owner, getBuildingContext() );
	}

	@Override
	SecondPass getSecondPass() {
		return new CollectionSecondPass( MapBinder.this.collection ) {
			public void secondPass(Map<String, PersistentClass> persistentClasses)
					throws MappingException {
				bindStarToManySecondPass( persistentClasses );
				bindKeyFromAssociationTable(
						getElementType(),
						persistentClasses,
						mapKeyPropertyName,
						property,
						isEmbedded,
						mapKeyColumns,
						mapKeyManyToManyColumns
				);
				makeOneToManyMapKeyColumnNullableIfNotInProperty( property );
			}
		};
	}

	private void makeOneToManyMapKeyColumnNullableIfNotInProperty(
			final XProperty property) {
		final org.hibernate.mapping.Map map = (org.hibernate.mapping.Map) this.collection;
		if ( map.isOneToMany() &&
				property.isAnnotationPresent( MapKeyColumn.class ) ) {
			final Value indexValue = map.getIndex();
			if ( indexValue.getColumnSpan() != 1 ) {
				throw new AssertionFailure( "Map key mapped by @MapKeyColumn does not have 1 column" );
			}
			final Selectable selectable = indexValue.getSelectables().get(0);
			if ( selectable.isFormula() ) {
				throw new AssertionFailure( "Map key mapped by @MapKeyColumn is a Formula" );
			}
			final Column column = (Column) selectable;
			if ( !column.isNullable() ) {
				final PersistentClass persistentClass = ( ( OneToMany ) map.getElement() ).getAssociatedClass();
				// check if the index column has been mapped by the associated entity to a property;
				// @MapKeyColumn only maps a column to the primary table for the one-to-many, so we only
				// need to check "un-joined" properties.
				if ( !propertiesContainColumn( persistentClass.getUnjoinedProperties(), column ) ) {
					// The index column is not mapped to an associated entity property so we can
					// safely make the index column nullable.
					column.setNullable( true );
				}
			}
		}
	}

	private boolean propertiesContainColumn(List<Property> properties, Column column) {
		for ( Property property : properties ) {
			for ( Selectable selectable: property.getSelectables() ) {
				if ( column.equals( selectable ) ) {
					final Column iteratedColumn = (Column) selectable;
					if ( column.getValue().getTable().equals( iteratedColumn.getValue().getTable() ) ) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void bindKeyFromAssociationTable(
			XClass elementType,
			Map<String, PersistentClass> persistentClasses,
			String mapKeyPropertyName,
			XProperty property,
			boolean isEmbedded,
			AnnotatedColumns mapKeyColumns,
			AnnotatedJoinColumns mapKeyManyToManyColumns) {
		if ( mapKeyPropertyName != null ) {
			//this is an EJB3 @MapKey
			handleExplicitMapKey( elementType, persistentClasses, mapKeyPropertyName );
		}
		else {
			//this is a true Map mapping
			final String mapKeyType = getKeyType( property );
			final PersistentClass collectionEntity = persistentClasses.get( mapKeyType );
			final boolean isIndexOfEntities = collectionEntity != null;
			final ManyToOne element;
			org.hibernate.mapping.Map map = (org.hibernate.mapping.Map) this.collection;
			if ( isIndexOfEntities ) {
				element = handleCollectionKeyedByEntities( buildingContext, mapKeyType, map );
			}
			else {
				element = null;
				final XClass keyClass;
				final AnnotatedClassType classType;
				if ( PRIMITIVE_NAMES.contains( mapKeyType ) ) {
					classType = AnnotatedClassType.NONE;
					keyClass = null;
				}
				else {
					final BootstrapContext bootstrapContext = buildingContext.getBootstrapContext();
					final Class<Object> mapKeyClass = bootstrapContext.getClassLoaderAccess().classForName( mapKeyType );
					keyClass = bootstrapContext.getReflectionManager().toXClass( mapKeyClass );
					// force in case of attribute override naming the key
					classType = isEmbedded || mappingDefinedAttributeOverrideOnMapKey( property )
							? AnnotatedClassType.EMBEDDABLE
							: buildingContext.getMetadataCollector().getClassType( keyClass );
				}

				handleIndex(
						property,
						mapKeyColumns,
						mapKeyType,
						map,
						keyClass,
						classType,
						buildCollectionPropertyHolder( property, map, keyClass ),
						getAccessType( map )
				);
			}
			//FIXME pass the Index Entity JoinColumns
			if ( !collection.isOneToMany() ) {
				//index column should not be null
				for ( AnnotatedJoinColumn column : mapKeyManyToManyColumns.getJoinColumns() ) {
					column.forceNotNull();
				}
			}

			if ( element != null ) {
				handleForeignKey( property, element );
			}

			if ( isIndexOfEntities ) {
				bindManyToManyInverseForeignKey(
						collectionEntity,
						mapKeyManyToManyColumns,
						element,
						false //a map key column has no unique constraint
				);
			}
		}
	}

	private static String getKeyType(XProperty property) {
		//target has priority over reflection for the map key type
		//JPA 2 has priority
		final Class<?> target = property.isAnnotationPresent( MapKeyClass.class )
				? property.getAnnotation( MapKeyClass.class ).value()
				: void.class;
		return void.class.equals( target ) ? property.getMapKey().getName() : target.getName();
	}

	private void handleExplicitMapKey(
			XClass elementType,
			Map<String, PersistentClass> persistentClasses,
			String mapKeyPropertyName) {
		final PersistentClass associatedClass = persistentClasses.get( elementType.getName() );
		if ( associatedClass == null ) {
			throw new AnnotationException( "Association '" + safeCollectionRole()
					+ "' targets the type '" + elementType.getName() + "' which is not an '@Entity' type" );
		}
		final Property mapProperty = findPropertyByName( associatedClass, mapKeyPropertyName );
		if ( mapProperty == null ) {
			throw new AnnotationException( "Map key property '" + mapKeyPropertyName
					+ "' not found in target entity '" + associatedClass.getEntityName() + "'" );
		}
		final org.hibernate.mapping.Map map = (org.hibernate.mapping.Map) this.collection;
		// HHH-11005 - if InheritanceType.JOINED then need to find class defining the column
		final InheritanceState inheritanceState = inheritanceStatePerClass.get(elementType);
		final PersistentClass targetEntity = InheritanceType.JOINED == inheritanceState.getType()
				? mapProperty.getPersistentClass()
				: associatedClass;
		final Value indexValue = createFormulatedValue( mapProperty.getValue(), map, associatedClass, targetEntity );
		map.setIndex( indexValue );
		map.setMapKeyPropertyName(mapKeyPropertyName);
	}

	private CollectionPropertyHolder buildCollectionPropertyHolder(
			XProperty property,
			org.hibernate.mapping.Map map,
			XClass keyClass) {
		final CollectionPropertyHolder holder = buildPropertyHolder(
				map,
				qualify( map.getRole(), "mapkey" ),
				keyClass,
				property,
				propertyHolder,
				buildingContext
		);
		// 'propertyHolder' is the PropertyHolder for the owner of the collection
		// 'holder' is the CollectionPropertyHolder.
		// 'property' is the collection XProperty
		propertyHolder.startingProperty( property );
		holder.prepare(property);
		return holder;
	}

	private void handleForeignKey(XProperty property, ManyToOne element) {
		final jakarta.persistence.ForeignKey foreignKey = getMapKeyForeignKey( property );
		if ( foreignKey != null ) {
			final ConstraintMode constraintMode = foreignKey.value();
			if ( constraintMode == ConstraintMode.NO_CONSTRAINT
					|| constraintMode == ConstraintMode.PROVIDER_DEFAULT
							&& getBuildingContext().getBuildingOptions().isNoConstraintByDefault() ) {
				element.disableForeignKey();
			}
			else {
				element.setForeignKeyName( nullIfEmpty( foreignKey.name() ) );
				element.setForeignKeyDefinition( nullIfEmpty( foreignKey.foreignKeyDefinition() ) );
			}
		}
	}

	//similar to CollectionBinder.handleCollectionOfEntities()
	private static ManyToOne handleCollectionKeyedByEntities(
			MetadataBuildingContext context,
			String mapKeyType,
			org.hibernate.mapping.Map map) {
		final ManyToOne element;
		element = new ManyToOne( context, map.getCollectionTable() );
		map.setIndex( element );
		element.setReferencedEntityName( mapKeyType );
		//element.setFetchMode( fetchMode );
		//element.setLazy( fetchMode != FetchMode.JOIN );
		//make the second join non lazy
		element.setFetchMode( FetchMode.JOIN );
		element.setLazy( false );
		//does not make sense for a map key element.setIgnoreNotFound( ignoreNotFound );
		return element;
	}

	private static AccessType getAccessType(org.hibernate.mapping.Map map) {
		final PersistentClass owner = map.getOwner();
		final AccessType accessType;
		// FIXME support @Access for collection of elements
		// String accessType = access != null ? access.value() : null;
		if ( owner.getIdentifierProperty() != null ) {
			accessType = owner.getIdentifierProperty().getPropertyAccessorName().equals( "property" )
					? AccessType.PROPERTY
					: AccessType.FIELD;
		}
		else if ( owner.getIdentifierMapper() != null && owner.getIdentifierMapper().getPropertySpan() > 0 ) {
			Property prop = owner.getIdentifierMapper().getProperties().get(0);
			accessType = prop.getPropertyAccessorName().equals( "property" )
					? AccessType.PROPERTY
					: AccessType.FIELD;
		}
		else {
			throw new AssertionFailure( "Unable to guess collection property accessor name" );
		}
		return accessType;
	}

	private void handleIndex(
			XProperty property,
			AnnotatedColumns mapKeyColumns,
			String mapKeyType,
			org.hibernate.mapping.Map map,
			XClass keyClass,
			AnnotatedClassType classType,
			CollectionPropertyHolder holder,
			AccessType accessType) {
		final Class<? extends CompositeUserType<?>> compositeUserType =
				resolveCompositeUserType( property, keyClass, buildingContext );

		if ( AnnotatedClassType.EMBEDDABLE == classType || compositeUserType != null ) {
			final EntityBinder entityBinder = new EntityBinder();
			final PropertyData inferredData = isHibernateExtensionMapping()
					? new PropertyPreloadedData( AccessType.PROPERTY, "index", keyClass)
					// "key" is the JPA 2 prefix for map keys
					: new PropertyPreloadedData( AccessType.PROPERTY, "key", keyClass);
			//TODO be smart with isNullable
			final Component component = AnnotationBinder.fillComponent(
					holder,
					inferredData,
					accessType,
					true,
					entityBinder,
					false,
					false,
					true,
					null,
					compositeUserType,
					buildingContext,
					inheritanceStatePerClass
			);
			map.setIndex( component );
		}
		else {
			final BasicValueBinder elementBinder = new BasicValueBinder( BasicValueBinder.Kind.MAP_KEY, buildingContext );
			elementBinder.setReturnedClassName( mapKeyType );
			final AnnotatedColumns keyColumns = createElementColumnsIfNecessary(
					collection,
					mapKeyColumns,
					Collection.DEFAULT_KEY_COLUMN_NAME,
					Size.DEFAULT_LENGTH, //TODO: is this really necessary??!!
					buildingContext
			);
			elementBinder.setColumns( keyColumns );
			//do not call setType as it extracts the type from @Type
			//the algorithm generally does not apply for map key anyway
			elementBinder.setType(
					property,
					keyClass,
					collection.getOwnerEntityName(),
					holder.mapKeyAttributeConverterDescriptor( property, keyClass )
			);
			elementBinder.setPersistentClassName( propertyHolder.getEntityName() );
			elementBinder.setAccessType( accessType );
			map.setIndex( elementBinder.make() );
		}
	}

	private static Class<? extends CompositeUserType<?>> resolveCompositeUserType(
			XProperty property,
			XClass returnedClass,
			MetadataBuildingContext context) {
		final MapKeyCompositeType compositeType = property.getAnnotation( MapKeyCompositeType.class );
		if ( compositeType != null ) {
			return compositeType.value();
		}

		if ( returnedClass != null ) {
			final Class<?> embeddableClass = context.getBootstrapContext()
					.getReflectionManager()
					.toClass( returnedClass );
			if ( embeddableClass != null ) {
				return context.getMetadataCollector().findRegisteredCompositeUserType( embeddableClass );
			}
		}

		return null;
	}

	private jakarta.persistence.ForeignKey getMapKeyForeignKey(XProperty property) {
		final MapKeyJoinColumns mapKeyJoinColumns = property.getAnnotation( MapKeyJoinColumns.class );
		if ( mapKeyJoinColumns != null ) {
			return mapKeyJoinColumns.foreignKey();
		}
		else {
			final MapKeyJoinColumn mapKeyJoinColumn = property.getAnnotation( MapKeyJoinColumn.class );
			if ( mapKeyJoinColumn != null ) {
				return mapKeyJoinColumn.foreignKey();
			}
		}
		return null;
	}

	private boolean mappingDefinedAttributeOverrideOnMapKey(XProperty property) {
		if ( property.isAnnotationPresent( AttributeOverride.class ) ) {
			return namedMapKey( property.getAnnotation( AttributeOverride.class ) );
		}

		if ( property.isAnnotationPresent( AttributeOverrides.class ) ) {
			final AttributeOverrides annotations = property.getAnnotation( AttributeOverrides.class );
			for ( AttributeOverride attributeOverride : annotations.value() ) {
				if ( namedMapKey( attributeOverride ) ) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean namedMapKey(AttributeOverride annotation) {
		return annotation.name().startsWith( "key." );
	}

	private Value createFormulatedValue(
			Value value,
			Collection collection,
			PersistentClass associatedClass,
			PersistentClass targetPropertyPersistentClass) {
		final Table mapKeyTable;
		// HHH-11005 - only if we are OneToMany and location of map key property is at a different level, need to add a select
		if ( !associatedClass.equals( targetPropertyPersistentClass ) ) {
			mapKeyTable = targetPropertyPersistentClass.getTable();
		}
		else {
			mapKeyTable = associatedClass.getTable();
		}

		if ( value instanceof Component ) {
			Component component = (Component) value;
			Component indexComponent = new Component( getBuildingContext(), collection );
			indexComponent.setComponentClassName( component.getComponentClassName() );
			for ( Property current : component.getProperties() ) {
				Property newProperty = new Property();
				newProperty.setCascade( current.getCascade() );
				newProperty.setValueGenerationStrategy( current.getValueGenerationStrategy() );
				newProperty.setInsertable( false );
				newProperty.setUpdateable( false );
				newProperty.setMetaAttributes( current.getMetaAttributes() );
				newProperty.setName( current.getName() );
				newProperty.setNaturalIdentifier( false );
				//newProperty.setOptimisticLocked( false );
				newProperty.setOptional( false );
				newProperty.setPersistentClass( current.getPersistentClass() );
				newProperty.setPropertyAccessorName( current.getPropertyAccessorName() );
				newProperty.setSelectable( current.isSelectable() );
				newProperty.setValue(
						createFormulatedValue( current.getValue(), collection, associatedClass, associatedClass )
				);
				indexComponent.addProperty( newProperty );
			}
			return indexComponent;
		}
		else if ( value instanceof BasicValue ) {
			final BasicValue sourceValue = (BasicValue) value;
			final DependantBasicValue dependantBasicValue = new DependantBasicValue(
					getBuildingContext(),
					mapKeyTable,
					sourceValue,
					false,
					false
			);

			final Selectable sourceValueColumn = sourceValue.getColumn();
			if ( sourceValueColumn instanceof Column ) {
				dependantBasicValue.addColumn( ( (Column) sourceValueColumn ).clone() );
			}
			else if ( sourceValueColumn instanceof Formula ) {
				dependantBasicValue.addFormula( new Formula( ( (Formula) sourceValueColumn ).getFormula() ) );
			}
			else {
				throw new AssertionFailure( "Unknown element column type : " + sourceValueColumn.getClass() );
			}

			return dependantBasicValue;
		}
		else if ( value instanceof SimpleValue ) {
			SimpleValue sourceValue = (SimpleValue) value;
			SimpleValue targetValue;
			if ( value instanceof ManyToOne ) {
				final ManyToOne sourceManyToOne = (ManyToOne) sourceValue;
				final ManyToOne targetManyToOne = new ManyToOne( getBuildingContext(), mapKeyTable );
				targetManyToOne.setFetchMode( FetchMode.DEFAULT );
				targetManyToOne.setLazy( true );
				//targetValue.setIgnoreNotFound( ); does not make sense for a map key
				targetManyToOne.setReferencedEntityName( sourceManyToOne.getReferencedEntityName() );
				targetValue = targetManyToOne;
			}
			else {
				targetValue = new BasicValue( getBuildingContext(), mapKeyTable );
				targetValue.copyTypeFrom( sourceValue );
			}
			for ( Selectable current : sourceValue.getSelectables() ) {
				if ( current instanceof Column ) {
					targetValue.addColumn( ( (Column) current ).clone() );
				}
				else if ( current instanceof Formula ) {
					targetValue.addFormula( new Formula( ( (Formula) current ).getFormula() ) );
				}
				else {
					throw new AssertionFailure( "Unknown element in column iterator: " + current.getClass() );
				}
			}
			return targetValue;
		}
		else {
			throw new AssertionFailure( "Unknown type encountered for map key: " + value.getClass() );
		}
	}
}
