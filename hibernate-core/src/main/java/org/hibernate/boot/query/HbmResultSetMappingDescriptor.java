/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.boot.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.hibernate.LockMode;
import org.hibernate.MappingException;
import org.hibernate.boot.BootLogging;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryCollectionLoadReturnType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryJoinReturnType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryPropertyReturnType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryReturnType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryScalarReturnType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmResultSetMappingType;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.metamodel.mapping.EntityDiscriminatorMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.internal.FetchMementoBasicStandard;
import org.hibernate.query.internal.FetchMementoHbmStandard;
import org.hibernate.query.internal.FetchMementoHbmStandard.FetchParentMemento;
import org.hibernate.query.internal.ModelPartResultMementoBasicImpl;
import org.hibernate.query.internal.NamedResultSetMappingMementoImpl;
import org.hibernate.query.internal.ResultMementoBasicStandard;
import org.hibernate.query.internal.ResultMementoCollectionStandard;
import org.hibernate.query.internal.ResultMementoEntityStandard;
import org.hibernate.query.internal.ResultSetMappingResolutionContext;
import org.hibernate.query.named.FetchMemento;
import org.hibernate.query.named.NamedResultSetMappingMemento;
import org.hibernate.query.named.ResultMemento;
import org.hibernate.query.named.ResultMementoBasic;
import org.hibernate.sql.results.graph.Fetchable;
import org.hibernate.sql.results.graph.FetchableContainer;
import org.hibernate.type.BasicType;

/**
 * Boot-time descriptor of a result-set mapping as defined in an `hbm.xml` file
 * either implicitly or explicitly
 *
 * @author Steve Ebersole
 */
public class HbmResultSetMappingDescriptor implements NamedResultSetMappingDescriptor {

	private final String registrationName;
	private final List<ResultDescriptor> resultDescriptors;
	private final Map<String, Map<String, JoinDescriptor>> joinDescriptors;
	private final Map<String, HbmFetchParent> fetchParentByAlias;


	/**
	 * Constructor for an explicit `<resultset/>` mapping.
	 */
	public HbmResultSetMappingDescriptor(
			JaxbHbmResultSetMappingType hbmResultSetMapping,
			MetadataBuildingContext context) {
		this.registrationName = hbmResultSetMapping.getName();

		BootLogging.LOGGER.debugf(
				"Creating explicit HbmResultSetMappingDescriptor : %s",
				registrationName
		);

		final List<?> hbmValueMappingsSource = hbmResultSetMapping.getValueMappingSources();
		final List<ResultDescriptor> localResultDescriptors = CollectionHelper.arrayList( hbmValueMappingsSource.size() );
		this.joinDescriptors = new HashMap<>();
		this.fetchParentByAlias = new HashMap<>();

		boolean foundCollectionReturn = false;

		//noinspection ForLoopReplaceableByForEach
		for ( int i = 0; i < hbmValueMappingsSource.size(); i++ ) {
			final Object hbmValueMapping = hbmValueMappingsSource.get( i );

			if ( hbmValueMapping == null ) {
				throw new IllegalStateException(
						"ValueMappingSources contained null reference(s)"
				);
			}

			if ( hbmValueMapping instanceof JaxbHbmNativeQueryReturnType ) {
				final JaxbHbmNativeQueryReturnType hbmEntityReturn = (JaxbHbmNativeQueryReturnType) hbmValueMapping;

				final EntityResultDescriptor entityResultDescriptor = new EntityResultDescriptor(
						hbmEntityReturn,
						() -> joinDescriptors,
						registrationName,
						context
				);
				localResultDescriptors.add( entityResultDescriptor );
				fetchParentByAlias.put( entityResultDescriptor.tableAlias, entityResultDescriptor );
			}
			else if ( hbmValueMapping instanceof JaxbHbmNativeQueryCollectionLoadReturnType ) {
				final JaxbHbmNativeQueryCollectionLoadReturnType hbmCollectionReturn = (JaxbHbmNativeQueryCollectionLoadReturnType) hbmValueMapping;
				foundCollectionReturn = true;

				final CollectionResultDescriptor collectionResultDescriptor = new CollectionResultDescriptor(
						hbmCollectionReturn,
						() -> joinDescriptors,
						registrationName,
						context
				);
				localResultDescriptors.add( collectionResultDescriptor );
				fetchParentByAlias.put( collectionResultDescriptor.tableAlias, collectionResultDescriptor );
			}
			else if ( hbmValueMapping instanceof JaxbHbmNativeQueryJoinReturnType ) {
				final JaxbHbmNativeQueryJoinReturnType jaxbHbmJoinReturn = (JaxbHbmNativeQueryJoinReturnType) hbmValueMapping;

				collectJoinFetch( jaxbHbmJoinReturn, joinDescriptors, fetchParentByAlias, registrationName, context );
			}
			else if ( hbmValueMapping instanceof JaxbHbmNativeQueryScalarReturnType ) {
				final JaxbHbmNativeQueryScalarReturnType hbmScalarReturn = (JaxbHbmNativeQueryScalarReturnType) hbmValueMapping;

				localResultDescriptors.add( new ScalarDescriptor( hbmScalarReturn ) );
			}
			else {
				throw new IllegalArgumentException(
						"Unknown NativeQueryReturn type : " + hbmValueMapping.getClass().getName()
				);
			}
		}

		if ( foundCollectionReturn && localResultDescriptors.size() > 1 ) {
			throw new MappingException(
					"Cannot combine other returns with a collection return (" + registrationName + ")"
			);
		}

		this.resultDescriptors = localResultDescriptors;
	}

