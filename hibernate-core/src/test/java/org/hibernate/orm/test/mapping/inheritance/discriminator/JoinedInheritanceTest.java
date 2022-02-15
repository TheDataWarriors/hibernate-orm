/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */

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
package org.hibernate.orm.test.mapping.inheritance.discriminator;

import java.util.Set;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.FailureExpected;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test cases for joined inheritance with a discriminator column.
 *
 * @author Etienne Miret
 */
@DomainModel(
		annotatedClasses = {
				Polygon.class,
				Quadrilateral.class,
				JoinedInheritanceTest.BaseEntity.class,
				JoinedInheritanceTest.EntityA.class,
				JoinedInheritanceTest.EntityB.class,
				JoinedInheritanceTest.EntityC.class,
				JoinedInheritanceTest.EntityD.class
		}
)
@SessionFactory
public class JoinedInheritanceTest {


	@Test
	public void simpleSelectTest(SessionFactoryScope scope) {
		scope.inTransaction( (s) -> {
			s.createQuery( "from Polygon" ).list();
		} );
	}

	@Test
	@JiraKey( "HHH-9357" )
	public void selectWhereTypeEqual(SessionFactoryScope scope) {
		scope.inTransaction( (s) -> {
			s.createQuery( "from Polygon p where type(p) = Quadrilateral" ).list();
		} );
	}

	@Test
	@JiraKey( "HHH-12332" )
	@FailureExpected(
			reason = "I *think* the changes made for HHH-12332 may not have been the best.  " +
					"This is Hibernate's legacy \"implicit\" treat handling.  Why would it not be " +
					"supported here?"
	)
	public void joinUnrelatedCollectionOnBaseType(SessionFactoryScope scope) {
		scope.inTransaction( (s) -> {
			try {
				s.createQuery("from BaseEntity b join b.attributes").list();
				fail( "Expected a resolution exception for property 'attributes'!" );
			}
			catch (IllegalArgumentException ex) {
				assertThat( ex.getMessage() ).contains( "could not resolve property: attributes " );
			}
		} );
	}

	// Test entities for metamodel building for HHH-12332
	@Entity(name = "BaseEntity")
	@Inheritance(strategy = InheritanceType.JOINED)
	public static class BaseEntity {
		@Id
		private long id;
	}

	@Entity(name = "EntityA")
	public static class EntityA extends BaseEntity {
		@OneToMany(fetch = FetchType.LAZY)
		private Set<EntityC> attributes;
		@ManyToOne(fetch = FetchType.LAZY)
		private EntityC relation;
	}

	@Entity(name = "EntityB")
	public static class EntityB extends BaseEntity {
		@OneToMany(fetch = FetchType.LAZY)
		private Set<EntityD> attributes;
		@ManyToOne(fetch = FetchType.LAZY)
		private EntityD relation;
	}

	@Entity(name = "EntityC")
	public static class EntityC {
		@Id
		private long id;
	}

	@Entity(name = "EntityD")
	public static class EntityD {
		@Id
		private long id;
	}

}
