/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.cache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQuery;
import javax.persistence.QueryHint;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cache.spi.CacheImplementor;
import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.stat.Statistics;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.cache.CachingRegionFactory;
import org.hibernate.testing.junit4.BaseNonConfigCoreFunctionalTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hibernate.testing.transaction.TransactionUtil.doInHibernate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Gail Badner
 */
public class SharedDomainDataQueryRegionNameGettersTest extends BaseNonConfigCoreFunctionalTestCase {

	private static final String QUERY = "SELECT a FROM Dog a";
	private static final String REGION_NAME = "TheRegion";
	private static final String PREFIX = "test";

	@Test
	@TestForIssue( jiraKey = "HHH-13586")
	public void test() {
		rebuildSessionFactory();

		final CacheImplementor cache = sessionFactory().getCache();
		final String defaultQueryResultsRegionName = cache.getDefaultQueryResultsCache().getRegion().getName();

		// before named QueryCacheRegion has been created.

		// cache.getCacheRegionNames() should return both
		// REGION_NAME and the default QueryCacheResultsRegion name.
		assertEquals( 2, cache.getCacheRegionNames().size() );
		assertTrue( cache.getCacheRegionNames().contains( REGION_NAME ) );
		assertTrue( cache.getCacheRegionNames().contains( defaultQueryResultsRegionName ) );

		assertEquals( 1, cache.getDomainDataRegionNames().size() );
		assertTrue( cache.getDomainDataRegionNames().contains( REGION_NAME ) );

		final DomainDataRegion domainDataRegion = cache.getDomainDataRegion( REGION_NAME );
		assertNotNull( domainDataRegion );
		assertSame( domainDataRegion, cache.getRegion( REGION_NAME ) );

		assertNull( cache.getRegion( "not a region name" ) );
		assertNull( cache.getDomainDataRegion( "not a region name" ) );
		assertNull( cache.getQueryResultsCacheStrictly( "not a region name" ) );

		// cache.getQueryCacheRegionNames() should not contain the
		// default QueryResultsRegion name.
		assertTrue( cache.getQueryCacheRegionNames().isEmpty() );
		// default QueryResultsRegion can be obtained by name from getRegion( defaultQueryResultsRegionName)
		assertSame(
				cache.getDefaultQueryResultsCache().getRegion(),
				cache.getRegion( defaultQueryResultsRegionName )
		);

		// There should not be a QueryResultsRegion named REGION_NAME until
		// the named query is executed.
		assertNull( cache.getQueryResultsCacheStrictly( REGION_NAME ) );
		// default QueryResultsRegion cannot be obtained by name from getQueryResultsCacheStrictly( defaultQueryResultsRegionName)
		assertNull( cache.getQueryResultsCacheStrictly( defaultQueryResultsRegionName ) );

		doInHibernate(
				this::sessionFactory, session -> {
					session.createNamedQuery( "Dog.findAll", Dog.class ).list();
				}
		);

		// after named QueryCacheRegion has been created.

		// cache.getCacheRegionNames() should return both
		// REGION_NAME and the default QueryCacheResultsRegion name.
		assertEquals( 2, cache.getCacheRegionNames().size() );
		assertTrue( cache.getCacheRegionNames().contains( REGION_NAME ) );
		assertTrue( cache.getCacheRegionNames().contains( defaultQueryResultsRegionName ) );

		assertEquals( 1, cache.getDomainDataRegionNames().size() );
		assertTrue( cache.getDomainDataRegionNames().contains( REGION_NAME ) );

		assertSame( domainDataRegion, cache.getRegion( REGION_NAME ) );
		assertSame( domainDataRegion, cache.getDomainDataRegion( REGION_NAME ) );

		// cache.getQueryCacheRegionNames() should contain REGION_NAME now;
		// it still should not contain the default QueryResultsRegion name.
		assertEquals( 1, cache.getQueryCacheRegionNames().size() );
		assertEquals( REGION_NAME, cache.getQueryCacheRegionNames().iterator().next() );
		assertNotNull( cache.getQueryResultsCacheStrictly( REGION_NAME ) );
		assertSame( cache.getQueryResultsCacheStrictly( REGION_NAME ), cache.getQueryResultsCache( REGION_NAME ) );
		assertEquals( REGION_NAME, cache.getQueryResultsCacheStrictly( REGION_NAME ).getRegion().getName() );

		// default QueryResultsRegion can still be obtained by name from getRegion( defaultQueryResultsRegionName)
		assertSame(
				cache.getDefaultQueryResultsCache().getRegion(),
				cache.getRegion( defaultQueryResultsRegionName )
		);

		// Now there is a DomainDataRegion and QueryResultsRegion named REGION_NAME.
		// Make sure that the same DomainDataRegion is returned by cache.getRegion( REGION_NAME ).
		assertSame( domainDataRegion, cache.getRegion( REGION_NAME ) );
	}

