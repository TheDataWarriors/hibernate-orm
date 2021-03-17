/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.collection.bag;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OrderBy;
import javax.persistence.Table;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Gail Badner
 */
@DomainModel(annotatedClasses = {
		BagElementNullBasicTest.AnEntity.class,
		BagElementNullBasicTest.NullableElementsEntity.class
})
@SessionFactory
public class BagElementNullBasicTest {

	@Test
	public void testPersistNullValue(SessionFactoryScope scope) {
		int entityId = scope.fromTransaction(
				session -> {
					AnEntity e = new AnEntity();
					e.aCollection.add( null );
					session.persist( e );
					return e.id;
				}
		);

		scope.inTransaction(
				session -> {
					AnEntity e = session.get( AnEntity.class, entityId );
					assertEquals( 0, e.aCollection.size() );
					assertEquals( 0, getCollectionElementRows( entityId, scope ).size() );
					session.delete( e );
				}
		);
	}

	@Test
	public void addNullValue(SessionFactoryScope scope) {
		int entityId = scope.fromTransaction(
				session -> {
					AnEntity e = new AnEntity();
					session.persist( e );
					return e.id;
				}
		);

		scope.inTransaction(
				session -> {
					AnEntity e = session.get( AnEntity.class, entityId );
					assertEquals( 0, e.aCollection.size() );
					assertEquals( 0, getCollectionElementRows( entityId, scope ).size() );
					e.aCollection.add( null );
				}
		);

		scope.inTransaction(
				session -> {
					AnEntity e = session.get( AnEntity.class, entityId );
					assertEquals( 0, e.aCollection.size() );
					assertEquals( 0, getCollectionElementRows( entityId, scope ).size() );
					session.delete( e );
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-13651")
	public void addNullValueToNullableCollections(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					NullableElementsEntity e = new NullableElementsEntity();
					e.list.add( null );
					session.persist( e );
					session.flush();
				}
		);
	}

	@Test
	public void testUpdateNonNullValueToNull(SessionFactoryScope scope) {
		int entityId = scope.fromTransaction(
				session -> {
					AnEntity e = new AnEntity();
					e.aCollection.add( "def" );
					session.persist( e );
					return e.id;
				}
		);

		scope.inTransaction(
				session -> {
					AnEntity e = session.get( AnEntity.class, entityId );
					assertEquals( 1, e.aCollection.size() );
					assertEquals( 1, getCollectionElementRows( entityId, scope ).size() );
					e.aCollection.set( 0, null );
				}
		);

		scope.inTransaction(
				session -> {
					AnEntity e = session.get( AnEntity.class, entityId );
					assertEquals( 0, e.aCollection.size() );
					assertEquals( 0, getCollectionElementRows( entityId, scope ).size() );
					session.delete( e );
				}
		);
	}

	@Test
	public void testUpdateNonNullValueToNullWithExtraValue(SessionFactoryScope scope) {
		int entityId = scope.fromTransaction(
				session -> {
					AnEntity e = new AnEntity();
					e.aCollection.add( "def" );
					e.aCollection.add( "ghi" );
					session.persist( e );
					return e.id;
				}
		);

		scope.inTransaction(
				session -> {
					AnEntity e = session.get( AnEntity.class, entityId );
					assertEquals( 2, e.aCollection.size() );
					assertEquals( 2, getCollectionElementRows( e.id, scope ).size() );
					e.aCollection.set( 0, null );
				}
		);

		scope.inTransaction(
				session -> {
					AnEntity e = session.get( AnEntity.class, entityId );
					assertEquals( 1, e.aCollection.size() );
					assertEquals( 1, getCollectionElementRows( e.id, scope ).size() );
					assertEquals( "ghi", e.aCollection.get( 0 ) );
					session.delete( e );
				}
		);
	}

	private List getCollectionElementRows(int id, SessionFactoryScope scope) {
		return scope.fromTransaction(
				session ->
						session.createNativeQuery(
								"SELECT aCollection FROM AnEntity_aCollection where AnEntity_id = " + id
						).list()
		);
	}

	@Entity( name = "AnEntity" )
	@Table(name = "AnEntity")
	public static class AnEntity {
		@Id
		@GeneratedValue
		private int id;

		@ElementCollection
		@CollectionTable(name = "AnEntity_aCollection", joinColumns = { @JoinColumn(name = "AnEntity_id") })
		@OrderBy
		private List<String> aCollection = new ArrayList<>();
	}

	@Entity( name = "NullableElementsEntity" )
	@Table(name = "NullableElementsEntity")
	public static class NullableElementsEntity {
		@Id
		@GeneratedValue
		private int id;

		@ElementCollection
		@CollectionTable(name = "e_2_string", joinColumns = @JoinColumn(name = "e_id"))
		@Column(name = "string_value", unique = false, nullable = true, insertable = true, updatable = true)
		private List<String> list = new ArrayList<>();
	}
}
