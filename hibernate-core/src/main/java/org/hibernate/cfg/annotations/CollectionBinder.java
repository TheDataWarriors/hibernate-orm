/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg.annotations;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import jakarta.persistence.Access;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.MapKey;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyJoinColumn;
import jakarta.persistence.MapKeyJoinColumns;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;

import jakarta.persistence.UniqueConstraint;
import org.hibernate.AnnotationException;
import org.hibernate.AssertionFailure;
import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.Remove;
import org.hibernate.annotations.Bag;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CollectionId;
import org.hibernate.annotations.CollectionIdJavaType;
import org.hibernate.annotations.CollectionIdJdbcType;
import org.hibernate.annotations.CollectionIdJdbcTypeCode;
import org.hibernate.annotations.CollectionType;
import org.hibernate.annotations.Columns;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CompositeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterJoinTable;
import org.hibernate.annotations.FilterJoinTables;
import org.hibernate.annotations.Filters;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.hibernate.annotations.LazyGroup;
import org.hibernate.annotations.ListIndexBase;
import org.hibernate.annotations.ListIndexJavaType;
import org.hibernate.annotations.ListIndexJdbcType;
import org.hibernate.annotations.ListIndexJdbcTypeCode;
import org.hibernate.annotations.Loader;
import org.hibernate.annotations.ManyToAny;
import org.hibernate.annotations.MapKeyJavaType;
import org.hibernate.annotations.MapKeyJdbcType;
import org.hibernate.annotations.MapKeyJdbcTypeCode;
import org.hibernate.annotations.MapKeyMutability;
import org.hibernate.annotations.MapKeyType;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.annotations.OrderBy;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Persister;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLDeleteAll;
import org.hibernate.annotations.SQLInsert;
import org.hibernate.annotations.SQLUpdate;
import org.hibernate.annotations.SortComparator;
import org.hibernate.annotations.SortNatural;
import org.hibernate.annotations.Where;
import org.hibernate.annotations.WhereJoinTable;
import org.hibernate.annotations.common.reflection.ReflectionManager;
import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.boot.BootLogging;
import org.hibernate.boot.model.IdentifierGeneratorDefinition;
import org.hibernate.boot.model.TypeDefinition;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.InFlightMetadataCollector.CollectionTypeRegistrationDescriptor;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.AccessType;
import org.hibernate.cfg.AnnotatedClassType;
import org.hibernate.cfg.AnnotatedColumn;
import org.hibernate.cfg.AnnotatedJoinColumn;
import org.hibernate.cfg.AnnotationBinder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.CollectionPropertyHolder;
import org.hibernate.cfg.CollectionSecondPass;
import org.hibernate.cfg.ComponentPropertyHolder;
import org.hibernate.cfg.IndexColumn;
import org.hibernate.cfg.InheritanceState;
import org.hibernate.cfg.NotYetImplementedException;
import org.hibernate.cfg.PropertyData;
import org.hibernate.cfg.PropertyHolder;
import org.hibernate.cfg.PropertyHolderBuilder;
import org.hibernate.cfg.PropertyInferredData;
import org.hibernate.cfg.PropertyPreloadedData;
import org.hibernate.cfg.SecondPass;
import org.hibernate.cfg.WrappedInferredData;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.mapping.Any;
import org.hibernate.mapping.Backref;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.Join;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.hibernate.metamodel.CollectionClassification;
import org.hibernate.metamodel.spi.EmbeddableInstantiator;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.resource.beans.spi.ManagedBean;
import org.hibernate.resource.beans.spi.ManagedBeanRegistry;
import org.hibernate.usertype.CompositeUserType;
import org.hibernate.usertype.ParameterizedType;
import org.hibernate.usertype.UserCollectionType;

import org.jboss.logging.Logger;

import static jakarta.persistence.AccessType.PROPERTY;
import static org.hibernate.cfg.AnnotatedColumn.buildColumnFromAnnotation;
import static org.hibernate.cfg.AnnotatedColumn.buildColumnFromNoAnnotation;
import static org.hibernate.cfg.AnnotatedColumn.buildColumnsFromAnnotations;
import static org.hibernate.cfg.AnnotatedColumn.buildFormulaFromAnnotation;
import static org.hibernate.cfg.AnnotatedColumn.checkPropertyConsistency;
import static org.hibernate.cfg.AnnotatedJoinColumn.buildJoinColumnsWithDefaultColumnSuffix;
import static org.hibernate.cfg.AnnotatedJoinColumn.buildJoinTableJoinColumns;
import static org.hibernate.cfg.AnnotationBinder.fillComponent;
import static org.hibernate.cfg.AnnotationBinder.getCascadeStrategy;
import static org.hibernate.cfg.BinderHelper.PRIMITIVE_NAMES;
import static org.hibernate.cfg.BinderHelper.buildAnyValue;
import static org.hibernate.cfg.BinderHelper.createSyntheticPropertyReference;
import static org.hibernate.cfg.BinderHelper.getOverridableAnnotation;
import static org.hibernate.cfg.BinderHelper.getPath;
import static org.hibernate.cfg.BinderHelper.isEmptyAnnotationValue;
import static org.hibernate.cfg.BinderHelper.toAliasEntityMap;
import static org.hibernate.cfg.BinderHelper.toAliasTableMap;
import static org.hibernate.engine.spi.ExecuteUpdateResultCheckStyle.fromExternalName;
import static org.hibernate.internal.util.StringHelper.getNonEmptyOrConjunctionIfBothNonEmpty;
import static org.hibernate.internal.util.StringHelper.isEmpty;
import static org.hibernate.internal.util.StringHelper.isNotEmpty;
import static org.hibernate.internal.util.StringHelper.nullIfEmpty;
import static org.hibernate.internal.util.StringHelper.qualify;

/**
 * Base class for binding different types of collections to Hibernate configuration objects.
 *
 * @author inger
 * @author Emmanuel Bernard
 */
@SuppressWarnings("deprecation")
public abstract class CollectionBinder {
	private static final CoreMessageLogger LOG = Logger.getMessageLogger(CoreMessageLogger.class, CollectionBinder.class.getName());

	private static final List<Class<?>> INFERRED_CLASS_PRIORITY = List.of(
			List.class,
			java.util.SortedSet.class,
			java.util.Set.class,
			java.util.SortedMap.class,
			Map.class,
			java.util.Collection.class
	);

	private final MetadataBuildingContext buildingContext;
	private final Supplier<ManagedBean<? extends UserCollectionType>> customTypeBeanResolver;
	private final boolean isSortedCollection;

	protected Collection collection;
	protected String propertyName;
	PropertyHolder propertyHolder;
	private int batchSize;
	private String mappedBy;
	private XClass collectionElementType;
	private XClass targetEntity;
	private AnnotatedJoinColumn[] inverseJoinColumns;
	private String cascadeStrategy;
	private String cacheConcurrencyStrategy;
	private String cacheRegionName;
	private boolean oneToMany;
	protected IndexColumn indexColumn;
	protected boolean cascadeDeleteEnabled;
	protected String mapKeyPropertyName;
	private boolean insertable = true;
	private boolean updatable = true;
	private AnnotatedJoinColumn[] fkJoinColumns;
	private boolean isExplicitAssociationTable;
	private AnnotatedColumn[] elementColumns;
	private boolean isEmbedded;
	private XProperty property;
	private NotFoundAction notFoundAction;
	private TableBinder tableBinder;
	private AnnotatedColumn[] mapKeyColumns;
	private AnnotatedJoinColumn[] mapKeyManyToManyColumns;
	protected Map<String, IdentifierGeneratorDefinition> localGenerators;
	protected Map<XClass, InheritanceState> inheritanceStatePerClass;
	private XClass declaringClass;
	private boolean declaringClassSet;
	private AccessType accessType;
	private boolean hibernateExtensionMapping;

	private jakarta.persistence.OrderBy jpaOrderBy;
	private OrderBy sqlOrderBy;
	private SortNatural naturalSort;
	private SortComparator comparatorSort;

	private String explicitType;
	private final Properties explicitTypeParameters = new Properties();

	protected CollectionBinder(
			Supplier<ManagedBean<? extends UserCollectionType>> customTypeBeanResolver,
			boolean isSortedCollection,
			MetadataBuildingContext buildingContext) {
		this.customTypeBeanResolver = customTypeBeanResolver;
		this.isSortedCollection = isSortedCollection;
		this.buildingContext = buildingContext;
	}

	public static void bindCollection(
			PropertyHolder propertyHolder,
			Nullability nullability,
			PropertyData inferredData,
			Map<String, IdentifierGeneratorDefinition> classGenerators,
			EntityBinder entityBinder,
			boolean isIdentifierMapper,
			MetadataBuildingContext context,
			Map<XClass, InheritanceState> inheritanceStatePerClass,
			XProperty property,
			AnnotatedJoinColumn[] joinColumns) {

		final OneToMany oneToManyAnn = property.getAnnotation( OneToMany.class );
		final ManyToMany manyToManyAnn = property.getAnnotation( ManyToMany.class );
		final ElementCollection elementCollectionAnn = property.getAnnotation( ElementCollection.class );

		if ( ( oneToManyAnn != null || manyToManyAnn != null || elementCollectionAnn != null )
				&& isToManyAssociationWithinEmbeddableCollection( propertyHolder ) ) {
			String ann = oneToManyAnn!=null ? "'@OneToMany'" : manyToManyAnn!=null ? "'@ManyToMany'" : "'@ElementCollection'";
			throw new AnnotationException( "Property '" + getPath( propertyHolder, inferredData ) +
					"' belongs to an '@Embeddable' class that is contained in an '@ElementCollection' and may not be a " + ann );
		}

		if ( property.isAnnotationPresent( OrderColumn.class )
				&& manyToManyAnn != null && !manyToManyAnn.mappedBy().isEmpty() ) {
			throw new AnnotationException("Collection '" + getPath( propertyHolder, inferredData ) +
					"' is the unowned side of a bidirectional '@ManyToMany' and may not have an '@OrderColumn'");
		}

		final IndexColumn indexColumn = IndexColumn.fromAnnotations(
				property.getAnnotation( OrderColumn.class ),
				property.getAnnotation( org.hibernate.annotations.IndexColumn.class ),
				property.getAnnotation( ListIndexBase.class ),
				propertyHolder,
				inferredData,
				entityBinder.getSecondaryTables(),
				context
		);

		CollectionBinder collectionBinder = getCollectionBinder( property, hasMapKeyAnnotation( property ), context );
		collectionBinder.setIndexColumn( indexColumn );
		collectionBinder.setMapKey( property.getAnnotation( MapKey.class ) );
		collectionBinder.setPropertyName( inferredData.getPropertyName() );

		collectionBinder.setBatchSize( property.getAnnotation( BatchSize.class ) );

		collectionBinder.setJpaOrderBy( property.getAnnotation( jakarta.persistence.OrderBy.class ) );
		collectionBinder.setSqlOrderBy( getOverridableAnnotation( property, OrderBy.class, context ) );

		collectionBinder.setNaturalSort( property.getAnnotation( SortNatural.class ) );
		collectionBinder.setComparatorSort( property.getAnnotation( SortComparator.class ) );

		collectionBinder.setCache( property.getAnnotation( Cache.class ) );
		collectionBinder.setPropertyHolder(propertyHolder);
		Cascade hibernateCascade = property.getAnnotation( Cascade.class );
		NotFound notFound = property.getAnnotation( NotFound.class );
		collectionBinder.setNotFoundAction( notFound == null ? null : notFound.action() );

		collectionBinder.setElementType( inferredData.getProperty().getElementClass() );
		collectionBinder.setAccessType( inferredData.getDefaultAccess() );

		//do not use "element" if you are a JPA 2 @ElementCollection, only for legacy Hibernate mappings
		final PropertyData virtualProperty = property.isAnnotationPresent( ElementCollection.class )
				? inferredData
				: new WrappedInferredData( inferredData, "element" );
		final Comment comment = property.getAnnotation(Comment.class);
		final AnnotatedColumn[] elementColumns = elementColumns(
				propertyHolder,
				nullability,
				entityBinder,
				context,
				property,
				virtualProperty,
				comment
		);
		final JoinColumn[] joinKeyColumns = mapKeyColumns(
				propertyHolder,
				inferredData,
				entityBinder,
				context,
				property,
				collectionBinder,
				comment
		);
		final AnnotatedJoinColumn[] mapJoinColumns = buildJoinColumnsWithDefaultColumnSuffix(
				joinKeyColumns,
				comment,
				null,
				entityBinder.getSecondaryTables(),
				propertyHolder,
				inferredData.getPropertyName(),
				"_KEY",
				context
		);
		collectionBinder.setMapKeyManyToManyColumns( mapJoinColumns );

		//potential element
		collectionBinder.setEmbedded( property.isAnnotationPresent( Embedded.class ) );
		collectionBinder.setElementColumns( elementColumns );
		collectionBinder.setProperty(property);

		final String mappedBy = handleTargetEntity(
				propertyHolder,
				inferredData,
				context,
				property,
				joinColumns,
				oneToManyAnn,
				manyToManyAnn,
				elementCollectionAnn,
				collectionBinder,
				hibernateCascade
		);

		bindJoinedTableAssociation(
				property,
				context,
				entityBinder,
				collectionBinder,
				propertyHolder,
				inferredData,
				mappedBy
		);

		OnDelete onDeleteAnn = property.getAnnotation( OnDelete.class );
		boolean onDeleteCascade = onDeleteAnn != null && OnDeleteAction.CASCADE == onDeleteAnn.action();
		collectionBinder.setCascadeDeleteEnabled( onDeleteCascade );
		if ( isIdentifierMapper ) {
			collectionBinder.setInsertable( false );
			collectionBinder.setUpdatable( false );
		}
		if ( property.isAnnotationPresent( CollectionId.class ) ) { //do not compute the generators unless necessary
			HashMap<String, IdentifierGeneratorDefinition> localGenerators = new HashMap<>(classGenerators);
			localGenerators.putAll( AnnotationBinder.buildGenerators(property, context) );
			collectionBinder.setLocalGenerators( localGenerators );

		}
		collectionBinder.setInheritanceStatePerClass(inheritanceStatePerClass);
		collectionBinder.setDeclaringClass( inferredData.getDeclaringClass() );
		collectionBinder.bind();
	}

