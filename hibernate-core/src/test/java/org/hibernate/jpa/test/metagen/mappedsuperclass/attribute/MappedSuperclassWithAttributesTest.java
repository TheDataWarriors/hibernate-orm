/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpa.test.metagen.mappedsuperclass.attribute;

import java.util.Arrays;
import javax.persistence.EntityManagerFactory;

import org.hibernate.jpa.AvailableSettings;
import org.hibernate.jpa.test.TestingEntityManagerFactoryGenerator;

import org.hibernate.testing.FailureExpected;
import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.junit4.BaseUnitTestCase;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * @author Steve Ebersole
 */
@TestForIssue( jiraKey = "HHH-5024" )
public class MappedSuperclassWithAttributesTest extends BaseUnitTestCase {
	@Test
	public void testStaticMetamodel() {
		EntityManagerFactory emf = TestingEntityManagerFactoryGenerator.generateEntityManagerFactory(
				AvailableSettings.LOADED_CLASSES,
				Arrays.asList( Product.class )
		);
		try {
			assertNotNull( "'Product_.id' should not be null)", Product_.id );
			assertNotNull( "'Product_.name' should not be null)", Product_.name );

			assertNotNull( "'AbstractNameable_.name' should not be null)", AbstractNameable_.name );
		}
		finally {
			emf.close();
		}
	}

	@Test
	@FailureExpected(jiraKey = "HHH-11078")
	@TestForIssue(jiraKey = "HHH-11078")
	public void testStaticMetamodelOverridden() {
		EntityManagerFactory emf = TestingEntityManagerFactoryGenerator.generateEntityManagerFactory(
				AvailableSettings.LOADED_CLASSES,
				Arrays.asList( Product.class )
		);
		try {
			assertNotNull( "'AbstractNameable_.overridenName' should not be null)", AbstractNameable_.overridenName );

			assertNotNull( "'Product_.overridenName' should not be null)", Product_.overridenName ); // is null
		}
		finally {
			emf.close();
		}
	}
}
