/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpamodelgen.test.embeddable.generics;

import org.hibernate.jpamodelgen.test.util.CompilationTest;
import org.hibernate.jpamodelgen.test.util.TestForIssue;
import org.hibernate.jpamodelgen.test.util.WithClasses;
import org.junit.Test;

import static java.lang.System.out;
import static org.hibernate.jpamodelgen.test.util.TestUtil.*;

/**
 * @author Chris Cranford
 */
@TestForIssue(jiraKey = "HHH_12030")
public class EmbeddableGenericsTest extends CompilationTest {
	@Test
	@WithClasses({ ChildEmbeddable.class, ParentEmbeddable.class })
	public void testGeneratingEmbeddablesWithGenerics() {
		out.println( getMetaModelSourceAsString(ParentEmbeddable.class) );
		assertMetamodelClassGeneratedFor( ChildEmbeddable.class );
		assertMetamodelClassGeneratedFor( ParentEmbeddable.class );

//		assertAttributeTypeInMetaModelFor(
//				ParentEmbeddable.class,
//				"fields",
//				"java.util.Set<? extends MyTypeInterface>",
//				"Expected Set for attribute named 'fields'"
//		);

		assertSuperClassRelationShipInMetamodel(
				ChildEmbeddable.class,
				ParentEmbeddable.class
		);
	}
}
