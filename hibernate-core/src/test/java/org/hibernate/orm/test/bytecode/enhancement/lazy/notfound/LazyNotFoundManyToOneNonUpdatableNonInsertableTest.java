/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.bytecode.enhancement.lazy.notfound;

import jakarta.persistence.CascadeType;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.Hibernate;
import org.hibernate.annotations.LazyToOne;
import org.hibernate.annotations.LazyToOneOption;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.bytecode.enhancement.BytecodeEnhancerRunner;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.testing.transaction.TransactionUtil.doInHibernate;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * @author Gail Badner
 */
@TestForIssue( jiraKey = "HHH-12226")
@RunWith( BytecodeEnhancerRunner.class )
public class LazyNotFoundManyToOneNonUpdatableNonInsertableTest extends BaseCoreFunctionalTestCase {
	private static int ID = 1;

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class[] {
				User.class,
				Lazy.class
		};
	}

	@Test
	public void test() {
		doInHibernate(
				this::sessionFactory, session -> {
					Lazy p = new Lazy();
					p.id = ID;
					User u = new User();
					u.id = ID;
					u.setLazy( p );
					session.persist( u );
				}
		);

		doInHibernate(
				this::sessionFactory, session -> {
					session.delete( session.get( Lazy.class, ID ) );
				}
		);

		doInHibernate(
				this::sessionFactory, session -> {
					User user = session.find( User.class, ID );
					// per UserGuide (and simply correct behavior), `@NotFound` forces EAGER fetching
					assertThat( Hibernate.isPropertyInitialized( user, "lazy" ) )
							.describedAs( "`User#lazy` is not eagerly initialized due to presence of `@NotFound`" )
							.isTrue();
					assertNull( user.getLazy() );
				}
		);
	}

	@Entity(name="User")
	@Table(name = "USER_TABLE")
	public static class User {

		@Id
		private Integer id;

		@ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, optional = true)
		@LazyToOne(value = LazyToOneOption.NO_PROXY)
		@NotFound(action = NotFoundAction.IGNORE)
		@JoinColumn(
				foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
				name = "id",
				referencedColumnName = "id",
				insertable = false,
				updatable = false
		)
		private Lazy lazy;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public Lazy getLazy() {
			return lazy;
		}

		public void setLazy(Lazy lazy) {
			this.lazy = lazy;
		}

	}

	@Entity(name = "Lazy")
	@Table(name = "LAZY")
	public static class Lazy {
		@Id
		private Integer id;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}
	}

}