	private static String handleTargetEntity(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			MetadataBuildingContext context,
			XProperty property,
			AnnotatedJoinColumn[] joinColumns,
			OneToMany oneToManyAnn,
			ManyToMany manyToManyAnn,
			ElementCollection elementCollectionAnn,
			CollectionBinder collectionBinder,
			Cascade hibernateCascade) {

		//TODO enhance exception with @ManyToAny and @CollectionOfElements
		if ( oneToManyAnn != null && manyToManyAnn != null ) {
			throw new AnnotationException( "Property '" + getPath( propertyHolder, inferredData )
					+ "' is annotated both '@OneToMany' and '@ManyToMany'" );
		}
		String mappedBy = null;
		ReflectionManager reflectionManager = context.getBootstrapContext().getReflectionManager();
		if ( oneToManyAnn != null ) {
			for ( AnnotatedJoinColumn column : joinColumns) {
				if ( column.isSecondary() ) {
					throw new NotYetImplementedException( "Collections having FK in secondary table" );
				}
			}
			collectionBinder.setFkJoinColumns(joinColumns);
			mappedBy = oneToManyAnn.mappedBy();
			//noinspection unchecked
			collectionBinder.setTargetEntity( reflectionManager.toXClass( oneToManyAnn.targetEntity() ) );
			collectionBinder.setCascadeStrategy(
					getCascadeStrategy( oneToManyAnn.cascade(), hibernateCascade, oneToManyAnn.orphanRemoval(), false )
			);
			collectionBinder.setOneToMany( true );
		}
		else if ( elementCollectionAnn != null ) {
			for ( AnnotatedJoinColumn column : joinColumns) {
				if ( column.isSecondary() ) {
					throw new NotYetImplementedException( "Collections having FK in secondary table" );
				}
			}
			collectionBinder.setFkJoinColumns(joinColumns);
			mappedBy = "";
			final Class<?> targetElement = elementCollectionAnn.targetClass();
			collectionBinder.setTargetEntity( reflectionManager.toXClass( targetElement ) );
			//collectionBinder.setCascadeStrategy( getCascadeStrategy( embeddedCollectionAnn.cascade(), hibernateCascade ) );
			collectionBinder.setOneToMany( true );
		}
		else if ( manyToManyAnn != null ) {
			mappedBy = manyToManyAnn.mappedBy();
			//noinspection unchecked
			collectionBinder.setTargetEntity( reflectionManager.toXClass( manyToManyAnn.targetEntity() ) );
			collectionBinder.setCascadeStrategy(
					getCascadeStrategy( manyToManyAnn.cascade(), hibernateCascade, false, false )
			);
			collectionBinder.setOneToMany( false );
		}
		else if ( property.isAnnotationPresent( ManyToAny.class ) ) {
			mappedBy = "";
			collectionBinder.setTargetEntity( reflectionManager.toXClass( void.class ) );
			collectionBinder.setCascadeStrategy(
					getCascadeStrategy( null, hibernateCascade, false, false )
			);
			collectionBinder.setOneToMany( false );
		}
		collectionBinder.setMappedBy( mappedBy );
		return mappedBy;
	}

	private static boolean hasMapKeyAnnotation(XProperty property) {
		return property.isAnnotationPresent(MapKeyJavaType.class)
				|| property.isAnnotationPresent(MapKeyJdbcType.class)
				|| property.isAnnotationPresent(MapKeyJdbcTypeCode.class)
				|| property.isAnnotationPresent(MapKeyMutability.class)
				|| property.isAnnotationPresent(MapKey.class)
				|| property.isAnnotationPresent(MapKeyType.class);
	}

	private static boolean isToManyAssociationWithinEmbeddableCollection(PropertyHolder propertyHolder) {
		if(propertyHolder instanceof ComponentPropertyHolder) {
			ComponentPropertyHolder componentPropertyHolder = (ComponentPropertyHolder) propertyHolder;
			return componentPropertyHolder.isWithinElementCollection();
		}
		return false;
	}

	private static AnnotatedColumn[] elementColumns(
			PropertyHolder propertyHolder,
			Nullability nullability,
			EntityBinder entityBinder,
			MetadataBuildingContext context,
			XProperty property,
			PropertyData virtualProperty,
			Comment comment) {
		if ( property.isAnnotationPresent( jakarta.persistence.Column.class ) ) {
			return buildColumnFromAnnotation(
					property.getAnnotation( jakarta.persistence.Column.class ),
					comment,
					nullability,
					propertyHolder,
					virtualProperty,
					entityBinder.getSecondaryTables(),
					context
			);
		}
		else if ( property.isAnnotationPresent( Formula.class ) ) {
			return buildFormulaFromAnnotation(
					getOverridableAnnotation(property, Formula.class, context),
					comment,
					nullability,
					propertyHolder,
					virtualProperty,
					entityBinder.getSecondaryTables(),
					context
			);
		}
		else if ( property.isAnnotationPresent( Columns.class ) ) {
			return buildColumnsFromAnnotations(
					property.getAnnotation( Columns.class ).columns(),
					comment,
					nullability,
					propertyHolder,
					virtualProperty,
					entityBinder.getSecondaryTables(),
					context
			);
		}
		else {
			return buildColumnFromNoAnnotation(
					comment,
					nullability,
					propertyHolder,
					virtualProperty,
					entityBinder.getSecondaryTables(),
					context
			);
		}
	}

	private static JoinColumn[] mapKeyColumns(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			EntityBinder entityBinder,
			MetadataBuildingContext context,
			XProperty property,
			CollectionBinder collectionBinder,
			Comment comment) {

		final jakarta.persistence.Column[] keyColumns = property.isAnnotationPresent(MapKeyColumn.class)
				? new jakarta.persistence.Column[]{ new MapKeyColumnDelegator( property.getAnnotation(MapKeyColumn.class) ) }
				: null;

		final AnnotatedColumn[] mapColumns = buildColumnsFromAnnotations(
				keyColumns,
				comment,
				Nullability.FORCED_NOT_NULL,
				propertyHolder,
				inferredData,
				"_KEY",
				entityBinder.getSecondaryTables(),
				context
		);
		collectionBinder.setMapKeyColumns( mapColumns );

		if ( property.isAnnotationPresent( MapKeyJoinColumns.class ) ) {
			final MapKeyJoinColumn[] mapKeyJoinColumns = property.getAnnotation( MapKeyJoinColumns.class ).value();
			JoinColumn[] joinKeyColumns = new JoinColumn[mapKeyJoinColumns.length];
			int index = 0;
			for ( MapKeyJoinColumn joinColumn : mapKeyJoinColumns ) {
				joinKeyColumns[index] = new MapKeyJoinColumnDelegator( joinColumn );
				index++;
			}
			if ( property.isAnnotationPresent( MapKeyJoinColumn.class ) ) {
				throw new AnnotationException( "Property '" + getPath( propertyHolder, inferredData )
						+ "' is annotated both '@MapKeyJoinColumn' and '@MapKeyJoinColumns'" );
			}
			return joinKeyColumns;
		}
		else if ( property.isAnnotationPresent( MapKeyJoinColumn.class ) ) {
			return new JoinColumn[] {
					new MapKeyJoinColumnDelegator(
							property.getAnnotation(
									MapKeyJoinColumn.class
							)
					)
			};
		}
		return null;
	}

	private static void bindJoinedTableAssociation(
			XProperty property,
			MetadataBuildingContext buildingContext,
			EntityBinder entityBinder,
			CollectionBinder collectionBinder,
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			String mappedBy) {
		TableBinder associationTableBinder = new TableBinder();
		JoinColumn[] annJoins;
		JoinColumn[] annInverseJoins;
		JoinTable assocTable = propertyHolder.getJoinTable( property );
		CollectionTable collectionTable = property.getAnnotation( CollectionTable.class );
		if ( assocTable != null || collectionTable != null ) {

			final String catalog;
			final String schema;
			final String tableName;
			final UniqueConstraint[] uniqueConstraints;
			final JoinColumn[] joins;
			final JoinColumn[] inverseJoins;
			final jakarta.persistence.Index[] jpaIndexes;


			//JPA 2 has priority
			if ( collectionTable != null ) {
				catalog = collectionTable.catalog();
				schema = collectionTable.schema();
				tableName = collectionTable.name();
				uniqueConstraints = collectionTable.uniqueConstraints();
				joins = collectionTable.joinColumns();
				inverseJoins = null;
				jpaIndexes = collectionTable.indexes();
			}
			else {
				catalog = assocTable.catalog();
				schema = assocTable.schema();
				tableName = assocTable.name();
				uniqueConstraints = assocTable.uniqueConstraints();
				joins = assocTable.joinColumns();
				inverseJoins = assocTable.inverseJoinColumns();
				jpaIndexes = assocTable.indexes();
			}

			collectionBinder.setExplicitAssociationTable( true );
			if ( jpaIndexes != null && jpaIndexes.length > 0 ) {
				associationTableBinder.setJpaIndex( jpaIndexes );
			}
			if ( !isEmptyAnnotationValue( schema ) ) {
				associationTableBinder.setSchema( schema );
			}
			if ( !isEmptyAnnotationValue( catalog ) ) {
				associationTableBinder.setCatalog( catalog );
			}
			if ( !isEmptyAnnotationValue( tableName ) ) {
				associationTableBinder.setName( tableName );
			}
			associationTableBinder.setUniqueConstraints( uniqueConstraints );
			associationTableBinder.setJpaIndex( jpaIndexes );
			//set check constraint in the second pass
			annJoins = joins.length == 0 ? null : joins;
			annInverseJoins = inverseJoins == null || inverseJoins.length == 0 ? null : inverseJoins;
		}
		else {
			annJoins = null;
			annInverseJoins = null;
		}
		AnnotatedJoinColumn[] joinColumns = buildJoinTableJoinColumns(
				annJoins,
				entityBinder.getSecondaryTables(),
				propertyHolder,
				inferredData.getPropertyName(),
				mappedBy,
				buildingContext
		);
		AnnotatedJoinColumn[] inverseJoinColumns = buildJoinTableJoinColumns(
				annInverseJoins,
				entityBinder.getSecondaryTables(),
				propertyHolder,
				inferredData.getPropertyName(),
				mappedBy,
				buildingContext
		);
		associationTableBinder.setBuildingContext( buildingContext );
		collectionBinder.setTableBinder( associationTableBinder );
		collectionBinder.setJoinColumns( joinColumns );
		collectionBinder.setInverseJoinColumns( inverseJoinColumns );
	}

	protected MetadataBuildingContext getBuildingContext() {
		return buildingContext;
	}

	public Supplier<ManagedBean<? extends UserCollectionType>> getCustomTypeBeanResolver() {
		return customTypeBeanResolver;
	}

	public boolean isMap() {
		return false;
	}

	protected void setIsHibernateExtensionMapping(boolean hibernateExtensionMapping) {
		this.hibernateExtensionMapping = hibernateExtensionMapping;
	}

	protected boolean isHibernateExtensionMapping() {
		return hibernateExtensionMapping;
	}

	public void setUpdatable(boolean updatable) {
		this.updatable = updatable;
	}

	public void setInheritanceStatePerClass(Map<XClass, InheritanceState> inheritanceStatePerClass) {
		this.inheritanceStatePerClass = inheritanceStatePerClass;
	}

	public void setInsertable(boolean insertable) {
		this.insertable = insertable;
	}

