/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg.annotations;

import java.lang.annotation.Annotation;
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
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.MapKey;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyJoinColumn;
import jakarta.persistence.MapKeyJoinColumns;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.AnnotationException;
import org.hibernate.AssertionFailure;
import org.hibernate.FetchMode;
import org.hibernate.MappingException;
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
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.InFlightMetadataCollector.CollectionTypeRegistrationDescriptor;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.AccessType;
import org.hibernate.cfg.AnnotatedClassType;
import org.hibernate.cfg.AnnotatedColumn;
import org.hibernate.cfg.AnnotatedColumns;
import org.hibernate.cfg.AnnotatedJoinColumn;
import org.hibernate.cfg.AnnotatedJoinColumns;
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
import org.hibernate.mapping.Value;
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
import static jakarta.persistence.FetchType.EAGER;
import static jakarta.persistence.FetchType.LAZY;
import static org.hibernate.cfg.AnnotatedColumn.buildColumnFromAnnotation;
import static org.hibernate.cfg.AnnotatedColumn.buildColumnFromNoAnnotation;
import static org.hibernate.cfg.AnnotatedColumn.buildColumnsFromAnnotations;
import static org.hibernate.cfg.AnnotatedColumn.buildFormulaFromAnnotation;
import static org.hibernate.cfg.AnnotatedJoinColumns.buildJoinColumnsWithDefaultColumnSuffix;
import static org.hibernate.cfg.AnnotatedJoinColumns.buildJoinTableJoinColumns;
import static org.hibernate.cfg.AnnotationBinder.fillComponent;
import static org.hibernate.cfg.BinderHelper.buildAnyValue;
import static org.hibernate.cfg.BinderHelper.createSyntheticPropertyReference;
import static org.hibernate.cfg.BinderHelper.getCascadeStrategy;
import static org.hibernate.cfg.BinderHelper.getFetchMode;
import static org.hibernate.cfg.BinderHelper.getOverridableAnnotation;
import static org.hibernate.cfg.BinderHelper.getPath;
import static org.hibernate.cfg.BinderHelper.isEmptyAnnotationValue;
import static org.hibernate.cfg.BinderHelper.isPrimitive;
import static org.hibernate.cfg.BinderHelper.toAliasEntityMap;
import static org.hibernate.cfg.BinderHelper.toAliasTableMap;
import static org.hibernate.cfg.PropertyHolderBuilder.buildPropertyHolder;
import static org.hibernate.engine.spi.ExecuteUpdateResultCheckStyle.fromResultCheckStyle;
import static org.hibernate.internal.util.StringHelper.getNonEmptyOrConjunctionIfBothNonEmpty;
import static org.hibernate.internal.util.StringHelper.isEmpty;
import static org.hibernate.internal.util.StringHelper.isNotEmpty;
import static org.hibernate.internal.util.StringHelper.nullIfEmpty;
import static org.hibernate.internal.util.StringHelper.qualify;

