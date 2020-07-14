/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.entitygraph.parser;

import java.util.Set;
import javax.persistence.AttributeNode;
import javax.persistence.Entity;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Subgraph;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hibernate.testing.hamcrest.CollectionMatchers.hasSize;
import static org.hibernate.testing.hamcrest.CollectionMatchers.isEmpty;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Steve Ebersole
 * @author Nathan Xu
 */
@DomainModel(
		annotatedClasses = {
				BasicEntityGraphTests.Entity1.class
		}
)
@SessionFactory
public class BasicEntityGraphTests {

	@Test
	void testBasicGraphBuilding(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					EntityManager em = session.unwrap( EntityManager.class );
					EntityGraph<Entity1> graphRoot = em.createEntityGraph( Entity1.class );
					assertThat( graphRoot.getName(), nullValue() );
					assertThat( graphRoot.getAttributeNodes(), isEmpty() );
				}
		);
	}

	@Test
	void testBasicSubgraphBuilding(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					EntityManager em = session.unwrap( EntityManager.class );
					EntityGraph<Entity1> graphRoot = em.createEntityGraph( Entity1.class );
					Subgraph<Entity1> parentGraph = graphRoot.addSubgraph( "parent" );
					Subgraph<Entity1> childGraph = graphRoot.addSubgraph( "children" );

					assertThat( graphRoot.getName(), nullValue() );
					assertThat( graphRoot.getAttributeNodes(), hasSize( 2 ) );
					graphRoot.getAttributeNodes().forEach( attributeNode ->
						assertThat( attributeNode.getSubgraphs().containsValue( parentGraph )
											|| attributeNode.getSubgraphs().containsValue( childGraph ),
									is( true ) )
					);
				}
		);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testBasicGraphImmutability(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					EntityManager em = session.unwrap( EntityManager.class );
					EntityGraph<Entity1> graphRoot = em.createEntityGraph( Entity1.class );
					graphRoot.addSubgraph( "parent" );
					graphRoot.addSubgraph( "children" );

					em.getEntityManagerFactory().addNamedEntityGraph( "immutable", graphRoot );

					graphRoot = (EntityGraph<Entity1>) em.getEntityGraph( "immutable" );

					assertThat( graphRoot.getName(), is( "immutable" ) );
					assertThat( graphRoot.getAttributeNodes(), hasSize( 2 ) );
					try {
						graphRoot.addAttributeNodes( "parent" );
						fail( "Should have failed" );
					}
					catch (IllegalStateException ignore) {
						// expected outcome
					}

					for ( AttributeNode<?> attrNode : graphRoot.getAttributeNodes() ) {
						assertThat( attrNode.getSubgraphs().entrySet(), hasSize( 1 ) );
						Subgraph<?> subgraph = attrNode.getSubgraphs().values().iterator().next();
						try {
							graphRoot.addAttributeNodes( "parent" );
							fail( "Should have failed" );
						}
						catch (IllegalStateException ignore) {
							// expected outcome
						}
					}
				}
		);
	}

	@Entity( name = "Entity1" )
	public static class Entity1 {
		@Id
		public Integer id;
		public String name;
		@ManyToOne
		public Entity1 parent;
		@OneToMany( mappedBy = "parent" )
		public Set<Entity1> children;
	}
}