	public static void collectJoinFetch(
			JaxbHbmNativeQueryJoinReturnType jaxbHbmJoin,
			Map<String, Map<String, JoinDescriptor>> joinDescriptors,
			Map<String, HbmFetchParent> fetchParentByAlias,
			String registrationName,
			MetadataBuildingContext context) {
		// property path is in the form `{ownerAlias}.{joinedPath}`.  Split it into the 2 parts
		final String fullPropertyPath = jaxbHbmJoin.getProperty();
		final int firstDot = fullPropertyPath.indexOf( '.' );
		if ( firstDot < 1 ) {
			throw new MappingException(
					"Illegal <return-join/> property attribute: `" + fullPropertyPath + "`.  Should"
					+ "be in the form `{ownerAlias.joinedPropertyPath}` (" + registrationName + ")"
			);
		}

		final String ownerTableAlias = fullPropertyPath.substring( 0, firstDot - 1 );
		final String propertyPath = fullPropertyPath.substring( firstDot + 1 );
		final String tableAlias = jaxbHbmJoin.getAlias();

		Map<String, JoinDescriptor> joinDescriptorsForAlias = joinDescriptors.get( ownerTableAlias );
		//noinspection Java8MapApi
		if ( joinDescriptorsForAlias == null ) {
			joinDescriptorsForAlias = new HashMap<>();
			joinDescriptors.put( ownerTableAlias, joinDescriptorsForAlias );
		}

		final JoinDescriptor existing = joinDescriptorsForAlias.get( propertyPath );
		if ( existing != null ) {
			throw new MappingException(
					"Property join specified twice for join-return `" + ownerTableAlias + "." + propertyPath
							+ "` (" + registrationName + ")"
			);
		}

		final JoinDescriptor joinDescriptor = new JoinDescriptor(
				jaxbHbmJoin,
				() -> joinDescriptors,
				() -> fetchParentByAlias,
				registrationName,
				context
		);
		joinDescriptorsForAlias.put( propertyPath, joinDescriptor );
		fetchParentByAlias.put( tableAlias, joinDescriptor );
	}


