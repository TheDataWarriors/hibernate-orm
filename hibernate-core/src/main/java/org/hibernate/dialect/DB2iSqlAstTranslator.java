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
 * A SQL AST translator for DB2i.
 *
 * @author Christian Beikov
 */
public class DB2iSqlAstTranslator<T extends JdbcOperation> extends DB2SqlAstTranslator<T> {

	private final int version;

	public DB2iSqlAstTranslator(SessionFactoryImplementor sessionFactory, Statement statement, int version) {
		super( sessionFactory, statement );
		this.version = version;
	}

	@Override
	protected boolean shouldEmulateFetchClause(QueryPart queryPart) {
		// Percent fetches or ties fetches aren't supported in DB2 iSeries
		// According to LegacyDB2LimitHandler, variable limit also isn't supported before 7.1
		return getQueryPartForRowNumbering() != queryPart && (
				useOffsetFetchClause( queryPart ) && !isRowsOnlyFetchClauseType( queryPart )
						|| version < 710 && ( queryPart.isRoot() && hasLimit() || !( queryPart.getFetchClauseExpression() instanceof Literal ) )
		);
	}

	@Override
	protected boolean supportsOffsetClause() {
		return version >= 710;
	}


}
