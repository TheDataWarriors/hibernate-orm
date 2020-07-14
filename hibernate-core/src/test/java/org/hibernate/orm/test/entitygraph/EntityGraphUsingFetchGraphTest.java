/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.entitygraph;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.persistence.AttributeNode;
import javax.persistence.Entity;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Subgraph;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.TypedQuery;
import javax.persistence.metamodel.Attribute;

import org.hibernate.graph.GraphSemantic;
import org.hibernate.metamodel.model.domain.EntityDomainType;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hibernate.testing.hamcrest.CollectionMatchers.hasSize;
import static org.hibernate.testing.hamcrest.InitializationCheckMatcher.isInitialized;

/**
 * @author Baris Cubukcuoglu
 */
@DomainModel(
		annotatedClasses = {
				EntityGraphUsingFetchGraphTest.CustomerOrder.class,
				EntityGraphUsingFetchGraphTest.OrderPosition.class,
				EntityGraphUsingFetchGraphTest.Product.class,
				EntityGraphUsingFetchGraphTest.Address.class
		}
)
@SessionFactory
public class EntityGraphUsingFetchGraphTest {

	@Test
	@TestForIssue( jiraKey = "HHH-9392")
	void fetchSubGraphFromSubgraph(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					Address address = new Address();
					address.city = "TestCity";

					CustomerOrder customerOrder = new CustomerOrder();
					customerOrder.shippingAddress = address;

					Product product = new Product();

					OrderPosition orderPosition = new OrderPosition();
					orderPosition.product = product;

					customerOrder.orderPosition = orderPosition;
					session.persist( address );
					session.persist( orderPosition );
					session.persist( product );
					session.persist( customerOrder );
				}
		);

		scope.inTransaction(
				session -> {
					EntityManager em = session.unwrap( EntityManager.class );
					final EntityGraph<CustomerOrder> entityGraph = em.createEntityGraph( CustomerOrder.class );
					//entityGraph.addAttributeNodes( "shippingAddress", "orderDate" );
					entityGraph.addAttributeNodes( "shippingAddress" );

					final Subgraph<OrderPosition> orderProductsSubgraph = entityGraph.addSubgraph( "orderPosition" );
					//orderProductsSubgraph.addAttributeNodes( "amount" );

					final Subgraph<Product> productSubgraph = orderProductsSubgraph.addSubgraph( "product" );
					//productSubgraph.addAttributeNodes( "productName" );

					TypedQuery<CustomerOrder> query = em.createQuery(
							"SELECT o FROM EntityGraphUsingFetchGraphTest$CustomerOrder o", CustomerOrder.class
					);
					query.setHint( GraphSemantic.LOAD.getJpaHintName(), entityGraph );
					final List<CustomerOrder> results = query.getResultList();

					assertThat( results, isInitialized() );
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-9392")
	void fetchAttributeNodeByStringFromSubgraph(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					Address address = new Address();
					address.city = "TestCity";

					CustomerOrder customerOrder = new CustomerOrder();
					customerOrder.shippingAddress = address;

					Product product = new Product();

					OrderPosition orderPosition = new OrderPosition();
					orderPosition.product = product;

					customerOrder.orderPosition = orderPosition;
					session.persist( address );
					session.persist( orderPosition );
					session.persist( product );
					session.persist( customerOrder );
				}
		);

		scope.inTransaction(
				session -> {
					final EntityManager em = session.unwrap( EntityManager.class );
					final EntityGraph<CustomerOrder> entityGraph = em.createEntityGraph( CustomerOrder.class );
					entityGraph.addAttributeNodes( "shippingAddress", "orderDate" );
					entityGraph.addAttributeNodes( "shippingAddress" );

					final Subgraph<OrderPosition> orderProductsSubgraph = entityGraph.addSubgraph( "orderPosition" );
					orderProductsSubgraph.addAttributeNodes( "amount" );
					orderProductsSubgraph.addAttributeNodes( "product" );

					final Subgraph<Product> productSubgraph = orderProductsSubgraph.addSubgraph( "product" );
					productSubgraph.addAttributeNodes( "productName" );

					TypedQuery<CustomerOrder> query = em.createQuery(
							"SELECT o FROM EntityGraphUsingFetchGraphTest$CustomerOrder o", CustomerOrder.class
					);
					query.setHint( GraphSemantic.LOAD.getJpaHintName(), entityGraph );
					final List<CustomerOrder> results = query.getResultList();

					assertEntityGraph( entityGraph );
					assertThat( results, isInitialized() );
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-13233")
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void fetchAttributeNodeByAttributeFromSubgraph(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					Address address = new Address();
					address.city = "TestCity";

					CustomerOrder customerOrder = new CustomerOrder();
					customerOrder.shippingAddress = address;

					Product product = new Product();

					OrderPosition orderPosition = new OrderPosition();
					orderPosition.product = product;

					customerOrder.orderPosition = orderPosition;
					session.persist( address );
					session.persist( orderPosition );
					session.persist( product );
					session.persist( customerOrder );
				}
		);

		scope.inTransaction(
				session -> {
					final EntityManager em = session.unwrap( EntityManager.class );
					final EntityGraph<CustomerOrder> entityGraph = em.createEntityGraph( CustomerOrder.class );
					EntityDomainType<CustomerOrder> customerOrderEntityType =
							scope.getSessionFactory().getMetamodel().entity( CustomerOrder.class );
					entityGraph.addAttributeNodes(
							(Attribute) customerOrderEntityType.getAttribute( "shippingAddress" ),
							(Attribute) customerOrderEntityType.getAttribute( "orderDate" )
					);
					entityGraph.addAttributeNodes( (Attribute) customerOrderEntityType.getAttribute( "shippingAddress" ) );

					final Subgraph<OrderPosition> orderProductsSubgraph =
							entityGraph.addSubgraph( (Attribute) customerOrderEntityType.getAttribute( "orderPosition" ) );
					EntityDomainType<OrderPosition> positionEntityType =
							scope.getSessionFactory().getMetamodel().entity( OrderPosition.class );
					orderProductsSubgraph.addAttributeNodes( (Attribute) positionEntityType.getAttribute( "amount" ) );
					orderProductsSubgraph.addAttributeNodes( (Attribute) positionEntityType.getAttribute( "product" ) );

					final Subgraph<Product> productSubgraph =
							orderProductsSubgraph.addSubgraph( (Attribute) positionEntityType.getAttribute( "product" ) );
					EntityDomainType<Product> productEntityType = scope.getSessionFactory().getMetamodel().entity( Product.class );
					productSubgraph.addAttributeNodes( (Attribute) productEntityType.getAttribute( "productName" ) );

					TypedQuery<CustomerOrder> query = em.createQuery(
							"SELECT o FROM EntityGraphUsingFetchGraphTest$CustomerOrder o", CustomerOrder.class
					);
					query.setHint( GraphSemantic.LOAD.getJpaHintName(), entityGraph );
					final List<CustomerOrder> results = query.getResultList();

					assertEntityGraph( entityGraph );
					assertThat( results, isInitialized() );
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-9392")
	void fetchUsingHql(SessionFactoryScope scope) {
		// This test is here only for comparison with results from fetchAttributeNodeFromSubgraph.
		// At the time this was written, the generated SQL from the HQL is the same as that generated with the
		// query hint in fetchAttributeNodeFromSubgraph. I am leaving this here for future debugging purposes.
		scope.inTransaction(
				session -> {
					Address address = new Address();
					address.city = "TestCity";

					CustomerOrder customerOrder = new CustomerOrder();
					customerOrder.shippingAddress = address;

					Product product = new Product();

					OrderPosition orderPosition = new OrderPosition();
					orderPosition.product = product;

					customerOrder.orderPosition = orderPosition;
					session.persist( address );
					session.persist( orderPosition );
					session.persist( product );
					session.persist( customerOrder );
				}
		);

		scope.inTransaction(
				session -> {
					final EntityManager em = session.unwrap( EntityManager.class );
					TypedQuery<CustomerOrder> query = em.createQuery(
							"SELECT o FROM EntityGraphUsingFetchGraphTest$CustomerOrder o left join fetch o.orderPosition pos left join fetch pos.product left join fetch o.shippingAddress", CustomerOrder.class
					);
					final List<CustomerOrder> results = query.getResultList();

					assertThat( results, isInitialized() );
				}
		);
	}

	/**
	 * Verify that entityGraph has expected state
	 *
	 * customerOrder - shippingAddress
	 *               - orderDate
	 *               - orderPosition - amount
	 *                               - product - productName
	 *
	 * @param entityGraph entityGraph
	 */
	private void assertEntityGraph(EntityGraph<CustomerOrder> entityGraph) {
		assertThat(entityGraph.getAttributeNodes(), hasSize( 3 ) );
		for ( AttributeNode<?> entityGraphAttributeNode : entityGraph.getAttributeNodes() ) {
			if ( "orderPosition".equals( entityGraphAttributeNode.getAttributeName() ) ) {
				Collection<Subgraph> orderPositionGraph = entityGraphAttributeNode.getSubgraphs().values();
				assertThat( orderPositionGraph, hasSize( 1 ) );
				List<AttributeNode<?>> orderPositionAttributes = orderPositionGraph.iterator().next().getAttributeNodes();
				assertThat( orderPositionAttributes, hasSize( 2 ) );
				for ( AttributeNode<?> orderPositionAttributeNode : orderPositionAttributes ) {
					if ( "product".equals( orderPositionAttributeNode.getAttributeName() ) ) {
						assertThat( orderPositionAttributeNode.getSubgraphs().entrySet(), hasSize( 1 ) );
					} else {
						assertThat( orderPositionAttributeNode.getSubgraphs().isEmpty(), is( true ) );
					}
				}
			} else {
				assertThat( entityGraphAttributeNode.getSubgraphs().isEmpty(), is( true ) );
			}
		}
	}

	@Entity
	@Table(name = "customerOrder")
	public static class CustomerOrder {
		@Id
		@GeneratedValue
		public Long id;

		@OneToOne
		public OrderPosition orderPosition;

		@Temporal(TemporalType.TIMESTAMP)
		public Date orderDate;

		@OneToOne
		public Address shippingAddress;
	}

	@Entity
	@Table(name = "address")
	public static class Address {
		@Id
		@GeneratedValue
		public Long id;

		public String city;
	}

	@Entity
	@Table(name = "orderPosition")
	public static class OrderPosition {
		@Id
		@GeneratedValue
		public Long id;

		public Integer amount;

		@ManyToOne
		@JoinColumn(name = "product")
		public Product product;
	}

	@Entity
	@Table(name = "product")
	public static class Product {
		@Id
		@GeneratedValue
		public Long id;

		public String productName;
	}
}
