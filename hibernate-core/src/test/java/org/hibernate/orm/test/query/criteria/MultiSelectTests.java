/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.criteria;

import java.util.List;

import org.hibernate.query.sqm.NodeBuilder;

import org.hibernate.testing.orm.domain.gambit.BasicEntity;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Steve Ebersole
 */
@DomainModel( annotatedClasses = BasicEntity.class )
@SessionFactory
public class MultiSelectTests {
	@Test
	public void simpleArrayTest(SessionFactoryScope scope) {
		scope.inTransaction( (session) -> {
			session.persist( new BasicEntity( 1, "abc" ) );
		} );

		scope.inTransaction( (session) -> {
			final CriteriaBuilder nodeBuilder = session.getFactory().getNodeBuilder();

			final CriteriaQuery criteria = nodeBuilder.createQuery();
			final Root<BasicEntity> root = criteria.from( BasicEntity.class );
			final EntityType<BasicEntity> model = root.getModel();

			criteria.select(
					nodeBuilder.array(
							root.get( model.getDeclaredSingularAttribute( "id", Integer.class ) ),
							root.get( model.getDeclaredSingularAttribute( "data", String.class ) )
					)
			);

			final List<Object[]> results = session.createQuery( criteria ).list();
			assertThat( results ).hasSize( 1 );
			final Object[] firstResult = results.get( 0 );
			assertThat( firstResult[0] ).isEqualTo( 1 );
			assertThat( firstResult[1] ).isEqualTo( "abc" );
		} );
	}

	@Test
	public void multiselectArrayTest(SessionFactoryScope scope) {
		scope.inTransaction( (session) -> {
			session.persist( new BasicEntity( 1, "abc" ) );
		} );

		scope.inTransaction( (session) -> {
			final CriteriaBuilder nodeBuilder = session.getFactory().getNodeBuilder();

			final CriteriaQuery criteria = nodeBuilder.createQuery();
			final Root<BasicEntity> root = criteria.from( BasicEntity.class );
			final EntityType<BasicEntity> model = root.getModel();

			criteria.multiselect(
					root.get( model.getDeclaredSingularAttribute( "id", Integer.class ) ),
					root.get( model.getDeclaredSingularAttribute( "data", String.class ) )
			);

			final List<Object[]> results = session.createQuery( criteria ).list();
			assertThat( results ).hasSize( 1 );
			final Object[] firstResult = results.get( 0 );
			assertThat( firstResult[0] ).isEqualTo( 1 );
			assertThat( firstResult[1] ).isEqualTo( "abc" );
		} );
	}

	@Test
	public void typedArrayTest(SessionFactoryScope scope) {
		scope.inTransaction( (session) -> {
			session.persist( new BasicEntity( 1, "abc" ) );
		} );

		scope.inTransaction( (session) -> {
			final CriteriaBuilder nodeBuilder = session.getFactory().getNodeBuilder();

			final CriteriaQuery<Object[]> criteria = nodeBuilder.createQuery(Object[].class);
			final Root<BasicEntity> root = criteria.from( BasicEntity.class );
			final EntityType<BasicEntity> model = root.getModel();

			criteria.select(
					nodeBuilder.array(
							root.get( model.getDeclaredSingularAttribute( "id", Integer.class ) ),
							root.get( model.getDeclaredSingularAttribute( "data", String.class ) )
					)
			);

			final List<Object[]> results = session.createQuery( criteria ).list();
			assertThat( results ).hasSize( 1 );
			final Object[] firstResult = results.get( 0 );
			assertThat( firstResult[0] ).isEqualTo( 1 );
			assertThat( firstResult[1] ).isEqualTo( "abc" );
		} );
	}

	@Test
	public void simpleTupleTest(SessionFactoryScope scope) {
		scope.inTransaction( (session) -> {
			session.persist( new BasicEntity( 1, "abc" ) );
		} );

		scope.inTransaction( (session) -> {
			final CriteriaBuilder nodeBuilder = session.getFactory().getNodeBuilder();

			final CriteriaQuery<Tuple> criteria = nodeBuilder.createTupleQuery();
			final Root<BasicEntity> root = criteria.from( BasicEntity.class );
			final EntityType<BasicEntity> model = root.getModel();

			criteria.select(
					nodeBuilder.tuple(
							root.get( model.getDeclaredSingularAttribute( "id", Integer.class ) ),
							root.get( model.getDeclaredSingularAttribute( "data", String.class ) )
					)
			);

			final List<Tuple> results = session.createQuery( criteria ).list();
			assertThat( results ).hasSize( 1 );
			final Tuple firstResult = results.get( 0 );
			assertThat( firstResult.get(0) ).isEqualTo( 1 );
			assertThat( firstResult.get(1) ).isEqualTo( "abc" );
		} );
	}

	@Test
	public void typedTupleTest(SessionFactoryScope scope) {
		scope.inTransaction( (session) -> {
			session.persist( new BasicEntity( 1, "abc" ) );
		} );

		scope.inTransaction( (session) -> {
			final CriteriaBuilder nodeBuilder = session.getFactory().getNodeBuilder();

			final CriteriaQuery<Tuple> criteria = nodeBuilder.createQuery( Tuple.class );
			final Root<BasicEntity> root = criteria.from( BasicEntity.class );
			final EntityType<BasicEntity> model = root.getModel();

			criteria.select(
					nodeBuilder.tuple(
							root.get( model.getDeclaredSingularAttribute( "id", Integer.class ) ),
							root.get( model.getDeclaredSingularAttribute( "data", String.class ) )
					)
			);

			final List<Tuple> results = session.createQuery( criteria ).list();
			assertThat( results ).hasSize( 1 );
			final Tuple firstResult = results.get( 0 );
			assertThat( firstResult.get(0) ).isEqualTo( 1 );
			assertThat( firstResult.get(1) ).isEqualTo( "abc" );
		} );
	}

	@AfterEach
	public void dropTestData(SessionFactoryScope scope) {
		scope.inTransaction( (session) -> session.createQuery( "delete BasicEntity" ).executeUpdate() );
	}
}
