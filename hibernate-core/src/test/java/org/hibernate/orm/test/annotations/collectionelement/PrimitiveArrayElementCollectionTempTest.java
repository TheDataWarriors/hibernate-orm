/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.annotations.collectionelement;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OrderColumn;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Andrea Boriero
 */
@DomainModel(
		annotatedClasses = {
				PrimitiveArrayElementCollectionTempTest.Favorite.class
		}
)
@SessionFactory
public class PrimitiveArrayElementCollectionTempTest {

	@BeforeEach
	public void setUp(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					Favorite favorite = new Favorite( 1L );
					int[] favoriteNumbers = new int[2];
					favoriteNumbers[0] = 3;
					favoriteNumbers[1] = 7;
					favorite.setFavoriteNumbers( favoriteNumbers );
					session.persist( favorite );
				}
		);
	}

	@Test
	public void testIt(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					Favorite favorite = session.get( Favorite.class, 1L );
					int[] favoriteNumbers = favorite.getFavoriteNumbers();
					assertThat( favoriteNumbers.length, is( 2 ) );
					assertThat( favoriteNumbers[0], is( 3 ) );
					assertThat( favoriteNumbers[1], is( 7 ) );
				}
		);
	}

	@Entity(name = "Favorite")
	public static class Favorite {
		@Id
		Long id;

		public Favorite() {
		}

		public Favorite(Long id) {
			this.id = id;
		}

		@ElementCollection
		@OrderColumn(name = "nbr_index")
		int[] favoriteNumbers;

		public int[] getFavoriteNumbers() {
			return favoriteNumbers;
		}

		public void setFavoriteNumbers(int[] favoriteNumbers) {
			this.favoriteNumbers = favoriteNumbers;
		}
	}
}
