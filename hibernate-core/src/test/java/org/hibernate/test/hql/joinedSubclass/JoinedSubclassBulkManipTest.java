/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.hql.joinedSubclass;


import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

/**
 * @author Steve Ebersole
 */
@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsTemporaryTable.class)
@DomainModel(
		annotatedClasses = { Employee.class }
)
@SessionFactory
public class JoinedSubclassBulkManipTest {

	@Test
	@TestForIssue(jiraKey = "HHH-1657")
	public void testHqlDeleteOnJoinedSubclass(SessionFactoryScope scope) {
		scope.inTransaction(
				s -> {
					// syntax checking on the database...
					s.createQuery( "delete from Employee" ).executeUpdate();
					s.createQuery( "delete from Person" ).executeUpdate();
					s.createQuery( "delete from Employee e" ).executeUpdate();
					s.createQuery( "delete from Person p" ).executeUpdate();
					s.createQuery( "delete from Employee where name like 'S%'" ).executeUpdate();
					s.createQuery( "delete from Employee e where e.name like 'S%'" ).executeUpdate();
					s.createQuery( "delete from Person where name like 'S%'" ).executeUpdate();
					s.createQuery( "delete from Person p where p.name like 'S%'" ).executeUpdate();

					// now the forms that actually fail from problem underlying HHH-1657
					// which is limited to references to properties mapped to column names existing in both tables
					// which is normally just the pks.  super critical ;)

					s.createQuery( "delete from Employee where id = 1" ).executeUpdate();
					s.createQuery( "delete from Employee e where e.id = 1" ).executeUpdate();
					s.createQuery( "delete from Person where id = 1" ).executeUpdate();
					s.createQuery( "delete from Person p where p.id = 1" ).executeUpdate();
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-1657")
	public void testHqlUpdateOnJoinedSubclass(SessionFactoryScope scope) {
		scope.inTransaction(
				s -> {
					// syntax checking on the database...
					s.createQuery( "update Employee set name = 'Some Other Name' where employeeNumber like 'A%'" )
							.executeUpdate();
					s.createQuery( "update Employee e set e.name = 'Some Other Name' where e.employeeNumber like 'A%'" )
							.executeUpdate();
					s.createQuery( "update Person set name = 'Some Other Name' where name like 'S%'" ).executeUpdate();
					s.createQuery( "update Person p set p.name = 'Some Other Name' where p.name like 'S%'" )
							.executeUpdate();

					// now the forms that actually fail from problem underlying HHH-1657
					// which is limited to references to properties mapped to column names existing in both tables
					// which is normally just the pks.  super critical ;)

					s.createQuery( "update Employee set name = 'Some Other Name' where id = 1" ).executeUpdate();
					s.createQuery( "update Employee e set e.name = 'Some Other Name' where e.id = 1" ).executeUpdate();
					s.createQuery( "update Person set name = 'Some Other Name' where id = 1" ).executeUpdate();
					s.createQuery( "update Person p set p.name = 'Some Other Name' where p.id = 1" ).executeUpdate();
				}
		);
	}
}
