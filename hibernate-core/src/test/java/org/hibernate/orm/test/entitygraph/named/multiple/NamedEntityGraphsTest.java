/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.entitygraph.named.multiple;

import java.util.List;
import javax.persistence.AttributeNode;
import javax.persistence.Entity;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedEntityGraphs;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hibernate.testing.hamcrest.CollectionMatchers.hasSize;

/**
 * @author Steve Ebersole
 */
@DomainModel(
		annotatedClasses = {
				NamedEntityGraphsTest.Person.class,
				NamedEntityGraphsTest.Employee.class
		}
)
@SessionFactory
public class NamedEntityGraphsTest {

	@Test
	void testIt(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					EntityManager em = session.unwrap( EntityManager.class );
					EntityGraph<?> graph = em.getEntityGraph( "abc" );
					assertThat( graph, notNullValue() );
					graph = em.getEntityGraph( "xyz" );
					assertThat( graph, notNullValue() );
				}
		);
	}

	@Test
	void testAttributeNodesAreAvailable(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					EntityManager em = session.unwrap( EntityManager.class );
					EntityGraph<?> graph = em.getEntityGraph( "name_salary_graph" );
					assertThat( graph, notNullValue() );

					List<AttributeNode<?>> list =  graph.getAttributeNodes();
					assertThat( list, notNullValue() );
					assertThat( list, hasSize( 2 ) );

					AttributeNode<?> attributeNode1 = list.get( 0 );
					AttributeNode<?> attributeNode2 = list.get( 1 );
					assertThat( attributeNode1, notNullValue() );
					assertThat( attributeNode2, notNullValue() );

					assertThat( attributeNode1.getAttributeName(), anyOf( is( "name" ), is( "salary" ) ) );
					assertThat( attributeNode2.getAttributeName(), anyOf( is( "name" ), is( "salary" ) ) );
				}
		);
	}

	@Entity(name = "Person")
	@NamedEntityGraphs({
			@NamedEntityGraph( name = "abc" ),
			@NamedEntityGraph( name = "xyz" )
	})
	public static class Person {
		@Id
		public Long id;
	}

	@Entity(name = "Employee")
	@NamedEntityGraphs({
			@NamedEntityGraph(
					name = "name_salary_graph",
					includeAllAttributes = false,
					attributeNodes = {
							@NamedAttributeNode(value = "name"),
							@NamedAttributeNode(value = "salary")
					}
			),
	})
	public static class Employee {
		@Id
		public Long id;

		private String name;
		private double salary;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public double getSalary() {
			return salary;
		}

		public void setSalary(double salary) {
			this.salary = salary;
		}
	}
}