	@Test
	@TestForIssue( jiraKey = "HHH-13586")
	public void testEvictCaches() {
		rebuildSessionFactory();

		final Statistics statistics = sessionFactory().getStatistics();
		statistics.clear();

		doInHibernate(
				this::sessionFactory, session -> {
					Dog yogi = new Dog( "Yogi" );
					yogi.nickNames.add( "The Yog" );
					yogi.nickNames.add( "Little Boy" );
					yogi.nickNames.add( "Yogaroni Macaroni" );
					Dog irma = new Dog( "Irma" );
					irma.nickNames.add( "Squirmy" );
					irma.nickNames.add( "Bird" );
					session.persist( yogi );
					session.persist( irma );
				}
		);

		doInHibernate(
				this::sessionFactory, session -> {

					List<Dog> dogs = session.createNamedQuery( "Dog.findAll", Dog.class ).list();

					assertEquals( 2, dogs.size() );

					for ( Dog dog : dogs ) {
						dog.nickNames.size();
					}
				}
		);

		// put entities, collections, query results in

		doInHibernate(
				this::sessionFactory, session -> {

					List<Dog> dogs = session.createNamedQuery( "Dog.findAll", Dog.class ).list();

					assertEquals( 2, dogs.size() );

					for ( Dog dog : dogs ) {
						dog.nickNames.size();
					}
				}
		);

		assertTrue( statistics.getSecondLevelCacheHitCount() > 0 );
		assertTrue( statistics.getQueryCacheHitCount() > 0 );
		assertTrue( statistics.getQueryRegionStatistics( REGION_NAME ).getHitCount() > 0 );

		statistics.clear();

		sessionFactory().getCache().evictRegion( REGION_NAME );

		doInHibernate(
				this::sessionFactory, session -> {

					List<Dog> dogs = session.createNamedQuery( "Dog.findAll", Dog.class ).list();
					for ( Dog dog : dogs ) {
						dog.nickNames.size();
					}
				}
		);

		assertEquals( 0, statistics.getSecondLevelCacheHitCount() );
		assertEquals( 0, statistics.getQueryCacheHitCount() );
		assertEquals( 0, statistics.getQueryRegionStatistics( REGION_NAME ).getHitCount() );
	}

	@Override
	protected void configureStandardServiceRegistryBuilder(StandardServiceRegistryBuilder ssrb) {
		super.configureStandardServiceRegistryBuilder( ssrb );
		ssrb.applySetting( AvailableSettings.USE_SECOND_LEVEL_CACHE, true );
		ssrb.applySetting( AvailableSettings.USE_QUERY_CACHE, true );
		ssrb.applySetting( AvailableSettings.CACHE_REGION_PREFIX, PREFIX );
		ssrb.applySetting( AvailableSettings.CACHE_REGION_FACTORY, new CachingRegionFactory() );
		ssrb.applySetting( AvailableSettings.GENERATE_STATISTICS, "true" );
	}

	@Override
	protected void applyMetadataSources(MetadataSources metadataSources) {
		super.applyMetadataSources( metadataSources );
		metadataSources.addAnnotatedClass( Dog.class );
	}

	@After
	public void cleanupData() {
		doInHibernate(
				this::sessionFactory, session -> {
					List<Dog> dogs = session.createQuery( "from Dog", Dog.class ).getResultList();
					for ( Dog dog : dogs ) {
						session.delete( dog );
					}
				}
		);
	}

	@Entity(name = "Dog")
	@NamedQuery(name = "Dog.findAll", query = QUERY,
			hints = {
					@QueryHint(name = "org.hibernate.cacheable", value = "true"),
					@QueryHint(name = "org.hibernate.cacheRegion", value = REGION_NAME)
			}
	)
	@Cache(usage = CacheConcurrencyStrategy.READ_ONLY, region= REGION_NAME)
	public static class Dog {
		@Id
		private String name;

		@Cache(usage = CacheConcurrencyStrategy.READ_ONLY, region= REGION_NAME)
		@ElementCollection
		private Set<String> nickNames = new HashSet<>();

		public Dog(String name) {
			this.name = name;
		}

		public Dog() {
		}
	}
}
