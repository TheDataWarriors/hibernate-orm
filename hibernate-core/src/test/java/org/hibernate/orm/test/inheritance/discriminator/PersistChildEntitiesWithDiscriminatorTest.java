/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.inheritance.discriminator;

import org.hibernate.Session;
import org.hibernate.dialect.PostgreSQL81Dialect;
import org.hibernate.dialect.PostgreSQLDialect;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.hibernate.testing.orm.junit.RequiresDialects;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.test.inheritance.discriminator.InheritingEntity;
import org.hibernate.test.inheritance.discriminator.ParentEntity;
import org.junit.jupiter.api.Test;

/**
 * @author Pawel Stawicki
 */
@SessionFactory
@RequiresDialect(value = PostgreSQLDialect.class, version = 800)
@DomainModel(annotatedClasses = {
		ParentEntity.class, InheritingEntity.class
})
@TestForIssue(jiraKey = "HHH-6580")
public class PersistChildEntitiesWithDiscriminatorTest {

	@Test
	public void doIt(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					// we need the 2 inserts so that the id is incremented on the second get-generated-keys-result set, since
					// on the first insert both the pk and the discriminator values are 1
					session.save( new InheritingEntity( "yabba" ) );
					session.save( new InheritingEntity( "dabba" ) );
				}
		);

		scope.inTransaction(
				session -> {
					session.createQuery( "delete ParentEntity" ).executeUpdate();

				}
		);
	}

}