	public void setCascadeStrategy(String cascadeStrategy) {
		this.cascadeStrategy = cascadeStrategy;
	}

	public void setAccessType(AccessType accessType) {
		this.accessType = accessType;
	}

	public void setInverseJoinColumns(AnnotatedJoinColumn[] inverseJoinColumns) {
		this.inverseJoinColumns = inverseJoinColumns;
	}

	public void setJoinColumns(AnnotatedJoinColumn[] joinColumns) {
		this.joinColumns = joinColumns;
	}

	private AnnotatedJoinColumn[] joinColumns;

	public void setPropertyHolder(PropertyHolder propertyHolder) {
		this.propertyHolder = propertyHolder;
	}

	public void setBatchSize(BatchSize batchSize) {
		this.batchSize = batchSize == null ? -1 : batchSize.size();
	}

	public void setJpaOrderBy(jakarta.persistence.OrderBy jpaOrderBy) {
		this.jpaOrderBy = jpaOrderBy;
	}

	public void setSqlOrderBy(OrderBy sqlOrderBy) {
		this.sqlOrderBy = sqlOrderBy;
	}

	public void setNaturalSort(SortNatural naturalSort) {
		this.naturalSort = naturalSort;
	}

	public void setComparatorSort(SortComparator comparatorSort) {
		this.comparatorSort = comparatorSort;
	}

	/**
	 * collection binder factory
	 */
	public static CollectionBinder getCollectionBinder(
			XProperty property,
			boolean isHibernateExtensionMapping,
			MetadataBuildingContext buildingContext) {

		final CollectionBinder binder;
		final CollectionType typeAnnotation = HCANNHelper.findAnnotation( property, CollectionType.class );
		if ( typeAnnotation != null ) {
			binder = createBinderFromCustomTypeAnnotation( property, typeAnnotation, buildingContext );
			// todo (6.0) - technically, these should no longer be needed
			binder.explicitType = typeAnnotation.type().getName();
			for ( Parameter param : typeAnnotation.parameters() ) {
				binder.explicitTypeParameters.setProperty( param.name(), param.value() );
			}
		}
		else {
			binder = createBinderAutomatically( property, buildingContext );
		}
		binder.setIsHibernateExtensionMapping( isHibernateExtensionMapping );
		return binder;
	}

	private static CollectionBinder createBinderAutomatically(XProperty property, MetadataBuildingContext buildingContext) {
		final CollectionClassification classification = determineCollectionClassification( property, buildingContext );
		final CollectionTypeRegistrationDescriptor typeRegistration = buildingContext.getMetadataCollector()
				.findCollectionTypeRegistration( classification );
		return typeRegistration != null
				? createBinderFromTypeRegistration( property, classification, typeRegistration, buildingContext )
				: createBinderFromProperty( property, buildingContext );
	}

	private static CollectionBinder createBinderFromTypeRegistration(
			XProperty property,
			CollectionClassification classification,
			CollectionTypeRegistrationDescriptor typeRegistration,
			MetadataBuildingContext buildingContext) {
		return createBinder(
				property,
				() -> createCustomType(
						property.getDeclaringClass().getName() + "#" + property.getName(),
						typeRegistration.getImplementation(),
						typeRegistration.getParameters(),
						buildingContext
				),
				classification,
				buildingContext
		);
	}

	private static ManagedBean<? extends UserCollectionType> createCustomType(
			String role,
			Class<? extends UserCollectionType> implementation,
			Properties parameters,
			MetadataBuildingContext buildingContext) {
		final StandardServiceRegistry serviceRegistry = buildingContext.getBuildingOptions().getServiceRegistry();
		final ManagedBeanRegistry beanRegistry = serviceRegistry.getService( ManagedBeanRegistry.class );
		if ( CollectionHelper.isNotEmpty( parameters ) ) {
			return beanRegistry.getBean( implementation );
		}
		else {
			// defined parameters...
			if ( ParameterizedType.class.isAssignableFrom( implementation ) ) {
				// because there are config parameters and the type is configurable, we need
				// a separate bean instance which means uniquely naming it
				final ManagedBean<? extends UserCollectionType> typeBean = beanRegistry.getBean( role, implementation );
				final UserCollectionType type = typeBean.getBeanInstance();
				( (ParameterizedType) type ).setParameterValues( parameters );
				return typeBean;
			}
			else {
				// log a "warning"
				BootLogging.BOOT_LOGGER.debugf(
						"Custom collection-type (`%s`) assigned to attribute (`%s`) does not implement `%s`, but its `@CollectionType` defined parameters",
						implementation.getName(),
						role,
						ParameterizedType.class.getName()
				);

				// but still return the bean - we can again use the no-config bean instance
				return beanRegistry.getBean( implementation );
			}
		}
	}

	private static CollectionBinder createBinderFromProperty(
			XProperty property,
			MetadataBuildingContext buildingContext) {
		final CollectionClassification classification = determineCollectionClassification( property, buildingContext );
		return createBinder( property, null, classification, buildingContext );
	}

	private static CollectionBinder createBinderFromCustomTypeAnnotation(
			XProperty property,
			CollectionType typeAnnotation,
			MetadataBuildingContext buildingContext) {
		determineSemanticJavaType( property );
		final ManagedBean<? extends UserCollectionType> customTypeBean
				= resolveCustomType( property, typeAnnotation, buildingContext );
		return createBinder(
				property,
				() -> customTypeBean,
				customTypeBean.getBeanInstance().getClassification(),
				buildingContext
		);
	}

	public static ManagedBean<? extends UserCollectionType> resolveCustomType(
			XProperty property,
			CollectionType typeAnnotation,
			MetadataBuildingContext buildingContext) {
		final ManagedBeanRegistry beanRegistry = buildingContext.getBootstrapContext()
				.getServiceRegistry()
				.getService( ManagedBeanRegistry.class );

		final Class<? extends UserCollectionType> typeImpl = typeAnnotation.type();
		if ( typeAnnotation.parameters().length == 0 ) {
			// no parameters - we can re-use a no-config bean instance
			return beanRegistry.getBean( typeImpl );
		}
		else {
			// defined parameters...
			final String attributeKey = property.getDeclaringClass().getName() + "#" + property.getName();

			if ( ParameterizedType.class.isAssignableFrom( typeImpl ) ) {
				// because there are config parameters and the type is configurable, we need
				// a separate bean instance which means uniquely naming it
				final ManagedBean<? extends UserCollectionType> typeBean = beanRegistry.getBean( attributeKey, typeImpl );
				final UserCollectionType type = typeBean.getBeanInstance();
				( (ParameterizedType) type ).setParameterValues( extractParameters( typeAnnotation ) );
				return typeBean;
			}
			else {
				// log a "warning"
				BootLogging.BOOT_LOGGER.debugf(
						"Custom collection-type (`%s`) assigned to attribute (`%s`) does not implement `%s`, but its `@CollectionType` defined parameters",
						typeImpl.getName(),
						attributeKey,
						ParameterizedType.class.getName()
				);

				// but still return the bean - we can again use the no-config bean instance
				return beanRegistry.getBean( typeImpl );
			}
		}
	}

	private static Properties extractParameters(CollectionType typeAnnotation) {
		final Parameter[] parameterAnnotations = typeAnnotation.parameters();
		final Properties configParams = new Properties( parameterAnnotations.length );
		for ( Parameter parameterAnnotation : parameterAnnotations ) {
			configParams.put( parameterAnnotation.name(), parameterAnnotation.value() );
		}
		return configParams;
	}

	private static CollectionBinder createBinder(
			XProperty property,
			Supplier<ManagedBean<? extends UserCollectionType>> customTypeBeanAccess,
			CollectionClassification classification,
			MetadataBuildingContext buildingContext) {
		switch ( classification ) {
			case ARRAY:
				return property.getElementClass().isPrimitive()
						? new PrimitiveArrayBinder( customTypeBeanAccess, buildingContext )
						: new ArrayBinder( customTypeBeanAccess, buildingContext );
			case BAG:
				return new BagBinder( customTypeBeanAccess, buildingContext );
			case ID_BAG:
				return new IdBagBinder( customTypeBeanAccess, buildingContext );
			case LIST:
				return new ListBinder( customTypeBeanAccess, buildingContext );
			case MAP:
			case ORDERED_MAP:
				return new MapBinder( customTypeBeanAccess, false, buildingContext );
			case SORTED_MAP:
				return new MapBinder( customTypeBeanAccess, true, buildingContext );
			case SET:
			case ORDERED_SET:
				return new SetBinder( customTypeBeanAccess, false, buildingContext );
			case SORTED_SET:
				return new SetBinder( customTypeBeanAccess, true, buildingContext );
			default:
				throw new AnnotationException(
						String.format(
								Locale.ROOT,
								"Unable to determine proper CollectionBinder (`%s) : %s.%s",
								classification,
								property.getDeclaringClass().getName(),
								property.getName()
						)
				);
		}
	}

	private static CollectionClassification determineCollectionClassification(
			XProperty property,
			MetadataBuildingContext buildingContext) {
		if ( property.isArray() ) {
			return CollectionClassification.ARRAY;
		}
		else if ( HCANNHelper.findAnnotation( property, Bag.class ) == null ) {
			return determineCollectionClassification( determineSemanticJavaType( property ), property, buildingContext );
		}
		else {
			final Class<?> collectionJavaType = property.getCollectionClass();
			if ( java.util.List.class.equals( collectionJavaType )
					|| java.util.Collection.class.equals( collectionJavaType ) ) {
				return CollectionClassification.BAG;
			}
			else {
				throw new MappingException(
						String.format(
								Locale.ROOT,
								"@Bag annotation encountered on an attribute `%s#%s` of type `%s`; only `%s` and `%s` are supported",
								property.getDeclaringClass().getName(),
								property.getName(),
								collectionJavaType.getName(),
								java.util.List.class.getName(),
								java.util.Collection.class.getName()
						)
				);
			}
		}
	}

	private static CollectionClassification determineCollectionClassification(
			Class<?> semanticJavaType,
			XProperty property,
			MetadataBuildingContext buildingContext) {
		if ( semanticJavaType.isArray() ) {
			return CollectionClassification.ARRAY;
		}
		else if ( property.isAnnotationPresent( CollectionId.class )
				|| property.isAnnotationPresent( CollectionIdJdbcType.class )
				|| property.isAnnotationPresent( CollectionIdJdbcTypeCode.class )
				|| property.isAnnotationPresent( CollectionIdJavaType.class ) ) {
			// explicitly an ID_BAG
			return CollectionClassification.ID_BAG;
		}
		else if ( java.util.List.class.isAssignableFrom( semanticJavaType ) ) {
			if ( property.isAnnotationPresent( OrderColumn.class )
					|| property.isAnnotationPresent( org.hibernate.annotations.IndexColumn.class )
					|| property.isAnnotationPresent( ListIndexBase.class )
					|| property.isAnnotationPresent( ListIndexJdbcType.class )
					|| property.isAnnotationPresent( ListIndexJdbcTypeCode.class )
					|| property.isAnnotationPresent( ListIndexJavaType.class ) ) {
				// it is implicitly a LIST because of presence of explicit List index config
				return CollectionClassification.LIST;
			}
			if ( property.isAnnotationPresent( jakarta.persistence.OrderBy.class )
					|| property.isAnnotationPresent( OrderBy.class ) ) {
				return CollectionClassification.BAG;
			}
			ManyToMany manyToMany = property.getAnnotation( ManyToMany.class );
			if ( manyToMany != null && ! isEmpty( manyToMany.mappedBy() ) ) {
				// We don't support @OrderColumn on the non-owning side of a many-to-many association.
				return CollectionClassification.BAG;
			}
			OneToMany oneToMany = property.getAnnotation( OneToMany.class );
			if ( oneToMany != null && ! isEmpty( oneToMany.mappedBy() ) ) {
				// Unowned to-many mappings are always considered BAG by default
				return CollectionClassification.BAG;
			}
			// otherwise, return the implicit classification for List attributes
			return buildingContext.getBuildingOptions().getMappingDefaults().getImplicitListClassification();
		}
		else if ( java.util.SortedSet.class.isAssignableFrom( semanticJavaType ) ) {
			return CollectionClassification.SORTED_SET;
		}
		else if ( java.util.Set.class.isAssignableFrom( semanticJavaType ) ) {
			return CollectionClassification.SET;
		}
		else if ( java.util.SortedMap.class.isAssignableFrom( semanticJavaType ) ) {
			return CollectionClassification.SORTED_MAP;
		}
		else if ( java.util.Map.class.isAssignableFrom( semanticJavaType ) ) {
			return CollectionClassification.MAP;
		}
		else if ( java.util.Collection.class.isAssignableFrom( semanticJavaType ) ) {
			if ( property.isAnnotationPresent( CollectionId.class ) ) {
				return CollectionClassification.ID_BAG;
			}
			else {
				return CollectionClassification.BAG;
			}
		}
		else {
			return null;
		}
	}