	/**
	 * Constructor for an implicit resultset mapping defined inline as part of a `<sql-query/>`
	 * stanza
	 */
	public HbmResultSetMappingDescriptor(
			String registrationName,
			List<ResultDescriptor> resultDescriptors,
			Map<String, Map<String, JoinDescriptor>> joinDescriptors,
			Map<String,HbmFetchParent> fetchParentsByAlias) {
		this.registrationName = registrationName;
		this.resultDescriptors = resultDescriptors;
		this.joinDescriptors = joinDescriptors;

		assert fetchParentsByAlias != null;
		assert joinDescriptors != null;

		this.fetchParentByAlias = fetchParentsByAlias;

//		resultDescriptors.forEach(
//				resultDescriptor -> {
//					if ( resultDescriptor instanceof EntityResultDescriptor ) {
//						final EntityResultDescriptor entityResultDescriptor = (EntityResultDescriptor) resultDescriptor;
//						fetchParentByAlias.put( entityResultDescriptor.tableAlias, entityResultDescriptor );
//					}
//					else if ( resultDescriptor instanceof CollectionResultDescriptor ) {
//						final CollectionResultDescriptor collectionResultDescriptor = (CollectionResultDescriptor) resultDescriptor;
//						fetchParentByAlias.put( collectionResultDescriptor.tableAlias, collectionResultDescriptor );
//					}
//				}
//		);
//
//		joinDescriptors.forEach(
//				(ownerAlias, joinsForOwner) -> joinsForOwner.forEach(
//						(path, joinDescriptor) -> {
//							final HbmFetchParent existing = fetchParentByAlias.get( path );
//							if ( existing != null ) {
//								throw new MappingException(
//										"Found 2 return descriptors with the same alias: ["
//												+ joinDescriptor + "] & [" + existing + "]"
//								);
//							}
//
//							fetchParentByAlias.put( joinDescriptor.tableAlias, joinDescriptor );
//						}
//				)
//		);
	}

	@Override
	public String getRegistrationName() {
		return registrationName;
	}

