/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.processor.test.mappedsuperclass.embeddablemappedsuperclass;

import org.hibernate.processor.test.util.CompilationTest;
import org.hibernate.processor.test.util.WithClasses;

import org.hibernate.testing.orm.junit.JiraKey;
import org.junit.Test;

import static org.hibernate.processor.test.util.TestUtil.assertMetamodelClassGeneratedFor;

/**
 * @author Hardy Ferentschik
 */
public class EmbeddableMappedSuperClassTest extends CompilationTest {

	@Test
	@JiraKey(value = "METAGEN-36")
	@WithClasses(EmbeddableAndMappedSuperClass.class)
	public void testMetaModelsGenerated() {
		assertMetamodelClassGeneratedFor( EmbeddableAndMappedSuperClass.class );
	}
}
