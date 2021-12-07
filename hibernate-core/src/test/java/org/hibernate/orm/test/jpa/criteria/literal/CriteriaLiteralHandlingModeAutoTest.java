/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.criteria.literal;

import org.hibernate.dialect.H2Dialect;
import org.hibernate.query.CastType;

import org.hibernate.testing.RequiresDialect;

import static org.hibernate.testing.transaction.TransactionUtil.doInJPA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Vlad Mihalcea
 */
@RequiresDialect(H2Dialect.class)
public class CriteriaLiteralHandlingModeAutoTest extends AbstractCriteriaLiteralHandlingModeTest {

	@Override
	protected String expectedSQL() {
		final String expression = casted( "?", CastType.STRING );
		return "select " + expression + ",b1_0.name from Book b1_0 where b1_0.id=? and b1_0.name=?";
	}
}
