/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.set;

import java.util.List;

import javax.persistence.Tuple;

import org.hibernate.testing.orm.domain.StandardDomainModel;
import org.hibernate.testing.orm.domain.gambit.EntityOfLists;
import org.hibernate.testing.orm.domain.gambit.EnumValue;
import org.hibernate.testing.orm.domain.gambit.SimpleComponent;
import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 *
 * @author Christian Beikov
 */
@DomainModel( standardModels = StandardDomainModel.GAMBIT )
@ServiceRegistry
@SessionFactory
public class SetOperationTest {
    @BeforeEach
    public void createTestData(SessionFactoryScope scope) {
        scope.inTransaction(
                session -> {
                    session.save( new EntityOfLists( 1, "first" ) );
                    session.save( new EntityOfLists( 2, "second" ) );
                    session.save( new EntityOfLists( 3, "third" ) );
                }
        );
    }

    @AfterEach
    public void dropTestData(SessionFactoryScope scope) {
        scope.inTransaction(
                session -> {
                    session.createQuery( "delete from EntityOfLists" ).executeUpdate();
                    session.createQuery( "delete from SimpleEntity" ).executeUpdate();
                }
        );
    }

    @Test
    @RequiresDialectFeature(feature = DialectFeatureChecks.SupportsUnion.class)
    public void testUnionAll(SessionFactoryScope scope) {
        scope.inSession(
                session -> {
                    List<EntityOfLists> list = session.createQuery(
                            "select e from EntityOfLists e where e.id = 1 " +
                                    "union all " +
                                    "select e from EntityOfLists e where e.id = 2",
                            EntityOfLists.class
                    ).list();
                    assertThat( list.size(), is( 2 ) );
                }
        );
    }

    @Test
    @RequiresDialectFeature(feature = DialectFeatureChecks.SupportsUnion.class)
    public void testUnionAllLimit(SessionFactoryScope scope) {
        scope.inSession(
                session -> {
                    List<Tuple> list = session.createQuery(
                            "(select e.id, e from EntityOfLists e where e.id = 1 " +
                                    "union all " +
                                    "select e.id, e from EntityOfLists e where e.id = 2) " +
                                    "order by 1 fetch first 1 row only",
                            Tuple.class
                    ).list();
                    assertThat( list.size(), is( 1 ) );
                }
        );
    }

    @Test
    @RequiresDialectFeature(feature = DialectFeatureChecks.SupportsUnion.class)
    public void testUnionAllLimitSubquery(SessionFactoryScope scope) {
        scope.inSession(
                session -> {
                    List<Tuple> list = session.createQuery(
                            "select e.id, e from EntityOfLists e where e.id = 1 " +
                                    "union all " +
                                    "select e.id, e from EntityOfLists e where e.id = 2 " +
                                    "order by 1 fetch first 1 row only",
                            Tuple.class
                    ).list();
                    assertThat( list.size(), is( 2 ) );
                }
        );
    }

    @Test
    @RequiresDialectFeature(feature = DialectFeatureChecks.SupportsUnion.class)
    public void testUnionAllLimitNested(SessionFactoryScope scope) {
        scope.inSession(
                session -> {
                    List<Tuple> list = session.createQuery(
                            "(select e.id, e from EntityOfLists e where e.id = 1 " +
                                    "union all " +
                                    "(select e.id, e from EntityOfLists e where e.id = 2 order by 1 fetch first 1 row only)) " +
                                    "order by 1 fetch first 1 row only",
                            Tuple.class
                    ).list();
                    assertThat( list.size(), is( 1 ) );
                }
        );
    }
}
