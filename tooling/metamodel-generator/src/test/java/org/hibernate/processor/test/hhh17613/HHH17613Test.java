package org.hibernate.processor.test.hhh17613;

import org.hibernate.processor.test.hhh17613.a.ChildA;
import org.hibernate.processor.test.util.CompilationTest;
import org.hibernate.processor.test.util.TestUtil;
import org.hibernate.processor.test.util.WithClasses;

import org.hibernate.testing.orm.junit.JiraKey;
import org.junit.Test;

@JiraKey(value = " HHH-17613")
public class HHH17613Test extends CompilationTest {

	@Test
	@WithClasses({ ChildA.class, ChildB.class, Parent.class })
	@JiraKey(value = " HHH-17613")
	public void test() {
		System.out.println( TestUtil.getMetaModelSourceAsString( ChildA.class ) );
		System.out.println( TestUtil.getMetaModelSourceAsString( ChildB.class ) );
		System.out.println( TestUtil.getMetaModelSourceAsString( Parent.class ) );
	}
}