/**
 * Base class for binding different types of collections to mapping model objects
 * of type {@link Collection}.
 *
 * @author inger
 * @author Emmanuel Bernard
 */
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

	final MetadataBuildingContext buildingContext;
	private final Supplier<ManagedBean<? extends UserCollectionType>> customTypeBeanResolver;
	private final boolean isSortedCollection;

	protected Collection collection;
	protected String propertyName;
	protected PropertyHolder propertyHolder;
	private int batchSize;
	private String mappedBy;
	private XClass collectionElementType;
	private XClass targetEntity;
	private String cascadeStrategy;
	private String cacheConcurrencyStrategy;
	private String cacheRegionName;
	private boolean oneToMany;
	protected IndexColumn indexColumn;
	protected boolean cascadeDeleteEnabled;
	protected String mapKeyPropertyName;
	private boolean insertable = true;
	private boolean updatable = true;
	protected AnnotatedJoinColumns inverseJoinColumns;
	protected AnnotatedJoinColumns foreignJoinColumns;
	private AnnotatedJoinColumns joinColumns;
	private boolean isExplicitAssociationTable;
	private AnnotatedColumns elementColumns;
	protected boolean isEmbedded;
	protected XProperty property;
	protected NotFoundAction notFoundAction;
	private TableBinder tableBinder;
	protected AnnotatedColumns mapKeyColumns;
	protected AnnotatedJoinColumns mapKeyManyToManyColumns;
	protected Map<String, IdentifierGeneratorDefinition> localGenerators;
	protected Map<XClass, InheritanceState> inheritanceStatePerClass;
	private XClass declaringClass;
	private boolean declaringClassSet;
	private AccessType accessType;
	private boolean hibernateExtensionMapping;

	private OrderBy jpaOrderBy;
	private org.hibernate.annotations.OrderBy sqlOrderBy;
	private SortNatural naturalSort;
	private SortComparator comparatorSort;

	private String explicitType;
	private final Map<String,String> explicitTypeParameters = new HashMap<>();

	protected CollectionBinder(
			Supplier<ManagedBean<? extends UserCollectionType>> customTypeBeanResolver,
			boolean isSortedCollection,
			MetadataBuildingContext buildingContext) {
		this.customTypeBeanResolver = customTypeBeanResolver;
		this.isSortedCollection = isSortedCollection;
		this.buildingContext = buildingContext;
	}

	/**
	 * The first pass at binding a collection.
	 */
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
			AnnotatedJoinColumns joinColumns) {

		final OneToMany oneToManyAnn = property.getAnnotation( OneToMany.class );
		final ManyToMany manyToManyAnn = property.getAnnotation( ManyToMany.class );
		final ElementCollection elementCollectionAnn = property.getAnnotation( ElementCollection.class );
		checkAnnotations( propertyHolder, inferredData, property, oneToManyAnn, manyToManyAnn, elementCollectionAnn );

		final CollectionBinder collectionBinder = getCollectionBinder( property, hasMapKeyAnnotation( property ), context );
		collectionBinder.setIndexColumn( getIndexColumn( propertyHolder, inferredData, entityBinder, context, property ) );
		collectionBinder.setMapKey( property.getAnnotation( MapKey.class ) );
		collectionBinder.setPropertyName( inferredData.getPropertyName() );
		collectionBinder.setBatchSize( property.getAnnotation( BatchSize.class ) );
		collectionBinder.setJpaOrderBy( property.getAnnotation( OrderBy.class ) );
		collectionBinder.setSqlOrderBy( getOverridableAnnotation( property, org.hibernate.annotations.OrderBy.class, context ) );
		collectionBinder.setNaturalSort( property.getAnnotation( SortNatural.class ) );
		collectionBinder.setComparatorSort( property.getAnnotation( SortComparator.class ) );
		collectionBinder.setCache( property.getAnnotation( Cache.class ) );
		collectionBinder.setPropertyHolder(propertyHolder);

		collectionBinder.setNotFoundAction( notFoundAction( propertyHolder, inferredData, property, manyToManyAnn ) );
		collectionBinder.setElementType( inferredData.getProperty().getElementClass() );
		collectionBinder.setAccessType( inferredData.getDefaultAccess() );
		collectionBinder.setEmbedded( property.isAnnotationPresent( Embedded.class ) );
		collectionBinder.setProperty( property );
		collectionBinder.setCascadeDeleteEnabled( hasOnDeleteCascade( property ) );
		collectionBinder.setInheritanceStatePerClass( inheritanceStatePerClass );
		collectionBinder.setDeclaringClass( inferredData.getDeclaringClass() );

		final Comment comment = property.getAnnotation( Comment.class );
		final Cascade hibernateCascade = property.getAnnotation( Cascade.class );

		collectionBinder.setElementColumns( elementColumns(
				propertyHolder,
				nullability,
				entityBinder,
				context,
				property,
				virtualPropertyData( inferredData, property ),
				comment
		) );

		collectionBinder.setMapKeyColumns( mapKeyColumns(
				propertyHolder,
				inferredData,
				entityBinder,
				context,
				property,
				comment
		) );

		collectionBinder.setMapKeyManyToManyColumns( mapKeyJoinColumns(
				propertyHolder,
				inferredData,
				entityBinder,
				context,
				property,
				comment)
		);

		bindJoinedTableAssociation(
				property,
				context,
				entityBinder,
				collectionBinder,
				propertyHolder,
				inferredData,
				handleTargetEntity(
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
				)
		);

		if ( isIdentifierMapper ) {
			collectionBinder.setInsertable( false );
			collectionBinder.setUpdatable( false );
		}
		if ( property.isAnnotationPresent( CollectionId.class ) ) { //do not compute the generators unless necessary
			final HashMap<String, IdentifierGeneratorDefinition> localGenerators = new HashMap<>(classGenerators);
			localGenerators.putAll( AnnotationBinder.buildGenerators( property, context ) );
			collectionBinder.setLocalGenerators( localGenerators );

		}
		collectionBinder.bind();
	}

	private static NotFoundAction notFoundAction(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			XProperty property,
			ManyToMany manyToManyAnn) {
		final NotFound notFound = property.getAnnotation( NotFound.class );
		if ( notFound != null ) {
			if ( manyToManyAnn == null ) {
				throw new AnnotationException( "Collection '" + getPath(propertyHolder, inferredData)
						+ "' annotated '@NotFound' is not a '@ManyToMany' association" );
			}
			return notFound.action();
		}
		else {
			return null;
		}
	}

	private static AnnotatedJoinColumns mapKeyJoinColumns(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			EntityBinder entityBinder,
			MetadataBuildingContext context,
			XProperty property,
			Comment comment) {
		return buildJoinColumnsWithDefaultColumnSuffix(
				mapKeyJoinColumnAnnotations( propertyHolder, inferredData, property ),
				comment,
				null,
				entityBinder.getSecondaryTables(),
				propertyHolder,
				inferredData.getPropertyName(),
				"_KEY",
				context
		);
	}

	private static boolean hasOnDeleteCascade(XProperty property) {
		final OnDelete onDelete = property.getAnnotation( OnDelete.class );
		return onDelete != null && OnDeleteAction.CASCADE == onDelete.action();
	}

	private static PropertyData virtualPropertyData(PropertyData inferredData, XProperty property) {
		//do not use "element" if you are a JPA 2 @ElementCollection, only for legacy Hibernate mappings
		return property.isAnnotationPresent( ElementCollection.class )
				? inferredData
				: new WrappedInferredData(inferredData, "element" );
	}

	private static void checkAnnotations(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			XProperty property,
			OneToMany oneToManyAnn,
			ManyToMany manyToManyAnn,
			ElementCollection elementCollectionAnn) {
		if ( ( oneToManyAnn != null || manyToManyAnn != null || elementCollectionAnn != null )
				&& isToManyAssociationWithinEmbeddableCollection(propertyHolder) ) {
			String ann = oneToManyAnn !=null ? "'@OneToMany'" : manyToManyAnn !=null ? "'@ManyToMany'" : "'@ElementCollection'";
			throw new AnnotationException( "Property '" + getPath(propertyHolder, inferredData) +
					"' belongs to an '@Embeddable' class that is contained in an '@ElementCollection' and may not be a " + ann );
		}

		if ( property.isAnnotationPresent( OrderColumn.class )
				&& manyToManyAnn != null && !manyToManyAnn.mappedBy().isEmpty() ) {
			throw new AnnotationException("Collection '" + getPath(propertyHolder, inferredData) +
					"' is the unowned side of a bidirectional '@ManyToMany' and may not have an '@OrderColumn'");
		}
	}

	private static IndexColumn getIndexColumn(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			EntityBinder entityBinder,
			MetadataBuildingContext context,
			XProperty property) {
		return IndexColumn.fromAnnotations(
				property.getAnnotation( OrderColumn.class ),
				property.getAnnotation( org.hibernate.annotations.IndexColumn.class ),
				property.getAnnotation( ListIndexBase.class ),
				propertyHolder,
				inferredData,
				entityBinder.getSecondaryTables(),
				context
		);
	}

	private static String handleTargetEntity(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			MetadataBuildingContext context,
			XProperty property,
			AnnotatedJoinColumns joinColumns,
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
		final String mappedBy;
		final ReflectionManager reflectionManager = context.getBootstrapContext().getReflectionManager();
		if ( oneToManyAnn != null ) {
			if ( joinColumns.isSecondary() ) {
				//TODO: fix the error message
				throw new NotYetImplementedException( "Collections having FK in secondary table" );
			}
			collectionBinder.setFkJoinColumns( joinColumns );
			mappedBy = oneToManyAnn.mappedBy();
			//noinspection unchecked
			collectionBinder.setTargetEntity( reflectionManager.toXClass( oneToManyAnn.targetEntity() ) );
			collectionBinder.setCascadeStrategy(
					getCascadeStrategy( oneToManyAnn.cascade(), hibernateCascade, oneToManyAnn.orphanRemoval(), false )
			);
			collectionBinder.setOneToMany( true );
		}
		else if ( elementCollectionAnn != null ) {
			if ( joinColumns.isSecondary() ) {
				//TODO: fix the error message
				throw new NotYetImplementedException( "Collections having FK in secondary table" );
			}
			collectionBinder.setFkJoinColumns( joinColumns );
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
		else {
			mappedBy = null;
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
		if ( propertyHolder instanceof ComponentPropertyHolder ) {
			ComponentPropertyHolder componentPropertyHolder = (ComponentPropertyHolder) propertyHolder;
			return componentPropertyHolder.isWithinElementCollection();
		}
		else {
			return false;
		}
	}

	private static AnnotatedColumns elementColumns(
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

	private static JoinColumn[] mapKeyJoinColumnAnnotations(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			XProperty property) {
		if ( property.isAnnotationPresent( MapKeyJoinColumns.class ) ) {
			final MapKeyJoinColumn[] mapKeyJoinColumns = property.getAnnotation( MapKeyJoinColumns.class ).value();
			final JoinColumn[] joinKeyColumns = new JoinColumn[mapKeyJoinColumns.length];
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
			return new JoinColumn[] { new MapKeyJoinColumnDelegator( property.getAnnotation( MapKeyJoinColumn.class ) ) };
		}
		else {
			return null;
		}
	}

	private static AnnotatedColumns mapKeyColumns(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			EntityBinder entityBinder,
			MetadataBuildingContext context,
			XProperty property,
			Comment comment) {
		return buildColumnsFromAnnotations(
				property.isAnnotationPresent( MapKeyColumn.class )
						? new jakarta.persistence.Column[] {
								new MapKeyColumnDelegator( property.getAnnotation( MapKeyColumn.class ) )
						}
						: null,
				comment,
				Nullability.FORCED_NOT_NULL,
				propertyHolder,
				inferredData,
				"_KEY",
				entityBinder.getSecondaryTables(),
				context
		);
	}

	private static void bindJoinedTableAssociation(
			XProperty property,
			MetadataBuildingContext buildingContext,
			EntityBinder entityBinder,
			CollectionBinder collectionBinder,
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			String mappedBy) {
		final TableBinder associationTableBinder = new TableBinder();
		final JoinTable assocTable = propertyHolder.getJoinTable( property );
		final CollectionTable collectionTable = property.getAnnotation( CollectionTable.class );
		final JoinColumn[] annJoins;
		final JoinColumn[] annInverseJoins;
		if ( assocTable != null || collectionTable != null ) {

			final String catalog;
			final String schema;
			final String tableName;
			final UniqueConstraint[] uniqueConstraints;
			final JoinColumn[] joins;
			final JoinColumn[] inverseJoins;
			final Index[] jpaIndexes;

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
		associationTableBinder.setBuildingContext( buildingContext );
		collectionBinder.setTableBinder( associationTableBinder );
		collectionBinder.setJoinColumns( buildJoinTableJoinColumns(
				annJoins,
				entityBinder.getSecondaryTables(),
				propertyHolder,
				inferredData.getPropertyName(),
				mappedBy,
				buildingContext
		) );
		collectionBinder.setInverseJoinColumns( buildJoinTableJoinColumns(
				annInverseJoins,
				entityBinder.getSecondaryTables(),
				propertyHolder,
				inferredData.getPropertyName(),
				mappedBy,
				buildingContext
		) );
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

	public void setInverseJoinColumns(AnnotatedJoinColumns inverseJoinColumns) {
		this.inverseJoinColumns = inverseJoinColumns;
	}

	public void setJoinColumns(AnnotatedJoinColumns joinColumns) {
		this.joinColumns = joinColumns;
	}

	public void setPropertyHolder(PropertyHolder propertyHolder) {
		this.propertyHolder = propertyHolder;
	}

	public void setBatchSize(BatchSize batchSize) {
		this.batchSize = batchSize == null ? -1 : batchSize.size();
	}

	public void setJpaOrderBy(jakarta.persistence.OrderBy jpaOrderBy) {
		this.jpaOrderBy = jpaOrderBy;
	}

	public void setSqlOrderBy(org.hibernate.annotations.OrderBy sqlOrderBy) {
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
				binder.explicitTypeParameters.put( param.name(), param.value() );
			}
		}
		else {
			binder = createBinderAutomatically( property, buildingContext );
		}
		binder.setIsHibernateExtensionMapping( isHibernateExtensionMapping );
		return binder;
	}

	private static CollectionBinder createBinderAutomatically(XProperty property, MetadataBuildingContext context) {
		final CollectionClassification classification = determineCollectionClassification( property, context );
		final CollectionTypeRegistrationDescriptor typeRegistration = context.getMetadataCollector()
				.findCollectionTypeRegistration( classification );
		return typeRegistration != null
				? createBinderFromTypeRegistration( property, classification, typeRegistration, context )
				: createBinderFromProperty( property, context );
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
			Map<String,String> parameters,
			MetadataBuildingContext buildingContext) {
		final ManagedBeanRegistry beanRegistry = buildingContext.getBuildingOptions()
				.getServiceRegistry()
				.getService( ManagedBeanRegistry.class );
		if ( CollectionHelper.isNotEmpty( parameters ) ) {
			return beanRegistry.getBean( implementation );
		}
		else {
			// defined parameters...
			if ( ParameterizedType.class.isAssignableFrom( implementation ) ) {
				// because there are config parameters and the type is configurable,
				// we need a separate bean instance which means uniquely naming it
				final ManagedBean<? extends UserCollectionType> typeBean = beanRegistry.getBean( role, implementation );
				final UserCollectionType type = typeBean.getBeanInstance();
				final Properties properties = new Properties();
				properties.putAll( parameters );
				( (ParameterizedType) type ).setParameterValues( properties );
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

	private static CollectionBinder createBinderFromProperty(XProperty property, MetadataBuildingContext context) {
		final CollectionClassification classification = determineCollectionClassification( property, context );
		return createBinder( property, null, classification, context );
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
			MetadataBuildingContext context) {
		final ManagedBeanRegistry beanRegistry = context.getBootstrapContext()
				.getServiceRegistry()
				.getService( ManagedBeanRegistry.class );
		final Class<? extends UserCollectionType> typeImpl = typeAnnotation.type();
		if ( typeAnnotation.parameters().length == 0 ) {
			// no parameters - we can reuse a no-config bean instance
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
					|| property.isAnnotationPresent( org.hibernate.annotations.OrderBy.class ) ) {
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
		collection = createCollection( propertyHolder.getPersistentClass() );
		final String role = qualify( propertyHolder.getPath(), propertyName );
		LOG.debugf( "Collection role: %s", role );
		collection.setRole( role );
		collection.setMappedByProperty( mappedBy );

		checkMapKeyColumn();
		bindExplicitTypes();
		//set laziness
		defineFetchingStrategy();
		collection.setBatchSize( batchSize );
		collection.setMutable( isMutable() );
		//work on association
		boolean isUnowned = isUnownedCollection();
		bindOptimisticLock( isUnowned );
		bindCustomPersister();
		applySortingAndOrdering( collection );
		bindCache();
		bindLoader();
		detectMappedByProblem( isUnowned );
		collection.setInverse( isUnowned );

		//TODO reduce tableBinder != null and oneToMany
		scheduleSecondPass( isUnowned );
		buildingContext.getMetadataCollector().addCollectionBinding( collection );
		bindProperty();
	}

	private boolean isUnownedCollection() {
		return !isEmptyAnnotationValue( mappedBy );
	}

	private boolean isMutable() {
		return !property.isAnnotationPresent(Immutable.class);
	}

	private void checkMapKeyColumn() {
		if ( property.isAnnotationPresent( MapKeyColumn.class ) && mapKeyPropertyName != null ) {
			throw new AnnotationException( "Collection '" + qualify( propertyHolder.getPath(), propertyName )
					+ "' is annotated both '@MapKey' and '@MapKeyColumn'" );
		}
	}

	private void scheduleSecondPass(boolean isMappedBy) {
		final InFlightMetadataCollector metadataCollector = buildingContext.getMetadataCollector();
		//many to many may need some second pass information
		if ( !oneToMany && isMappedBy ) {
			metadataCollector.addMappedBy( getElementType().getName(), mappedBy, propertyName );
		}

		if ( inheritanceStatePerClass == null) {
			throw new AssertionFailure( "inheritanceStatePerClass not set" );
		}
		metadataCollector.addSecondPass( getSecondPass(), !isMappedBy );
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
		if ( isMappedBy
				&& ( property.isAnnotationPresent( JoinColumn.class )
					|| property.isAnnotationPresent( JoinColumns.class ) ) ) {
			throw new AnnotationException( "Association '"
					+ qualify( propertyHolder.getPath(), propertyName )
					+ "' is 'mappedBy' another entity and may not specify the '@JoinColumn'" );
		}

		if ( isMappedBy
				&& propertyHolder.getJoinTable( property ) != null ) {
			throw new AnnotationException( "Association '"
					+ qualify( propertyHolder.getPath(), propertyName )
					+ "' is 'mappedBy' another entity and may not specify the '@JoinTable'" );
		}

		if ( !isMappedBy
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

		final SQLInsert sqlInsert = property.getAnnotation( SQLInsert.class );
		if ( sqlInsert != null ) {
			collection.setCustomSQLInsert(
					sqlInsert.sql().trim(),
					sqlInsert.callable(),
					fromResultCheckStyle( sqlInsert.check() )
			);

		}

		final SQLUpdate sqlUpdate = property.getAnnotation( SQLUpdate.class );
		if ( sqlUpdate != null ) {
			collection.setCustomSQLUpdate(
					sqlUpdate.sql().trim(),
					sqlUpdate.callable(),
					fromResultCheckStyle( sqlUpdate.check() )
			);
		}

		final SQLDelete sqlDelete = property.getAnnotation( SQLDelete.class );
		if ( sqlDelete != null ) {
			collection.setCustomSQLDelete(
					sqlDelete.sql().trim(),
					sqlDelete.callable(),
					fromResultCheckStyle( sqlDelete.check() )
			);
		}

		final SQLDeleteAll sqlDeleteAll = property.getAnnotation( SQLDeleteAll.class );
		if ( sqlDeleteAll != null ) {
			collection.setCustomSQLDeleteAll(
					sqlDeleteAll.sql().trim(),
					sqlDeleteAll.callable(),
					fromResultCheckStyle( sqlDeleteAll.check() )
			);
		}

		final Loader loader = property.getAnnotation( Loader.class );
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
						org.hibernate.annotations.OrderBy.class.getName()
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
						org.hibernate.annotations.OrderBy.class.getName(),
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
		handleLazy();
		handleFetch();
	}

	private void handleFetch() {
		if ( property.isAnnotationPresent( Fetch.class ) ) {
			// Hibernate @Fetch annotation takes precedence
			handleHibernateFetchMode();
		}
		else {
			collection.setFetchMode( getFetchMode( getJpaFetchType() ) );
		}
	}

	private void handleHibernateFetchMode() {
		switch ( property.getAnnotation( Fetch.class ).value() ) {
			case JOIN:
				collection.setFetchMode( FetchMode.JOIN );
				collection.setLazy( false );
				break;
			case SELECT:
				collection.setFetchMode( FetchMode.SELECT );
				break;
			case SUBSELECT:
				collection.setFetchMode( FetchMode.SELECT );
				collection.setSubselectLoadable( true );
				collection.getOwner().setSubselectLoadableCollections( true );
				break;
			default:
				throw new AssertionFailure( "unknown fetch type");
		}
	}

	private void handleLazy() {
		final FetchType jpaFetchType = getJpaFetchType();
		if ( property.isAnnotationPresent( LazyCollection.class ) ) {
			final LazyCollection lazy = property.getAnnotation( LazyCollection.class );
			boolean eager = lazy.value() == LazyCollectionOption.FALSE;
			if ( !eager && jpaFetchType == EAGER ) {
				throw new AnnotationException("Collection '" + safeCollectionRole()
						+ "' is marked 'fetch=EAGER' and '@LazyCollection(" + lazy.value() + ")'");
			}
			collection.setLazy( !eager );
			collection.setExtraLazy( lazy.value() == LazyCollectionOption.EXTRA );
		}
		else {
			collection.setLazy( jpaFetchType == LAZY );
			collection.setExtraLazy( false );
		}
	}

	private FetchType getJpaFetchType() {
		final OneToMany oneToMany = property.getAnnotation( OneToMany.class );
		final ManyToMany manyToMany = property.getAnnotation( ManyToMany.class );
		final ElementCollection elementCollection = property.getAnnotation( ElementCollection.class );
		final ManyToAny manyToAny = property.getAnnotation( ManyToAny.class );
		if ( oneToMany != null ) {
			return oneToMany.fetch();
		}
		else if ( manyToMany != null ) {
			return manyToMany.fetch();
		}
		else if ( elementCollection != null ) {
			return elementCollection.fetch();
		}
		else if ( manyToAny != null ) {
			return LAZY;
		}
		else {
			throw new AssertionFailure(
					"Define fetch strategy on a property not annotated with @ManyToOne nor @OneToMany nor @CollectionOfElements"
			);
		}
	}

	XClass getElementType() {
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

	SecondPass getSecondPass() {
		return new CollectionSecondPass( collection ) {
			@Override
			public void secondPass(Map<String, PersistentClass> persistentClasses) throws MappingException {
				bindStarToManySecondPass( persistentClasses );
			}
		};
	}

	/**
	 * return true if it's a Fk, false if it's an association table
	 */
	protected boolean bindStarToManySecondPass(Map<String, PersistentClass> persistentClasses) {
		final PersistentClass persistentClass = persistentClasses.get( getElementType().getName() );
		if ( noAssociationTable( persistentClass ) ) {
			//this is a foreign key
			bindOneToManySecondPass( persistentClasses );
			return true;
		}
		else {
			//this is an association table
			bindManyToManySecondPass( persistentClasses );
			return false;
		}
	}

	private boolean isReversePropertyInJoin(XClass elementType, PersistentClass persistentClass) {
		if ( persistentClass != null && hasMappedBy() ) {
			try {
				return persistentClass.getJoinNumber( persistentClass.getRecursiveProperty( mappedBy ) ) != 0;
			}
			catch (MappingException e) {
				throw new AnnotationException( "Collection '" + safeCollectionRole()
						+ "' is 'mappedBy' a property named '" + mappedBy
						+ "' which does not exist in the target entity '" + elementType.getName() + "'" );
			}
		}
		else {
			return false;
		}
	}

	private boolean noAssociationTable(PersistentClass persistentClass) {
		return persistentClass != null
			&& !isReversePropertyInJoin( getElementType(), persistentClass )
			&& oneToMany
			&& !isExplicitAssociationTable
			&& ( implicitJoinColumn() || explicitForeignJoinColumn() );
	}

	private boolean implicitJoinColumn() {
		return joinColumns.getJoinColumns().get(0).isImplicit()
			&& hasMappedBy(); //implicit @JoinColumn
	}

	private boolean explicitForeignJoinColumn() {
		return !foreignJoinColumns.getJoinColumns().get(0).isImplicit(); //this is an explicit @JoinColumn
	}

	private boolean hasMappedBy() {
		return isNotEmpty( mappedBy );
	}

	/**
	 * Bind a {@link OneToMany} association.
	 */
	protected void bindOneToManySecondPass(Map<String, PersistentClass> persistentClasses) {
		if ( property == null ) {
			throw new AssertionFailure( "null was passed for argument property" );
		}

		logOneToManySecondPass();

		final org.hibernate.mapping.OneToMany oneToMany =
				new org.hibernate.mapping.OneToMany( buildingContext, getCollection().getOwner() );
		collection.setElement( oneToMany );
		oneToMany.setReferencedEntityName( getElementType().getName() );
		oneToMany.setNotFoundAction( notFoundAction );

		final String referencedEntityName = oneToMany.getReferencedEntityName();
		final PersistentClass associatedClass = persistentClasses.get( referencedEntityName );
		handleJpaOrderBy( collection, associatedClass );
		if ( associatedClass == null ) {
			throw new MappingException(
					String.format( "Association [%s] for entity [%s] references unmapped class [%s]",
							propertyName, propertyHolder.getClassName(), referencedEntityName )
			);
		}
		oneToMany.setAssociatedClass( associatedClass );

		final Map<String, Join> joins = buildingContext.getMetadataCollector().getJoins( referencedEntityName );
		foreignJoinColumns.setPropertyHolder( buildPropertyHolder(
				associatedClass,
				joins,
				foreignJoinColumns.getBuildingContext(),
				inheritanceStatePerClass
		) );
		foreignJoinColumns.setJoins( joins);
		collection.setCollectionTable( foreignJoinColumns.getTable() );
		if ( LOG.isDebugEnabled() ) {
			LOG.debugf( "Mapping collection: %s -> %s", collection.getRole(), collection.getCollectionTable().getName() );
		}

		bindFilters( false );
		handleWhere( false );

		final PersistentClass targetEntity = persistentClasses.get( getElementType().getName() );
		bindCollectionSecondPass( targetEntity, foreignJoinColumns, cascadeDeleteEnabled );

		if ( !collection.isInverse() && !collection.getKey().isNullable() ) {
			createOneToManyBackref( oneToMany );
		}
	}

	private void createOneToManyBackref(org.hibernate.mapping.OneToMany oneToMany) {
		final InFlightMetadataCollector collector = buildingContext.getMetadataCollector();
		// for non-inverse one-to-many, with a not-null fk, add a backref!
		final String entityName = oneToMany.getReferencedEntityName();
		final PersistentClass referenced = collector.getEntityBinding( entityName );
		final Backref backref = new Backref();
		final String backrefName = '_' + foreignJoinColumns.getPropertyName()
				+ '_' + foreignJoinColumns.getColumns().get(0).getLogicalColumnName()
				+ "Backref";
		backref.setName( backrefName );
		backref.setUpdateable( false);
		backref.setSelectable( false );
		backref.setCollectionRole( collection.getRole() );
		backref.setEntityName( collection.getOwner().getEntityName() );
		backref.setValue( collection.getKey() );
		referenced.addProperty( backref );
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
		final Filter simpleFilter = property.getAnnotation( Filter.class );
		//set filtering
		//test incompatible choices
		//if ( StringHelper.isNotEmpty( where ) ) collection.setWhere( where );
		if ( simpleFilter != null ) {
			addFilter( hasAssociationTable, simpleFilter );
		}
		final Filters filters = getOverridableAnnotation( property, Filters.class, buildingContext );
		if ( filters != null ) {
			for ( Filter filter : filters.value() ) {
				addFilter( hasAssociationTable, filter );
			}
		}
		final FilterJoinTable simpleFilterJoinTable = property.getAnnotation( FilterJoinTable.class );
		if ( simpleFilterJoinTable != null ) {
			addFilter( hasAssociationTable, simpleFilterJoinTable );
		}
		final FilterJoinTables filterJoinTables = property.getAnnotation( FilterJoinTables.class );
		if ( filterJoinTables != null ) {
			for ( FilterJoinTable filter : filterJoinTables.value() ) {
				addFilter( hasAssociationTable, filter );
			}
		}
	}

	private void addFilter(boolean hasAssociationTable, Filter filter) {
		if ( hasAssociationTable ) {
			collection.addManyToManyFilter(
					filter.name(),
					getFilterCondition( filter ),
					filter.deduceAliasInjectionPoints(),
					toAliasTableMap( filter.aliases() ),
					toAliasEntityMap( filter.aliases() )
			);
		}
		else {
			collection.addFilter(
					filter.name(),
					getFilterCondition( filter ),
					filter.deduceAliasInjectionPoints(),
					toAliasTableMap( filter.aliases() ),
					toAliasEntityMap( filter.aliases() )
			);
		}
	}

	private void handleWhere(boolean hasAssociationTable) {
		final String whereClause = getWhereClause();
		if ( hasAssociationTable ) {
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

		final String whereJoinTableClause = getWhereJoinTableClause();
		if ( isNotEmpty( whereJoinTableClause ) ) {
			if ( hasAssociationTable ) {
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

	private String getWhereJoinTableClause() {
		final WhereJoinTable whereJoinTable = property.getAnnotation( WhereJoinTable.class );
		return whereJoinTable == null ? null : whereJoinTable.clause();
	}

	private String getWhereClause() {
		// There are 2 possible sources of "where" clauses that apply to the associated entity table:
		// 1) from the associated entity mapping; i.e., @Entity @Where(clause="...")
		//    (ignored if useEntityWhereClauseForCollections == false)
		// 2) from the collection mapping;
		//    for one-to-many, e.g., @OneToMany @JoinColumn @Where(clause="...") public Set<Rating> getRatings();
		//    for many-to-many e.g., @ManyToMany @Where(clause="...") public Set<Rating> getRatings();
		return getNonEmptyOrConjunctionIfBothNonEmpty( getWhereOnClassClause(), getWhereOnCollectionClause() );
	}

	private String getWhereOnCollectionClause() {
		final Where whereOnCollection = getOverridableAnnotation( property, Where.class, getBuildingContext() );
		return whereOnCollection != null ? whereOnCollection.clause() : null;
	}

	private String getWhereOnClassClause() {
		if ( useEntityWhereClauseForCollections() && property.getElementClass() != null ) {
			final Where whereOnClass = getOverridableAnnotation( property.getElementClass(), Where.class, getBuildingContext() );
			return whereOnClass != null ? whereOnClass.clause() : null;
		}
		else {
			return null;
		}
	}

	private boolean useEntityWhereClauseForCollections() {
		return ConfigurationHelper.getBoolean(
				AvailableSettings.USE_ENTITY_WHERE_CLAUSE_FOR_COLLECTIONS,
				buildingContext
						.getBuildingOptions()
						.getServiceRegistry()
						.getService(ConfigurationService.class)
						.getSettings(),
				true
		);
	}

	private void addFilter(boolean hasAssociationTable, FilterJoinTable filter) {
		if ( hasAssociationTable ) {
			collection.addFilter(
					filter.name(),
					getFilterConditionForJoinTable( filter ),
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

	private String getFilterConditionForJoinTable(FilterJoinTable filter) {
		final String condition = filter.condition();
		return isEmptyAnnotationValue( condition ) ? getDefaultFilterCondition( filter.name(), filter ) : condition;
	}

	private String getFilterCondition(Filter filter) {
		final String condition = filter.condition();
		return isEmptyAnnotationValue( condition ) ? getDefaultFilterCondition( filter.name(), filter ) : condition;
	}

	private String getDefaultFilterCondition(String name, Annotation annotation) {
		final FilterDefinition definition = buildingContext.getMetadataCollector().getFilterDefinition( name );
		if ( definition == null ) {
			throw new AnnotationException( "Collection '" + qualify( propertyHolder.getPath(), propertyName )
					+ "' has a '@" + annotation.annotationType().getSimpleName()
					+ "' for an undefined filter named '" + name + "'" );
		}
		final String defaultCondition = definition.getDefaultFilterCondition();
		if ( isEmpty( defaultCondition ) ) {
			throw new AnnotationException( "Collection '" + qualify( propertyHolder.getPath(), propertyName ) +
					"' has a '@"  + annotation.annotationType().getSimpleName()
					+ "' with no 'condition' and no default condition was given by the '@FilterDef' named '"
					+ name + "'" );
		}
		return defaultCondition;
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

	private DependantValue buildCollectionKey(
			Collection collection,
			AnnotatedJoinColumns joinColumns,
			boolean cascadeDeleteEnabled,
			boolean noConstraintByDefault,
			XProperty property,
			PropertyHolder propertyHolder) {

		// give a chance to override the referenced property name
		// has to do that here because the referencedProperty creation happens in a FKSecondPass for ManyToOne yuk!
		overrideReferencedPropertyName( collection, joinColumns );

		final String referencedPropertyName = collection.getReferencedPropertyName();
		//binding key reference using column
		final PersistentClass owner = collection.getOwner();
		final KeyValue keyValue = referencedPropertyName == null
				? owner.getIdentifier()
				: (KeyValue) owner.getReferencedProperty( referencedPropertyName ).getValue();

		final DependantValue key = new DependantValue( buildingContext, collection.getCollectionTable(), keyValue );
		key.setTypeName( null );
		joinColumns.checkPropertyConsistency();
		final List<AnnotatedColumn> columns = joinColumns.getColumns();
		key.setNullable( columns.isEmpty() || columns.get(0).isNullable() );
		key.setUpdateable( columns.isEmpty() || columns.get(0).isUpdatable() );
		key.setCascadeDeleteEnabled( cascadeDeleteEnabled );
		collection.setKey( key );

		if ( property != null ) {
			final org.hibernate.annotations.ForeignKey fk = property.getAnnotation( org.hibernate.annotations.ForeignKey.class );
			if ( fk != null && !isEmptyAnnotationValue( fk.name() ) ) {
				key.setForeignKeyName( fk.name() );
			}
			else {
				final CollectionTable collectionTableAnn = property.getAnnotation( CollectionTable.class );
				if ( collectionTableAnn != null ) {
					final ForeignKey foreignKey = collectionTableAnn.foreignKey();
					if ( foreignKey.value() == ConstraintMode.NO_CONSTRAINT
							|| foreignKey.value() == ConstraintMode.PROVIDER_DEFAULT && noConstraintByDefault ) {
						key.disableForeignKey();
					}
					else {
						key.setForeignKeyName( nullIfEmpty( foreignKey.name() ) );
						key.setForeignKeyDefinition( nullIfEmpty( foreignKey.foreignKeyDefinition() ) );
						if ( key.getForeignKeyName() == null
								&& key.getForeignKeyDefinition() == null
								&& collectionTableAnn.joinColumns().length == 1 ) {
							final JoinColumn joinColumn = collectionTableAnn.joinColumns()[0];
							key.setForeignKeyName( nullIfEmpty( joinColumn.foreignKey().name() ) );
							key.setForeignKeyDefinition( nullIfEmpty( joinColumn.foreignKey().foreignKeyDefinition() ) );
						}
					}
				}
				else {
					final JoinTable joinTableAnn = property.getAnnotation( JoinTable.class );
					if ( joinTableAnn != null ) {
						final ForeignKey foreignKey = joinTableAnn.foreignKey();
						String foreignKeyName = foreignKey.name();
						String foreignKeyDefinition = foreignKey.foreignKeyDefinition();
						ConstraintMode foreignKeyValue = foreignKey.value();
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
						final String propertyPath = qualify( propertyHolder.getPath(), property.getName() );
						final ForeignKey foreignKey = propertyHolder.getOverriddenForeignKey( propertyPath );
						if ( foreignKey != null ) {
							handleForeignKeyConstraint( noConstraintByDefault, key, foreignKey );
						}
						else {
							final OneToMany oneToManyAnn = property.getAnnotation( OneToMany.class );
							final OnDelete onDeleteAnn = property.getAnnotation( OnDelete.class );
							if ( oneToManyAnn != null
									&& !oneToManyAnn.mappedBy().isEmpty()
									&& ( onDeleteAnn == null || onDeleteAnn.action() != OnDeleteAction.CASCADE ) ) {
								// foreign key should be up to @ManyToOne side
								// @OnDelete generate "on delete cascade" foreign key
								key.disableForeignKey();
							}
							else {
								final JoinColumn joinColumnAnn = property.getAnnotation( JoinColumn.class );
								if ( joinColumnAnn != null ) {
									handleForeignKeyConstraint( noConstraintByDefault, key, joinColumnAnn.foreignKey() );
								}
							}
						}
					}
				}
			}
		}

		return key;
	}

	private static void handleForeignKeyConstraint(boolean noConstraintByDefault, DependantValue key, ForeignKey foreignKey) {
		final ConstraintMode constraintMode = foreignKey.value();
		if ( constraintMode == ConstraintMode.NO_CONSTRAINT
				|| constraintMode == ConstraintMode.PROVIDER_DEFAULT && noConstraintByDefault) {
			key.disableForeignKey();
		}
		else {
			key.setForeignKeyName( nullIfEmpty( foreignKey.name() ) );
			key.setForeignKeyDefinition( nullIfEmpty( foreignKey.foreignKeyDefinition() ) );
		}
	}

	private void overrideReferencedPropertyName(Collection collection, AnnotatedJoinColumns joinColumns) {
		if ( hasMappedBy() && !joinColumns.getColumns().isEmpty() ) {
			final String entityName = joinColumns.getManyToManyOwnerSideEntityName() != null
					? "inverse__" + joinColumns.getManyToManyOwnerSideEntityName()
					: joinColumns.getPropertyHolder().getEntityName();
			final InFlightMetadataCollector collector = buildingContext.getMetadataCollector();
			final String referencedProperty = collector.getPropertyReferencedAssociation( entityName, mappedBy );
			if ( referencedProperty != null ) {
				collection.setReferencedPropertyName( referencedProperty );
				collector.addPropertyReference( collection.getOwnerEntityName(), referencedProperty );
			}
		}
	}

	/**
	 * Bind a {@link ManyToMany} association or {@link ElementCollection}.
	 */
	private void bindManyToManySecondPass(Map<String, PersistentClass> persistentClasses) throws MappingException {
		if ( property == null ) {
			throw new AssertionFailure( "null was passed for argument property" );
		}

		final XClass elementType = getElementType();
		final PersistentClass targetEntity = persistentClasses.get( elementType.getName() ); //null if this is an @ElementCollection
		final String hqlOrderBy = extractHqlOrderBy( jpaOrderBy );

		final boolean isCollectionOfEntities = targetEntity != null;
		final boolean isManyToAny = property.isAnnotationPresent( ManyToAny.class );

		logManyToManySecondPass( oneToMany, isCollectionOfEntities, isManyToAny );

		//check for user error
		detectManyToManyProblems(
				elementType,
				property,
				propertyHolder,
				isCollectionOfEntities,
				isManyToAny
		);

		if ( hasMappedBy() ) {
			handleUnownedManyToMany(
					collection,
					joinColumns,
					elementType,
					targetEntity,
					isCollectionOfEntities
			);
		}
		else {
			handleOwnedManyToMany(
					collection,
					joinColumns,
					tableBinder,
					property,
					buildingContext,
					targetEntity,
					isCollectionOfEntities
			);
		}
		bindFilters( isCollectionOfEntities );
		handleWhere( isCollectionOfEntities );

		bindCollectionSecondPass( targetEntity, joinColumns, cascadeDeleteEnabled );

		if ( isCollectionOfEntities ) {
			final ManyToOne element = handleCollectionOfEntities(
					collection,
					elementType,
					notFoundAction,
					property,
					buildingContext,
					targetEntity,
					hqlOrderBy
			);
			bindManyToManyInverseForeignKey( targetEntity, inverseJoinColumns, element, oneToMany );
		}
		else if ( isManyToAny ) {
			handleManyToAny(
					collection,
					inverseJoinColumns,
					cascadeDeleteEnabled,
					property,
					buildingContext
			);
		}
		else {
			handleElementCollection(
					collection,
					elementColumns,
					isEmbedded,
					elementType,
					property,
					propertyHolder,
					hqlOrderBy
			);
		}

		checkFilterConditions( collection );
	}

	private void handleElementCollection(
			Collection collection,
			AnnotatedColumns elementColumns,
			boolean isEmbedded,
			XClass elementType,
			XProperty property,
			PropertyHolder parentPropertyHolder,
			String hqlOrderBy) {
		// 'parentPropertyHolder' is the PropertyHolder for the owner of the collection
		// 'holder' is the CollectionPropertyHolder.
		// 'property' is the collection XProperty

		final XClass elementClass = isPrimitive( elementType.getName() ) ? null : elementType;
		final AnnotatedClassType classType = annotatedElementType( isEmbedded, property, elementType );
		final boolean primitive = classType == AnnotatedClassType.NONE;
		if ( !primitive ) {
			parentPropertyHolder.startingProperty( property );
		}

		final CollectionPropertyHolder holder = buildPropertyHolder(
				collection,
				collection.getRole(),
				elementClass,
				property,
				parentPropertyHolder,
				buildingContext
		);
		holder.prepare( property );

		final Class<? extends CompositeUserType<?>> compositeUserType =
				resolveCompositeUserType( property, elementClass, buildingContext );
		if ( classType == AnnotatedClassType.EMBEDDABLE || compositeUserType != null ) {
			handleCompositeCollectionElement( collection, property, hqlOrderBy, elementClass, holder, compositeUserType );
		}
		else {
			handleCollectionElement( collection, elementColumns, elementType, property, hqlOrderBy, elementClass, holder );
		}
	}

	private void handleCollectionElement(
			Collection collection,
			AnnotatedColumns elementColumns,
			XClass elementType,
			XProperty property,
			String hqlOrderBy,
			XClass elementClass,
			CollectionPropertyHolder holder) {
		final BasicValueBinder elementBinder =
				new BasicValueBinder( BasicValueBinder.Kind.COLLECTION_ELEMENT, buildingContext );
		elementBinder.setReturnedClassName( elementType.getName() );
		final AnnotatedColumns actualColumns = createElementColumnsIfNecessary(
				collection,
				elementColumns,
				Collection.DEFAULT_ELEMENT_COLUMN_NAME,
				null,
				buildingContext
		);
		elementBinder.setColumns( actualColumns );
		elementBinder.setType(
				property,
				elementClass,
				collection.getOwnerEntityName(),
				holder.resolveElementAttributeConverterDescriptor(property, elementClass)
		);
		elementBinder.setPersistentClassName( propertyHolder.getEntityName() );
		elementBinder.setAccessType( accessType );
		collection.setElement( elementBinder.make() );
		final String orderBy = adjustUserSuppliedValueCollectionOrderingFragment(hqlOrderBy);
		if ( orderBy != null ) {
			collection.setOrderBy( orderBy );
		}
	}

	private void handleCompositeCollectionElement(
			Collection collection,
			XProperty property,
			String hqlOrderBy,
			XClass elementClass,
			CollectionPropertyHolder holder,
			Class<? extends CompositeUserType<?>> compositeUserType) {
		//TODO be smart with isNullable
		final Component component = fillComponent(
				holder,
				getSpecialMembers( elementClass ),
				accessType( property, collection.getOwner() ),
				true,
				new EntityBinder(),
				false,
				false,
				true,
				resolveCustomInstantiator( property, elementClass, buildingContext ),
				compositeUserType,
				buildingContext,
				inheritanceStatePerClass
		);
		collection.setElement( component );
		if ( isNotEmpty( hqlOrderBy ) ) {
			final String orderBy = adjustUserSuppliedValueCollectionOrderingFragment( hqlOrderBy );
			if ( orderBy != null ) {
				collection.setOrderBy( orderBy );
			}
		}
	}

	static AccessType accessType(XProperty property, PersistentClass owner) {
		final Access accessAnn = property.getAnnotation( Access.class );
		if ( accessAnn != null ) {
			// the attribute is locally annotated with `@Access`, use that
			return accessAnn.value() == PROPERTY
					? AccessType.PROPERTY
					: AccessType.FIELD;
		}
		else if ( owner.getIdentifierProperty() != null ) {
			// use the access for the owning entity's id attribute, if one
			return owner.getIdentifierProperty().getPropertyAccessorName().equals( "property" )
					? AccessType.PROPERTY
					: AccessType.FIELD;
		}
		else if ( owner.getIdentifierMapper() != null && owner.getIdentifierMapper().getPropertySpan() > 0 ) {
			// use the access for the owning entity's "id mapper", if one
			return owner.getIdentifierMapper().getProperties().get(0).getPropertyAccessorName().equals( "property" )
					? AccessType.PROPERTY
					: AccessType.FIELD;
		}
		else {
			// otherwise...
			throw new AssertionFailure( "Unable to guess collection property accessor name" );
		}
	}

	AnnotatedClassType annotatedElementType(
			boolean isEmbedded,
			XProperty property,
			XClass elementType) {
		if ( isPrimitive( elementType.getName() ) ) {
			return AnnotatedClassType.NONE;
		}
		else {
			//force in case of attribute override
			final boolean attributeOverride = property.isAnnotationPresent( AttributeOverride.class )
					|| property.isAnnotationPresent( AttributeOverrides.class );
			// todo : force in the case of Convert annotation(s) with embedded paths (beyond key/value prefixes)?
			return isEmbedded || attributeOverride
					? AnnotatedClassType.EMBEDDABLE
					: buildingContext.getMetadataCollector().getClassType( elementType );
		}
	}

	static AnnotatedColumns createElementColumnsIfNecessary(
			Collection collection,
			AnnotatedColumns elementColumns,
			String defaultName,
			Long defaultLength,
			MetadataBuildingContext context) {
		if ( elementColumns == null || elementColumns.getColumns().isEmpty() ) {
			final AnnotatedColumns columns = new AnnotatedColumns();
			columns.setBuildingContext( context );
			final AnnotatedColumn column = new AnnotatedColumn();
			column.setLogicalColumnName( defaultName );
			if ( defaultLength != null ) {
				column.setLength( defaultLength );
			}
			column.setImplicit( false );
			//not following the spec but more clean
			column.setNullable( true );
//			column.setContext( context );
			column.setParent( columns );
			column.bind();
			elementColumns = columns;
		}
		//override the table
		elementColumns.setTable( collection.getCollectionTable() );
		return elementColumns;
	}

	private ManyToOne handleCollectionOfEntities(
			Collection collection,
			XClass elementType,
			NotFoundAction notFoundAction,
			XProperty property,
			MetadataBuildingContext buildingContext,
			PersistentClass collectionEntity,
			String hqlOrderBy) {
		final ManyToOne element = new ManyToOne( buildingContext,  collection.getCollectionTable() );
		collection.setElement( element );
		element.setReferencedEntityName( elementType.getName() );
		//element.setFetchMode( fetchMode );
		//element.setLazy( fetchMode != FetchMode.JOIN );
		//make the second join non lazy
		element.setFetchMode( FetchMode.JOIN );
		element.setLazy( false );
		element.setNotFoundAction( notFoundAction );
		// as per 11.1.38 of JPA 2.0 spec, default to primary key if no column is specified by @OrderBy.
		if ( hqlOrderBy != null ) {
			collection.setManyToManyOrdering( buildOrderByClauseFromHql( hqlOrderBy, collectionEntity ) );
		}

		final org.hibernate.annotations.ForeignKey fk = property.getAnnotation( org.hibernate.annotations.ForeignKey.class );
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
			Collection collection,
			AnnotatedJoinColumns inverseJoinColumns,
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

		final XProperty prop = inferredData.getProperty();
		final jakarta.persistence.Column discriminatorColumnAnn = prop.getAnnotation( jakarta.persistence.Column.class );
		final Formula discriminatorFormulaAnn = getOverridableAnnotation( prop, Formula.class, buildingContext);

		//override the table
		inverseJoinColumns.setTable( collection.getCollectionTable() );

		final ManyToAny anyAnn = property.getAnnotation( ManyToAny.class );
		final Any any = buildAnyValue(
				discriminatorColumnAnn,
				discriminatorFormulaAnn,
				inverseJoinColumns,
				inferredData,
				cascadeDeleteEnabled,
				anyAnn.fetch() == LAZY,
				Nullability.NO_CONSTRAINT,
				propertyHolder,
				new EntityBinder(),
				true,
				buildingContext
		);
		collection.setElement( any );
	}

	private PropertyData getSpecialMembers(XClass elementClass) {
		if ( isMap() ) {
			//"value" is the JPA 2 prefix for map values (used to be "element")
			if ( isHibernateExtensionMapping() ) {
				return new PropertyPreloadedData( AccessType.PROPERTY, "element", elementClass );
			}
			else {
				return new PropertyPreloadedData( AccessType.PROPERTY, "value", elementClass );
			}
		}
		else {
			if ( isHibernateExtensionMapping() ) {
				return new PropertyPreloadedData( AccessType.PROPERTY, "element", elementClass );
			}
			else {
				//"collection&&element" is not a valid property name => placeholder
				return new PropertyPreloadedData( AccessType.PROPERTY, "collection&&element", elementClass );
			}
		}
	}

	private void handleOwnedManyToMany(
			Collection collection,
			AnnotatedJoinColumns joinColumns,
			TableBinder associationTableBinder,
			XProperty property,
			MetadataBuildingContext context,
			PersistentClass collectionEntity,
			boolean isCollectionOfEntities) {
		//TODO: only for implicit columns?
		//FIXME NamingStrategy
		final InFlightMetadataCollector collector = context.getMetadataCollector();
		final PersistentClass owner = collection.getOwner();
		joinColumns.setMappedBy(
				owner.getEntityName(),
				collector.getLogicalTableName( owner.getTable() ),
				collector.getFromMappedBy( owner.getEntityName(), joinColumns.getPropertyName() )
		);
		if ( isEmpty( associationTableBinder.getName() ) ) {
			//default value
			associationTableBinder.setDefaultName(
					owner.getClassName(),
					owner.getEntityName(),
					owner.getJpaEntityName(),
					collector.getLogicalTableName( owner.getTable() ),
					collectionEntity != null ? collectionEntity.getClassName() : null,
					collectionEntity != null ? collectionEntity.getEntityName() : null,
					collectionEntity != null ? collectionEntity.getJpaEntityName() : null,
					collectionEntity != null ? collector.getLogicalTableName( collectionEntity.getTable() ) : null,
					joinColumns.getPropertyName()
			);
		}
		associationTableBinder.setJPA2ElementCollection(
				!isCollectionOfEntities && property.isAnnotationPresent(ElementCollection.class)
		);
		collection.setCollectionTable( associationTableBinder.bind() );
	}

	private void handleUnownedManyToMany(
			Collection collection,
			AnnotatedJoinColumns joinColumns,
			XClass elementType,
			PersistentClass collectionEntity,
			boolean isCollectionOfEntities) {

		if ( !isCollectionOfEntities) {
			throw new AnnotationException( "Association '" + safeCollectionRole()
							+ "' targets the type '" + elementType.getName() + "' which is not an '@Entity' type" );
		}

		joinColumns.setManyToManyOwnerSideEntityName( collectionEntity.getEntityName() );

		final Property otherSideProperty;
		try {
			otherSideProperty = collectionEntity.getRecursiveProperty( mappedBy );
		}
		catch (MappingException e) {
			throw new AnnotationException( "Association '" + safeCollectionRole() +
					"is 'mappedBy' a property named '" + mappedBy
					+ "' which does not exist in the target entity '" + elementType.getName() + "'" );
		}
		final Value otherSidePropertyValue = otherSideProperty.getValue();
		final Table table = otherSidePropertyValue instanceof Collection
				// this is a collection on the other side
				? ( (Collection) otherSidePropertyValue ).getCollectionTable()
				// this is a ToOne with a @JoinTable or a regular property
				: otherSidePropertyValue.getTable();
		collection.setCollectionTable( table );
	}

	private void detectManyToManyProblems(
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
				final JoinTable joinTableAnn = parentPropertyHolder.getJoinTable( property );
				if ( joinTableAnn != null && joinTableAnn.inverseJoinColumns().length > 0 ) {
					throw new AnnotationException( "Association '" + safeCollectionRole()
							+ " has a '@JoinTable' with 'inverseJoinColumns' and targets the type '"
							+ elementType.getName() + "' which is not an '@Entity' type" );
				}
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
		else if ( returnedClass != null ) {
			final Class<?> embeddableClass = context.getBootstrapContext().getReflectionManager()
					.toClass( returnedClass );
			return embeddableClass == null ? null
					: context.getMetadataCollector().findRegisteredCompositeUserType( embeddableClass );
		}
		else {
			return null;
		}
	}

	private String extractHqlOrderBy(OrderBy jpaOrderBy) {
		if ( jpaOrderBy != null ) {
			return jpaOrderBy.value(); // Null not possible. In case of empty expression, apply default ordering.
		}
		else {
			return null; // @OrderBy not found.
		}
	}

	private static void checkFilterConditions(Collection collection) {
		//for now it can't happen, but sometime soon...
		if ( ( collection.getFilters().size() != 0 || isNotEmpty( collection.getWhere() ) )
				&& collection.getFetchMode() == FetchMode.JOIN
				&& !( collection.getElement() instanceof SimpleValue ) //SimpleValue (CollectionOfElements) are always SELECT but it does not matter
				&& collection.getElement().getFetchMode() != FetchMode.JOIN ) {
			throw new MappingException(
					"@ManyToMany or @ElementCollection defining filter or where without join fetching "
							+ "not valid within collection using join fetching[" + collection.getRole() + "]"
			);
		}
	}

	private void bindCollectionSecondPass(
			PersistentClass targetEntity,
			AnnotatedJoinColumns joinColumns,
			boolean cascadeDeleteEnabled) {

		if ( !hasMappedBy() ) {
			createSyntheticPropertyReference(
					joinColumns,
					collection.getOwner(),
					collection.getOwner(),
					collection,
					propertyName,
					false,
					buildingContext
			);
		}

		final DependantValue key = buildCollectionKey(
				collection,
				joinColumns,
				cascadeDeleteEnabled,
				buildingContext.getBuildingOptions().isNoConstraintByDefault(),
				property,
				propertyHolder
		);

		if ( property.isAnnotationPresent( ElementCollection.class ) ) {
			joinColumns.setElementCollection( true );
		}

		TableBinder.bindForeignKey(
				collection.getOwner(),
				targetEntity,
				joinColumns,
				key,
				false,
				buildingContext
		);
		key.sortProperties();
	}

	public void setCascadeDeleteEnabled(boolean onDeleteCascade) {
		this.cascadeDeleteEnabled = onDeleteCascade;
	}

	String safeCollectionRole() {
		return propertyHolder != null ? propertyHolder.getEntityName() + "." + propertyName : "";
	}

	/**
	 * Bind the inverse foreign key of a {@link ManyToMany}, that is, the columns
	 * specified by {@code @JoinTable(inverseJoinColumns=...)}, which are the
	 * columns that reference the target entity of the many-to-many association.
	 * If we are in a {@code mappedBy} case, read the columns from the associated
	 * collection element in the target entity.
	 */
	public void bindManyToManyInverseForeignKey(
			PersistentClass targetEntity,
			AnnotatedJoinColumns joinColumns,
			SimpleValue value,
			boolean unique) {
		if ( hasMappedBy() ) {
			bindUnownedManyToManyInverseForeignKey( targetEntity, joinColumns, value );
		}
		else {
			bindOwnedManyToManyForeignKeyMappedBy( targetEntity, joinColumns, value, unique );
		}
	}

	private void bindOwnedManyToManyForeignKeyMappedBy(
			PersistentClass targetEntity,
			AnnotatedJoinColumns joinColumns,
			SimpleValue value,
			boolean unique) { // true when it's actually a logical @OneToMany
		createSyntheticPropertyReference(
				joinColumns,
				targetEntity,
				collection.getOwner(),
				value,
				propertyName,
				true,
				buildingContext
		);
		if ( notFoundAction == NotFoundAction.IGNORE ) {
			value.disableForeignKey();
		}
		TableBinder.bindForeignKey(
				targetEntity,
				collection.getOwner(),
				joinColumns,
				value,
				unique,
				buildingContext
		);
	}

	private void bindUnownedManyToManyInverseForeignKey(
			PersistentClass targetEntity,
			AnnotatedJoinColumns joinColumns,
			SimpleValue value) {
		final Property property = targetEntity.getRecursiveProperty( mappedBy );
		final List<Selectable> mappedByColumns = mappedByColumns(targetEntity, property );
		final AnnotatedJoinColumn firstColumn = joinColumns.getJoinColumns().get(0);
		for ( Selectable selectable: mappedByColumns ) {
			firstColumn.linkValueUsingAColumnCopy( (Column) selectable, value);
		}
		final String referencedPropertyName = buildingContext.getMetadataCollector()
				.getPropertyReferencedAssociation( targetEntity.getEntityName(), mappedBy );
		if ( referencedPropertyName != null ) {
			//TODO always a many to one?
			( (ManyToOne) value).setReferencedPropertyName( referencedPropertyName );
			buildingContext.getMetadataCollector()
					.addUniquePropertyReference( targetEntity.getEntityName(), referencedPropertyName );
		}
		( (ManyToOne) value).setReferenceToPrimaryKey( referencedPropertyName == null );
		value.createForeignKey();
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

	public void setFkJoinColumns(AnnotatedJoinColumns annotatedJoinColumns) {
		this.foreignJoinColumns = annotatedJoinColumns;
	}

	public void setExplicitAssociationTable(boolean explicitAssocTable) {
		this.isExplicitAssociationTable = explicitAssocTable;
	}

	public void setElementColumns(AnnotatedColumns elementColumns) {
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

	public void setMapKeyColumns(AnnotatedColumns mapKeyColumns) {
		this.mapKeyColumns = mapKeyColumns;
	}

	public void setMapKeyManyToManyColumns(AnnotatedJoinColumns mapJoinColumns) {
		this.mapKeyManyToManyColumns = mapJoinColumns;
	}

	public void setLocalGenerators(Map<String, IdentifierGeneratorDefinition> localGenerators) {
		this.localGenerators = localGenerators;
	}

	private void logOneToManySecondPass() {
		if ( LOG.isDebugEnabled() ) {
			LOG.debugf( "Binding a OneToMany: %s through a foreign key", safeCollectionRole() );
		}
	}

	private void logManyToManySecondPass(
			boolean isOneToMany,
			boolean isCollectionOfEntities,
			boolean isManyToAny) {
		if ( LOG.isDebugEnabled() ) {
			if ( isCollectionOfEntities && isOneToMany ) {
				LOG.debugf( "Binding a OneToMany: %s through an association table", safeCollectionRole() );
			}
			else if ( isCollectionOfEntities ) {
				LOG.debugf( "Binding a ManyToMany: %s", safeCollectionRole() );
			}
			else if ( isManyToAny ) {
				LOG.debugf( "Binding a ManyToAny: %s", safeCollectionRole() );
			}
			else {
				LOG.debugf( "Binding a collection of element: %s", safeCollectionRole() );
			}
		}
	}

}
