/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect;

import java.util.List;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.ComparisonOperator;
import org.hibernate.sql.ast.spi.AbstractSqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.cte.CteStatement;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.Literal;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.Summarization;
import org.hibernate.sql.ast.tree.select.QueryPart;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.exec.spi.JdbcOperation;

/**
 * A SQL AST translator for Sybase ASE.
 *
 * @author Christian Beikov
 */
public class SybaseASESqlAstTranslator<T extends JdbcOperation> extends AbstractSqlAstTranslator<T> {

	public SybaseASESqlAstTranslator(SessionFactoryImplementor sessionFactory, Statement statement) {
		super( sessionFactory, statement );
	}

	@Override
	protected void renderSearchClause(CteStatement cte) {
		// Sybase ASE does not support this, but it's just a hint anyway
	}

	@Override
	protected void renderCycleClause(CteStatement cte) {
		// Sybase ASE does not support this, but it can be emulated
	}

	@Override
	protected void visitSqlSelections(SelectClause selectClause) {
		if ( supportsTopClause() ) {
			renderTopClause( (QuerySpec) getQueryPartStack().getCurrent(), true );
		}
		super.visitSqlSelections( selectClause );
	}

	@Override
	protected void renderFetchPlusOffsetExpression(
			Expression fetchClauseExpression,
			Expression offsetClauseExpression,
			int offset) {
		renderFetchPlusOffsetExpressionAsSingleParameter( fetchClauseExpression, offsetClauseExpression, offset );
	}

	@Override
	public void visitOffsetFetchClause(QueryPart queryPart) {
		assertRowsOnlyFetchClauseType( queryPart );
		if ( !queryPart.isRoot() && queryPart.hasOffsetOrFetchClause() ) {
			if ( queryPart.getFetchClauseExpression() != null && !supportsTopClause() || queryPart.getOffsetClauseExpression() != null ) {
				throw new IllegalArgumentException( "Can't emulate offset fetch clause in subquery" );
			}
		}
	}

	@Override
	protected void renderFetchExpression(Expression fetchExpression) {
		if ( supportsParameterOffsetFetchExpression() ) {
			super.renderFetchExpression( fetchExpression );
		}
		else {
			renderExpressionAsLiteral( fetchExpression, getJdbcParameterBindings() );
		}
	}

	@Override
	protected void renderOffsetExpression(Expression offsetExpression) {
		if ( supportsParameterOffsetFetchExpression() ) {
			super.renderOffsetExpression( offsetExpression );
		}
		else {
			renderExpressionAsLiteral( offsetExpression, getJdbcParameterBindings() );
		}
	}

	@Override
	protected void renderComparison(Expression lhs, ComparisonOperator operator, Expression rhs) {
		renderComparisonEmulateIntersect( lhs, operator, rhs );
	}

	@Override
	protected void renderSelectTupleComparison(
			List<SqlSelection> lhsExpressions,
			SqlTuple tuple,
			ComparisonOperator operator) {
		emulateTupleComparison( lhsExpressions, tuple.getExpressions(), operator, true );
	}

	@Override
	protected void renderPartitionItem(Expression expression) {
		if ( expression instanceof Literal ) {
			// todo (6.0): We need to introduce a dummy from clause item
//			String fromItem = ", (select 1 x " + dialect.getFromDual() + ") dummy";
//			sqlBuffer.insert( fromEndIndex, fromItem );
//			appendSql( "dummy.x" );
			throw new UnsupportedOperationException( "Column reference strategy is not yet implemented!" );
		}
		else if ( expression instanceof Summarization ) {
			// This could theoretically be emulated by rendering all grouping variations of the query and
			// connect them via union all but that's probably pretty inefficient and would have to happen
			// on the query spec level
			throw new UnsupportedOperationException( "Summarization is not supported by DBMS!" );
		}
		else {
			expression.accept( this );
		}
	}

	@Override
	protected boolean needsRowsToSkip() {
		return true;
	}

	@Override
	protected boolean needsMaxRows() {
		return !supportsTopClause();
	}

	@Override
	protected boolean supportsRowValueConstructorSyntax() {
		return false;
	}

	@Override
	protected boolean supportsRowValueConstructorSyntaxInInList() {
		return false;
	}

	@Override
	protected boolean supportsRowValueConstructorSyntaxInQuantifiedPredicates() {
		return false;
	}

	private boolean supportsTopClause() {
		return getDialect().getVersion() >= 1250;
	}

	private boolean supportsParameterOffsetFetchExpression() {
		return false;
	}
}
