/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.xml;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;

import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:stliu@hibernate.org">Strong Liu</a>
 */
@TestForIssue( jiraKey = "HHH-6039, HHH-6100" )
@Jpa(
		xmlMappings = {"org/hibernate/orm/test/jpa/xml/Qualifier.hbm.xml"}
)
public class JpaEntityNameTest {

	@Test
	public void testUsingSimpleHbmInJpa(EntityManagerFactoryScope scope){
		scope.inTransaction(
				entityManager -> {
					CriteriaBuilder cb = entityManager.getCriteriaBuilder();
					CriteriaQuery<Qualifier> cq = cb.createQuery(Qualifier.class);
					Root<Qualifier> qualifRoot = cq.from(Qualifier.class);
					cq.where( cb.equal( qualifRoot.get( "qualifierId" ), 32l ) );
					entityManager.createQuery(cq).getResultList();
				}
		);
	}
}
