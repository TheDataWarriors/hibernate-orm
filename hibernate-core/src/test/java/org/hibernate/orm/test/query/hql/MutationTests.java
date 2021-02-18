/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.hql;

import org.hibernate.query.sqm.tree.delete.SqmDeleteStatement;
import org.hibernate.query.sqm.tree.predicate.SqmComparisonPredicate;

import org.hibernate.testing.orm.domain.StandardDomainModel;
import org.hibernate.testing.orm.domain.gambit.BasicEntity;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Steve Ebersole
 */
@SuppressWarnings("WeakerAccess")
@DomainModel( standardModels = StandardDomainModel.GAMBIT )
@ServiceRegistry
@SessionFactory( exportSchema = true )
public class MutationTests {
	@Test
	public void testSimpleDeleteTranslation(SessionFactoryScope scope) {
		final SqmDeleteStatement<?> sqmDelete = (SqmDeleteStatement<?>) scope.getSessionFactory()
				.getQueryEngine()
				.getHqlTranslator()
				.translate( "delete BasicEntity" );

		assertThat( sqmDelete, notNullValue() );
		assertThat( sqmDelete.getTarget().getEntityName(), is( BasicEntity.class.getName() ) );
		assertThat( sqmDelete.getRestriction(), nullValue() );
	}

	@Test
	public void testSimpleRestrictedDeleteTranslation(SessionFactoryScope scope) {
		final SqmDeleteStatement<?> sqmDelete = (SqmDeleteStatement<?>) scope.getSessionFactory()
				.getQueryEngine()
				.getHqlTranslator()
				.translate( "delete BasicEntity where data = 'abc'" );

		assertThat( sqmDelete, notNullValue() );
		assertThat( sqmDelete.getTarget().getEntityName(), is( BasicEntity.class.getName() ) );
		assertThat( sqmDelete.getRestriction(), notNullValue() );
		assertThat( sqmDelete.getRestriction(), instanceOf( SqmComparisonPredicate.class ) );
	}
}