	@Override
	public NamedResultSetMappingMemento resolve(ResultSetMappingResolutionContext resolutionContext) {
		BootQueryLogging.LOGGER.debugf(
				"Resolving HbmResultSetMappingDescriptor into memento for [%s]",
				registrationName
		);

		final List<ResultMemento> resultMementos = new ArrayList<>( resultDescriptors.size() );
		resultDescriptors.forEach(
				(descriptor) -> resultMementos.add( descriptor.resolve( resolutionContext ) )
		);

		return new NamedResultSetMappingMementoImpl( registrationName, resultMementos );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// `hbm.xml` returns

	public interface HbmFetchDescriptor extends FetchDescriptor {
		String getFetchablePath();
	}

	public interface HbmFetchParent {
		FetchParentMemento resolveParentMemento(ResultSetMappingResolutionContext resolutionContext);
	}

	public static class HbmFetchParentMemento implements FetchParentMemento {
		private final NavigablePath navigablePath;
		private final FetchableContainer fetchableContainer;

		public HbmFetchParentMemento(
				NavigablePath navigablePath,
				FetchableContainer fetchableContainer) {
			this.navigablePath = navigablePath;
			this.fetchableContainer = fetchableContainer;
		}

		@Override
		public NavigablePath getNavigablePath() {
			return navigablePath;
		}

		@Override
		public FetchableContainer getFetchableContainer() {
			return fetchableContainer;
		}
	}

	/**
	 * @see org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryReturnType
	 */
	public static class EntityResultDescriptor implements ResultDescriptor, HbmFetchParent {
		private final String entityName;
		private final String tableAlias;
		private final String discriminatorColumnAlias;

		private final LockMode lockMode;

		private final List<HbmFetchDescriptor> propertyFetchDescriptors;

		private final Supplier<Map<String, Map<String, JoinDescriptor>>> joinDescriptorsAccess;

		private final String registrationName;

		public EntityResultDescriptor(
				JaxbHbmNativeQueryReturnType hbmEntityReturn,
				Supplier<Map<String, Map<String, JoinDescriptor>>> joinDescriptorsAccess,
				String registrationName,
				MetadataBuildingContext context) {
			assert joinDescriptorsAccess != null;

			this.entityName = hbmEntityReturn.getEntityName() != null
					? hbmEntityReturn.getEntityName()
					: hbmEntityReturn.getClazz();
			if ( entityName == null ) {
				throw new MappingException(
						"Entity <return/> mapping did not specify entity name"
				);
			}

			this.tableAlias = hbmEntityReturn.getAlias();
			if ( tableAlias == null ) {
				throw new MappingException(
						"Entity <return/> mapping did not specify alias"
				);
			}

			BootQueryLogging.LOGGER.debugf(
					"Creating EntityResultDescriptor (%s : %s) for ResultSet mapping - %s",
					tableAlias,
					entityName,
					registrationName
			);

			this.discriminatorColumnAlias = hbmEntityReturn.getClazz();
			this.lockMode = hbmEntityReturn.getLockMode();
			this.joinDescriptorsAccess = joinDescriptorsAccess;
			this.registrationName = registrationName;

			this.propertyFetchDescriptors = extractPropertyFetchDescriptors(
					hbmEntityReturn.getReturnProperty(),
					this,
					registrationName,
					context
			);
		}

		@Override
		public ResultMemento resolve(ResultSetMappingResolutionContext resolutionContext) {
			BootQueryLogging.LOGGER.debugf(
					"Resolving HBM EntityResultDescriptor into memento - %s : %s (%s)",
					tableAlias,
					entityName,
					registrationName
			);

			final EntityMappingType entityDescriptor = resolutionContext
					.getSessionFactory()
					.getRuntimeMetamodels()
					.getEntityMappingType( entityName );
			applyFetchJoins( joinDescriptorsAccess, tableAlias, propertyFetchDescriptors );

			final NavigablePath entityPath = new NavigablePath( entityName );

			final ResultMementoBasic discriminatorMemento;
			if ( discriminatorColumnAlias == null ) {
				discriminatorMemento = null;
			}
			else {
				if ( entityDescriptor.getDiscriminatorMapping() == null ) {
					throw new MappingException(
							"Discriminator column mapping given for non-discriminated entity ["
									+ entityName + "] as part of resultset mapping [" + registrationName + "]"
					);
				}

				discriminatorMemento = new ModelPartResultMementoBasicImpl(
						entityPath.append( EntityDiscriminatorMapping.ROLE_NAME ),
						entityDescriptor.getDiscriminatorMapping(),
						discriminatorColumnAlias
				);
			}

			final Map<String,FetchMemento> fetchDescriptorMap = new HashMap<>();
			propertyFetchDescriptors.forEach(
					hbmFetchDescriptor -> fetchDescriptorMap.put(
							hbmFetchDescriptor.getFetchablePath(),
							hbmFetchDescriptor.resolve( resolutionContext )
					)
			);

			return new ResultMementoEntityStandard(
					entityDescriptor,
					lockMode,
					discriminatorMemento,
					fetchDescriptorMap
			);
		}

		private HbmFetchParentMemento thisAsParentMemento;

		@Override
		public FetchParentMemento resolveParentMemento(ResultSetMappingResolutionContext resolutionContext) {
			if ( thisAsParentMemento == null ) {
				final EntityMappingType entityDescriptor = resolutionContext
						.getSessionFactory()
						.getRuntimeMetamodels()
						.getEntityMappingType( entityName );
				thisAsParentMemento = new HbmFetchParentMemento(
						new NavigablePath( entityDescriptor.getEntityName() ),
						entityDescriptor
				);
			}

			return thisAsParentMemento;
		}
	}

	public static List<HbmFetchDescriptor> extractPropertyFetchDescriptors(
			List<JaxbHbmNativeQueryPropertyReturnType> hbmReturnProperties,
			HbmFetchParent fetchParent,
			String registrationName,
			MetadataBuildingContext context) {
		final List<HbmFetchDescriptor> propertyFetchDescriptors = new ArrayList<>( hbmReturnProperties.size() );

		hbmReturnProperties.forEach(
				propertyReturn -> propertyFetchDescriptors.add(
						new PropertyFetchDescriptor( propertyReturn, fetchParent, registrationName, context )
				)
		);

		return propertyFetchDescriptors;
	}

	public static void applyFetchJoins(
			Supplier<Map<String, Map<String, JoinDescriptor>>> joinDescriptorsAccess,
			String tableAlias,
			List<HbmFetchDescriptor> propertyFetchDescriptors) {
		final Map<String, Map<String, JoinDescriptor>> joinDescriptors = joinDescriptorsAccess.get();

		if ( joinDescriptors == null ) {
			return;
		}

		final Map<String, JoinDescriptor> ownerJoinDescriptors = joinDescriptors.get( tableAlias );
		if ( ownerJoinDescriptors != null ) {
			final Set<String> processedFetchableNames = new HashSet<>();
			ownerJoinDescriptors.forEach(
					(fetchableName, joinDescriptor) -> {
						final boolean added = processedFetchableNames.add( fetchableName );

						if ( ! added ) {
							// the fetch is most likely more complete of a mapping so replace the original
							for ( int i = 0; i < propertyFetchDescriptors.size(); i++ ) {
								final HbmFetchDescriptor propertyFetchDescriptor = propertyFetchDescriptors.get( i );
								if ( propertyFetchDescriptor.getFetchablePath().equals( fetchableName ) ) {
									propertyFetchDescriptors.set( i, joinDescriptor );
								}
							}
						}
					}
			);
		}
	}

	/**
	 * @see org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryPropertyReturnType
	 */
	public static class PropertyFetchDescriptor implements HbmFetchDescriptor {
		private final HbmFetchParent parent;
		private final String propertyPath;
		private final String[] propertyPathParts;
		private final List<String> columnAliases;

		public PropertyFetchDescriptor(
				JaxbHbmNativeQueryPropertyReturnType hbmPropertyMapping,
				HbmFetchParent parent,
				String registrationName,
				MetadataBuildingContext context) {
			this.parent = parent;
			this.propertyPath = hbmPropertyMapping.getName();
			this.propertyPathParts = propertyPath.split( "\\." );
			this.columnAliases = extractColumnAliases( hbmPropertyMapping, context );

			BootQueryLogging.LOGGER.debugf(
					"Creating PropertyFetchDescriptor (%s : %s) for ResultSet mapping - %s",
					parent,
					propertyPath,
					registrationName
			);
		}

		@Override
		public String getFetchablePath() {
			return propertyPath;
		}

		private static List<String> extractColumnAliases(
				JaxbHbmNativeQueryPropertyReturnType hbmPropertyMapping,
				MetadataBuildingContext context) {
			if ( hbmPropertyMapping.getColumn() != null ) {
				return Collections.singletonList( hbmPropertyMapping.getColumn() );
			}

			final List<String> columnAliases = new ArrayList<>( hbmPropertyMapping.getReturnColumn().size() );
			hbmPropertyMapping.getReturnColumn().forEach(
					(column) -> columnAliases.add( column.getName() )
			);
			return columnAliases;
		}

		@Override
		public FetchMemento resolve(ResultSetMappingResolutionContext resolutionContext) {
			BootQueryLogging.LOGGER.debugf(
					"Resolving HBM PropertyFetchDescriptor into memento - %s : %s",
					parent,
					propertyPath
			);

			final FetchParentMemento fetchParentMemento = parent.resolveParentMemento( resolutionContext );

			NavigablePath navigablePath = fetchParentMemento.getNavigablePath().append( propertyPathParts[ 0 ] );
			Fetchable fetchable = (Fetchable) fetchParentMemento.getFetchableContainer().findSubPart(
					propertyPathParts[ 0 ],
					null
			);

			for ( int i = 1; i < propertyPathParts.length; i++ ) {
				if ( ! ( fetchable instanceof FetchableContainer ) ) {
					throw new MappingException(
							"Non-terminal property path [" + navigablePath.getFullPath()
									+ " did not reference FetchableContainer"
					);
				}
				navigablePath = fetchParentMemento.getNavigablePath().append( propertyPathParts[ i ] );
				fetchable = (Fetchable) ( (FetchableContainer) fetchable ).findSubPart( propertyPathParts[i], null );
			}

			return new FetchMementoBasicStandard( navigablePath, (BasicValuedModelPart) fetchable, columnAliases.get( 0 ) );
		}

		@Override
		public ResultMemento asResultMemento(NavigablePath path, ResultSetMappingResolutionContext resolutionContext) {
			throw new UnsupportedOperationException( "PropertyFetchDescriptor cannot be converted to a result" );
		}
	}

	/**
	 * @see org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryJoinReturnType
	 */
	public static class JoinDescriptor implements HbmFetchDescriptor, HbmFetchParent {
		private final String ownerTableAlias;
		private final String tableAlias;
		private final String propertyPath;
		private final LockMode lockMode;
		private final List<HbmFetchDescriptor> propertyFetchDescriptors;
		private final Supplier<Map<String, Map<String, JoinDescriptor>>> joinDescriptorsAccess;
		private final Supplier<Map<String, HbmFetchParent>> fetchParentByAliasAccess;

		public JoinDescriptor(
				JaxbHbmNativeQueryJoinReturnType hbmJoinReturn,
				Supplier<Map<String, Map<String, JoinDescriptor>>> joinDescriptorsAccess,
				Supplier<Map<String,HbmFetchParent>> fetchParentByAliasAccess,
				String registrationName,
				MetadataBuildingContext context) {
			this.joinDescriptorsAccess = joinDescriptorsAccess;
			this.fetchParentByAliasAccess = fetchParentByAliasAccess;
			final String fullPropertyPath = hbmJoinReturn.getProperty();
			final int firstDot = fullPropertyPath.indexOf( '.' );
			if ( firstDot < 1 ) {
				throw new MappingException(
						"Illegal <return-join/> property attribute: `" + fullPropertyPath + "`.  Should"
						+ "be in the form `{ownerAlias.joinedPropertyPath}`"
				);
			}

			this.ownerTableAlias = fullPropertyPath.substring( 0, firstDot - 1 );

			this.propertyPath = fullPropertyPath.substring( firstDot + 1 );
			this.tableAlias = hbmJoinReturn.getAlias();
			if ( tableAlias == null ) {
				throw new MappingException(
						"<return-join/> did not specify alias [" + ownerTableAlias + "." + propertyPath + "]"
				);
			}

			this.lockMode = hbmJoinReturn.getLockMode();
			this.propertyFetchDescriptors = extractPropertyFetchDescriptors(
					hbmJoinReturn.getReturnProperty(),
					this,
					registrationName,
					context
			);
		}

		@Override
		public String getFetchablePath() {
			return propertyPath;
		}

		private FetchMementoHbmStandard memento;
		private HbmFetchParentMemento thisAsParentMemento;

		@Override
		public FetchMemento resolve(ResultSetMappingResolutionContext resolutionContext) {
			BootQueryLogging.LOGGER.debugf(
					"Resolving HBM JoinDescriptor into memento - %s : %s . %s",
					tableAlias,
					ownerTableAlias,
					propertyPath
			);

			if ( memento == null ) {
				final HbmFetchParentMemento thisAsParentMemento = resolveParentMemento( resolutionContext );

				applyFetchJoins( joinDescriptorsAccess, tableAlias, propertyFetchDescriptors );

				memento = new FetchMementoHbmStandard(
						thisAsParentMemento.getNavigablePath(),
						thisAsParentMemento,
						(Fetchable) thisAsParentMemento.getFetchableContainer()
				);
			}

			return memento;
		}

		@Override
		public HbmFetchParentMemento resolveParentMemento(ResultSetMappingResolutionContext resolutionContext) {
			if ( thisAsParentMemento == null ) {
				final HbmFetchParent hbmFetchParent = fetchParentByAliasAccess.get().get( ownerTableAlias );
				if ( hbmFetchParent == null ) {
					throw new MappingException(
							"Could not locate join-return owner by alias [" + ownerTableAlias + "] for join path [" + propertyPath + "]"
					);
				}

				final FetchParentMemento ownerMemento = hbmFetchParent.resolveParentMemento( resolutionContext );

				final String[] parts = propertyPath.split( "\\." );
				NavigablePath navigablePath = ownerMemento.getNavigablePath().append( parts[ 0 ] );
				FetchableContainer fetchable = (FetchableContainer) ownerMemento.getFetchableContainer().findSubPart( parts[ 0 ], null );

				for ( int i = 1; i < parts.length; i++ ) {
					navigablePath = navigablePath.append( parts[ i ] );
					fetchable = (FetchableContainer) fetchable.findSubPart( parts[ i ], null );
				}

				thisAsParentMemento = new HbmFetchParentMemento( navigablePath, fetchable );
			}

			return thisAsParentMemento;
		}

		@Override
		public ResultMemento asResultMemento(
				NavigablePath path,
				ResultSetMappingResolutionContext resolutionContext) {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * @see org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryCollectionLoadReturnType
	 */
	public static class CollectionResultDescriptor implements ResultDescriptor, HbmFetchParent {
		private final NavigablePath collectionPath;
		private final String tableAlias;
		private final LockMode lockMode;
		private final Supplier<Map<String, Map<String, JoinDescriptor>>> joinDescriptorsAccess;
		private final List<HbmFetchDescriptor> propertyFetchDescriptors;

		public CollectionResultDescriptor(
				JaxbHbmNativeQueryCollectionLoadReturnType hbmCollectionReturn,
				Supplier<Map<String, Map<String, JoinDescriptor>>> joinDescriptorsAccess,
				String registrationName,
				MetadataBuildingContext context) {
			this.collectionPath = new NavigablePath( hbmCollectionReturn.getRole() );
			this.tableAlias = hbmCollectionReturn.getAlias();
			if ( tableAlias == null ) {
				throw new MappingException(
						"<return-collection/> did not specify alias [" + collectionPath.getFullPath() + "]"
				);
			}

			BootQueryLogging.LOGGER.debugf(
					"Creating CollectionResultDescriptor (%s : %s)",
					tableAlias,
					collectionPath
			);

			this.lockMode = hbmCollectionReturn.getLockMode();
			this.joinDescriptorsAccess = joinDescriptorsAccess;

			this.propertyFetchDescriptors = extractPropertyFetchDescriptors(
					hbmCollectionReturn.getReturnProperty(),
					this,
					registrationName,
					context
			);
		}

		private ResultMemento memento;
		private FetchParentMemento thisAsParentMemento;

		@Override
		public ResultMemento resolve(ResultSetMappingResolutionContext resolutionContext) {
			BootQueryLogging.LOGGER.debugf(
					"Resolving HBM CollectionResultDescriptor into memento - %s : %s",
					tableAlias,
					collectionPath
			);

			if ( memento == null ) {
				applyFetchJoins( joinDescriptorsAccess, tableAlias, propertyFetchDescriptors );

				final FetchParentMemento thisAsParentMemento = resolveParentMemento( resolutionContext );

				memento = new ResultMementoCollectionStandard(
						thisAsParentMemento.getNavigablePath(),
						(PluralAttributeMapping) thisAsParentMemento.getFetchableContainer()
				);
			}

			return memento;
		}

		@Override
		public FetchParentMemento resolveParentMemento(ResultSetMappingResolutionContext resolutionContext) {
			if ( thisAsParentMemento == null ) {
				final CollectionPersister collectionDescriptor = resolutionContext.getSessionFactory()
						.getRuntimeMetamodels()
						.getMappingMetamodel()
						.getCollectionDescriptor( collectionPath.getFullPath() );

				thisAsParentMemento = new HbmFetchParentMemento( collectionPath, collectionDescriptor.getAttributeMapping() );
			}

			return thisAsParentMemento;
		}
	}

	/**
	 * @see org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryScalarReturnType
	 */
	public static class ScalarDescriptor implements ResultDescriptor {
		private final String columnName;
		private final String hibernateTypeName;

		public ScalarDescriptor(String columnName, String hibernateTypeName) {
			this.columnName = columnName;
			this.hibernateTypeName = hibernateTypeName;

			BootQueryLogging.LOGGER.debugf(
					"Creating ScalarDescriptor (%s)",
					columnName
			);
		}

		public ScalarDescriptor(JaxbHbmNativeQueryScalarReturnType hbmScalarReturn) {
			this( hbmScalarReturn.getColumn(), hbmScalarReturn.getType() );
		}

		@Override
		public ResultMementoBasicStandard resolve(ResultSetMappingResolutionContext resolutionContext) {
			BootQueryLogging.LOGGER.debugf(
					"Resolving HBM ScalarDescriptor into memento - %s",
					columnName
			);

			if ( hibernateTypeName != null ) {
				final BasicType<?> namedType = resolutionContext.getSessionFactory()
						.getTypeConfiguration()
						.getBasicTypeRegistry()
						.getRegisteredType( hibernateTypeName );

				if ( namedType == null ) {
					throw new IllegalArgumentException( "Could not resolve named type : " + hibernateTypeName );
				}

				return new ResultMementoBasicStandard( columnName, namedType, resolutionContext );
			}

			// todo (6.0) : column name may be optional in HBM - double check

			return new ResultMementoBasicStandard( columnName, null, resolutionContext );
		}
	}
}