	private static Class<?> determineSemanticJavaType(XProperty property) {
		@SuppressWarnings("rawtypes")
		Class<? extends java.util.Collection> collectionClass = property.getCollectionClass();
		if ( collectionClass != null ) {
			return inferCollectionClassFromSubclass( collectionClass );
		}
		else {
			throw new AnnotationException(
					String.format(
							Locale.ROOT,
							"Property '%s.%s' is not a collection and may not be a '@OneToMany', '@ManyToMany', or '@ElementCollection'",
							property.getDeclaringClass().getName(),
							property.getName()
					)
			);
		}
	}

	private static Class<?> inferCollectionClassFromSubclass(Class<?> clazz) {
		for ( Class<?> priorityClass : INFERRED_CLASS_PRIORITY ) {
			if ( priorityClass.isAssignableFrom( clazz ) ) {
				return priorityClass;
			}
		}

		return null;
	}

	public void setMappedBy(String mappedBy) {
		this.mappedBy = mappedBy;
	}

	public void setTableBinder(TableBinder tableBinder) {
		this.tableBinder = tableBinder;
	}

	/**
	 * @deprecated : Use {@link #setElementType(XClass)} instead.
	 */
	@Deprecated(since = "6.1") @Remove
	public void setCollectionType(XClass collectionType) {
		// NOTE: really really badly named.  This is actually NOT the collection-type, but rather the collection-element-type!
		this.collectionElementType = collectionType;
	}

	public void setElementType(XClass collectionElementType) {
		this.collectionElementType = collectionElementType;
	}

	public void setTargetEntity(XClass targetEntity) {
		this.targetEntity = targetEntity;
	}

	protected abstract Collection createCollection(PersistentClass persistentClass);

	public Collection getCollection() {
		return collection;
	}

	public void setPropertyName(String propertyName) {
		this.propertyName = propertyName;
	}

	public void setDeclaringClass(XClass declaringClass) {
		this.declaringClass = declaringClass;
		this.declaringClassSet = true;
	}

	public void bind() {
		this.collection = createCollection( propertyHolder.getPersistentClass() );
		String role = qualify( propertyHolder.getPath(), propertyName );
		LOG.debugf( "Collection role: %s", role );
		collection.setRole( role );
		collection.setMappedByProperty( mappedBy );

		if ( property.isAnnotationPresent( MapKeyColumn.class )
			&& mapKeyPropertyName != null ) {
			throw new AnnotationException( "Collection '" + qualify( propertyHolder.getPath(), propertyName )
					+ "' is annotated both '@MapKey' and '@MapKeyColumn'" );
		}

		bindExplicitTypes();

		//set laziness
		defineFetchingStrategy();
		collection.setBatchSize( batchSize );

		collection.setMutable( !property.isAnnotationPresent( Immutable.class ) );

		//work on association
		boolean isMappedBy = !isEmptyAnnotationValue( mappedBy );

		bindOptimisticLock( isMappedBy );

		bindCustomPersister();

		applySortingAndOrdering( collection );

		bindCache();

		bindLoader();

		detectMappedByProblem( isMappedBy );

		collection.setInverse( isMappedBy );

		final InFlightMetadataCollector metadataCollector = buildingContext.getMetadataCollector();

		//TODO reduce tableBinder != null and oneToMany
		scheduleSecondPass( isMappedBy, metadataCollector );

		metadataCollector.addCollectionBinding( collection );

		bindProperty();
	}

