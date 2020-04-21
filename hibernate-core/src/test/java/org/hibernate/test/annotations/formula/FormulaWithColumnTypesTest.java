/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.annotations.formula;

import java.io.Serializable;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.annotations.Formula;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.criterion.Order;
import org.hibernate.dialect.H2Dialect;

import org.hibernate.testing.RequiresDialect;
import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test that the queries generated by {@link org.hibernate.annotations.Formula} properly ignore type names when escaping identifiers.
 * <p/>
 * Created by Michael Hum on 17/07/2015.
 */
@RequiresDialect(H2Dialect.class)
public class FormulaWithColumnTypesTest extends BaseCoreFunctionalTestCase {

	@Override
	protected void configure(Configuration configuration) {
		configuration.setProperty(
				Environment.DIALECT,
				ExtendedDialect.class.getName()
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-9951")
	public void testFormulaAnnotationWithTypeNames() {

		Session session = openSession();
		Transaction transaction = session.beginTransaction();

		DisplayItem displayItem20 = new DisplayItem();
		displayItem20.setDisplayCode( "20" );

		DisplayItem displayItem03 = new DisplayItem();
		displayItem03.setDisplayCode( "03" );

		DisplayItem displayItem100 = new DisplayItem();
		displayItem100.setDisplayCode( "100" );

		session.persist( displayItem20 );
		session.persist( displayItem03 );
		session.persist( displayItem100 );

		transaction.commit();
		session.close();

		// 1. Default sorting by display code natural ordering (resulting in 3-100-20).
		session = openSession();
		transaction = session.beginTransaction();

		List displayItems = session.createCriteria( DisplayItem.class )
				.addOrder( Order.asc( "displayCode" ) )
				.list();

		assertNotNull( displayItems );
		assertEquals( displayItems.size(), 3 );
		assertEquals(
				"03",
				( (DisplayItem) displayItems.get( 0 ) ).getDisplayCode()
		);
		assertEquals(
				"100",
				( (DisplayItem) displayItems.get( 1 ) ).getDisplayCode()
		);
		assertEquals(
				"20",
				( (DisplayItem) displayItems.get( 2 ) ).getDisplayCode()
		);
		transaction.commit();
		session.close();


		// 2. Sorting by the casted type (resulting in 3-20-100).
		session = openSession();
		transaction = session.beginTransaction();

		List displayItemsSortedByInteger = session.createCriteria( DisplayItem.class )
				.addOrder( Order.asc( "displayCodeAsInteger" ) )
				.list();

		assertNotNull( displayItemsSortedByInteger );
		assertEquals( displayItemsSortedByInteger.size(), 3 );
		assertEquals(
				"03",
				( (DisplayItem) displayItemsSortedByInteger.get( 0 ) ).getDisplayCode()
		);
		assertEquals(
				"20",
				( (DisplayItem) displayItemsSortedByInteger.get( 1 ) ).getDisplayCode()
		);
		assertEquals(
				"100",
				( (DisplayItem) displayItemsSortedByInteger.get( 2 ) ).getDisplayCode()
		);
		transaction.commit();
		session.close();
	}

	@Override
	public Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] {DisplayItem.class};
	}

	/**
	 * Test entity for formulas.
	 * <p>
	 * INTEGER is registered as a keyword for testing lower-case sensitivity.
	 * FLOAT is registered as a valid column type with oracle dialects.
	 * <p>
	 * Created by Michael Hum on 17/07/2015.
	 */
	@Entity(name = "DisplayItem")
	public static class DisplayItem implements Serializable {

		private int id;

		private String displayCode;

		private Integer displayCodeAsInteger;

		private Integer displayCodeAsFloat;

		@Id
		@GeneratedValue
		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		@Column(name = "DISPLAY_CODE")
		public String getDisplayCode() {
			return this.displayCode;
		}

		public void setDisplayCode(final String displayCode) {
			this.displayCode = displayCode;
		}

		@Formula("CAST(DISPLAY_CODE AS FLOAT)")
		public Integer getDisplayCodeAsFloat() {
			return displayCodeAsFloat;
		}

		public void setDisplayCodeAsFloat(final Integer displayCodeAsFloat) {
			this.displayCodeAsFloat = displayCodeAsFloat;
		}

		@Formula("CAST(DISPLAY_CODE AS INTEGER)")
		public Integer getDisplayCodeAsInteger() {
			return displayCodeAsInteger;
		}

		public void setDisplayCodeAsInteger(final Integer displayCodeAsInteger) {
			this.displayCodeAsInteger = displayCodeAsInteger;
		}
	}

	/**
	 * Dialect for test case where we register a keyword and see if it gets escaped or not.
	 * <p>
	 * Created by Mike on 18/07/2015.
	 */
	public static class ExtendedDialect extends H2Dialect {

		public ExtendedDialect() {
			super();
			registerKeyword( "INTEGER" );
		}

	}
}
