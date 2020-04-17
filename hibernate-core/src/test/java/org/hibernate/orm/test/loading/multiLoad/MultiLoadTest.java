/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.loading.multiLoad;

import java.util.List;
import java.util.Objects;
import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.SharedCacheMode;
import javax.persistence.Table;

import org.hibernate.CacheMode;
import org.hibernate.annotations.BatchSize;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.stat.Statistics;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.jdbc.SQLStatementInterceptor;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.FailureExpected;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryProducer;
import org.hibernate.testing.orm.junit.SessionFactoryScope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Steve Ebersole
 */
@SuppressWarnings("WeakerAccess")
@ServiceRegistry(
		settings = {
				@ServiceRegistry.Setting( name = AvailableSettings.USE_SECOND_LEVEL_CACHE, value = "true" ),
				@ServiceRegistry.Setting( name = AvailableSettings.GENERATE_STATISTICS, value = "true" ),
				@ServiceRegistry.Setting( name = AvailableSettings.HBM2DDL_DATABASE_ACTION, value = "create-drop" )
		}
)
@DomainModel(
		annotatedClasses = MultiLoadTest.SimpleEntity.class,
		sharedCacheMode = SharedCacheMode.ENABLE_SELECTIVE,
		accessType = AccessType.READ_WRITE
)
@SessionFactory
public class MultiLoadTest implements SessionFactoryProducer {
	private SQLStatementInterceptor sqlStatementInterceptor;

	@Override
	public SessionFactoryImplementor produceSessionFactory(MetadataImplementor model) {
		final SessionFactoryBuilder sessionFactoryBuilder = model.getSessionFactoryBuilder();
		sqlStatementInterceptor = new SQLStatementInterceptor( sessionFactoryBuilder );
		return (SessionFactoryImplementor) sessionFactoryBuilder.build();
	}


