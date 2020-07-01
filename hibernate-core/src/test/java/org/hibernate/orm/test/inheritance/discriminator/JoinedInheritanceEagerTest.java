/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2014, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.orm.test.inheritance.discriminator;

import java.util.List;
import java.util.Set;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import org.hibernate.Hibernate;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test cases for joined inheritance with eager fetching.
 *
 * @author Christian Beikov
 */
@DomainModel(
		annotatedClasses = {
				JoinedInheritanceEagerTest.BaseEntity.class,
				JoinedInheritanceEagerTest.EntityA.class,
				JoinedInheritanceEagerTest.EntityB.class,
				JoinedInheritanceEagerTest.EntityC.class,
				JoinedInheritanceEagerTest.EntityD.class
		}
)
@SessionFactory
public class JoinedInheritanceEagerTest {

	@BeforeEach
	public void setUp(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			EntityC entityC = new EntityC( 1L );
			EntityD entityD = new EntityD( 2L );

			EntityB entityB = new EntityB( 3L );
			entityB.setRelation( entityD );

			EntityA entityA = new EntityA( 4L );
			entityA.setRelation( entityC );

			session.persist( entityC );
			session.persist( entityD );
			session.persist( entityA );
			session.persist( entityB );
		} );
	}

	@AfterEach
	public void cleanUp(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					EntityA entityA = session.get( EntityA.class, 4L );
					EntityB entityB = session.get( EntityB.class, 3L );
					EntityD entityD = session.get( EntityD.class, 2L );
					EntityC entityC = session.get( EntityC.class, 1L );

					session.delete( entityD );
					session.delete( entityC );
					session.delete( entityA );
					session.delete( entityB );
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-12375")
	public void joinFindEntity(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			EntityA entityA = session.get( EntityA.class, 4L );
			assertTrue( Hibernate.isInitialized( entityA.getRelation() ) );
			assertFalse( Hibernate.isInitialized( entityA.getAttributes() ) );
		} );
	}

	@Test
	@TestForIssue(jiraKey = "HHH-12375")
	public void joinFindParenEntity(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			BaseEntity baseEntity = session.get( BaseEntity.class, 4L );
			assertThat( baseEntity, notNullValue() );
			assertThat( baseEntity, instanceOf( EntityA.class ) );
			assertTrue( Hibernate.isInitialized( ( (EntityA) baseEntity ).getRelation() ) );
			assertFalse( Hibernate.isInitialized( ( (EntityA) baseEntity ).getAttributes() ) );
		} );

		scope.inTransaction( session -> {
			BaseEntity baseEntity = session.get( BaseEntity.class, 3L );
			assertThat( baseEntity, notNullValue() );
			assertThat( baseEntity, instanceOf( EntityB.class ) );
			assertTrue( Hibernate.isInitialized( ( (EntityB) baseEntity ).getRelation() ) );
			assertFalse( Hibernate.isInitialized( ( (EntityB) baseEntity ).getAttributes() ) );
		} );
	}

	@Test
	@TestForIssue(jiraKey = "HHH-12375")
	public void selectBaseType(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			List result = session.createQuery( "from BaseEntity" ).list();
			assertEquals( 2, result.size() );
		} );
	}

	@Entity(name = "BaseEntity")
	@Inheritance(strategy = InheritanceType.JOINED)
	public static class BaseEntity {
		@Id
		private Long id;

		public BaseEntity() {
		}

		public BaseEntity(Long id) {
			this.id = id;
		}
	}

	@Entity(name = "EntityA")
	public static class EntityA extends BaseEntity {
		@OneToMany(fetch = FetchType.LAZY)
		private Set<EntityC> attributes;
		@ManyToOne(fetch = FetchType.EAGER)
		private EntityC relation;

		public EntityA() {
		}

		public EntityA(Long id) {
			super( id );
		}

		public void setRelation(EntityC relation) {
			this.relation = relation;
		}

		public EntityC getRelation() {
			return relation;
		}

		public Set<EntityC> getAttributes() {
			return attributes;
		}
	}

	@Entity(name = "EntityB")
	public static class EntityB extends BaseEntity {
		@OneToMany(fetch = FetchType.LAZY)
		private Set<EntityD> attributes;
		@ManyToOne(fetch = FetchType.EAGER)
		private EntityD relation;

		public EntityB() {
		}

		public EntityB(Long id) {
			super( id );
		}

		public void setRelation(EntityD relation) {
			this.relation = relation;
		}

		public EntityD getRelation() {
			return relation;
		}

		public Set<EntityD> getAttributes() {
			return attributes;
		}
	}

	@Entity(name = "EntityC")
	public static class EntityC {
		@Id
		private Long id;

		public EntityC() {
		}

		public EntityC(Long id) {
			this.id = id;
		}
	}

	@Entity(name = "EntityD")
	public static class EntityD {
		@Id
		private Long id;

		public EntityD() {
		}

		public EntityD(Long id) {
			this.id = id;
		}
	}
}
