/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.xml.sequences;

import javax.persistence.EntityManager;

import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;

import org.junit.jupiter.api.Test;

/**
 * @author Emmanuel Bernard
 */
@RequiresDialectFeature( feature = DialectFeatureChecks.SupportsSequences.class )
@Jpa(
		xmlMappings = {"org/hibernate/orm/test/jpa/xml/sequences/orm.xml", "org/hibernate/orm/test/jpa/xml/sequences/orm2.xml"}
)
public class XmlTest {
	@Test
	public void testXmlMappingCorrectness(EntityManagerFactoryScope scope) {
		EntityManager em = scope.getEntityManagerFactory().createEntityManager();
		em.close();
	}
}
