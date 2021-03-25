/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.xml;

import javax.persistence.EntityManager;
import javax.persistence.SharedCacheMode;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;

import org.hibernate.testing.orm.jpa.NonStringValueSettingProvider;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

/**
 * @author Emmanuel Bernard
 */
@Jpa(
		xmlMappings = {"org/hibernate/orm/test/jpa/xml/orm.xml", "org/hibernate/orm/test/jpa/xml/orm2.xml"},
		nonStringValueSettingProviders = { XmlTest.SharedCacheModeProvider.class }
)
public class XmlTest {
	@Test
	public void testXmlMappingCorrectness(EntityManagerFactoryScope scope) {
		EntityManager em = scope.getEntityManagerFactory().createEntityManager();
		em.close();
	}

	@Test
	public void testXmlMappingWithCacheable(EntityManagerFactoryScope scope) {
		EntityManager em = scope.getEntityManagerFactory().createEntityManager();
		SharedSessionContractImplementor session = em.unwrap( SharedSessionContractImplementor.class );
		EntityPersister entityPersister= session.getFactory().getMetamodel().entityPersister( Lighter.class );
		Assertions.assertTrue(entityPersister.canReadFromCache());
		Assertions.assertTrue(entityPersister.canWriteToCache());
	}

	public static class SharedCacheModeProvider extends NonStringValueSettingProvider {
		@Override
		public String getKey() {
			return AvailableSettings.JPA_SHARED_CACHE_MODE;
		}

		@Override
		public Object getValue() {
			return SharedCacheMode.ENABLE_SELECTIVE;
		}
	}
}