	@BeforeEach
	public void before(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.setCacheMode( CacheMode.IGNORE );
					for ( int i = 1; i <= 60; i++ ) {
						session.save( new SimpleEntity( i, "Entity #" + i ) );
					}
				}
		);
	}

	@AfterEach
	public void after(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery( "delete SimpleEntity" ).executeUpdate();
				}
		);
	}

	@Test
	public void testBasicMultiLoad(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					sqlStatementInterceptor.getSqlQueries().clear();

					List<SimpleEntity> list = session.byMultipleIds( SimpleEntity.class ).multiLoad( ids( 5 ) );
					assertEquals( 5, list.size() );

					assertTrue( sqlStatementInterceptor.getSqlQueries().getFirst().endsWith( "id in (?, ?, ?, ?, ?)" ) );

				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-10984" )
	public void testUnflushedDeleteAndThenMultiLoad(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					// delete one of them (but do not flush)...
					session.delete( session.load( SimpleEntity.class, 5 ) );

					// as a baseline, assert based on how load() handles it
					SimpleEntity s5 = session.load( SimpleEntity.class, 5 );
					assertNotNull( s5 );

					// and then, assert how get() handles it
					s5 = session.get( SimpleEntity.class, 5 );
					assertNull( s5 );

					// finally assert how multiLoad handles it
					List<SimpleEntity> list = session.byMultipleIds( SimpleEntity.class ).multiLoad( ids(56) );
					assertEquals( 56, list.size() );
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-10617" )
	public void testDuplicatedRequestedIds(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					// ordered multiLoad
					List<SimpleEntity> list = session.byMultipleIds( SimpleEntity.class ).multiLoad( 1, 2, 3, 2, 2 );
					assertEquals( 5, list.size() );
					assertSame( list.get( 1 ), list.get( 3 ) );
					assertSame( list.get( 1 ), list.get( 4 ) );
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-10617")
	public void testDuplicatedRequestedIdswithDisableOrderedReturn(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					// un-ordered multiLoad
					List<SimpleEntity> list = session.byMultipleIds( SimpleEntity.class )
							.enableOrderedReturn( false )
							.multiLoad( 1, 2, 3, 2, 2 );
					assertEquals( 3, list.size() );
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-10617" )
	public void testNonExistentIdRequest(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					// ordered multiLoad
					List<SimpleEntity> list = session.byMultipleIds( SimpleEntity.class ).multiLoad( 1, 699, 2 );
					assertEquals( 3, list.size() );
					assertNull( list.get( 1 ) );

					// un-ordered multiLoad
					list = session.byMultipleIds( SimpleEntity.class ).enableOrderedReturn( false ).multiLoad( 1, 699, 2 );
					assertEquals( 2, list.size() );
				}
		);
	}

	@Test
	public void testBasicMultiLoadWithManagedAndNoChecking(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					SimpleEntity first = session.byId( SimpleEntity.class ).load( 1 );
					List<SimpleEntity> list = session.byMultipleIds( SimpleEntity.class ).multiLoad( ids( 56 ) );
					assertEquals( 56, list.size() );
					// this check is HIGHLY specific to implementation in the batch loader
					// which puts existing managed entities first...
					assertSame( first, list.get( 0 ) );
				}
		);
	}

	@Test
	public void testBasicMultiLoadWithManagedAndChecking(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					SimpleEntity first = session.byId( SimpleEntity.class ).load( 1 );
					List<SimpleEntity> list = session.byMultipleIds( SimpleEntity.class )
							.enableSessionCheck( true )
							.multiLoad( ids( 56 ) );
					assertEquals( 56, list.size() );
					// this check is HIGHLY specific to implementation in the batch loader
					// which puts existing managed entities first...
					assertSame( first, list.get( 0 ) );
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-12944")
	@FailureExpected( reason = "Caching/CacheMode supported not yet implemented" )
	public void testMultiLoadFrom2ndLevelCache(SessionFactoryScope scope) {
		Statistics statistics = scope.getSessionFactory().getStatistics();
		scope.getSessionFactory().getCache().evictAll();
		statistics.clear();

		scope.inTransaction(
				session -> {
					// Load 1 of the items directly
					SimpleEntity entity = session.get( SimpleEntity.class, 2 );
					assertNotNull( entity );

					assertEquals( 1, statistics.getSecondLevelCacheMissCount() );
					assertEquals( 0, statistics.getSecondLevelCacheHitCount() );
					assertEquals( 1, statistics.getSecondLevelCachePutCount() );
					assertTrue( session.getSessionFactory().getCache().containsEntity( SimpleEntity.class, 2 ) );
				}
		);

		statistics.clear();

		scope.inTransaction(
				session -> {
					// Validate that the entity is still in the Level 2 cache
					assertTrue( session.getSessionFactory().getCache().containsEntity( SimpleEntity.class, 2 ) );

					sqlStatementInterceptor.getSqlQueries().clear();

					// Multiload 3 items and ensure that multiload pulls 2 from the database & 1 from the cache.
					List<SimpleEntity> entities = session.byMultipleIds( SimpleEntity.class )
							.with( CacheMode.NORMAL )
							.enableSessionCheck( true )
							.multiLoad( ids( 3 ) );
					assertEquals( 3, entities.size() );
					assertEquals( 1, statistics.getSecondLevelCacheHitCount() );

					for(SimpleEntity entity: entities) {
						assertTrue( session.contains( entity ) );
					}

					assertTrue( sqlStatementInterceptor.getSqlQueries().getFirst().endsWith( "id in (?, ?)" ) );
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-12944")
	@FailureExpected( reason = "Caching/CacheMode supported not yet implemented" )
	public void testUnorderedMultiLoadFrom2ndLevelCache(SessionFactoryScope scope) {
		Statistics statistics = scope.getSessionFactory().getStatistics();
		scope.getSessionFactory().getCache().evictAll();
		statistics.clear();
		scope.inTransaction(
				session -> {
					// Load 1 of the items directly
					SimpleEntity entity = session.get( SimpleEntity.class, 2 );
					assertNotNull( entity );

					assertEquals( 1, statistics.getSecondLevelCacheMissCount() );
					assertEquals( 0, statistics.getSecondLevelCacheHitCount() );
					assertEquals( 1, statistics.getSecondLevelCachePutCount() );
					assertTrue( session.getSessionFactory().getCache().containsEntity( SimpleEntity.class, 2 ) );
				}
		);

		statistics.clear();

		scope.inTransaction(
				session -> {

					// Validate that the entity is still in the Level 2 cache
					assertTrue( session.getSessionFactory().getCache().containsEntity( SimpleEntity.class, 2 ) );

					sqlStatementInterceptor.getSqlQueries().clear();

					// Multiload 3 items and ensure that multiload pulls 2 from the database & 1 from the cache.
					List<SimpleEntity> entities = session.byMultipleIds( SimpleEntity.class )
							.with( CacheMode.NORMAL )
							.enableSessionCheck( true )
							.enableOrderedReturn( false )
							.multiLoad( ids( 3 ) );
					assertEquals( 3, entities.size() );
					assertEquals( 1, statistics.getSecondLevelCacheHitCount() );

					for(SimpleEntity entity: entities) {
						assertTrue( session.contains( entity ) );
					}

					assertTrue( sqlStatementInterceptor.getSqlQueries().getFirst().endsWith( "id in (?, ?)" ) );
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-12944")
	@FailureExpected( reason = "Caching/CacheMode supported not yet implemented" )
	public void testOrderedMultiLoadFrom2ndLevelCachePendingDelete(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.remove( session.find( SimpleEntity.class, 2 ) );

					sqlStatementInterceptor.getSqlQueries().clear();

					// Multiload 3 items and ensure that multiload pulls 2 from the database & 1 from the cache.
					List<SimpleEntity> entities = session.byMultipleIds( SimpleEntity.class )
							.with( CacheMode.NORMAL )
							.enableSessionCheck( true )
							.enableOrderedReturn( true )
							.multiLoad( ids( 3 ) );
					assertEquals( 3, entities.size() );

					assertNull( entities.get(1) );

					assertTrue( sqlStatementInterceptor.getSqlQueries().getFirst().endsWith( "id in (?,?)" ) );
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-12944")
	public void testOrderedMultiLoadFrom2ndLevelCachePendingDeleteReturnRemoved(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
			session.remove( session.find( SimpleEntity.class, 2 ) );

			sqlStatementInterceptor.getSqlQueries().clear();

			// Multiload 3 items and ensure that multiload pulls 2 from the database & 1 from the cache.
			List<SimpleEntity> entities = session.byMultipleIds( SimpleEntity.class )
					.with( CacheMode.NORMAL )
					.enableSessionCheck( true )
					.enableOrderedReturn( true )
					.enableReturnOfDeletedEntities( true )
					.multiLoad( ids( 3 ) );
			assertEquals( 3, entities.size() );

			SimpleEntity deletedEntity = entities.get(1);
			assertNotNull( deletedEntity );

			final EntityEntry entry = ((SharedSessionContractImplementor) session).getPersistenceContext().getEntry( deletedEntity );
			assertTrue( entry.getStatus() == Status.DELETED || entry.getStatus() == Status.GONE );

			assertTrue( sqlStatementInterceptor.getSqlQueries().getFirst().endsWith( "id in (?, ?)" ) );
		} );
	}

	@Test
	@TestForIssue(jiraKey = "HHH-12944")
	@FailureExpected( reason = "Caching/CacheMode supported not yet implemented" )
	public void testUnorderedMultiLoadFrom2ndLevelCachePendingDelete(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {

			session.remove( session.find( SimpleEntity.class, 2 ) );

			sqlStatementInterceptor.getSqlQueries().clear();

			// Multiload 3 items and ensure that multiload pulls 2 from the database & 1 from the cache.
			List<SimpleEntity> entities = session.byMultipleIds( SimpleEntity.class )
					.with( CacheMode.NORMAL )
					.enableSessionCheck( true )
					.enableOrderedReturn( false )
					.multiLoad( ids( 3 ) );
			assertEquals( 3, entities.size() );

			assertTrue( entities.stream().anyMatch( Objects::isNull ) );

			assertTrue( sqlStatementInterceptor.getSqlQueries().getFirst().endsWith( "id in (?,?)" ) );
		} );
	}

	@Test
	@TestForIssue(jiraKey = "HHH-12944")
	public void testUnorderedMultiLoadFrom2ndLevelCachePendingDeleteReturnRemoved(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {

			session.remove( session.find( SimpleEntity.class, 2 ) );

			sqlStatementInterceptor.getSqlQueries().clear();

			// Multiload 3 items and ensure that multiload pulls 2 from the database & 1 from the cache.
			List<SimpleEntity> entities = session.byMultipleIds( SimpleEntity.class )
					.with( CacheMode.NORMAL )
					.enableSessionCheck( true )
					.enableOrderedReturn( false )
					.enableReturnOfDeletedEntities( true )
					.multiLoad( ids( 3 ) );
			assertEquals( 3, entities.size() );

			SimpleEntity deletedEntity = entities.stream().filter( simpleEntity -> simpleEntity.getId().equals( 2 ) ).findAny().orElse( null );
			assertNotNull( deletedEntity );

			final EntityEntry entry = ((SharedSessionContractImplementor) session).getPersistenceContext().getEntry( deletedEntity );
			assertTrue( entry.getStatus() == Status.DELETED || entry.getStatus() == Status.GONE );

			assertTrue( sqlStatementInterceptor.getSqlQueries().getFirst().endsWith( "id in (?, ?)" ) );
		} );
	}

	@Test
	@FailureExpected( reason = "CacheMode not yet implemented" )
	public void testMultiLoadWithCacheModeIgnore(SessionFactoryScope scope) {
		// do the multi-load, telling Hibernate to IGNORE the L2 cache -
		//		the end result should be that the cache is (still) empty afterwards
		scope.inTransaction(
				session -> {
					session.getTransaction().begin();
					List<SimpleEntity> list = session.byMultipleIds( SimpleEntity.class )
							.with( CacheMode.IGNORE )
							.multiLoad( ids( 56 ) );
					session.getTransaction().commit();
					session.close();

					assertEquals( 56, list.size() );
					for ( SimpleEntity entity : list ) {
						assertFalse( scope.getSessionFactory().getCache().containsEntity( SimpleEntity.class, entity.getId() ) );
					}
				}
		);
	}

	@Test
	public void testMultiLoadClearsBatchFetchQueue(SessionFactoryScope scope) {
		final EntityKey entityKey = new EntityKey(
				1,
				scope.getSessionFactory().getEntityPersister( SimpleEntity.class.getName() )
		);

		scope.inTransaction(
				session -> {
					// create a proxy, which should add an entry to the BatchFetchQueue
					SimpleEntity first = session.byId( SimpleEntity.class ).getReference( 1 );
					assertTrue( session.getPersistenceContext()
										.getBatchFetchQueue()
										.containsEntityKey( entityKey ) );

					// now bulk load, which should clean up the BatchFetchQueue entry
					List<SimpleEntity> list = session.byMultipleIds( SimpleEntity.class )
							.enableSessionCheck( true )
							.multiLoad( ids( 56 ) );

					assertEquals( 56, list.size() );
					assertFalse( session.getPersistenceContext()
										 .getBatchFetchQueue()
										 .containsEntityKey( entityKey ) );

				}
		);
	}

	private Integer[] ids(int count) {
		Integer[] ids = new Integer[count];
		for ( int i = 1; i <= count; i++ ) {
			ids[i-1] = i;
		}
		return ids;
	}

	@Entity( name = "SimpleEntity" )
	@Table( name = "SimpleEntity" )
	@Cacheable()
	@BatchSize( size = 15 )
	public static class SimpleEntity {
		Integer id;
		String text;

		public SimpleEntity() {
		}

		public SimpleEntity(Integer id, String text) {
			this.id = id;
			this.text = text;
		}

		@Id
		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}
	}
}
