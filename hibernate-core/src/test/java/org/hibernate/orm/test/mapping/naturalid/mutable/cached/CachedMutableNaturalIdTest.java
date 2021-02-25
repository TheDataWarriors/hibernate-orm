/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.mapping.naturalid.mutable.cached;

import org.hibernate.LockOptions;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.NotImplementedYet;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests of mutable natural ids stored in second level cache
 * 
 * @author Guenther Demetz
 * @author Steve Ebersole
 */
public abstract class CachedMutableNaturalIdTest {

	@AfterEach
	public void dropTestData(SessionFactoryScope scope) {
		scope.inTransaction(
				(session) -> {
					session.createQuery( "delete from Another" ).executeUpdate();
					session.createQuery( "delete from AllCached" ).executeUpdate();
					session.createQuery( "delete from SubClass" ).executeUpdate();
					session.createQuery( "delete from A" ).executeUpdate();
				}
		);
	}

	@Test
	public void testNaturalIdChangedWhileAttached(SessionFactoryScope scope) {
		scope.inTransaction(
				(session) -> session.save( new Another( "it" ) )
		);

		scope.inTransaction(
				(session) -> {
					Another it = session.bySimpleNaturalId( Another.class ).load( "it" );
					assertNotNull( it );
					// change it's name
					it.setName( "it2" );
				}
		);

		scope.inTransaction(
				(session) -> {
					final Another shouldBeGone = session.bySimpleNaturalId( Another.class ).load( "it" );
					assertNull( shouldBeGone );
					final Another updated = session.bySimpleNaturalId( Another.class ).load( "it2" );
					assertNotNull( updated );
				}
		);
	}

	@Test
	public void testNaturalIdChangedWhileDetached(SessionFactoryScope scope) {
		scope.inTransaction(
				(session) -> session.save( new Another( "it" ) )
		);

		final Another detached = scope.fromTransaction(
				(session) -> {
					final Another it = session.bySimpleNaturalId( Another.class ).load( "it" );
					assertNotNull( it );
					return it;
				}
		);

		detached.setName( "it2" );

		scope.inTransaction(
				(session) -> session.update( detached )
		);

		scope.inTransaction(
				(session) -> {
					final Another shouldBeGone = session.bySimpleNaturalId( Another.class ).load( "it" );
					assertNull( shouldBeGone );
					final Another updated = session.bySimpleNaturalId( Another.class ).load( "it2" );
					assertNotNull( updated );
				}
		);
	}

	@Test
	@NotImplementedYet( reason = "Caching is not yet implemented", strict = false )
	public void testNaturalIdReCachingWhenNeeded(SessionFactoryScope scope) {

		final Integer id = scope.fromTransaction(
				(session) -> {
					Another it = new Another( "it" );
					session.save( it );
					return it.getId();
				}
		);

		scope.inTransaction(
				(session) -> {
					final Another it = session.byId( Another.class ).load( id );
					it.setName( "it2" );
					// changing something but not the natural-id's
					it.setSurname( "surname" );

				}
		);

		scope.inTransaction(
				(session) -> {
					final Another shouldBeGone = session.bySimpleNaturalId(Another.class).load("it");
					assertNull( shouldBeGone );
					assertEquals( 0, session.getSessionFactory().getStatistics().getNaturalIdCacheHitCount() );
				}
		);
		
		// finally there should be only 2 NaturalIdCache puts : 1. insertion, 2. when updating natural-id from 'it' to 'name9'
		assertEquals( 2, scope.getSessionFactory().getStatistics().getNaturalIdCachePutCount() );
	}
	
	@Test
	@TestForIssue( jiraKey = "HHH-7245" )
	public void testNaturalIdChangeAfterResolveEntityFrom2LCache(SessionFactoryScope scope) {

		final Integer id = scope.fromTransaction(
				(session) -> {
					AllCached it = new AllCached( "it" );
					session.save( it );
					return it.getId();
				}
		);

		scope.inTransaction(
				(session) -> {
					final AllCached it = session.byId( AllCached.class ).load( id );
					it.setName( "it2" );

					final AllCached shouldBeGone = session.bySimpleNaturalId( AllCached.class ).load( "it" );
					assertNull( shouldBeGone );

					final AllCached updated = session.bySimpleNaturalId( AllCached.class ).load( "it2" );
					assertNotNull( updated );
				}
		);
	}
	
	@Test
	@TestForIssue( jiraKey = "HHH-12657" )
	public void testBySimpleNaturalIdResolveEntityFrom2LCacheSubClass(SessionFactoryScope scope) {
		scope.inTransaction(
				(session) -> session.save( new SubClass( "it" ) )
		);

		scope.inTransaction(
				(session) -> {
					// load by super-type
					final AllCached bySuper = session.bySimpleNaturalId( AllCached.class ).load( "it" );
					assertNotNull( bySuper );

					// load by concrete type
					final SubClass byConcrete = session.bySimpleNaturalId( SubClass.class ).load( "it" );
					assertNotNull( byConcrete );
				}
		);
	}
	
	@Test
	public void testReattachUnmodifiedInstance(SessionFactoryScope scope) {
		final B created = scope.fromTransaction(
				(session) -> {
					A a = new A();
					B b = new B();
					b.naturalid = 100;
					session.persist( a );
					session.persist( b );
					b.assA = a;
					a.assB.add( b );

					return b;
				}
		);

		scope.inTransaction(
				(session) -> {
					// HHH-7513 failure during reattachment
					session.buildLockRequest( LockOptions.NONE ).lock( created );
					session.delete( created.assA );
					session.delete( created );
				}
		);

		scope.inTransaction(
				(session) -> {
					// true if the re-attachment worked
					assertEquals( session.createQuery( "FROM A" ).list().size(), 0 );
					assertEquals( session.createQuery( "FROM B" ).list().size(), 0 );
				}
		);
	}

}

