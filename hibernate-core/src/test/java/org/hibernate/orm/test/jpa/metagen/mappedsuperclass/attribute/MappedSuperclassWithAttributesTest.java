/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.metagen.mappedsuperclass.attribute;

import java.util.Arrays;
import javax.persistence.EntityManagerFactory;

import org.hibernate.jpa.AvailableSettings;
import org.hibernate.jpa.test.TestingEntityManagerFactoryGenerator;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.BaseUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Steve Ebersole
 */
@TestForIssue( jiraKey = "HHH-5024" )
@BaseUnitTest
public class MappedSuperclassWithAttributesTest {
	@Test
	public void testStaticMetamodel() {
		EntityManagerFactory emf = TestingEntityManagerFactoryGenerator.generateEntityManagerFactory(
				AvailableSettings.LOADED_CLASSES,
				Arrays.asList( Product.class )
		);
		try {
			assertNotNull( Product_.id, "'Product_.id' should not be null)" );
			assertNotNull( Product_.name, "'Product_.name' should not be null)" );

			assertNotNull( AbstractNameable_.name, "'AbstractNameable_.name' should not be null)" );
		}
		finally {
			emf.close();
		}
	}
}