	private void scheduleSecondPass(boolean isMappedBy, InFlightMetadataCollector metadataCollector) {
		//many to many may need some second pass information
		if ( !oneToMany && isMappedBy ) {
			metadataCollector.addMappedBy( getElementType().getName(), mappedBy, propertyName );
		}

		if ( inheritanceStatePerClass == null) {
			throw new AssertionFailure( "inheritanceStatePerClass not set" );
		}
		metadataCollector.addSecondPass(
				getSecondPass(
						fkJoinColumns,
						joinColumns,
						inverseJoinColumns,
						elementColumns,
						mapKeyColumns,
						mapKeyManyToManyColumns,
						isEmbedded,
						property,
						getElementType(),
						notFoundAction,
						oneToMany,
						tableBinder,
						buildingContext
				),
				!isMappedBy
		);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void bindCustomPersister() {
		Persister persisterAnn = property.getAnnotation( Persister.class );
		if ( persisterAnn != null ) {
			Class clazz = persisterAnn.impl();
			if ( !CollectionPersister.class.isAssignableFrom( clazz ) ) {
				throw new AnnotationException( "Persister class '" + clazz.getName()
						+ "' does not implement 'CollectionPersister'" );
			}
			collection.setCollectionPersisterClass( clazz );
		}
	}

	private void bindOptimisticLock(boolean isMappedBy) {
		final OptimisticLock lockAnn = property.getAnnotation( OptimisticLock.class );
		final boolean includeInOptimisticLockChecks = lockAnn != null ? !lockAnn.excluded() : !isMappedBy;
		collection.setOptimisticLocked( includeInOptimisticLockChecks );
	}

	private void bindCache() {
		//set cache
		if ( isNotEmpty( cacheConcurrencyStrategy ) ) {
			collection.setCacheConcurrencyStrategy( cacheConcurrencyStrategy );
			collection.setCacheRegionName( cacheRegionName );
		}
	}

	private void bindExplicitTypes() {
		// set explicit type information
		final InFlightMetadataCollector metadataCollector = buildingContext.getMetadataCollector();
		if ( explicitType != null ) {
			final TypeDefinition typeDef = metadataCollector.getTypeDefinition( explicitType );
			if ( typeDef == null ) {
				collection.setTypeName( explicitType );
				collection.setTypeParameters( explicitTypeParameters );
			}
			else {
				collection.setTypeName( typeDef.getTypeImplementorClass().getName() );
				collection.setTypeParameters( typeDef.getParameters() );
			}
		}
	}

	private void detectMappedByProblem(boolean isMappedBy) {
		if (isMappedBy
				&& ( property.isAnnotationPresent( JoinColumn.class )
					|| property.isAnnotationPresent( JoinColumns.class ) ) ) {
			throw new AnnotationException( "Association '"
					+ qualify( propertyHolder.getPath(), propertyName )
					+ "' is 'mappedBy' another entity and may not specify the '@JoinColumn'" );
		}

		if (isMappedBy
				&& propertyHolder.getJoinTable( property ) != null ) {
			throw new AnnotationException( "Association '"
					+ qualify( propertyHolder.getPath(), propertyName )
					+ "' is 'mappedBy' another entity and may not specify the '@JoinTable'" );
		}

		if (!isMappedBy
				&& oneToMany
				&& property.isAnnotationPresent( OnDelete.class )
				&& !property.isAnnotationPresent( JoinColumn.class )
				&& !property.isAnnotationPresent( JoinColumns.class )) {
			throw new AnnotationException( "Unidirectional '@OneToMany' association '"
					+ qualify( propertyHolder.getPath(), propertyName )
					+ "' is annotated '@OnDelete' and must explicitly specify a '@JoinColumn'" );
		}
	}

	private void bindProperty() {
		//property building
		PropertyBinder binder = new PropertyBinder();
		binder.setName( propertyName );
		binder.setValue( collection );
		binder.setCascade( cascadeStrategy );
		if ( cascadeStrategy != null && cascadeStrategy.contains( "delete-orphan" ) ) {
			collection.setOrphanDelete( true );
		}
		binder.setLazy( collection.isLazy() );
		final LazyGroup lazyGroupAnnotation = property.getAnnotation( LazyGroup.class );
		if ( lazyGroupAnnotation != null ) {
			binder.setLazyGroup( lazyGroupAnnotation.value() );
		}
		binder.setAccessType( accessType );
		binder.setProperty( property );
		binder.setInsertable( insertable );
		binder.setUpdatable( updatable );
		Property prop = binder.makeProperty();
		//we don't care about the join stuffs because the column is on the association table.
		if (! declaringClassSet) {
			throw new AssertionFailure( "DeclaringClass is not set in CollectionBinder while binding" );
		}
		propertyHolder.addProperty( prop, declaringClass );
	}

	private void bindLoader() {
		//SQL overriding

		SQLInsert sqlInsert = property.getAnnotation( SQLInsert.class );
		if ( sqlInsert != null ) {
			collection.setCustomSQLInsert(
					sqlInsert.sql().trim(),
					sqlInsert.callable(),
					fromExternalName( sqlInsert.check().toString().toLowerCase(Locale.ROOT) )
			);

		}
		SQLUpdate sqlUpdate = property.getAnnotation( SQLUpdate.class );
		if ( sqlUpdate != null ) {
			collection.setCustomSQLUpdate(
					sqlUpdate.sql(),
					sqlUpdate.callable(),
					fromExternalName( sqlUpdate.check().toString().toLowerCase(Locale.ROOT) )
			);
		}

		SQLDelete sqlDelete = property.getAnnotation( SQLDelete.class );
		if ( sqlDelete != null ) {
			collection.setCustomSQLDelete(
					sqlDelete.sql(),
					sqlDelete.callable(),
					fromExternalName( sqlDelete.check().toString().toLowerCase(Locale.ROOT) )
			);
		}

		SQLDeleteAll sqlDeleteAll = property.getAnnotation( SQLDeleteAll.class );
		if ( sqlDeleteAll != null ) {
			collection.setCustomSQLDeleteAll(
					sqlDeleteAll.sql(),
					sqlDeleteAll.callable(),
					fromExternalName( sqlDeleteAll.check().toString().toLowerCase(Locale.ROOT) )
			);
		}

		Loader loader = property.getAnnotation( Loader.class );
		if ( loader != null ) {
			collection.setLoaderName( loader.namedQuery() );
		}
	}

	private void applySortingAndOrdering(Collection collection) {

		if ( naturalSort != null && comparatorSort != null ) {
			throw buildIllegalSortCombination();
		}
		final boolean sorted = naturalSort != null || comparatorSort != null;
		final Class<? extends Comparator<?>> comparatorClass;
		if ( naturalSort != null ) {
			comparatorClass = null;
		}
		else if ( comparatorSort != null ) {
			comparatorClass = comparatorSort.value();
		}
		else {
			comparatorClass = null;
		}

		if ( jpaOrderBy != null && sqlOrderBy != null ) {
			throw buildIllegalOrderCombination();
		}
		boolean ordered = jpaOrderBy != null || sqlOrderBy != null;
		if ( ordered ) {
			// we can only apply the sql-based order by up front.  The jpa order by has to wait for second pass
			if ( sqlOrderBy != null ) {
				collection.setOrderBy( sqlOrderBy.clause() );
			}
		}

		final boolean isSorted = isSortedCollection || sorted;
		if ( isSorted && ordered ) {
			throw buildIllegalOrderAndSortCombination();
		}
		collection.setSorted( isSorted );
		instantiateComparator( collection, comparatorClass );
	}

	private void instantiateComparator(Collection collection, Class<? extends Comparator<?>> comparatorClass) {
		if ( comparatorClass != null ) {
			try {
				collection.setComparator( comparatorClass.newInstance() );
			}
			catch (Exception e) {
				throw new AnnotationException(
						String.format(
								"Could not instantiate comparator class '%s' for collection '%s'",
								comparatorClass.getName(),
								safeCollectionRole()
						),
						e
				);
			}
		}
	}

	private AnnotationException buildIllegalOrderCombination() {
		return new AnnotationException(
				String.format(
						Locale.ROOT,
						"Collection '%s' is annotated both '@%s' and '@%s'",
						safeCollectionRole(),
						jakarta.persistence.OrderBy.class.getName(),
						OrderBy.class.getName()
				)
		);
	}

	private AnnotationException buildIllegalOrderAndSortCombination() {
		throw new AnnotationException(
				String.format(
						Locale.ROOT,
						"Collection '%s' is both sorted and ordered (only one of '@%s', '@%s', '@%s', and '@%s' may be used)",
						safeCollectionRole(),
						jakarta.persistence.OrderBy.class.getName(),
						OrderBy.class.getName(),
						SortComparator.class.getName(),
						SortNatural.class.getName()
				)
		);
	}

	private AnnotationException buildIllegalSortCombination() {
		return new AnnotationException(
				String.format(
						"Collection '%s' is annotated both '@%s' and '@%s'",
						safeCollectionRole(),
						SortNatural.class.getName(),
						SortComparator.class.getName()
				)
		);
	}

	private void defineFetchingStrategy() {
		LazyCollection lazy = property.getAnnotation( LazyCollection.class );
		Fetch fetch = property.getAnnotation( Fetch.class );
		OneToMany oneToMany = property.getAnnotation( OneToMany.class );
		ManyToMany manyToMany = property.getAnnotation( ManyToMany.class );
		ElementCollection elementCollection = property.getAnnotation( ElementCollection.class );
		ManyToAny manyToAny = property.getAnnotation( ManyToAny.class );
		NotFound notFound = property.getAnnotation( NotFound.class );

		FetchType fetchType;
		if ( oneToMany != null ) {
			fetchType = oneToMany.fetch();
		}
		else if ( manyToMany != null ) {
			fetchType = manyToMany.fetch();
		}
		else if ( elementCollection != null ) {
			fetchType = elementCollection.fetch();
		}
		else if ( manyToAny != null ) {
			fetchType = FetchType.LAZY;
		}
		else {
			throw new AssertionFailure(
					"Define fetch strategy on a property not annotated with @ManyToOne nor @OneToMany nor @CollectionOfElements"
			);
		}
		if ( notFound != null ) {
			collection.setLazy( false );

			if ( lazy != null ) {
				collection.setExtraLazy( lazy.value() == LazyCollectionOption.EXTRA );
			}

			if ( fetch != null ) {
				if ( fetch.value() != null ) {
					collection.setFetchMode( fetch.value().getHibernateFetchMode() );
					if ( fetch.value() == org.hibernate.annotations.FetchMode.SUBSELECT ) {
						collection.setSubselectLoadable( true );
						collection.getOwner().setSubselectLoadableCollections( true );
					}
				}
			}
			else {
				collection.setFetchMode( AnnotationBinder.getFetchMode( fetchType ) );
			}
		}
		else {
			if ( lazy != null ) {
				collection.setLazy( !( lazy.value() == LazyCollectionOption.FALSE ) );
				collection.setExtraLazy( lazy.value() == LazyCollectionOption.EXTRA );
			}
			else {
				collection.setLazy( fetchType == FetchType.LAZY );
				collection.setExtraLazy( false );
			}
			if ( fetch != null ) {
				if ( fetch.value() == org.hibernate.annotations.FetchMode.JOIN ) {
					collection.setFetchMode( FetchMode.JOIN );
					collection.setLazy( false );
				}
				else if ( fetch.value() == org.hibernate.annotations.FetchMode.SELECT ) {
					collection.setFetchMode( FetchMode.SELECT );
				}
				else if ( fetch.value() == org.hibernate.annotations.FetchMode.SUBSELECT ) {
					collection.setFetchMode( FetchMode.SELECT );
					collection.setSubselectLoadable( true );
					collection.getOwner().setSubselectLoadableCollections( true );
				}
				else {
					throw new AssertionFailure( "Unknown FetchMode: " + fetch.value() );
				}
			}
			else {
				collection.setFetchMode( AnnotationBinder.getFetchMode( fetchType ) );
			}
		}
	}

	private XClass getElementType() {
		if ( AnnotationBinder.isDefault( targetEntity, buildingContext ) ) {
			if ( collectionElementType != null ) {
				return collectionElementType;
			}
			else {
				throw new AnnotationException( "Collection '" + safeCollectionRole()
						+ "' is declared with a raw type and has an explicit 'targetEntity'" );
			}
		}
		else {
			return targetEntity;
		}
	}

	public SecondPass getSecondPass(
			final AnnotatedJoinColumn[] fkJoinColumns,
			final AnnotatedJoinColumn[] keyColumns,
			final AnnotatedJoinColumn[] inverseColumns,
			final AnnotatedColumn[] elementColumns,
			final AnnotatedColumn[] mapKeyColumns,
			final AnnotatedJoinColumn[] mapKeyManyToManyColumns,
			final boolean isEmbedded,
			final XProperty property,
			final XClass elementType,
			final NotFoundAction notFoundAction,
			final boolean unique,
			final TableBinder assocTableBinder,
			final MetadataBuildingContext buildingContext) {
		return new CollectionSecondPass( buildingContext, collection ) {
			@Override
			public void secondPass(Map<String, PersistentClass> persistentClasses) throws MappingException {
				bindStarToManySecondPass(
						persistentClasses,
						elementType,
						fkJoinColumns,
						keyColumns,
						inverseColumns,
						elementColumns,
						isEmbedded,
						property,
						unique,
						assocTableBinder,
						notFoundAction,
						buildingContext
				);
			}
		};
	}

	/**
	 * return true if it's a Fk, false if it's an association table
	 */
	protected boolean bindStarToManySecondPass(
			Map<String, PersistentClass> persistentClasses,
			XClass elementType,
			AnnotatedJoinColumn[] fkJoinColumns,
			AnnotatedJoinColumn[] keyColumns,
			AnnotatedJoinColumn[] inverseColumns,
			AnnotatedColumn[] elementColumns,
			boolean isEmbedded,
			XProperty property,
			boolean unique,
			TableBinder associationTableBinder,
			NotFoundAction notFoundAction,
			MetadataBuildingContext buildingContext) {
		PersistentClass persistentClass = persistentClasses.get( elementType.getName() );
		boolean reversePropertyInJoin = false;
		if ( persistentClass != null && isNotEmpty( mappedBy ) ) {
			try {
				reversePropertyInJoin =
						0 != persistentClass.getJoinNumber( persistentClass.getRecursiveProperty( mappedBy ) );
			}
			catch (MappingException e) {
				throw new AnnotationException( "Collection '" + safeCollectionRole()
						+ "' is 'mappedBy' a property named '" + mappedBy
						+ "' which does not exist in the target entity '" + elementType.getName() + "'" );
			}
		}
		if ( persistentClass != null
				&& !reversePropertyInJoin
				&& oneToMany
				&& !this.isExplicitAssociationTable
				&& ( joinColumns[0].isImplicit() && !isEmptyAnnotationValue( this.mappedBy ) //implicit @JoinColumn
				|| !fkJoinColumns[0].isImplicit() ) //this is an explicit @JoinColumn
				) {
			//this is a Foreign key
			bindOneToManySecondPass(
					getCollection(),
					persistentClasses,
					fkJoinColumns,
					elementType,
					cascadeDeleteEnabled,
					notFoundAction,
					buildingContext,
					inheritanceStatePerClass
			);
			return true;
		}
		else {
			//this is an association table
			bindManyToManySecondPass(
					this.collection,
					persistentClasses,
					keyColumns,
					inverseColumns,
					elementColumns,
					isEmbedded,
					elementType,
					notFoundAction,
					unique,
					cascadeDeleteEnabled,
					associationTableBinder,
					property,
					propertyHolder,
					buildingContext
			);
			return false;
		}
	}

	protected void bindOneToManySecondPass(
			Collection collection,
			Map<String, PersistentClass> persistentClasses,
			AnnotatedJoinColumn[] fkJoinColumns,
			XClass collectionType,
			boolean cascadeDeleteEnabled,
			NotFoundAction notFoundAction,
			MetadataBuildingContext buildingContext,
			Map<XClass, InheritanceState> inheritanceStatePerClass) {

		if ( LOG.isDebugEnabled() ) {
			LOG.debugf( "Binding a OneToMany: %s.%s through a foreign key", propertyHolder.getEntityName(), propertyName );
		}
		if ( buildingContext == null ) {
			throw new AssertionFailure(
					"CollectionSecondPass for oneToMany should not be called with null mappings"
			);
		}
		org.hibernate.mapping.OneToMany oneToMany =
				new org.hibernate.mapping.OneToMany( buildingContext, collection.getOwner() );
		collection.setElement( oneToMany );
		oneToMany.setReferencedEntityName( collectionType.getName() );
		oneToMany.setNotFoundAction( notFoundAction );

		String assocClass = oneToMany.getReferencedEntityName();
		PersistentClass associatedClass = persistentClasses.get( assocClass );
		handleJpaOrderBy( collection, associatedClass );
		Map<String, Join> joins = buildingContext.getMetadataCollector().getJoins( assocClass );
		if ( associatedClass == null ) {
			throw new MappingException(
					String.format("Association [%s] for entity [%s] references unmapped class [%s]",
							propertyName, propertyHolder.getClassName(), assocClass)
			);
		}
		oneToMany.setAssociatedClass( associatedClass );
		for (AnnotatedJoinColumn column : fkJoinColumns) {
			column.setPersistentClass( associatedClass, joins, inheritanceStatePerClass );
			column.setJoins( joins );
			collection.setCollectionTable( column.getTable() );
		}
		if ( LOG.isDebugEnabled() ) {
			LOG.debugf( "Mapping collection: %s -> %s", collection.getRole(), collection.getCollectionTable().getName() );
		}
		bindFilters( false );
		handleWhere( false );

		bindCollectionSecondPass(
				collection,
				null,
				fkJoinColumns,
				cascadeDeleteEnabled,
				property,
				propertyHolder,
				buildingContext
		);

		if ( !collection.isInverse()
				&& !collection.getKey().isNullable() ) {
			// for non-inverse one-to-many, with a not-null fk, add a backref!
			String entityName = oneToMany.getReferencedEntityName();
			PersistentClass referenced = buildingContext.getMetadataCollector().getEntityBinding( entityName );
			Backref prop = new Backref();
			prop.setName( '_' + fkJoinColumns[0].getPropertyName() + '_' + fkJoinColumns[0].getLogicalColumnName() + "Backref" );
			prop.setUpdateable( false );
			prop.setSelectable( false );
			prop.setCollectionRole( collection.getRole() );
			prop.setEntityName( collection.getOwner().getEntityName() );
			prop.setValue( collection.getKey() );
			referenced.addProperty( prop );
		}
	}

	private void handleJpaOrderBy(Collection collection, PersistentClass associatedClass) {
		if ( jpaOrderBy != null ) {
			final String orderByFragment = buildOrderByClauseFromHql( jpaOrderBy.value(), associatedClass );
			if ( isNotEmpty( orderByFragment ) ) {
				collection.setOrderBy( orderByFragment );
			}
		}
	}

	private void bindFilters(boolean hasAssociationTable) {
		Filter simpleFilter = property.getAnnotation( Filter.class );
		//set filtering
		//test incompatible choices
		//if ( StringHelper.isNotEmpty( where ) ) collection.setWhere( where );
		if ( simpleFilter != null ) {
			addFilter( hasAssociationTable, simpleFilter );
		}
		Filters filters = getOverridableAnnotation( property, Filters.class, buildingContext );
		if ( filters != null ) {
			for ( Filter filter : filters.value() ) {
				addFilter( hasAssociationTable, filter );
			}
		}
		FilterJoinTable simpleFilterJoinTable = property.getAnnotation( FilterJoinTable.class );
		if ( simpleFilterJoinTable != null ) {
			addFilter( hasAssociationTable, simpleFilterJoinTable );
		}
		FilterJoinTables filterJoinTables = property.getAnnotation( FilterJoinTables.class );
		if ( filterJoinTables != null ) {
			for ( FilterJoinTable filter : filterJoinTables.value() ) {
				addFilter( hasAssociationTable, filter );
			}
		}
	}

	private void addFilter(boolean hasAssociationTable, Filter filter) {
		if (hasAssociationTable) {
			collection.addManyToManyFilter(
					filter.name(),
					getCondition(filter),
					filter.deduceAliasInjectionPoints(),
					toAliasTableMap( filter.aliases() ),
					toAliasEntityMap( filter.aliases() )
			);
		}
		else {
			collection.addFilter(
					filter.name(),
					getCondition(filter),
					filter.deduceAliasInjectionPoints(),
					toAliasTableMap( filter.aliases() ),
					toAliasEntityMap( filter.aliases() )
			);
		}
	}

	private void handleWhere(boolean hasAssociationTable) {

		final boolean useEntityWhereClauseForCollections = ConfigurationHelper.getBoolean(
				AvailableSettings.USE_ENTITY_WHERE_CLAUSE_FOR_COLLECTIONS,
				buildingContext
						.getBuildingOptions()
						.getServiceRegistry()
						.getService( ConfigurationService.class )
						.getSettings(),
				true
		);

		// There are 2 possible sources of "where" clauses that apply to the associated entity table:
		// 1) from the associated entity mapping; i.e., @Entity @Where(clause="...")
		//    (ignored if useEntityWhereClauseForCollections == false)
		// 2) from the collection mapping;
		//    for one-to-many, e.g., @OneToMany @JoinColumn @Where(clause="...") public Set<Rating> getRatings();
		//    for many-to-many e.g., @ManyToMany @Where(clause="...") public Set<Rating> getRatings();
		String whereOnClassClause = null;
		if ( useEntityWhereClauseForCollections && property.getElementClass() != null ) {
			Where whereOnClass = getOverridableAnnotation( property.getElementClass(), Where.class, getBuildingContext() );
			if ( whereOnClass != null ) {
				whereOnClassClause = whereOnClass.clause();
			}
		}
		Where whereOnCollection = getOverridableAnnotation( property, Where.class, getBuildingContext() );
		String whereOnCollectionClause = null;
		if ( whereOnCollection != null ) {
			whereOnCollectionClause = whereOnCollection.clause();
		}
		final String whereClause = getNonEmptyOrConjunctionIfBothNonEmpty(
				whereOnClassClause,
				whereOnCollectionClause
		);
		if (hasAssociationTable) {
			// A many-to-many association has an association (join) table
			// Collection#setManytoManyWhere is used to set the "where" clause that applies to
			// to the many-to-many associated entity table (not the join table).
			collection.setManyToManyWhere( whereClause );
		}
		else {
			// A one-to-many association does not have an association (join) table.
			// Collection#setWhere is used to set the "where" clause that applies to the collection table
			// (which is the associated entity table for a one-to-many association).
			collection.setWhere( whereClause );
		}

		WhereJoinTable whereJoinTable = property.getAnnotation( WhereJoinTable.class );
		String whereJoinTableClause = whereJoinTable == null ? null : whereJoinTable.clause();
		if ( isNotEmpty( whereJoinTableClause ) ) {
			if (hasAssociationTable) {
				// This is a many-to-many association.
				// Collection#setWhere is used to set the "where" clause that applies to the collection table
				// (which is the join table for a many-to-many association).
				collection.setWhere( whereJoinTableClause );
			}
			else {
				throw new AnnotationException(
						"Collection '" + qualify( propertyHolder.getPath(), propertyName )
								+ "' is an association with no join table and may not have a 'WhereJoinTable'"
				);
			}
		}
	}

	private void addFilter(boolean hasAssociationTable, FilterJoinTable filter) {
		if ( hasAssociationTable ) {
			final String condition;
			final String name = filter.name();
			if ( isEmpty( filter.condition() ) ) {
				final FilterDefinition definition = buildingContext.getMetadataCollector().getFilterDefinition( name );
				if ( definition == null ) {
					throw new AnnotationException( "Collection '" + qualify( propertyHolder.getPath(), propertyName )
							+ "' has a '@FilterJoinTable' for an undefined filter named '" + name + "'" );
				}
				condition = definition.getDefaultFilterCondition();
				if ( isEmpty( condition ) ) {
					throw new AnnotationException( "Collection '" + qualify( propertyHolder.getPath(), propertyName )
							+ "' has a '@FilterJoinTable' with no 'condition' and no default condition was given by the '@FilterDef' named '"
							+ name + "'");
				}
			}
			else {
				condition = filter.condition();
			}
			collection.addFilter(
					name,
					condition,
					filter.deduceAliasInjectionPoints(),
					toAliasTableMap( filter.aliases() ),
					toAliasEntityMap( filter.aliases() )
			);
		}
		else {
			throw new AnnotationException( "Collection '" + qualify( propertyHolder.getPath(), propertyName )
					+ "' is an association with no join table and may not have a '@FilterJoinTable'" );
		}
	}

	private String getCondition(Filter filter) {
		//set filtering
		final String condition = filter.condition();
		if ( isEmptyAnnotationValue( condition ) ) {
			final String name = filter.name();
			final FilterDefinition definition = buildingContext.getMetadataCollector().getFilterDefinition( name );
			if ( definition == null ) {
				throw new AnnotationException( "Collection '" + qualify( propertyHolder.getPath(), propertyName )
						+ "' has a '@Filter' for an undefined filter named '" + name + "'" );
			}
			final String defaultCondition = definition.getDefaultFilterCondition();
			if ( isEmpty( defaultCondition ) ) {
				throw new AnnotationException( "Collection '" + qualify( propertyHolder.getPath(), propertyName ) +
						"' has a '@Filter' with no 'condition' and no default condition was given by the '@FilterDef' named '"
						+ name + "'" );
			}
			return defaultCondition;
		}
		else {
			return condition;
		}
	}

	public void setCache(Cache cacheAnn) {
		if ( cacheAnn != null ) {
			cacheRegionName = isEmptyAnnotationValue( cacheAnn.region() ) ? null : cacheAnn.region();
			cacheConcurrencyStrategy = EntityBinder.getCacheConcurrencyStrategy( cacheAnn.usage() );
		}
		else {
			cacheConcurrencyStrategy = null;
			cacheRegionName = null;
		}
	}

	public void setOneToMany(boolean oneToMany) {
		this.oneToMany = oneToMany;
	}

	public void setIndexColumn(IndexColumn indexColumn) {
		this.indexColumn = indexColumn;
	}

	public void setMapKey(MapKey key) {
		if ( key != null ) {
			mapKeyPropertyName = key.name();
		}
	}

	private static String buildOrderByClauseFromHql(String orderByFragment, PersistentClass associatedClass) {
		if ( orderByFragment != null ) {
			if ( orderByFragment.length() == 0 ) {
				//order by id
				return buildOrderById( associatedClass, " asc" );
			}
			else if ( "desc".equals( orderByFragment ) ) {
				return buildOrderById( associatedClass, " desc" );
			}
		}
		return orderByFragment;
	}

	private static String buildOrderById(PersistentClass associatedClass, String order) {
		final StringBuilder sb = new StringBuilder();
		for ( Selectable selectable: associatedClass.getIdentifier().getSelectables() ) {
			sb.append( selectable.getText() );
			sb.append( order );
			sb.append( ", " );
		}
		sb.setLength( sb.length() - 2 );
		return sb.toString();
	}

	public static String adjustUserSuppliedValueCollectionOrderingFragment(String orderByFragment) {
		if ( orderByFragment != null ) {
			orderByFragment = orderByFragment.trim();
			if ( orderByFragment.length() == 0 || orderByFragment.equalsIgnoreCase( "asc" ) ) {
				// This indicates something like either:
				//		`@OrderBy()`
				//		`@OrderBy("asc")
				//
				// JPA says this should indicate an ascending natural ordering of the elements - id for
				//		entity associations or the value(s) for "element collections"
				return "$element$ asc";
			}
			else if ( orderByFragment.equalsIgnoreCase( "desc" ) ) {
				// This indicates:
				//		`@OrderBy("desc")`
				//
				// JPA says this should indicate a descending natural ordering of the elements - id for
				//		entity associations or the value(s) for "element collections"
				return "$element$ desc";
			}
		}

		return orderByFragment;
	}

	private static DependantValue buildCollectionKey(
			Collection collValue,
			AnnotatedJoinColumn[] joinColumns,
			boolean cascadeDeleteEnabled,
			boolean noConstraintByDefault,
			XProperty property,
			PropertyHolder propertyHolder,
			MetadataBuildingContext buildingContext) {

		//give a chance to override the referenced property name
		//has to do that here because the referencedProperty creation happens in a FKSecondPass for Many to one yuk!
		if ( joinColumns.length > 0 && isNotEmpty( joinColumns[0].getMappedBy() ) ) {
			String entityName = joinColumns[0].getManyToManyOwnerSideEntityName() != null ?
					"inverse__" + joinColumns[0].getManyToManyOwnerSideEntityName() :
					joinColumns[0].getPropertyHolder().getEntityName();
			InFlightMetadataCollector metadataCollector = buildingContext.getMetadataCollector();
			String propRef = metadataCollector.getPropertyReferencedAssociation(
					entityName,
					joinColumns[0].getMappedBy()
			);
			if ( propRef != null ) {
				collValue.setReferencedPropertyName( propRef );
				metadataCollector.addPropertyReference( collValue.getOwnerEntityName(), propRef );
			}
		}

		String propRef = collValue.getReferencedPropertyName();
		//binding key reference using column
		KeyValue keyVal = propRef == null
				? collValue.getOwner().getIdentifier()
				: (KeyValue) collValue.getOwner().getReferencedProperty(propRef).getValue();

		DependantValue key = new DependantValue( buildingContext, collValue.getCollectionTable(), keyVal );
		key.setTypeName( null );
		checkPropertyConsistency( joinColumns, collValue.getOwnerEntityName() );
		key.setNullable( joinColumns.length == 0 || joinColumns[0].isNullable() );
		key.setUpdateable( joinColumns.length == 0 || joinColumns[0].isUpdatable() );
		key.setCascadeDeleteEnabled( cascadeDeleteEnabled );
		collValue.setKey( key );

		if ( property != null ) {
			final ForeignKey fk = property.getAnnotation( ForeignKey.class );
			if ( fk != null && !isEmptyAnnotationValue( fk.name() ) ) {
				key.setForeignKeyName( fk.name() );
			}
			else {
				final CollectionTable collectionTableAnn = property.getAnnotation( CollectionTable.class );
				if ( collectionTableAnn != null ) {
					if ( collectionTableAnn.foreignKey().value() == ConstraintMode.NO_CONSTRAINT
							|| collectionTableAnn.foreignKey().value() == ConstraintMode.PROVIDER_DEFAULT && noConstraintByDefault ) {
						key.disableForeignKey();
					}
					else {
						key.setForeignKeyName( nullIfEmpty( collectionTableAnn.foreignKey().name() ) );
						key.setForeignKeyDefinition( nullIfEmpty( collectionTableAnn.foreignKey().foreignKeyDefinition() ) );
						if ( key.getForeignKeyName() == null &&
							key.getForeignKeyDefinition() == null &&
							collectionTableAnn.joinColumns().length == 1 ) {
							JoinColumn joinColumn = collectionTableAnn.joinColumns()[0];
							key.setForeignKeyName( nullIfEmpty( joinColumn.foreignKey().name() ) );
							key.setForeignKeyDefinition( nullIfEmpty( joinColumn.foreignKey().foreignKeyDefinition() ) );
						}
					}
				}
				else {
					final JoinTable joinTableAnn = property.getAnnotation( JoinTable.class );
					if ( joinTableAnn != null ) {
						String foreignKeyName = joinTableAnn.foreignKey().name();
						String foreignKeyDefinition = joinTableAnn.foreignKey().foreignKeyDefinition();
						ConstraintMode foreignKeyValue = joinTableAnn.foreignKey().value();
						if ( joinTableAnn.joinColumns().length != 0 ) {
							final JoinColumn joinColumnAnn = joinTableAnn.joinColumns()[0];
							if ( foreignKeyName != null && foreignKeyName.isEmpty() ) {
								foreignKeyName = joinColumnAnn.foreignKey().name();
								foreignKeyDefinition = joinColumnAnn.foreignKey().foreignKeyDefinition();
							}
							if ( foreignKeyValue != ConstraintMode.NO_CONSTRAINT ) {
								foreignKeyValue = joinColumnAnn.foreignKey().value();
							}
						}
						if ( foreignKeyValue == ConstraintMode.NO_CONSTRAINT
								|| foreignKeyValue == ConstraintMode.PROVIDER_DEFAULT && noConstraintByDefault ) {
							key.disableForeignKey();
						}
						else {
							key.setForeignKeyName( nullIfEmpty( foreignKeyName ) );
							key.setForeignKeyDefinition( nullIfEmpty( foreignKeyDefinition ) );
						}
					}
					else {
						final jakarta.persistence.ForeignKey fkOverride = propertyHolder.getOverriddenForeignKey(
								qualify( propertyHolder.getPath(), property.getName() )
						);
						if ( fkOverride != null && ( fkOverride.value() == ConstraintMode.NO_CONSTRAINT ||
								fkOverride.value() == ConstraintMode.PROVIDER_DEFAULT && noConstraintByDefault ) ) {
							key.disableForeignKey();
						}
						else if ( fkOverride != null ) {
							key.setForeignKeyName( nullIfEmpty( fkOverride.name() ) );
							key.setForeignKeyDefinition( nullIfEmpty( fkOverride.foreignKeyDefinition() ) );
						}
						else {
							final OneToMany oneToManyAnn = property.getAnnotation( OneToMany.class );
							final OnDelete onDeleteAnn = property.getAnnotation( OnDelete.class );
							if ( oneToManyAnn != null && !oneToManyAnn.mappedBy().isEmpty()
									&& ( onDeleteAnn == null || onDeleteAnn.action() != OnDeleteAction.CASCADE ) ) {
								// foreign key should be up to @ManyToOne side
								// @OnDelete generate "on delete cascade" foreign key
								key.disableForeignKey();
							}
							else {
								final JoinColumn joinColumnAnn = property.getAnnotation( JoinColumn.class );
								if ( joinColumnAnn != null ) {
									if ( joinColumnAnn.foreignKey().value() == ConstraintMode.NO_CONSTRAINT
											|| joinColumnAnn.foreignKey().value() == ConstraintMode.PROVIDER_DEFAULT && noConstraintByDefault ) {
										key.disableForeignKey();
									}
									else {
										key.setForeignKeyName( nullIfEmpty( joinColumnAnn.foreignKey().name() ) );
										key.setForeignKeyDefinition( nullIfEmpty( joinColumnAnn.foreignKey().foreignKeyDefinition() ) );
									}
								}
							}
						}
					}
				}
			}
		}

		return key;
	}

	private void bindManyToManySecondPass(
			Collection collValue,
			Map<String, PersistentClass> persistentClasses,
			AnnotatedJoinColumn[] joinColumns,
			AnnotatedJoinColumn[] inverseJoinColumns,
			AnnotatedColumn[] elementColumns,
			boolean isEmbedded,
			XClass elementType,
			NotFoundAction notFoundAction,
			boolean unique,
			boolean cascadeDeleteEnabled,
			TableBinder associationTableBinder,
			XProperty property,
			PropertyHolder parentPropertyHolder,
			MetadataBuildingContext buildingContext) throws MappingException {

		if ( property == null ) {
			throw new IllegalArgumentException( "null was passed for argument property" );
		}

		final PersistentClass collectionEntity = persistentClasses.get( elementType.getName() );
		final String hqlOrderBy = extractHqlOrderBy( jpaOrderBy );

		boolean isCollectionOfEntities = collectionEntity != null;
		boolean isManyToAny = property.isAnnotationPresent( ManyToAny.class );

		logManyToManySecondPass( collValue, joinColumns, unique, isCollectionOfEntities, isManyToAny );
		//check for user error
		detectManyToManyProblems(
				collValue,
				joinColumns,
				elementType,
				property,
				parentPropertyHolder,
				isCollectionOfEntities,
				isManyToAny
		);

		if ( !isEmptyAnnotationValue( joinColumns[0].getMappedBy() ) ) {
			handleUnownedManyToMany(
					collValue,
					joinColumns,
					elementType,
					collectionEntity,
					isCollectionOfEntities
			);
		}
		else {
			handleOwnedManyToMany(
					collValue,
					joinColumns,
					associationTableBinder,
					property,
					buildingContext,
					collectionEntity,
					isCollectionOfEntities
			);
		}
		bindFilters( isCollectionOfEntities );
		handleWhere( isCollectionOfEntities );

		bindCollectionSecondPass(
				collValue,
				collectionEntity,
				joinColumns,
				cascadeDeleteEnabled,
				property,
				propertyHolder,
				buildingContext
		);

		ManyToOne element = null;
		if ( isCollectionOfEntities ) {
			element = handleCollectionOfEntities(
					collValue,
					elementType,
					notFoundAction,
					property,
					buildingContext,
					collectionEntity,
					hqlOrderBy
			);
		}
		else if ( isManyToAny ) {
			handleManyToAny(
					collValue,
					inverseJoinColumns,
					cascadeDeleteEnabled,
					property,
					buildingContext
			);
		}
		else {
			handleElementCollection(
					collValue,
					elementColumns,
					isEmbedded,
					elementType,
					property,
					parentPropertyHolder,
					buildingContext,
					hqlOrderBy
			);
		}

		checkFilterConditions( collValue );

		//FIXME: do optional = false
		if ( isCollectionOfEntities ) {
			bindManytoManyInverseFk( collectionEntity, inverseJoinColumns, element, unique, buildingContext );
		}

	}

	private void handleElementCollection(
			Collection collValue,
			AnnotatedColumn[] elementColumns,
			boolean isEmbedded,
			XClass elementType,
			XProperty property,
			PropertyHolder parentPropertyHolder,
			MetadataBuildingContext buildingContext,
			String hqlOrderBy) {
		XClass elementClass;
		AnnotatedClassType classType;
		CollectionPropertyHolder holder;
		if ( PRIMITIVE_NAMES.contains( elementType.getName() ) ) {
			classType = AnnotatedClassType.NONE;
			elementClass = null;

			holder = PropertyHolderBuilder.buildPropertyHolder(
					collValue,
					collValue.getRole(),
					null,
					property,
					parentPropertyHolder,
					buildingContext
			);
		}
		else {
			elementClass = elementType;
			classType = buildingContext.getMetadataCollector().getClassType( elementClass );

			holder = PropertyHolderBuilder.buildPropertyHolder(
					collValue,
					collValue.getRole(),
					elementClass,
					property,
					parentPropertyHolder,
					buildingContext
			);

			// 'parentPropertyHolder' is the PropertyHolder for the owner of the collection
			// 'holder' is the CollectionPropertyHolder.
			// 'property' is the collection XProperty
			parentPropertyHolder.startingProperty(property);

			//force in case of attribute override
			boolean attributeOverride = property.isAnnotationPresent( AttributeOverride.class )
					|| property.isAnnotationPresent( AttributeOverrides.class );
			// todo : force in the case of Convert annotation(s) with embedded paths (beyond key/value prefixes)?
			if ( isEmbedded || attributeOverride ) {
				classType = AnnotatedClassType.EMBEDDABLE;
			}
		}

		final Class<? extends CompositeUserType<?>> compositeUserType = resolveCompositeUserType(
				property,
				elementClass,
				buildingContext
		);
		if ( AnnotatedClassType.EMBEDDABLE == classType || compositeUserType != null ) {
			holder.prepare( property, true );

			EntityBinder entityBinder = new EntityBinder();
			PersistentClass owner = collValue.getOwner();

			final AccessType baseAccessType;
			final Access accessAnn = property.getAnnotation( Access.class );
			if ( accessAnn != null ) {
				// the attribute is locally annotated with `@Access`, use that
				baseAccessType = accessAnn.value() == PROPERTY
						? AccessType.PROPERTY
						: AccessType.FIELD;
			}
			else if ( owner.getIdentifierProperty() != null ) {
				// use the access for the owning entity's id attribute, if one
				baseAccessType = owner.getIdentifierProperty().getPropertyAccessorName().equals( "property" )
						? AccessType.PROPERTY
						: AccessType.FIELD;
			}
			else if ( owner.getIdentifierMapper() != null && owner.getIdentifierMapper().getPropertySpan() > 0 ) {
				// use the access for the owning entity's "id mapper", if one
				Property prop = owner.getIdentifierMapper().getProperties().get(0);
				baseAccessType = prop.getPropertyAccessorName().equals( "property" )
						? AccessType.PROPERTY
						: AccessType.FIELD;
			}
			else {
				// otherwise...
				throw new AssertionFailure( "Unable to guess collection property accessor name" );
			}

			//TODO be smart with isNullable
			Component component = fillComponent(
					holder,
					getSpecialMembers( elementClass ),
					baseAccessType,
					true,
					entityBinder,
					false,
					false,
					true,
					resolveCustomInstantiator(property, elementClass, buildingContext),
					compositeUserType,
					buildingContext,
					inheritanceStatePerClass
			);

			collValue.setElement( component );

			if ( isNotEmpty(hqlOrderBy) ) {
				String orderBy = adjustUserSuppliedValueCollectionOrderingFragment(hqlOrderBy);
				if ( orderBy != null ) {
					collValue.setOrderBy( orderBy );
				}
			}
		}
		else {
			holder.prepare( property, false );

			final BasicValueBinder elementBinder =
					new BasicValueBinder( BasicValueBinder.Kind.COLLECTION_ELEMENT, buildingContext);
			elementBinder.setReturnedClassName( elementType.getName() );
			if ( elementColumns == null || elementColumns.length == 0 ) {
				elementColumns = new AnnotatedColumn[1];
				AnnotatedColumn column = new AnnotatedColumn();
				column.setImplicit( false );
				//not following the spec but more clean
				column.setNullable( true );
				column.setLogicalColumnName( Collection.DEFAULT_ELEMENT_COLUMN_NAME );
				//TODO create an EMPTY_JOINS collection
				column.setJoins( new HashMap<>() );
				column.setBuildingContext(buildingContext);
				column.bind();
				elementColumns[0] = column;
			}
			//override the table
			for (AnnotatedColumn column : elementColumns) {
				column.setTable( collValue.getCollectionTable() );
			}
			elementBinder.setColumns(elementColumns);
			elementBinder.setType(
					property,
					elementClass,
					collValue.getOwnerEntityName(),
					holder.resolveElementAttributeConverterDescriptor( property, elementClass )
			);
			elementBinder.setPersistentClassName( propertyHolder.getEntityName() );
			elementBinder.setAccessType( accessType );
			collValue.setElement( elementBinder.make() );
			String orderBy = adjustUserSuppliedValueCollectionOrderingFragment(hqlOrderBy);
			if ( orderBy != null ) {
				collValue.setOrderBy( orderBy );
			}
		}
	}

	private ManyToOne handleCollectionOfEntities(
			Collection collValue,
			XClass elementType,
			NotFoundAction notFoundAction,
			XProperty property,
			MetadataBuildingContext buildingContext,
			PersistentClass collectionEntity,
			String hqlOrderBy) {
		ManyToOne element = new ManyToOne( buildingContext,  collValue.getCollectionTable() );
		collValue.setElement( element );
		element.setReferencedEntityName( elementType.getName() );
		//element.setFetchMode( fetchMode );
		//element.setLazy( fetchMode != FetchMode.JOIN );
		//make the second join non lazy
		element.setFetchMode( FetchMode.JOIN );
		element.setLazy( false );
		element.setNotFoundAction( notFoundAction );
		// as per 11.1.38 of JPA 2.0 spec, default to primary key if no column is specified by @OrderBy.
		if ( hqlOrderBy != null ) {
			collValue.setManyToManyOrdering( buildOrderByClauseFromHql( hqlOrderBy, collectionEntity ) );
		}

		final ForeignKey fk = property.getAnnotation( ForeignKey.class );
		if ( fk != null && !isEmptyAnnotationValue( fk.name() ) ) {
			element.setForeignKeyName( fk.name() );
		}
		else {
			final JoinTable joinTableAnn = property.getAnnotation( JoinTable.class );
			if ( joinTableAnn != null ) {
				String foreignKeyName = joinTableAnn.inverseForeignKey().name();
				String foreignKeyDefinition = joinTableAnn.inverseForeignKey().foreignKeyDefinition();
				if ( joinTableAnn.inverseJoinColumns().length != 0 ) {
					final JoinColumn joinColumnAnn = joinTableAnn.inverseJoinColumns()[0];
					if ( foreignKeyName != null && foreignKeyName.isEmpty() ) {
						foreignKeyName = joinColumnAnn.foreignKey().name();
						foreignKeyDefinition = joinColumnAnn.foreignKey().foreignKeyDefinition();
					}
				}
				if ( joinTableAnn.inverseForeignKey().value() == ConstraintMode.NO_CONSTRAINT
						|| joinTableAnn.inverseForeignKey().value() == ConstraintMode.PROVIDER_DEFAULT
								&& buildingContext.getBuildingOptions().isNoConstraintByDefault() ) {
					element.disableForeignKey();
				}
				else {
					element.setForeignKeyName( nullIfEmpty( foreignKeyName ) );
					element.setForeignKeyDefinition( nullIfEmpty( foreignKeyDefinition ) );
				}
			}
		}
		return element;
	}

	private void handleManyToAny(
			Collection collValue,
			AnnotatedJoinColumn[] inverseJoinColumns,
			boolean cascadeDeleteEnabled,
			XProperty property,
			MetadataBuildingContext buildingContext) {
		//@ManyToAny
		//Make sure that collTyp is never used during the @ManyToAny branch: it will be set to void.class
		final PropertyData inferredData = new PropertyInferredData(
				null,
				property,
				"unsupported",
				buildingContext.getBootstrapContext().getReflectionManager()
		);

		XProperty prop = inferredData.getProperty();
		final jakarta.persistence.Column discriminatorColumnAnn = prop.getAnnotation( jakarta.persistence.Column.class );
		final Formula discriminatorFormulaAnn = getOverridableAnnotation( prop, Formula.class, buildingContext);

		//override the table
		for (AnnotatedColumn column : inverseJoinColumns) {
			column.setTable( collValue.getCollectionTable() );
		}

		ManyToAny anyAnn = property.getAnnotation( ManyToAny.class );
		final Any any = buildAnyValue(
				discriminatorColumnAnn,
				discriminatorFormulaAnn,
				inverseJoinColumns,
				inferredData,
				cascadeDeleteEnabled,
				anyAnn.fetch() == FetchType.LAZY,
				Nullability.NO_CONSTRAINT,
				propertyHolder,
				new EntityBinder(),
				true,
				buildingContext
		);
		collValue.setElement( any );
	}

	private PropertyData getSpecialMembers(XClass elementClass) {
		if ( isMap() ) {
			//"value" is the JPA 2 prefix for map values (used to be "element")
			if ( isHibernateExtensionMapping() ) {
				return new PropertyPreloadedData( AccessType.PROPERTY, "element", elementClass);
			}
			else {
				return new PropertyPreloadedData( AccessType.PROPERTY, "value", elementClass);
			}
		}
		else {
			if ( isHibernateExtensionMapping() ) {
				return new PropertyPreloadedData( AccessType.PROPERTY, "element", elementClass);
			}
			else {
				//"collection&&element" is not a valid property name => placeholder
				return new PropertyPreloadedData( AccessType.PROPERTY, "collection&&element", elementClass);
			}
		}
	}

	private void handleOwnedManyToMany(
			Collection collValue,
			AnnotatedJoinColumn[] joinColumns,
			TableBinder associationTableBinder,
			XProperty property,
			MetadataBuildingContext buildingContext,
			PersistentClass collectionEntity,
			boolean isCollectionOfEntities) {
		//TODO: only for implicit columns?
		//FIXME NamingStrategy
		for (AnnotatedJoinColumn column : joinColumns) {
			String mappedByProperty = buildingContext.getMetadataCollector().getFromMappedBy(
					collValue.getOwnerEntityName(), column.getPropertyName()
			);
			Table ownerTable = collValue.getOwner().getTable();
			column.setMappedBy(
					collValue.getOwner().getEntityName(),
					collValue.getOwner().getJpaEntityName(),
					buildingContext.getMetadataCollector().getLogicalTableName( ownerTable ),
					mappedByProperty
			);
//				String header = ( mappedByProperty == null ) ? mappings.getLogicalTableName( ownerTable ) : mappedByProperty;
//				column.setDefaultColumnHeader( header );
		}
		if ( isEmpty( associationTableBinder.getName() ) ) {
			//default value
			PersistentClass owner = collValue.getOwner();
			associationTableBinder.setDefaultName(
					owner.getClassName(),
					owner.getEntityName(),
					owner.getJpaEntityName(),
					buildingContext.getMetadataCollector().getLogicalTableName( owner.getTable() ),
					collectionEntity != null ? collectionEntity.getClassName() : null,
					collectionEntity != null ? collectionEntity.getEntityName() : null,
					collectionEntity != null ? collectionEntity.getJpaEntityName() : null,
					collectionEntity != null ? buildingContext.getMetadataCollector().getLogicalTableName(
							collectionEntity.getTable()
					) : null,
					joinColumns[0].getPropertyName()
			);
		}
		associationTableBinder.setJPA2ElementCollection(
				!isCollectionOfEntities && property.isAnnotationPresent(ElementCollection.class)
		);
		collValue.setCollectionTable( associationTableBinder.bind() );
	}

	private void handleUnownedManyToMany(
			Collection collValue,
			AnnotatedJoinColumn[] joinColumns,
			XClass elementType,
			PersistentClass collectionEntity,
			boolean isCollectionOfEntities) {

		if ( !isCollectionOfEntities) {
			throw new AnnotationException( "Association '" + safeCollectionRole()
							+ "' targets the type '" + elementType.getName() + "' which is not an '@Entity' type" );
		}

		Property otherSideProperty;
		try {
			otherSideProperty = collectionEntity.getRecursiveProperty( joinColumns[0].getMappedBy() );
		}
		catch (MappingException e) {
			throw new AnnotationException( "Association '" + safeCollectionRole() +
					"is 'mappedBy' a property named '" + mappedBy
					+ "' which does not exist in the target entity '" + elementType.getName() + "'" );
		}
		Table table = otherSideProperty.getValue() instanceof Collection
				? ( (Collection) otherSideProperty.getValue() ).getCollectionTable()
				: otherSideProperty.getValue().getTable();
		//this is a collection on the other side
		//This is a ToOne with a @JoinTable or a regular property
		collValue.setCollectionTable( table );
		String entityName = collectionEntity.getEntityName();
		for (AnnotatedJoinColumn column : joinColumns) {
			//column.setDefaultColumnHeader( joinColumns[0].getMappedBy() ); //seems not to be used, make sense
			column.setManyToManyOwnerSideEntityName( entityName );
		}
	}

	private void detectManyToManyProblems(
			Collection collValue,
			AnnotatedJoinColumn[] joinColumns,
			XClass elementType,
			XProperty property,
			PropertyHolder parentPropertyHolder,
			boolean isCollectionOfEntities,
			boolean isManyToAny) {

		if ( !isCollectionOfEntities) {
			if ( property.isAnnotationPresent( ManyToMany.class ) || property.isAnnotationPresent( OneToMany.class ) ) {
				throw new AnnotationException( "Association '" + safeCollectionRole()
						+ "' targets the type '" + elementType.getName() + "' which is not an '@Entity' type" );
			}
			else if (isManyToAny) {
				if ( parentPropertyHolder.getJoinTable( property ) == null ) {
					throw new AnnotationException( "Association '" + safeCollectionRole()
							+ "' is a '@ManyToAny' and must specify a '@JoinTable'" );
				}
			}
			else {
				JoinTable joinTableAnn = parentPropertyHolder.getJoinTable( property );
				if ( joinTableAnn != null && joinTableAnn.inverseJoinColumns().length > 0 ) {
					throw new AnnotationException( "Association '" + safeCollectionRole()
							+ " has a '@JoinTable' with 'inverseJoinColumns' and targets the type '"
							+ elementType.getName() + "' which is not an '@Entity' type" );
				}
			}
		}
	}

	private void logManyToManySecondPass(
			Collection collValue,
			AnnotatedJoinColumn[] joinColumns,
			boolean unique,
			boolean isCollectionOfEntities,
			boolean isManyToAny) {

		if ( LOG.isDebugEnabled() ) {
			String path = collValue.getOwnerEntityName() + "." + joinColumns[0].getPropertyName();
			if ( isCollectionOfEntities && unique) {
				LOG.debugf("Binding a OneToMany: %s through an association table", path);
			}
			else if (isCollectionOfEntities) {
				LOG.debugf("Binding a ManyToMany: %s", path);
			}
			else if (isManyToAny) {
				LOG.debugf("Binding a ManyToAny: %s", path);
			}
			else {
				LOG.debugf("Binding a collection of element: %s", path);
			}
		}
	}

	private Class<? extends EmbeddableInstantiator> resolveCustomInstantiator(
			XProperty property,
			XClass propertyClass,
			MetadataBuildingContext context) {
		final org.hibernate.annotations.EmbeddableInstantiator propertyAnnotation
				= property.getAnnotation( org.hibernate.annotations.EmbeddableInstantiator.class );
		if ( propertyAnnotation != null ) {
			return propertyAnnotation.value();
		}

		final org.hibernate.annotations.EmbeddableInstantiator classAnnotation
				= propertyClass.getAnnotation( org.hibernate.annotations.EmbeddableInstantiator.class );
		if ( classAnnotation != null ) {
			return classAnnotation.value();
		}

		final Class<?> embeddableClass = context.getBootstrapContext().getReflectionManager().toClass( propertyClass );
		if ( embeddableClass != null ) {
			return context.getMetadataCollector().findRegisteredEmbeddableInstantiator( embeddableClass );
		}

		return null;
	}

	private static Class<? extends CompositeUserType<?>> resolveCompositeUserType(
			XProperty property,
			XClass returnedClass,
			MetadataBuildingContext context) {
		final CompositeType compositeType = property.getAnnotation( CompositeType.class );
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

	private String extractHqlOrderBy(jakarta.persistence.OrderBy jpaOrderBy) {
		if ( jpaOrderBy != null ) {
			return jpaOrderBy.value(); // Null not possible. In case of empty expression, apply default ordering.
		}
		return null; // @OrderBy not found.
	}

	private static void checkFilterConditions(Collection collValue) {
		//for now it can't happen, but sometime soon...
		if ( ( collValue.getFilters().size() != 0 || isNotEmpty( collValue.getWhere() ) ) &&
				collValue.getFetchMode() == FetchMode.JOIN &&
				!( collValue.getElement() instanceof SimpleValue ) && //SimpleValue (CollectionOfElements) are always SELECT but it does not matter
				collValue.getElement().getFetchMode() != FetchMode.JOIN ) {
			throw new MappingException(
					"@ManyToMany or @ElementCollection defining filter or where without join fetching "
							+ "not valid within collection using join fetching[" + collValue.getRole() + "]"
			);
		}
	}

	private static void bindCollectionSecondPass(
			Collection collValue,
			PersistentClass collectionEntity,
			AnnotatedJoinColumn[] joinColumns,
			boolean cascadeDeleteEnabled,
			XProperty property,
			PropertyHolder propertyHolder,
			MetadataBuildingContext buildingContext) {
		try {
			createSyntheticPropertyReference(
					joinColumns,
					collValue.getOwner(),
					collectionEntity,
					collValue,
					false,
					buildingContext
			);
		}
		catch (AnnotationException ex) {
			throw new AnnotationException( "Unable to map collection "
					+ collValue.getOwner().getClassName() + "." + property.getName(), ex );
		}
		DependantValue key = buildCollectionKey( collValue, joinColumns, cascadeDeleteEnabled,
				buildingContext.getBuildingOptions().isNoConstraintByDefault(), property, propertyHolder, buildingContext );
		if ( property.isAnnotationPresent( ElementCollection.class ) && joinColumns.length > 0 ) {
			joinColumns[0].setJPA2ElementCollection( true );
		}
		TableBinder.bindForeignKey( collValue.getOwner(), collectionEntity, joinColumns, key, false, buildingContext );
		key.sortProperties();
	}

	public void setCascadeDeleteEnabled(boolean onDeleteCascade) {
		this.cascadeDeleteEnabled = onDeleteCascade;
	}

	String safeCollectionRole() {
		return propertyHolder != null ? propertyHolder.getEntityName() + "." + propertyName : "";
	}


	/**
	 * bind the inverse FK of a {@link ManyToMany}.
	 * If we are in a mappedBy case, read the columns from the associated
	 * collection element
	 * Otherwise delegates to the usual algorithm
	 */
	public void bindManytoManyInverseFk(
			PersistentClass referencedEntity,
			AnnotatedJoinColumn[] columns,
			SimpleValue value,
			boolean unique,
			MetadataBuildingContext buildingContext) {
		final String mappedBy = columns[0].getMappedBy();
		if ( isNotEmpty( mappedBy ) ) {
			final Property property = referencedEntity.getRecursiveProperty( mappedBy );
			final List<Selectable> mappedByColumns = mappedByColumns( referencedEntity, property );
			for ( Selectable selectable: mappedByColumns ) {
				columns[0].linkValueUsingAColumnCopy( (Column) selectable, value );
			}
			final String referencedPropertyName = buildingContext.getMetadataCollector()
					.getPropertyReferencedAssociation( referencedEntity.getEntityName(), mappedBy );
			if ( referencedPropertyName != null ) {
				//TODO always a many to one?
				( (ManyToOne) value ).setReferencedPropertyName( referencedPropertyName );
				buildingContext.getMetadataCollector().addUniquePropertyReference(
						referencedEntity.getEntityName(),
						referencedPropertyName
				);
			}
			( (ManyToOne) value ).setReferenceToPrimaryKey( referencedPropertyName == null );
			value.createForeignKey();
		}
		else {
			createSyntheticPropertyReference( columns, referencedEntity, null, value, true, buildingContext );
			TableBinder.bindForeignKey( referencedEntity, null, columns, value, unique, buildingContext );
		}
	}

	private static List<Selectable> mappedByColumns(PersistentClass referencedEntity, Property property) {
		if ( property.getValue() instanceof Collection ) {
			return ( (Collection) property.getValue() ).getKey().getSelectables();
		}
		else {
			//find the appropriate reference key, can be in a join
			KeyValue key = null;
			for ( Join join : referencedEntity.getJoins() ) {
				if ( join.containsProperty(property) ) {
					key = join.getKey();
					break;
				}
			}
			if ( key == null ) {
				key = property.getPersistentClass().getIdentifier();
			}
			return key.getSelectables();
		}
	}

	public void setFkJoinColumns(AnnotatedJoinColumn[] annotatedJoinColumns) {
		this.fkJoinColumns = annotatedJoinColumns;
	}

	public void setExplicitAssociationTable(boolean explicitAssocTable) {
		this.isExplicitAssociationTable = explicitAssocTable;
	}

	public void setElementColumns(AnnotatedColumn[] elementColumns) {
		this.elementColumns = elementColumns;
	}

	public void setEmbedded(boolean annotationPresent) {
		this.isEmbedded = annotationPresent;
	}

	public void setProperty(XProperty property) {
		this.property = property;
	}

	public NotFoundAction getNotFoundAction() {
		return notFoundAction;
	}

	public void setNotFoundAction(NotFoundAction notFoundAction) {
		this.notFoundAction = notFoundAction;
	}

	public void setIgnoreNotFound(boolean ignoreNotFound) {
		this.notFoundAction = ignoreNotFound
				? NotFoundAction.IGNORE
				: null;
	}

	public void setMapKeyColumns(AnnotatedColumn[] mapKeyColumns) {
		this.mapKeyColumns = mapKeyColumns;
	}

	public void setMapKeyManyToManyColumns(AnnotatedJoinColumn[] mapJoinColumns) {
		this.mapKeyManyToManyColumns = mapJoinColumns;
	}

	public void setLocalGenerators(Map<String, IdentifierGeneratorDefinition> localGenerators) {
		this.localGenerators = localGenerators;
	}
}
