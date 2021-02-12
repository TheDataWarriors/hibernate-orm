/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect;

import java.util.List;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.collections.Stack;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.spi.AbstractSqlAstTranslator;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.insert.Values;
import org.hibernate.sql.ast.tree.select.QueryGroup;
import org.hibernate.sql.ast.tree.select.QueryPart;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.exec.spi.JdbcOperation;

/**
 * A SQL AST translator for Oracle.
 *
 * @author Christian Beikov
 */
public class OracleSqlAstTranslator<T extends JdbcOperation> extends AbstractSqlAstTranslator<T> {

	public OracleSqlAstTranslator(SessionFactoryImplementor sessionFactory, Statement statement) {
		super( sessionFactory, statement );
	}

	protected boolean shouldEmulateFetchClause(QueryPart queryPart) {
		// Check if current query part is already row numbering to avoid infinite recursion
		return getQueryPartForRowNumbering() != queryPart && !supportsOffsetFetchClause() && (
				queryPart.isRoot() && hasLimit() || queryPart.getFetchClauseExpression() != null || queryPart.getOffsetClauseExpression() != null
		);
	}

	@Override
	protected void visitValuesList(List<Values> valuesList) {
		if ( valuesList.size() < 2 ) {
			super.visitValuesList( valuesList );
		}
		else {
			// Oracle doesn't support a multi-values insert
			// So we render a select union emulation instead
			String separator = "";
			final Stack<Clause> clauseStack = getClauseStack();
			try {
				clauseStack.push( Clause.VALUES );
				for ( Values values : valuesList ) {
					appendSql( separator );
					renderExpressionsAsSubquery( values.getExpressions() );
					separator = " union all ";
				}
			}
			finally {
				clauseStack.pop();
			}
		}
	}

	@Override
	public void visitQueryGroup(QueryGroup queryGroup) {
		if ( shouldEmulateFetchClause( queryGroup ) ) {
			emulateFetchOffsetWithWindowFunctions( queryGroup, true );
		}
		else {
			super.visitQueryGroup( queryGroup );
		}
	}

	@Override
	public void visitQuerySpec(QuerySpec querySpec) {
		if ( shouldEmulateFetchClause( querySpec ) ) {
			emulateFetchOffsetWithWindowFunctions( querySpec, true );
		}
		else {
			super.visitQuerySpec( querySpec );
		}
	}

	@Override
	public void visitOffsetFetchClause(QueryPart queryPart) {
		if ( !isRowNumberingCurrentQueryPart() ) {
			if ( supportsOffsetFetchClause() ) {
				renderOffsetFetchClause( queryPart, true );
			}
			else {
				assertRowsOnlyFetchClauseType( queryPart );
			}
		}
	}

	@Override
	protected void renderRowNumber(SelectClause selectClause, QueryPart queryPart) {
		if ( supportsOffsetFetchClause() || selectClause.isDistinct() ) {
			super.renderRowNumber( selectClause, queryPart );
		}
		else {
			appendSql( "rownum" );
		}
	}

	private boolean supportsOffsetFetchClause() {
		return getDialect().getVersion() >= 12;
	}

}
