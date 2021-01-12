/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect;

import org.hibernate.FetchClauseType;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.expression.Literal;
import org.hibernate.sql.ast.tree.select.QueryPart;
import org.hibernate.sql.exec.spi.JdbcOperation;

/**
 * A SQL AST translator for DB2z.
 *
 * @author Christian Beikov
 */
public class DB2zSqlAstTranslator<T extends JdbcOperation> extends DB2SqlAstTranslator<T> {

	private final int version;

	public DB2zSqlAstTranslator(SessionFactoryImplementor sessionFactory, Statement statement, int version) {
		super( sessionFactory, statement );
		this.version = version;
	}

	@Override
	protected boolean shouldEmulateFetchClause(QueryPart queryPart) {
		// Percent fetches or ties fetches aren't supported in DB2 z/OS
		// Also, variable limit isn't supported before 12.0
		return getQueryPartForRowNumbering() != queryPart && (
				useOffsetFetchClause( queryPart ) && !isRowsOnlyFetchClauseType( queryPart )
						|| version < 1200 && queryPart.isRoot() && hasLimit()
						|| version < 1200 && queryPart.getFetchClauseExpression() != null && !( queryPart.getFetchClauseExpression() instanceof Literal )
		);
	}

	@Override
	protected boolean supportsOffsetClause() {
		return version >= 1200;
	}

}
