/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.query;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import jakarta.persistence.TypedQuery;
import java.util.List;

import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author Andrea Boriero
 */
@Jpa(annotatedClasses = {
		TupleQueryTest.User.class
})
public class TupleQueryTest {

	@BeforeEach
	public void setUp(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> {
					User u = new User( "Fab" );
					entityManager.persist( u );
				}
		);
	}

	@AfterEach
	public void tearDown(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> {
					entityManager.createQuery( "delete from User" ).executeUpdate();
				}
		);
	}

	@Test
	public void testGetAliasReturnNullIfNoAliasExist(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> {
					TypedQuery<Tuple> query = entityManager.createQuery( "SELECT u.firstName from User u", Tuple.class );

					List<Tuple> result = query.getResultList();
					List<TupleElement<?>> elements = result.get( 0 ).getElements();

					assertThat( elements.size(), is( 1 ) );
					final String alias = elements.get( 0 ).getAlias();
					assertThat( alias, is( nullValue() ) );
				}
		);
	}

	@Test
	public void testGetAlias(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> {
					TypedQuery<Tuple> query = entityManager.createQuery( "SELECT u.firstName as fn from User u", Tuple.class );

					List<Tuple> result = query.getResultList();
					List<TupleElement<?>> elements = result.get( 0 ).getElements();

					assertThat( elements.size(), is( 1 ) );
					final String alias = elements.get( 0 ).getAlias();
					assertThat( alias, is( "fn" ) );
				}
		);
	}

	@Entity(name = "User")
	@Table(name = "USERS")
	public static class User {
		@Id
		@GeneratedValue
		long id;

		String firstName;

		public User() {
		}

		public User(String firstName) {
			this.firstName = firstName;
		}
	}
}
