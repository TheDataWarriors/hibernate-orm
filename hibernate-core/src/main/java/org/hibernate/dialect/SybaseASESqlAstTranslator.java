/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect;

import java.util.List;

import org.hibernate.LockMode;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.ComparisonOperator;
import org.hibernate.sql.ast.SqlAstJoinType;
import org.hibernate.sql.ast.spi.AbstractSqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.MutationStatement;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.cte.CteStatement;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.Literal;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.Summarization;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.from.UnionTableReference;
import org.hibernate.sql.ast.tree.select.QueryGroup;
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
	protected boolean renderTableReference(TableReference tableReference, LockMode lockMode) {
		super.renderTableReference( tableReference, lockMode );
		if ( getDialect().getVersion() < 1570 ) {
			if ( LockMode.READ.lessThan( lockMode ) ) {
				appendSql( " holdlock" );
			}
			return true;
		}
		return false;
	}

	@Override
	protected void renderJoinType(SqlAstJoinType joinType) {
		if ( joinType == SqlAstJoinType.CROSS ) {
			appendSql( ", " );
		}
		else {
			super.renderJoinType( joinType );
		}
	}

	@Override
	protected void renderForUpdateClause(QuerySpec querySpec, ForUpdateClause forUpdateClause) {
		if ( getDialect().getVersion() < 1570 ) {
			return;
		}
		super.renderForUpdateClause( querySpec, forUpdateClause );
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
			renderTopClause( (QuerySpec) getQueryPartStack().getCurrent(), true, false );
		}
		super.visitSqlSelections( selectClause );
	}

	@Override
	protected void renderFetchPlusOffsetExpression(
			Expression fetchClauseExpression,
			Expression offsetClauseExpression,
			int offset) {
		renderFetchPlusOffsetExpressionAsLiteral( fetchClauseExpression, offsetClauseExpression, offset );
	}

	@Override
	public void visitQueryGroup(QueryGroup queryGroup) {
		if ( queryGroup.hasSortSpecifications() || queryGroup.hasOffsetOrFetchClause() ) {
			appendSql( "select " );
			renderTopClause(
					queryGroup.getOffsetClauseExpression(),
					queryGroup.getFetchClauseExpression(),
					queryGroup.getFetchClauseType(),
					true,
					false
			);
			appendSql( "* from (" );
			renderQueryGroup( queryGroup, false );
			appendSql( ") grp_" );
			visitOrderBy( queryGroup.getSortSpecifications() );
		}
		else {
			super.visitQueryGroup( queryGroup );
		}
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
		// I think intersect is only supported in 16.0 SP3
//		renderComparisonEmulateIntersect( lhs, operator, rhs );

		lhs.accept( this );
		appendSql( " " );
		// This relies on the fact that Sybase usually is configured with ANSINULLS OFF
		switch ( operator ) {
			case DISTINCT_FROM:
				appendSql( "<>" );
				break;
			case NOT_DISTINCT_FROM:
				appendSql( '=' );
				break;
			default:
				appendSql( operator.sqlText() );
				break;
		}
		appendSql( " " );
		rhs.accept( this );
	}

	@Override
	protected boolean supportsIntersect() {
		// At least the version that
		return false;
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
			// Note that this depends on the SqmToSqlAstConverter to add a dummy table group
			appendSql( "dummy_.x" );
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
	public void visitColumnReference(ColumnReference columnReference) {
		if ( getDmlTargetTableAlias() != null && getDmlTargetTableAlias().equals( columnReference.getQualifier() ) ) {
			// Sybase needs a table name prefix
			// but not if this is a restricted union table reference subquery
			final QuerySpec currentQuerySpec = (QuerySpec) getQueryPartStack().getCurrent();
			final List<TableGroup> roots;
			if ( currentQuerySpec != null && !currentQuerySpec.isRoot()
					&& (roots = currentQuerySpec.getFromClause().getRoots()).size() == 1
					&& roots.get( 0 ).getPrimaryTableReference() instanceof UnionTableReference ) {
				appendSql( columnReference.getExpressionText() );
			}
			// for now, use the unqualified form
			else if ( columnReference.isColumnExpressionFormula() ) {
				// For formulas, we have to replace the qualifier as the alias was already rendered into the formula
				// This is fine for now as this is only temporary anyway until we render aliases for table references
				appendSql(
						columnReference.getColumnExpression()
								.replaceAll( "(\\b)(" + getDmlTargetTableAlias() + "\\.)(\\b)", "$1$3" )
				);
			}
			else {
				appendSql( ( (MutationStatement) getStatement() ).getTargetTable().getTableExpression() );
				appendSql( '.' );
				appendSql( columnReference.getColumnExpression() );
			}
		}
		else {
			appendSql( columnReference.getExpressionText() );
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

	@Override
	protected String getFromDual() {
		return " from (select 1) as dual(c1)";
	}

	private boolean supportsTopClause() {
		return getDialect().getVersion() >= 1250;
	}

	private boolean supportsParameterOffsetFetchExpression() {
		return false;
	}
}
