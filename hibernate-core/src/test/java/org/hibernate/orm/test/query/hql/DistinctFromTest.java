/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.hql;

import org.hibernate.testing.orm.domain.StandardDomainModel;
import org.hibernate.testing.orm.domain.gambit.EntityOfBasics;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author Christian Beikov
 */
@ServiceRegistry
@DomainModel(standardModels = StandardDomainModel.GAMBIT)
@SessionFactory
public class DistinctFromTest {

	@BeforeAll
	public void prepareData(SessionFactoryScope scope) {
		scope.inTransaction(
				em -> {
					EntityOfBasics entity1 = new EntityOfBasics();
					entity1.setId( 123 );
					em.persist( entity1 );
					EntityOfBasics entity = new EntityOfBasics();
					entity.setId( 456 );
					entity.setTheString( "abc" );
					em.persist( entity );
				}
		);
	}

	@Test
	public void testDistinctFrom(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					Assertions.assertEquals(
							456,
							session.createQuery(
									"select e.id from EntityOfBasics e where e.theString is distinct from :param",
									Integer.class
							)
									.setParameter( "param", null )
									.list()
									.get( 0 )
					);
					Assertions.assertEquals(
							123,
							session.createQuery(
									"select e.id from EntityOfBasics e where e.theString is distinct from :param",
									Integer.class
							)
									.setParameter( "param", "abc" )
									.list()
									.get( 0 )
					);
					Assertions.assertEquals(
							123,
							session.createQuery(
									"select e.id from EntityOfBasics e where e.theString is not distinct from :param",
									Integer.class
							)
									.setParameter( "param", null )
									.list()
									.get( 0 )
					);
					Assertions.assertEquals(
							456,
							session.createQuery(
									"select e.id from EntityOfBasics e where e.theString is not distinct from :param",
									Integer.class
							)
									.setParameter( "param", "abc" )
									.list()
									.get( 0 )
					);
				}
		);
	}

}