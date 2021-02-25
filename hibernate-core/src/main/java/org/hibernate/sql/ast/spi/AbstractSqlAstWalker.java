/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.spi;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.hibernate.CteSearchClauseKind;
import org.hibernate.FetchClauseType;
import org.hibernate.NotYetImplementedFor6Exception;
import org.hibernate.NullPrecedence;
import org.hibernate.SortOrder;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.AbstractDelegatingWrapperOptions;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.FilterJdbcParameter;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.collections.Stack;
import org.hibernate.internal.util.collections.StandardStack;
import org.hibernate.metamodel.mapping.BasicValuedMapping;
import org.hibernate.metamodel.mapping.CollectionPart;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.ModelPartContainer;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.mapping.internal.BasicValuedCollectionPart;
import org.hibernate.persister.entity.Loadable;
import org.hibernate.query.ComparisonOperator;
import org.hibernate.query.UnaryArithmeticOperator;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor;
import org.hibernate.query.sqm.sql.internal.EmbeddableValuedPathInterpretation;
import org.hibernate.query.sqm.sql.internal.NonAggregatedCompositeValuedPathInterpretation;
import org.hibernate.query.sqm.sql.internal.SqmParameterInterpretation;
import org.hibernate.query.sqm.tree.expression.Conversion;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlAstJoinType;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.tree.MutationStatement;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.cte.CteColumn;
import org.hibernate.sql.ast.tree.cte.CteContainer;
import org.hibernate.sql.ast.tree.cte.CteStatement;
import org.hibernate.sql.ast.tree.cte.SearchClauseSpecification;
import org.hibernate.sql.ast.tree.delete.DeleteStatement;
import org.hibernate.sql.ast.tree.expression.Any;
import org.hibernate.sql.ast.tree.expression.BinaryArithmeticExpression;
import org.hibernate.sql.ast.tree.expression.CaseSearchedExpression;
import org.hibernate.sql.ast.tree.expression.CaseSimpleExpression;
import org.hibernate.sql.ast.tree.expression.CastTarget;
import org.hibernate.sql.ast.tree.expression.Collate;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Distinct;
import org.hibernate.sql.ast.tree.expression.Duration;
import org.hibernate.sql.ast.tree.expression.DurationUnit;
import org.hibernate.sql.ast.tree.expression.EntityTypeLiteral;
import org.hibernate.sql.ast.tree.expression.Every;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.ExtractUnit;
import org.hibernate.sql.ast.tree.expression.Format;
import org.hibernate.sql.ast.tree.expression.JdbcLiteral;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.expression.Literal;
import org.hibernate.sql.ast.tree.expression.LiteralAsParameter;
import org.hibernate.sql.ast.tree.expression.NullnessLiteral;
import org.hibernate.sql.ast.tree.expression.QueryLiteral;
import org.hibernate.sql.ast.tree.expression.SelfRenderingExpression;
import org.hibernate.sql.ast.tree.expression.SqlSelectionExpression;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.Star;
import org.hibernate.sql.ast.tree.expression.Summarization;
import org.hibernate.sql.ast.tree.expression.TrimSpecification;
import org.hibernate.sql.ast.tree.expression.UnaryOperation;
import org.hibernate.sql.ast.tree.from.FromClause;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.from.TableReferenceJoin;
import org.hibernate.sql.ast.tree.from.VirtualTableGroup;
import org.hibernate.sql.ast.tree.insert.InsertStatement;
import org.hibernate.sql.ast.tree.insert.Values;
import org.hibernate.sql.ast.tree.predicate.BetweenPredicate;
import org.hibernate.sql.ast.tree.predicate.ComparisonPredicate;
import org.hibernate.sql.ast.tree.predicate.ExistsPredicate;
import org.hibernate.sql.ast.tree.predicate.FilterPredicate;
import org.hibernate.sql.ast.tree.predicate.GroupedPredicate;
import org.hibernate.sql.ast.tree.predicate.InListPredicate;
import org.hibernate.sql.ast.tree.predicate.InSubQueryPredicate;
import org.hibernate.sql.ast.tree.predicate.Junction;
import org.hibernate.sql.ast.tree.predicate.LikePredicate;
import org.hibernate.sql.ast.tree.predicate.NegatedPredicate;
import org.hibernate.sql.ast.tree.predicate.NullnessPredicate;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.ast.tree.predicate.SelfRenderingPredicate;
import org.hibernate.sql.ast.tree.select.QueryGroup;
import org.hibernate.sql.ast.tree.select.QueryPart;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.ast.tree.select.SortSpecification;
import org.hibernate.sql.ast.tree.update.Assignment;
import org.hibernate.sql.ast.tree.update.UpdateStatement;
import org.hibernate.sql.exec.ExecutionException;
import org.hibernate.sql.exec.internal.JdbcParametersImpl;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBinding;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.type.IntegerType;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.sql.JdbcLiteralFormatter;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

import static org.hibernate.query.TemporalUnit.NANOSECOND;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractSqlAstWalker implements SqlAstWalker, SqlAppender {

	private static final QueryLiteral<Integer> ONE_LITERAL = new QueryLiteral<>( 1, IntegerType.INSTANCE );

	// pre-req state
	private final SessionFactoryImplementor sessionFactory;

	// In-flight state
	private final StringBuilder sqlBuffer = new StringBuilder();

	private final List<JdbcParameterBinder> parameterBinders = new ArrayList<>();
	private final JdbcParametersImpl jdbcParameters = new JdbcParametersImpl();

	private final Set<FilterJdbcParameter> filterJdbcParameters = new HashSet<>();

	private final Stack<Clause> clauseStack = new StandardStack<>();
	private final Stack<QueryPart> queryPartStack = new StandardStack<>();

	private final Dialect dialect;
	private String dmlTargetTableAlias;
	private boolean needsSelectAliases;
	private QueryPart queryPartForRowNumbering;
	private int queryPartForRowNumberingAliasCounter;
	private int queryGroupAliasCounter;
	private transient AbstractSqmSelfRenderingFunctionDescriptor castFunction;
	private transient LazySessionWrapperOptions lazySessionWrapperOptions;

	public Dialect getDialect() {
		return dialect;
	}

	@SuppressWarnings("WeakerAccess")
	protected AbstractSqlAstWalker(SessionFactoryImplementor sessionFactory) {
		this.sessionFactory = sessionFactory;
		this.dialect = sessionFactory.getJdbcServices().getDialect();
	}

	public SessionFactoryImplementor getSessionFactory() {
		return sessionFactory;
	}

	protected AbstractSqmSelfRenderingFunctionDescriptor castFunction() {
		if ( castFunction == null ) {
			castFunction = (AbstractSqmSelfRenderingFunctionDescriptor) sessionFactory
					.getQueryEngine()
					.getSqmFunctionRegistry()
					.findFunctionDescriptor( "cast" );
		}
		return castFunction;
	}

	protected WrapperOptions getWrapperOptions() {
		if ( lazySessionWrapperOptions == null ) {
			lazySessionWrapperOptions = new LazySessionWrapperOptions( sessionFactory );
		}
		return lazySessionWrapperOptions;
	}

	/**
	 * A lazy session implementation that is needed for rendering literals.
	 * Usually, only the {@link org.hibernate.type.descriptor.WrapperOptions} interface is needed,
	 * but for creating LOBs, it might be to have a full blown session.
	 */
	private static class LazySessionWrapperOptions extends AbstractDelegatingWrapperOptions {

		private final SessionFactoryImplementor sessionFactory;
		private SessionImplementor session;

		public LazySessionWrapperOptions(SessionFactoryImplementor sessionFactory) {
			this.sessionFactory = sessionFactory;
		}

		public void cleanup() {
			if ( session != null ) {
				session.close();
				session = null;
			}
		}

		@Override
		protected SessionImplementor delegate() {
			if ( session == null ) {
				session = (SessionImplementor) sessionFactory.openTemporarySession();
			}
			return session;
		}

		@Override
		public SharedSessionContractImplementor getSession() {
			return delegate();
		}

		@Override
		public boolean useStreamForLobBinding() {
			return sessionFactory.getFastSessionServices().useStreamForLobBinding();
		}

		@Override
		public SqlTypeDescriptor remapSqlTypeDescriptor(SqlTypeDescriptor sqlTypeDescriptor) {
			return sessionFactory.getFastSessionServices().remapSqlTypeDescriptor( sqlTypeDescriptor );
		}

		@Override
		public TimeZone getJdbcTimeZone() {
			return sessionFactory.getSessionFactoryOptions().getJdbcTimeZone();
		}
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// for tests, for now
	public String getSql() {
		return sqlBuffer.toString();
	}

	protected void cleanup() {
		if ( lazySessionWrapperOptions != null ) {
			lazySessionWrapperOptions.cleanup();
			lazySessionWrapperOptions = null;
		}
	}

	@SuppressWarnings("WeakerAccess")
	public List<JdbcParameterBinder> getParameterBinders() {
		return parameterBinders;
	}
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public Set<FilterJdbcParameter> getFilterJdbcParameters() {
		return filterJdbcParameters;
	}

	@SuppressWarnings("unused")
	protected SqlAppender getSqlAppender() {
		return this;
	}

	@Override
	public void appendSql(String fragment) {
		sqlBuffer.append( fragment );
	}

	@Override
	public void appendSql(char fragment) {
		sqlBuffer.append( fragment );
	}

	protected JdbcServices getJdbcServices() {
		return getSessionFactory().getJdbcServices();
	}

	protected boolean isCurrentlyInPredicate() {
		return clauseStack.getCurrent() == Clause.WHERE
				|| clauseStack.getCurrent() == Clause.HAVING;
	}

	protected boolean inOverClause() {
		return clauseStack.findCurrentFirst(
				clause -> {
					if ( clause == Clause.OVER ) {
						return true;
					}
					return null;
				}
		) != null;
	}

	protected Stack<Clause> getClauseStack() {
		return clauseStack;
	}

	protected Stack<QueryPart> getQueryPartStack() {
		return queryPartStack;
	}

	@Override
	public void visitSelectStatement(SelectStatement statement) {
		String oldDmlTargetTableAlias = dmlTargetTableAlias;
		dmlTargetTableAlias = null;
		try {
			visitCteContainer( statement );
			statement.getQueryPart().accept( this );
		}
		finally {
			dmlTargetTableAlias = oldDmlTargetTableAlias;
		}
	}

	@Override
	public void visitDeleteStatement(DeleteStatement statement) {
		String oldDmlTargetTableAlias = dmlTargetTableAlias;
		dmlTargetTableAlias = null;
		try {
			visitCteContainer( statement );
			dmlTargetTableAlias = statement.getTargetTable().getIdentificationVariable();
			visitDeleteStatementOnly( statement );
		}
		finally {
			dmlTargetTableAlias = oldDmlTargetTableAlias;
		}
	}

	@Override
	public void visitUpdateStatement(UpdateStatement statement) {
		String oldDmlTargetTableAlias = dmlTargetTableAlias;
		dmlTargetTableAlias = null;
		try {
			visitCteContainer( statement );
			dmlTargetTableAlias = statement.getTargetTable().getIdentificationVariable();
			visitUpdateStatementOnly( statement );
		}
		finally {
			dmlTargetTableAlias = oldDmlTargetTableAlias;
		}
	}

	@Override
	public void visitInsertStatement(InsertStatement statement) {
		String oldDmlTargetTableAlias = dmlTargetTableAlias;
		dmlTargetTableAlias = null;
		try {
			visitCteContainer( statement );
			visitInsertStatementOnly( statement );
		}
		finally {
			dmlTargetTableAlias = oldDmlTargetTableAlias;
		}
	}

	protected void visitDeleteStatementOnly(DeleteStatement statement) {
		// todo (6.0) : to support joins we need dialect support
		appendSql( "delete from " );
		final Stack<Clause> clauseStack = getClauseStack();
		try {
			clauseStack.push( Clause.DELETE );
			renderTableReference( statement.getTargetTable() );
		}
		finally {
			clauseStack.pop();
		}

		if ( statement.getRestriction() != null ) {
			try {
				clauseStack.push( Clause.WHERE );
				appendSql( " where " );
				statement.getRestriction().accept( this );
			}
			finally {
				clauseStack.pop();
			}
		}
		visitReturningColumns( statement );
	}

	protected void visitUpdateStatementOnly(UpdateStatement statement) {
		// todo (6.0) : to support joins we need dialect support
		appendSql( "update " );
		final Stack<Clause> clauseStack = getClauseStack();
		try {
			clauseStack.push( Clause.UPDATE );
			renderTableReference( statement.getTargetTable() );
		}
		finally {
			clauseStack.pop();
		}

		appendSql( " set " );
		boolean firstPass = true;
		try {
			clauseStack.push( Clause.SET );
			for ( Assignment assignment : statement.getAssignments() ) {
				if ( firstPass ) {
					firstPass = false;
				}
				else {
					appendSql( ", " );
				}

				final List<ColumnReference> columnReferences = assignment.getAssignable().getColumnReferences();
				if ( columnReferences.size() == 1 ) {
					columnReferences.get( 0 ).accept( this );
				}
				else {
					appendSql( " (" );
					for ( ColumnReference columnReference : columnReferences ) {
						columnReference.accept( this );
					}
					appendSql( ") " );
				}
				appendSql( " = " );
				assignment.getAssignedValue().accept( this );
			}
		}
		finally {
			clauseStack.pop();
		}

		if ( statement.getRestriction() != null ) {
			appendSql( " where " );
			try {
				clauseStack.push( Clause.WHERE );
				statement.getRestriction().accept( this );
			}
			finally {
				clauseStack.pop();
			}
		}
		visitReturningColumns( statement );
	}

	protected void visitInsertStatementOnly(InsertStatement statement) {
		appendSql( "insert into " );
		appendSql( statement.getTargetTable().getTableExpression() );

		appendSql( " (" );
		boolean firstPass = true;

		final List<ColumnReference> targetColumnReferences = statement.getTargetColumnReferences();
		if ( targetColumnReferences == null ) {
			renderImplicitTargetColumnSpec();
		}
		else {
			for (ColumnReference targetColumnReference : targetColumnReferences) {
				if (firstPass) {
					firstPass = false;
				}
				else {
					appendSql( ", " );
				}

				appendSql( targetColumnReference.getColumnExpression() );
			}
		}

		appendSql( ") " );

		if ( statement.getSourceSelectStatement() != null ) {
			statement.getSourceSelectStatement().accept( this );
		}
		else {
			visitValuesList( statement.getValuesList() );
		}
		visitReturningColumns( statement );
	}

	private void renderImplicitTargetColumnSpec() {
	}

	protected void visitValuesList(List<Values> valuesList) {
		appendSql("values");
		boolean firstTuple = true;
		final Stack<Clause> clauseStack = getClauseStack();
		try {
			clauseStack.push( Clause.VALUES );
			for ( Values values : valuesList ) {
				if ( firstTuple ) {
					firstTuple = false;
				}
				else {
					appendSql( ", " );
				}
				appendSql( " (" );
				boolean firstExpr = true;
				for ( Expression expression : values.getExpressions() ) {
					if ( firstExpr ) {
						firstExpr = false;
					}
					else {
						appendSql( ", " );
					}
					expression.accept( this );
				}
				appendSql( ")" );
			}
		}
		finally {
			clauseStack.pop();
		}
	}

	protected void visitReturningColumns(MutationStatement mutationStatement) {
		final List<ColumnReference> returningColumns = mutationStatement.getReturningColumns();
		final int size = returningColumns.size();
		if ( size == 0 ) {
			return;
		}

		appendSql( " returning " );
		String separator = "";
		for ( int i = 0; i < size; i++ ) {
			appendSql( separator );
			appendSql( returningColumns.get( i ).getColumnExpression() );
			separator = ", ";
		}
	}

	public void visitCteContainer(CteContainer cteContainer) {
		final Collection<CteStatement> cteStatements = cteContainer.getCteStatements();
		if ( cteStatements.isEmpty() ) {
			return;
		}
		appendSql( "with " );

		if ( cteContainer.isWithRecursive() ) {
			appendSql( "recursive " );
		}

		String mainSeparator = "";
		for ( CteStatement cte : cteStatements ) {
			appendSql( mainSeparator );
			appendSql( cte.getCteTable().getTableExpression() );

			appendSql( " (" );

			String separator = "";

			for ( CteColumn cteColumn : cte.getCteTable().getCteColumns() ) {
				appendSql( separator );
				appendSql( cteColumn.getColumnExpression() );
				separator = ", ";
			}

			appendSql( ") as (" );

			cte.getCteDefinition().accept( this );

			appendSql( ')' );

			renderSearchClause( cte );
			renderCycleClause( cte );

			mainSeparator = ", ";
		}
		appendSql( ' ' );
	}

	protected void renderSearchClause(CteStatement cte) {
		String separator;
		if ( cte.getSearchClauseKind() != null ) {
			appendSql( " search " );
			if ( cte.getSearchClauseKind() == CteSearchClauseKind.DEPTH_FIRST ) {
				appendSql( " depth " );
			}
			else {
				appendSql( " breadth " );
			}
			appendSql( " first by " );
			separator = "";
			for ( SearchClauseSpecification searchBySpecification : cte.getSearchBySpecifications() ) {
				appendSql( separator );
				appendSql( searchBySpecification.getCteColumn().getColumnExpression() );
				if ( searchBySpecification.getSortOrder() != null ) {
					if ( searchBySpecification.getSortOrder() == SortOrder.ASCENDING ) {
						appendSql( " asc" );
					}
					else {
						appendSql( " desc" );
					}
					if ( searchBySpecification.getNullPrecedence() != null ) {
						if ( searchBySpecification.getNullPrecedence() == NullPrecedence.FIRST ) {
							appendSql( " nulls first" );
						}
						else {
							appendSql( " nulls last" );
						}
					}
				}
				separator = ", ";
			}
		}
	}

	protected void renderCycleClause(CteStatement cte) {
		String separator;
		if ( cte.getCycleMarkColumn() != null ) {
			appendSql( " cycle " );
			separator = "";
			for ( CteColumn cycleColumn : cte.getCycleColumns() ) {
				appendSql( separator );
				appendSql( cycleColumn.getColumnExpression() );
				separator = ", ";
			}
			appendSql( " set " );
			appendSql( cte.getCycleMarkColumn().getColumnExpression() );
			appendSql( " to '" );
			appendSql( cte.getCycleValue() );
			appendSql( "' default '" );
			appendSql( cte.getNoCycleValue() );
			appendSql( "'" );
		}
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// QuerySpec

	@Override
	public void visitQueryGroup(QueryGroup queryGroup) {
		final QueryPart queryPartForRowNumbering = this.queryPartForRowNumbering;
		final boolean needsSelectAliases = this.needsSelectAliases;
		try {
			String queryGroupAlias = null;
			final QueryPart currentQueryPart = queryPartStack.getCurrent();
			if ( currentQueryPart != null && queryPartForRowNumbering != currentQueryPart ) {
				this.queryPartForRowNumbering = null;
				this.needsSelectAliases = false;
			}
			// If we do row counting for this query group, the wrapper select is added by the caller
			if ( queryPartForRowNumbering != queryGroup && !queryGroup.isRoot() ) {
				this.needsSelectAliases = true;
				queryGroupAlias = "grp_" + queryGroupAliasCounter + "_";
				queryGroupAliasCounter++;
				appendSql( "select " );
				appendSql( queryGroupAlias );
				appendSql( ".* from (" );
			}
			queryPartStack.push( queryGroup );
			final List<QueryPart> queryParts = queryGroup.getQueryParts();
			final String setOperatorString = " " + queryGroup.getSetOperator().sqlString() + " ";
			String separator = "";
			for ( int i = 0; i < queryParts.size(); i++ ) {
				appendSql( separator );
				queryParts.get( i ).accept( this );
				separator = setOperatorString;
			}

			visitOrderBy( queryGroup.getSortSpecifications() );
			visitOffsetFetchClause( queryGroup );
			if ( queryGroupAlias != null ) {
				appendSql( ") " );
				appendSql( queryGroupAlias );
			}
		}
		finally {
			queryPartStack.pop();
			this.queryPartForRowNumbering = queryPartForRowNumbering;
			this.needsSelectAliases = needsSelectAliases;
		}
	}

	@Override
	public void visitQuerySpec(QuerySpec querySpec) {
		final QueryPart queryPartForRowNumbering = this.queryPartForRowNumbering;
		final boolean needsSelectAliases = this.needsSelectAliases;
		try {
			final QueryPart currentQueryPart = queryPartStack.getCurrent();
			if ( currentQueryPart != null && queryPartForRowNumbering != currentQueryPart ) {
				this.queryPartForRowNumbering = null;
			}
			String queryGroupAlias = "";
			final boolean needsParenthesis;
			if ( currentQueryPart instanceof QueryGroup ) {
				// We always need query wrapping if we are in a query group and the query part has a fetch clause
				if ( needsParenthesis = querySpec.hasOffsetOrFetchClause() ) {
					// If the parent is a query group with a fetch clause, we must use an alias
					// Some DBMS don't support grouping query expressions and need a select wrapper
					if ( !supportsSimpleQueryGrouping() || currentQueryPart.hasOffsetOrFetchClause() ) {
						this.needsSelectAliases = true;
						queryGroupAlias = " grp_" + queryGroupAliasCounter + "_";
						queryGroupAliasCounter++;
						appendSql( "select" );
						appendSql( queryGroupAlias );
						appendSql( ".* from " );
					}
				}
			}
			else {
				needsParenthesis = !querySpec.isRoot();
			}
			queryPartStack.push( querySpec );
			if ( needsParenthesis ) {
				appendSql( "(" );
			}
			visitSelectClause( querySpec.getSelectClause() );
			visitFromClause( querySpec.getFromClause() );
			visitWhereClause( querySpec );
			visitGroupByClause( querySpec, dialect.supportsSelectAliasInGroupByClause() );
			visitHavingClause( querySpec );
			visitOrderBy( querySpec.getSortSpecifications() );
			visitOffsetFetchClause( querySpec );

			if ( needsParenthesis ) {
				appendSql( ")" );
				appendSql( queryGroupAlias );
			}
		}
		finally {
			queryPartStack.pop();
			this.queryPartForRowNumbering = queryPartForRowNumbering;
			this.needsSelectAliases = needsSelectAliases;
		}
	}

	protected boolean supportsSimpleQueryGrouping() {
		return true;
	}

	protected final void visitWhereClause(QuerySpec querySpec) {
		final Predicate whereClauseRestrictions = querySpec.getWhereClauseRestrictions();
		if ( whereClauseRestrictions != null && !whereClauseRestrictions.isEmpty() ) {
			appendSql( " where " );

			clauseStack.push( Clause.WHERE );
			try {
				whereClauseRestrictions.accept( this );
			}
			finally {
				clauseStack.pop();
			}
		}
	}

	protected Expression resolveAliasedExpression(Expression expression) {
		return resolveAliasedExpression(
				queryPartStack.getCurrent().getFirstQuerySpec().getSelectClause().getSqlSelections(),
				expression
		);
	}

	protected Expression resolveAliasedExpression(List<SqlSelection> sqlSelections, Expression expression) {
		if ( expression instanceof Literal ) {
			Object literalValue = ( (Literal) expression ).getLiteralValue();
			if ( literalValue instanceof Integer ) {
				return sqlSelections.get( (Integer) literalValue ).getExpression();
			}
		}
		else if ( expression instanceof SqlSelectionExpression ) {
			return ( (SqlSelectionExpression) expression ).getSelection().getExpression();
		}
		return expression;
	}

	protected final void visitGroupByClause(QuerySpec querySpec, boolean supportsSelectAliases) {
		final List<Expression> partitionExpressions = querySpec.getGroupByClauseExpressions();
		if ( !partitionExpressions.isEmpty() ) {
			try {
				clauseStack.push( Clause.GROUP );
				appendSql( " group by " );
				visitPartitionExpressions( partitionExpressions, supportsSelectAliases );
			}
			finally {
				clauseStack.pop();
			}
		}
	}

	protected final void visitPartitionByClause(List<Expression> partitionExpressions) {
		if ( !partitionExpressions.isEmpty() ) {
			try {
				clauseStack.push( Clause.PARTITION );
				appendSql( "partition by " );
				visitPartitionExpressions( partitionExpressions, false );
			}
			finally {
				clauseStack.pop();
			}
		}
	}

	protected final void visitPartitionExpressions(List<Expression> partitionExpressions, boolean supportsSelectAliases) {
		String separator = "";
		if ( supportsSelectAliases ) {
			for ( Expression partitionExpression : partitionExpressions ) {
				if ( partitionExpression instanceof SqlTuple ) {
					for ( Expression expression : ( (SqlTuple) partitionExpression ).getExpressions() ) {
						appendSql( separator );
						renderPartitionItem( expression );
						separator = COMA_SEPARATOR;
					}
				}
				else {
					appendSql( separator );
					renderPartitionItem( partitionExpression );
				}
				separator = COMA_SEPARATOR;
			}
		}
		else {
			for ( Expression partitionExpression : partitionExpressions ) {
				if ( partitionExpression instanceof SqlTuple ) {
					for ( Expression expression : ( (SqlTuple) partitionExpression ).getExpressions() ) {
						appendSql( separator );
						renderPartitionItem( resolveAliasedExpression( expression ) );
						separator = COMA_SEPARATOR;
					}
				}
				else {
					appendSql( separator );
					renderPartitionItem( resolveAliasedExpression( partitionExpression ) );
				}
				separator = COMA_SEPARATOR;
			}
		}
	}

	protected void renderPartitionItem(Expression expression) {
		// We render an empty group instead of literals as some DBs don't support grouping by literals
		// Note that integer literals, which refer to select item positions, are handled in #visitGroupByClause
		if ( expression instanceof Literal ) {
			switch ( dialect.getGroupByConstantRenderingStrategy() ) {
				case CONSTANT:
					appendSql( "'0'" );
					break;
				case CONSTANT_EXPRESSION:
					appendSql( "'0' || '0'" );
					break;
				case EMPTY_GROUPING:
					appendSql( "()" );
					break;
				case SUBQUERY:
					appendSql( "(select 1" );
					final String fromDual = dialect.getFromDual();
					if ( !fromDual.isEmpty() ) {
						appendSql( " " );
						appendSql( fromDual );
					}
					appendSql( ')' );
					break;
				case COLUMN_REFERENCE:
					// todo (6.0): We need to introduce a dummy from clause item
//					String fromItem = ", (select 1 x " + dialect.getFromDual() + ") dummy";
//					sqlBuffer.insert( fromEndIndex, fromItem );
//					appendSql( "dummy.x" );
					throw new UnsupportedOperationException( "Column reference strategy is not yet implemented!" );
			}
		}
		else if ( expression instanceof Summarization ) {
			Summarization summarization = (Summarization) expression;
			switch ( dialect.getGroupBySummarizationRenderingStrategy() ) {
				case FUNCTION:
					appendSql( summarization.getKind().name().toLowerCase() );
					appendSql( OPEN_PARENTHESIS );
					renderCommaSeparated( summarization.getGroupings() );
					appendSql( CLOSE_PARENTHESIS );
					break;
				case CLAUSE:
					renderCommaSeparated( summarization.getGroupings() );
					appendSql( " with " );
					appendSql( summarization.getKind().name().toLowerCase() );
					break;
				default:
					// This could theoretically be emulated by rendering all grouping variations of the query and
					// connect them via union all but that's probably pretty inefficient and would have to happen
					// on the query spec level
					throw new UnsupportedOperationException( "Summarization is not supported by DBMS!" );
			}
		}
		else {
			expression.accept( this );
		}
	}

	protected final void visitHavingClause(QuerySpec querySpec) {
		final Predicate havingClauseRestrictions = querySpec.getHavingClauseRestrictions();
		if ( havingClauseRestrictions != null && !havingClauseRestrictions.isEmpty() ) {
			appendSql( " having " );

			clauseStack.push( Clause.HAVING );
			try {
				havingClauseRestrictions.accept( this );
			}
			finally {
				clauseStack.pop();
			}
		}
	}

	protected void visitOrderBy(List<SortSpecification> sortSpecifications) {
		// If we have a query part for row numbering, there is no need to render the order by clause
		// as that is part of the row numbering window function already, by which we then order by in the outer query
		if ( queryPartForRowNumbering == null ) {
			renderOrderBy( true, sortSpecifications );
		}
	}

	protected void renderOrderBy(boolean addWhitespace, List<SortSpecification> sortSpecifications) {
		if ( sortSpecifications != null && !sortSpecifications.isEmpty() ) {
			if ( addWhitespace ) {
				appendSql( ' ' );
			}
			appendSql( "order by " );

			clauseStack.push( Clause.ORDER );
			try {
				String separator = NO_SEPARATOR;
				for ( SortSpecification sortSpecification : sortSpecifications ) {
					appendSql( separator );
					visitSortSpecification( sortSpecification );
					separator = COMA_SEPARATOR;
				}
			}
			finally {
				clauseStack.pop();
			}
		}
	}


	/**
	 * A tuple comparison like <code>(a, b) &gt; (1, 2)</code> can be emulated through it logical definition: <code>a &gt; 1 or a = 1 and b &gt; 2</code>.
	 * The normal tuple comparison emulation is not very index friendly though because of the top level OR predicate.
	 * Index optimized emulation of tuple comparisons puts an AND predicate on the top level.
	 * The effect of that is, that the database can do an index seek to efficiently find a superset of matching rows.
	 * Generally, it is sufficient to just add a broader predicate like for <code>(a, b) &gt; (1, 2)</code> we add <code>a &gt;= 1 and (..)</code>.
	 * But we can further optimize this if we just remove the non-matching parts from this too broad predicate.
	 * For <code>(a, b, c) &gt; (1, 2, 3)</code> we use the broad predicate <code>a &gt;= 1</code> and then want to remove rows where <code>a = 1 and (b, c) &lt;= (2, 3)</code>
	 */
	protected void emulateTupleComparison(
			final List<? extends Expression> lhsExpressions,
			final List<? extends Expression> rhsExpressions,
			ComparisonOperator operator,
			boolean indexOptimized) {
		final boolean isCurrentWhereClause = clauseStack.getCurrent() == Clause.WHERE;
		if ( isCurrentWhereClause ) {
			appendSql( OPEN_PARENTHESIS );
		}

		final int size = lhsExpressions.size();
		assert size == rhsExpressions.size();
		switch ( operator ) {
			case EQUAL:
			case NOT_EQUAL: {
				final String operatorText = operator.sqlText();
				String separator = NO_SEPARATOR;
				for ( int i = 0; i < size; i++ ) {
					appendSql( separator );
					lhsExpressions.get( i ).accept( this );
					appendSql( operatorText );
					rhsExpressions.get( i ).accept( this );
					separator = " and ";
				}
				break;
			}
			case LESS_THAN_OR_EQUAL:
				// Optimized (a, b) <= (1, 2) as: a <= 1 and not (a = 1 and b > 2)
				// Normal    (a, b) <= (1, 2) as: a <  1 or a = 1 and (b <= 2)
			case GREATER_THAN_OR_EQUAL:
				// Optimized (a, b) >= (1, 2) as: a >= 1 and not (a = 1 and b < 2)
				// Normal    (a, b) >= (1, 2) as: a >  1 or a = 1 and (b >= 2)
			case LESS_THAN:
				// Optimized (a, b) <  (1, 2) as: a <= 1 and not (a = 1 and b >= 2)
				// Normal    (a, b) <  (1, 2) as: a <  1 or a = 1 and (b < 2)
			case GREATER_THAN: {
				// Optimized (a, b) >  (1, 2) as: a >= 1 and not (a = 1 and b <= 2)
				// Normal    (a, b) >  (1, 2) as: a >  1 or a = 1 and (b > 2)
				if ( indexOptimized ) {
					lhsExpressions.get( 0 ).accept( this );
					appendSql( operator.broader().sqlText() );
					rhsExpressions.get( 0 ).accept( this );
					appendSql( " and not " );
					final String negatedOperatorText = operator.negated().sqlText();
					emulateTupleComparisonSimple(
							lhsExpressions,
							rhsExpressions,
							negatedOperatorText,
							negatedOperatorText,
							true
					);
				}
				else {
					emulateTupleComparisonSimple(
							lhsExpressions,
							rhsExpressions,
							operator.sharper().sqlText(),
							operator.sqlText(),
							false
					);
				}
				break;
			}
		}

		if ( isCurrentWhereClause ) {
			appendSql( CLOSE_PARENTHESIS );
		}
	}

	protected void renderExpressionsAsSubquery(final List<? extends Expression> expressions) {
		clauseStack.push( Clause.SELECT );

		try {
			appendSql( "select " );

			renderCommaSeparated( expressions );
			final String fromDual = dialect.getFromDual();
			if ( !fromDual.isEmpty() ) {
				appendSql( " " );
				appendSql( fromDual );
			}
		}
		finally {
			clauseStack.pop();
		}
	}

	private void emulateTupleComparisonSimple(
			final List<? extends Expression> lhsExpressions,
			final List<? extends Expression> rhsExpressions,
			final String operatorText,
			final String finalOperatorText,
			final boolean optimized) {
		// Render (a, b) OP (1, 2) as: (a OP 1 or a = 1 and b FINAL_OP 2)

		final int size = lhsExpressions.size();
		final int lastIndex = size - 1;

		appendSql( OPEN_PARENTHESIS );
		String separator = NO_SEPARATOR;

		int i;
		if ( optimized ) {
			i = 1;
		}
		else {
			lhsExpressions.get( 0 ).accept( this );
			appendSql( operatorText );
			rhsExpressions.get( 0 ).accept( this );
			separator = " or ";
			i = 1;
		}

		for ( ; i < lastIndex; i++ ) {
			// Render the equals parts
			appendSql( separator );
			lhsExpressions.get( i - 1 ).accept( this );
			appendSql( '=' );
			rhsExpressions.get( i - 1 ).accept( this );

			// Render the actual operator part for the current component
			appendSql( " and (" );
			lhsExpressions.get( i ).accept( this );
			appendSql( operatorText );
			rhsExpressions.get( i ).accept( this );
			separator = " or ";
		}

		// Render the equals parts
		appendSql( separator );
		lhsExpressions.get( lastIndex - 1 ).accept( this );
		appendSql( '=' );
		rhsExpressions.get( lastIndex - 1 ).accept( this );

		// Render the actual operator part for the current component
		appendSql( " and " );
		lhsExpressions.get( lastIndex ).accept( this );
		appendSql( finalOperatorText );
		rhsExpressions.get( lastIndex ).accept( this );

		// Close all opened parenthesis
		for ( i = optimized ? 1 : 0; i < lastIndex; i++ ) {
			appendSql( CLOSE_PARENTHESIS );
		}
	}

	protected void renderSelectTupleComparison(final List<SqlSelection> lhsExpressions, SqlTuple tuple, ComparisonOperator operator) {
		if ( dialect.supportsRowValueConstructorSyntax() ) {
			appendSql( OPEN_PARENTHESIS );
			String separator = NO_SEPARATOR;
			for ( SqlSelection lhsExpression : lhsExpressions ) {
				appendSql( separator );
				lhsExpression.getExpression().accept( this );
				separator = COMA_SEPARATOR;
			}
			appendSql( CLOSE_PARENTHESIS );
			appendSql( " " );
			appendSql( operator.sqlText() );
			appendSql( " " );
			tuple.accept( this );
		}
		else {
			final List<Expression> lhs = new ArrayList<>( lhsExpressions.size() );
			for ( SqlSelection lhsExpression : lhsExpressions ) {
				lhs.add( lhsExpression.getExpression() );
			}

			emulateTupleComparison( lhs, tuple.getExpressions(), operator, true );
		}
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// ORDER BY clause

	@Override
	public void visitSortSpecification(SortSpecification sortSpecification) {
		final Expression sortExpression = sortSpecification.getSortExpression();
		final NullPrecedence nullPrecedence = sortSpecification.getNullPrecedence();
		final SortOrder sortOrder = sortSpecification.getSortOrder();
		if ( sortExpression instanceof SqlTuple ) {
			String separator = NO_SEPARATOR;
			for ( Expression expression : ( (SqlTuple) sortExpression ).getExpressions() ) {
				appendSql( separator );
				visitSortSpecification( expression, sortOrder, nullPrecedence );
				separator = COMA_SEPARATOR;
			}
		}
		else {
			visitSortSpecification( sortExpression, sortOrder, nullPrecedence );
		}
	}

	public void visitSortSpecification(Expression sortExpression, SortOrder sortOrder, NullPrecedence nullPrecedence) {
		final boolean renderNullPrecedence = nullPrecedence != null &&
				!nullPrecedence.isDefaultOrdering( sortOrder, dialect.getNullOrdering() );
		if ( renderNullPrecedence && !dialect.supportsNullPrecedence() ) {
			emulateSortSpecificationNullPrecedence( sortExpression, nullPrecedence );
		}

		if ( inOverClause() ) {
			resolveAliasedExpression( sortExpression ).accept( this );
		}
		else {
			sortExpression.accept( this );
		}

		if ( sortOrder == SortOrder.ASCENDING ) {
			appendSql( " asc" );
		}
		else if ( sortOrder == SortOrder.DESCENDING ) {
			appendSql( " desc" );
		}

		if ( renderNullPrecedence && dialect.supportsNullPrecedence() ) {
			appendSql( " nulls " );
			appendSql( nullPrecedence.name().toLowerCase( Locale.ROOT ) );
		}
	}

	protected void emulateSortSpecificationNullPrecedence(Expression sortExpression, NullPrecedence nullPrecedence) {
		// TODO: generate "virtual" select items and use them here positionally
		appendSql( "case when (" );
		resolveAliasedExpression( sortExpression ).accept( this );
		appendSql( ") is null then " );
		if ( nullPrecedence == NullPrecedence.FIRST ) {
			appendSql( "0 else 1" );
		}
		else {
			appendSql( "1 else 0" );
		}
		appendSql( " end" );
		appendSql( COMA_SEPARATOR );
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// LIMIT/OFFSET/FETCH clause

	@Override
	public void visitOffsetFetchClause(QueryPart queryPart) {
		if ( !isRowNumberingCurrentQueryPart() ) {
			renderOffsetFetchClause( queryPart, true );
		}
	}

	protected void renderOffsetFetchClause(QueryPart queryPart, boolean renderOffsetRowsKeyword) {
		renderOffsetFetchClause(
				queryPart.getOffsetClauseExpression(),
				queryPart.getFetchClauseExpression(),
				queryPart.getFetchClauseType(),
				renderOffsetRowsKeyword
		);
	}

	protected void renderOffsetFetchClause(
			Expression offsetExpression,
			Expression fetchExpression,
			FetchClauseType fetchClauseType,
			boolean renderOffsetRowsKeyword) {
		if ( offsetExpression != null ) {
			renderOffset( offsetExpression, renderOffsetRowsKeyword );
		}

		if ( fetchExpression != null ) {
			renderFetch( fetchExpression, null, fetchClauseType );
		}
	}

	@SuppressWarnings("WeakerAccess")
	protected void renderOffset(Expression offsetExpression, boolean renderOffsetRowsKeyword) {
		appendSql( " offset " );
		clauseStack.push( Clause.OFFSET );
		try {
			renderOffsetExpression( offsetExpression );
		}
		finally {
			clauseStack.pop();
		}
		if ( renderOffsetRowsKeyword ) {
			appendSql( " rows" );
		}
	}

	@SuppressWarnings("WeakerAccess")
	protected void renderFetch(
			Expression fetchExpression,
			Expression offsetExpressionToAdd,
			FetchClauseType fetchClauseType) {
		appendSql( " fetch first " );
		clauseStack.push( Clause.FETCH );
		try {
			if ( offsetExpressionToAdd == null ) {
				renderFetchExpression( fetchExpression );
			}
			else {
				renderFetchPlusOffsetExpression( fetchExpression, offsetExpressionToAdd, 0 );
			}
		}
		finally {
			clauseStack.pop();
		}
		switch ( fetchClauseType ) {
			case ROWS_ONLY:
				appendSql( " rows only" );
				break;
			case ROWS_WITH_TIES:
				appendSql( " rows with ties" );
				break;
			case PERCENT_ONLY:
				appendSql( " percent rows only" );
				break;
			case PERCENT_WITH_TIES:
				appendSql( " percent rows with ties" );
				break;
		}
	}

	protected void renderOffsetExpression(Expression offsetExpression) {
		offsetExpression.accept( this );
	}

	protected void renderFetchExpression(Expression fetchExpression) {
		fetchExpression.accept( this );
	}

	protected void renderTopClause(QuerySpec querySpec, boolean addOffset) {
		renderTopClause(
				querySpec.getOffsetClauseExpression(),
				querySpec.getFetchClauseExpression(),
				querySpec.getFetchClauseType(),
				addOffset
		);
	}

	protected void renderTopClause(
			Expression offsetExpression,
			Expression fetchExpression,
			FetchClauseType fetchClauseType,
			boolean addOffset) {
		if ( fetchExpression != null ) {
			appendSql( "top (" );
			final Stack<Clause> clauseStack = getClauseStack();
			clauseStack.push( Clause.FETCH );
			try {
				if ( addOffset && offsetExpression != null ) {
					renderFetchPlusOffsetExpression( fetchExpression, offsetExpression, 0 );
				}
				else {
					renderFetchExpression( fetchExpression );
				}
			}
			finally {
				clauseStack.pop();
			}
			appendSql( ") " );
			switch ( fetchClauseType ) {
				case ROWS_WITH_TIES:
					appendSql( "with ties " );
					break;
				case PERCENT_ONLY:
					appendSql( "percent " );
					break;
				case PERCENT_WITH_TIES:
					appendSql( "percent with ties " );
					break;
			}
		}
	}

	protected void renderTopStartAtClause(QuerySpec querySpec) {
		renderTopStartAtClause(
				querySpec.getOffsetClauseExpression(),
				querySpec.getFetchClauseExpression(),
				querySpec.getFetchClauseType()
		);
	}

	protected void renderTopStartAtClause(
			Expression offsetExpression,
			Expression fetchExpression,
			FetchClauseType fetchClauseType) {
		if ( fetchExpression != null ) {
			appendSql( "top " );
			final Stack<Clause> clauseStack = getClauseStack();
			clauseStack.push( Clause.FETCH );
			try {
				renderFetchExpression( fetchExpression );
			}
			finally {
				clauseStack.pop();
			}
			if ( offsetExpression != null ) {
				clauseStack.push( Clause.OFFSET );
				try {
					appendSql( " start at " );
					renderOffsetExpression( offsetExpression );
				}
				finally {
					clauseStack.pop();
				}
			}
			appendSql( ' ' );
			switch ( fetchClauseType ) {
				case ROWS_WITH_TIES:
					appendSql( "with ties " );
					break;
				case PERCENT_ONLY:
					appendSql( "percent " );
					break;
				case PERCENT_WITH_TIES:
					appendSql( "percent with ties " );
					break;
			}
		}
	}

	protected void renderRowsToClause(QuerySpec querySpec) {
		assertRowsOnlyFetchClauseType( querySpec );
		renderRowsToClause( querySpec.getOffsetClauseExpression(), querySpec.getFetchClauseExpression() );
	}

	protected void renderRowsToClause(Expression offsetClauseExpression, Expression fetchClauseExpression) {
		if ( fetchClauseExpression != null ) {
			appendSql( "rows " );
			final Stack<Clause> clauseStack = getClauseStack();
			clauseStack.push( Clause.FETCH );
			try {
				renderFetchExpression( fetchClauseExpression );
			}
			finally {
				clauseStack.pop();
			}
			if ( offsetClauseExpression != null ) {
				clauseStack.push( Clause.OFFSET );
				try {
					appendSql( " to " );
					// According to RowsLimitHandler this is 1 based so we need to add 1 to the offset
					renderFetchPlusOffsetExpression( fetchClauseExpression, offsetClauseExpression, 1 );
				}
				finally {
					clauseStack.pop();
				}
			}
			appendSql( ' ' );
		}
	}

	protected void renderFetchPlusOffsetExpression(
			Expression fetchClauseExpression,
			Expression offsetClauseExpression,
			int offset) {
		renderFetchExpression( fetchClauseExpression );
		appendSql( '+' );
		renderOffsetExpression( offsetClauseExpression );
		if ( offset != 0 ) {
			appendSql( '+' );
			appendSql( Integer.toString( offset ) );
		}
	}

	protected void renderFetchPlusOffsetExpressionAsSingleParameter(
			Expression fetchClauseExpression,
			Expression offsetClauseExpression,
			int offset) {
		if ( fetchClauseExpression instanceof Literal ) {
			final Number fetchCount = (Number) ( (Literal) fetchClauseExpression ).getLiteralValue();
			if ( offsetClauseExpression instanceof Literal ) {
				final Number offsetCount = (Number) ( (Literal) offsetClauseExpression ).getLiteralValue();
				appendSql( Integer.toString( fetchCount.intValue() + offsetCount.intValue() + offset ) );
			}
			else {
				appendSql( PARAM_MARKER );
				final JdbcParameter offsetParameter = (JdbcParameter) offsetClauseExpression;
				final int offsetValue = offset + fetchCount.intValue();
				jdbcParameters.addParameter( offsetParameter );
				parameterBinders.add(
						(statement, startPosition, jdbcParameterBindings, executionContext) -> {
							final JdbcParameterBinding binding = jdbcParameterBindings.getBinding( offsetParameter );
							if ( binding == null ) {
								throw new ExecutionException( "JDBC parameter value not bound - " + offsetParameter );
							}
							final Number bindValue = (Number) binding.getBindValue();
							offsetParameter.getExpressionType().getJdbcMappings().get( 0 ).getJdbcValueBinder().bind(
									statement,
									bindValue.intValue() + offsetValue,
									startPosition,
									executionContext.getSession()
							);
						}
				);
			}
		}
		else {
			appendSql( PARAM_MARKER );
			final JdbcParameter offsetParameter = (JdbcParameter) offsetClauseExpression;
			final JdbcParameter fetchParameter = (JdbcParameter) fetchClauseExpression;
			final OffsetReceivingParameterBinder fetchBinder = new OffsetReceivingParameterBinder(
					fetchParameter,
					offset
			);
			jdbcParameters.addParameter( fetchParameter );
			parameterBinders.add( fetchBinder );
			jdbcParameters.addParameter( offsetParameter );
			parameterBinders.add(
					(statement, startPosition, jdbcParameterBindings, executionContext) -> {
						final JdbcParameterBinding binding = jdbcParameterBindings.getBinding( offsetParameter );
						if ( binding == null ) {
							throw new ExecutionException( "JDBC parameter value not bound - " + offsetParameter );
						}
						fetchBinder.dynamicOffset = (Number) binding.getBindValue();
					}
			);
		}
	}

	private static class OffsetReceivingParameterBinder implements JdbcParameterBinder {

		private final JdbcParameter fetchParameter;
		private final int staticOffset;
		private Number dynamicOffset;

		public OffsetReceivingParameterBinder(JdbcParameter fetchParameter, int staticOffset) {
			this.fetchParameter = fetchParameter;
			this.staticOffset = staticOffset;
		}

		@Override
		public void bindParameterValue(
				PreparedStatement statement,
				int startPosition,
				JdbcParameterBindings jdbcParameterBindings,
				ExecutionContext executionContext) throws SQLException {
			final JdbcParameterBinding binding = jdbcParameterBindings.getBinding( fetchParameter );
			if ( binding == null ) {
				throw new ExecutionException( "JDBC parameter value not bound - " + fetchParameter );
			}
			final Number bindValue = (Number) binding.getBindValue();
			final int offsetValue = dynamicOffset.intValue() + staticOffset;
			dynamicOffset = null;
			fetchParameter.getExpressionType().getJdbcMappings().get( 0 ).getJdbcValueBinder().bind(
					statement,
					bindValue.intValue() + offsetValue,
					startPosition,
					executionContext.getSession()
			);
		}
	}

	protected void renderFirstSkipClause(QuerySpec querySpec) {
		assertRowsOnlyFetchClauseType( querySpec );
		renderFirstSkipClause( querySpec.getOffsetClauseExpression(), querySpec.getFetchClauseExpression() );
	}

	protected void renderFirstSkipClause(Expression offsetExpression, Expression fetchExpression) {
		final Stack<Clause> clauseStack = getClauseStack();
		if ( fetchExpression != null ) {
			appendSql( "first " );
			clauseStack.push( Clause.FETCH );
			try {
				renderFetchExpression( fetchExpression );
			}
			finally {
				clauseStack.pop();
			}
			appendSql( ' ' );
		}
		if ( offsetExpression != null ) {
			appendSql( "skip " );
			clauseStack.push( Clause.OFFSET );
			try {
				renderOffsetExpression( offsetExpression );
			}
			finally {
				clauseStack.pop();
			}
			appendSql( ' ' );
		}
	}

	protected void renderSkipFirstClause(QuerySpec querySpec) {
		assertRowsOnlyFetchClauseType( querySpec );
		renderSkipFirstClause( querySpec.getOffsetClauseExpression(), querySpec.getFetchClauseExpression() );
	}

	protected void renderSkipFirstClause(Expression offsetExpression, Expression fetchExpression) {
		final Stack<Clause> clauseStack = getClauseStack();
		if ( offsetExpression != null ) {
			appendSql( "skip " );
			clauseStack.push( Clause.OFFSET );
			try {
				renderOffsetExpression( offsetExpression );
			}
			finally {
				clauseStack.pop();
			}
			appendSql( ' ' );
		}
		if ( fetchExpression != null ) {
			appendSql( "first " );
			clauseStack.push( Clause.FETCH );
			try {
				renderFetchExpression( fetchExpression );
			}
			finally {
				clauseStack.pop();
			}
			appendSql( ' ' );
		}
	}

	protected void renderFirstClause(QuerySpec querySpec) {
		assertRowsOnlyFetchClauseType( querySpec );
		renderFirstClause( querySpec.getOffsetClauseExpression(), querySpec.getFetchClauseExpression() );
	}

	protected void renderFirstClause(Expression offsetExpression, Expression fetchExpression) {
		final Stack<Clause> clauseStack = getClauseStack();
		if ( fetchExpression != null ) {
			appendSql( "first " );
			clauseStack.push( Clause.FETCH );
			try {
				renderFetchPlusOffsetExpression( fetchExpression, offsetExpression, 0 );
			}
			finally {
				clauseStack.pop();
			}
			appendSql( ' ' );
		}
	}

	protected void renderCombinedLimitClause(QueryPart queryPart) {
		assertRowsOnlyFetchClauseType( queryPart );
		renderCombinedLimitClause( queryPart.getOffsetClauseExpression(), queryPart.getFetchClauseExpression() );
	}

	protected void renderCombinedLimitClause(Expression offsetExpression, Expression fetchExpression) {
		if ( offsetExpression != null ) {
			final Stack<Clause> clauseStack = getClauseStack();
			appendSql( " limit " );
			clauseStack.push( Clause.OFFSET );
			try {
				renderOffsetExpression( offsetExpression );
			}
			finally {
				clauseStack.pop();
			}
			appendSql( COMA_SEPARATOR );
			if ( fetchExpression != null ) {
				clauseStack.push( Clause.FETCH );
				try {
					renderFetchExpression( fetchExpression );
				}
				finally {
					clauseStack.pop();
				}
			}
			else {
				appendSql( Integer.toString( Integer.MAX_VALUE ) );
			}
		}
		else if ( fetchExpression != null ) {
			final Stack<Clause> clauseStack = getClauseStack();
			appendSql( " limit " );
			clauseStack.push( Clause.FETCH );
			try {
				renderFetchExpression( fetchExpression );
			}
			finally {
				clauseStack.pop();
			}
		}
	}

	protected void renderLimitOffsetClause(QueryPart queryPart) {
		assertRowsOnlyFetchClauseType( queryPart );
		renderLimitOffsetClause( queryPart.getOffsetClauseExpression(), queryPart.getFetchClauseExpression() );
	}

	protected void renderLimitOffsetClause(Expression offsetExpression, Expression fetchExpression) {
		if ( fetchExpression != null ) {
			appendSql( " limit " );
			clauseStack.push( Clause.FETCH );
			try {
				renderFetchExpression( fetchExpression );
			}
			finally {
				clauseStack.pop();
			}
		}
		else if ( offsetExpression != null ) {
			appendSql( " limit " );
			appendSql( Integer.toString( Integer.MAX_VALUE ) );
		}
		if ( offsetExpression != null ) {
			final Stack<Clause> clauseStack = getClauseStack();
			appendSql( " offset " );
			clauseStack.push( Clause.OFFSET );
			try {
				renderOffsetExpression( offsetExpression );
			}
			finally {
				clauseStack.pop();
			}
		}
	}

	protected void assertRowsOnlyFetchClauseType(QueryPart queryPart) {
		final FetchClauseType fetchClauseType = queryPart.getFetchClauseType();
		if ( fetchClauseType != null && fetchClauseType != FetchClauseType.ROWS_ONLY ) {
			throw new IllegalArgumentException( "Can't emulate fetch clause type: " + fetchClauseType );
		}
	}

	protected QueryPart getQueryPartForRowNumbering() {
		return queryPartForRowNumbering;
	}

	protected boolean isRowNumberingCurrentQueryPart() {
		return queryPartForRowNumbering != null;
	}

	protected void emulateFetchOffsetWithWindowFunctions(QueryPart queryPart, boolean emulateFetchClause) {
		emulateFetchOffsetWithWindowFunctions(
				queryPart,
				queryPart.getOffsetClauseExpression(),
				queryPart.getFetchClauseExpression(),
				queryPart.getFetchClauseType(),
				emulateFetchClause
		);
	}

	protected void emulateFetchOffsetWithWindowFunctions(
			QueryPart queryPart,
			Expression offsetExpression,
			Expression fetchExpression,
			FetchClauseType fetchClauseType,
			boolean emulateFetchClause) {
		final QueryPart queryPartForRowNumbering = this.queryPartForRowNumbering;
		final boolean needsSelectAliases = this.needsSelectAliases;
		try {
			this.queryPartForRowNumbering = queryPart;
			this.needsSelectAliases = true;
			final String alias = "r_" + queryPartForRowNumberingAliasCounter + "_";
			queryPartForRowNumberingAliasCounter++;
			appendSql( "select " );
			if ( getClauseStack().isEmpty() ) {
				appendSql( "*" );
			}
			else {
				final int size = queryPart.getFirstQuerySpec().getSelectClause().getSqlSelections().size();
				String separator = "";
				for ( int i = 0; i < size; i++ ) {
					appendSql( separator );
					appendSql( alias );
					appendSql( ".c" );
					appendSql( Integer.toString( i ) );
					separator = COMA_SEPARATOR;
				}
			}
			appendSql( " from (" );
			queryPart.accept( this );
			appendSql( " ) ");
			appendSql( alias );
			appendSql( " where " );
			final Stack<Clause> clauseStack = getClauseStack();
			clauseStack.push( Clause.WHERE );
			try {
				if ( emulateFetchClause ) {
					switch ( fetchClauseType ) {
						case PERCENT_ONLY:
							appendSql( alias );
							appendSql( ".rn <= " );
							if ( offsetExpression != null ) {
								offsetExpression.accept( this );
								appendSql( " + " );
							}
							appendSql( "ceil(");
							appendSql( alias );
							appendSql( ".cnt * " );
							fetchExpression.accept( this );
							appendSql( " / 100 )" );
							break;
						case ROWS_ONLY:
							appendSql( alias );
							appendSql( ".rn <= " );
							fetchExpression.accept( this );
							break;
						case PERCENT_WITH_TIES:
							appendSql( alias );
							appendSql( ".rnk <= " );
							if ( offsetExpression != null ) {
								offsetExpression.accept( this );
								appendSql( " + " );
							}
							appendSql( "ceil(");
							appendSql( alias );
							appendSql( ".cnt * " );
							fetchExpression.accept( this );
							appendSql( " / 100 )" );
							break;
						case ROWS_WITH_TIES:
							appendSql( alias );
							appendSql( ".rnk <= " );
							fetchExpression.accept( this );
							break;
					}
				}
				// todo: not sure if databases handle order by row number or the original ordering better..
				if ( offsetExpression == null ) {
					switch ( fetchClauseType ) {
						case PERCENT_ONLY:
						case ROWS_ONLY:
							appendSql( " order by " );
							appendSql( alias );
							appendSql( ".rn" );
							break;
						case PERCENT_WITH_TIES:
						case ROWS_WITH_TIES:
							appendSql( " order by " );
							appendSql( alias );
							appendSql( ".rnk" );
							break;
					}
				}
				else {
					if ( emulateFetchClause ) {
						appendSql( " and " );
					}
					appendSql( alias );
					appendSql( ".rn > " );
					offsetExpression.accept( this );
					appendSql( " order by " );
					appendSql( alias );
					appendSql( ".rn" );
				}
			}
			finally {
				clauseStack.pop();
			}
		}
		finally {
			this.queryPartForRowNumbering = queryPartForRowNumbering;
			this.needsSelectAliases = needsSelectAliases;
		}
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SELECT clause

	@Override
	public void visitSelectClause(SelectClause selectClause) {
		clauseStack.push( Clause.SELECT );

		try {
			appendSql( "select " );
			if ( selectClause.isDistinct() ) {
				appendSql( "distinct " );
			}
			visitSqlSelections( selectClause );
		}
		finally {
			clauseStack.pop();
		}
	}

	protected void visitSqlSelections(SelectClause selectClause) {
		final List<SqlSelection> sqlSelections = selectClause.getSqlSelections();
		final int size = sqlSelections.size();
		if ( needsSelectAliases ) {
			String separator = NO_SEPARATOR;
			for ( int i = 0; i < size; i++ ) {
				final SqlSelection sqlSelection = sqlSelections.get( i );
				appendSql( separator );
				visitSqlSelection( sqlSelection );
				appendSql( " c" );
				appendSql( Integer.toString( i ) );
				separator = COMA_SEPARATOR;
			}
			if ( queryPartForRowNumbering != null ) {
				final FetchClauseType fetchClauseType = getFetchClauseTypeForRowNumbering( queryPartForRowNumbering );
				if ( fetchClauseType != null ) {
					appendSql( separator );
					switch ( fetchClauseType ) {
						case PERCENT_ONLY:
							appendSql( "count(*) over () cnt," );
						case ROWS_ONLY:
							renderRowNumber( selectClause, queryPartForRowNumbering );
							appendSql( " rn " );
							break;
						case PERCENT_WITH_TIES:
							appendSql( "count(*) over () cnt," );
						case ROWS_WITH_TIES:
							if ( queryPartForRowNumbering.getOffsetClauseExpression() != null ) {
								renderRowNumber( selectClause, queryPartForRowNumbering );
								appendSql( " rn, " );
							}
							if ( selectClause.isDistinct() ) {
								appendSql( "dense_rank()" );
							}
							else {
								appendSql( "rank()" );
							}
							visitOverClause(
									Collections.emptyList(),
									getSortSpecificationsRowNumbering( selectClause, queryPartForRowNumbering )
							);
							appendSql( " rnk" );
							break;
					}
				}
			}
		}
		else {
			String separator = NO_SEPARATOR;
			for ( int i = 0; i < size; i++ ) {
				final SqlSelection sqlSelection = sqlSelections.get( i );
				appendSql( separator );
				visitSqlSelection( sqlSelection );
				separator = COMA_SEPARATOR;
			}
		}
	}

	protected FetchClauseType getFetchClauseTypeForRowNumbering(QueryPart queryPartForRowNumbering) {
		return queryPartForRowNumbering.getFetchClauseType();
	}

	protected void visitOverClause(
			List<Expression> partitionExpressions,
			List<SortSpecification> sortSpecifications) {
		try {
			clauseStack.push( Clause.OVER );
			appendSql( " over (" );
			visitPartitionByClause( partitionExpressions );
			renderOrderBy( !partitionExpressions.isEmpty(), sortSpecifications );
			appendSql( ')' );
		}
		finally {
			clauseStack.pop();
		}
	}

	protected void renderRowNumber(SelectClause selectClause, QueryPart queryPart) {
		if ( selectClause.isDistinct() ) {
			appendSql( "dense_rank()" );
		}
		else {
			appendSql( "row_number()" );
		}
		visitOverClause( Collections.emptyList(), getSortSpecificationsRowNumbering( selectClause, queryPart ) );
	}

	protected List<SortSpecification> getSortSpecificationsRowNumbering(
			SelectClause selectClause,
			QueryPart queryPart) {
		final List<SortSpecification> sortSpecifications = queryPart.getSortSpecifications();
		if ( selectClause.isDistinct() ) {
			// When select distinct is used, we need to add all select items to the order by clause
			final List<SqlSelection> sqlSelections = new ArrayList<>( selectClause.getSqlSelections() );
			final int specificationsSize = sortSpecifications.size();
			for ( int i = sqlSelections.size() - 1; i != 0; i-- ) {
				final Expression selectionExpression = sqlSelections.get( i ).getExpression();
				for ( int j = 0; j < specificationsSize; j++ ) {
					final Expression expression = resolveAliasedExpression(
							sqlSelections,
							sortSpecifications.get( j ).getSortExpression()
					);
					if ( expression.equals( selectionExpression ) ) {
						sqlSelections.remove( i );
						break;
					}
				}
			}
			final int sqlSelectionsSize = sqlSelections.size();
			if ( sqlSelectionsSize == 0 ) {
				return sortSpecifications;
			}
			else {
				final List<SortSpecification> sortSpecificationsRowNumbering = new ArrayList<>( sqlSelectionsSize + specificationsSize );
				sortSpecificationsRowNumbering.addAll( sortSpecifications );
				for ( int i = 0; i < sqlSelectionsSize; i++ ) {
					sortSpecifications.add(
							new SortSpecification(
									new SqlSelectionExpression( sqlSelections.get( i ) ),
									null,
									SortOrder.ASCENDING,
									NullPrecedence.NONE
							)
					);
				}
				return sortSpecificationsRowNumbering;
			}
		}
		else {
			return sortSpecifications;
		}
	}

	@Override
	public void visitSqlSelection(SqlSelection sqlSelection) {
		final Expression expression = sqlSelection.getExpression();
		// Null literals have to be casted in the select clause
		if ( expression instanceof Literal ) {
			final Literal literal = (Literal) expression;
			if ( literal.getLiteralValue() == null ) {
				renderNullCast( literal );
			}
			else {
				renderLiteral( literal, dialect.requiresCastingOfParametersInSelectClause() );
			}
		}
		else if ( expression instanceof NullnessLiteral ) {
			renderNullCast( expression );
		}
		else {
			expression.accept( this );
		}
	}

	protected void renderNullCast(Expression expression) {
		final List<SqlAstNode> arguments = new ArrayList<>( 2 );
		arguments.add( expression );
		arguments.add( new CastTarget( (BasicValuedMapping) expression.getExpressionType() ) );
		castFunction().render( this, arguments, this );
	}

	@SuppressWarnings("unchecked")
	protected void renderLiteral(Literal literal, boolean castParameter) {
		assert literal.getExpressionType().getJdbcTypeCount() == 1;
		final JdbcMapping jdbcMapping = literal.getJdbcMapping();
		final JdbcLiteralFormatter literalFormatter = jdbcMapping.getSqlTypeDescriptor()
				.getJdbcLiteralFormatter( jdbcMapping.getJavaTypeDescriptor() );
		// If we encounter a plain literal in the select clause which has no literal formatter, we must render it as parameter
		if ( literalFormatter == null ) {
			parameterBinders.add( literal );

			final LiteralAsParameter<Object> jdbcParameter = new LiteralAsParameter<>( literal );
			if ( castParameter ) {
				final List<SqlAstNode> arguments = new ArrayList<>( 2 );
				arguments.add( jdbcParameter );
				arguments.add( new CastTarget( (BasicValuedMapping) jdbcMapping ) );
				castFunction().render( this, arguments, this );
			}
			else {
				appendSql( PARAM_MARKER );
			}
		}
		else {
			appendSql(
					literalFormatter.toJdbcLiteral(
							literal.getLiteralValue(),
							dialect,
							getWrapperOptions()
					)
			);
		}
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// FROM clause

	@Override
	public void visitFromClause(FromClause fromClause) {
		if ( fromClause == null || fromClause.getRoots().isEmpty() ) {
			if ( !getDialect().supportsSelectQueryWithoutFromClause() ) {
				appendSql( " " );
				appendSql( getDialect().getFromDual() );
			}
		}
		else {
			appendSql( " from " );
			try {
				clauseStack.push( Clause.FROM );
				String separator = NO_SEPARATOR;
				for ( TableGroup root : fromClause.getRoots() ) {
					appendSql( separator );
					renderTableGroup( root );
					separator = COMA_SEPARATOR;
				}
			}
			finally {
				clauseStack.pop();
			}
		}
	}

	@SuppressWarnings("WeakerAccess")
	protected void renderTableGroup(TableGroup tableGroup) {
		// NOTE : commented out blocks render the TableGroup as a CTE

//		if ( tableGroup.getGroupAlias() !=  null ) {
//			sqlAppender.appendSql( OPEN_PARENTHESIS );
//		}

		renderTableReference( tableGroup.getPrimaryTableReference() );

		renderTableReferenceJoins( tableGroup );

//		if ( tableGroup.getGroupAlias() !=  null ) {
//			sqlAppender.appendSql( CLOSE_PARENTHESIS );
//			sqlAppender.appendSql( AS_KEYWORD );
//			sqlAppender.appendSql( tableGroup.getGroupAlias() );
//		}

		processTableGroupJoins( tableGroup );
	}

	protected void renderTableGroup(TableGroup tableGroup, Predicate predicate) {
		// NOTE : commented out blocks render the TableGroup as a CTE

//		if ( tableGroup.getGroupAlias() !=  null ) {
//			sqlAppender.appendSql( OPEN_PARENTHESIS );
//		}

		renderTableReference( tableGroup.getPrimaryTableReference() );

		appendSql( " on " );
		predicate.accept( this );

		renderTableReferenceJoins( tableGroup );

//		if ( tableGroup.getGroupAlias() !=  null ) {
//			sqlAppender.appendSql( CLOSE_PARENTHESIS );
//			sqlAppender.appendSql( AS_KEYWORD );
//			sqlAppender.appendSql( tableGroup.getGroupAlias() );
//		}

		processTableGroupJoins( tableGroup );
	}

	@SuppressWarnings("WeakerAccess")
	protected void renderTableReference(TableReference tableReference) {
		appendSql( tableReference.getTableExpression() );
		// todo (6.0) : For now we just skip the alias rendering in the delete and update clauses
		//  We need some dialect support if we want to support joins in delete and update statements
		final Clause currentClause = clauseStack.getCurrent();
		switch ( currentClause ) {
			case DELETE:
			case UPDATE:
				return;
		}
		final String identificationVariable = tableReference.getIdentificationVariable();
		if ( identificationVariable != null ) {
			appendSql( getDialect().getTableAliasSeparator() );
			appendSql( identificationVariable );
		}
	}

	@SuppressWarnings("WeakerAccess")
	protected void renderTableReferenceJoins(TableGroup tableGroup) {
		final List<TableReferenceJoin> joins = tableGroup.getTableReferenceJoins();
		if ( joins == null || joins.isEmpty() ) {
			return;
		}

		for ( TableReferenceJoin tableJoin : joins ) {
			appendSql( EMPTY_STRING );
			appendSql( tableJoin.getJoinType().getText() );
			appendSql( " join " );

			renderTableReference( tableJoin.getJoinedTableReference() );

			if ( tableJoin.getJoinPredicate() != null && !tableJoin.getJoinPredicate().isEmpty() ) {
				appendSql( " on " );
				tableJoin.getJoinPredicate().accept( this );
			}
		}
	}

	@SuppressWarnings("WeakerAccess")
	protected void processTableGroupJoins(TableGroup source) {
		source.visitTableGroupJoins( this::processTableGroupJoin );
	}

	@SuppressWarnings("WeakerAccess")
	protected void processTableGroupJoin(TableGroupJoin tableGroupJoin) {
		final TableGroup joinedGroup = tableGroupJoin.getJoinedGroup();

		if ( joinedGroup instanceof VirtualTableGroup ) {
			processTableGroupJoins( tableGroupJoin.getJoinedGroup() );
		}
		else {
			appendSql( EMPTY_STRING );
			SqlAstJoinType joinType = tableGroupJoin.getJoinType();
			if ( joinType == SqlAstJoinType.INNER && !joinedGroup.getTableReferenceJoins().isEmpty() ) {
				joinType = SqlAstJoinType.LEFT;
			}
			appendSql( joinType.getText() );
			appendSql( " join " );

			if ( tableGroupJoin.getPredicate() != null && !tableGroupJoin.getPredicate().isEmpty() ) {
				renderTableGroup( joinedGroup, tableGroupJoin.getPredicate() );
			}
			else {
				renderTableGroup( joinedGroup );
			}
		}
	}

	@Override
	public void visitTableGroup(TableGroup tableGroup) {
		// TableGroup and TableGroup handling should be performed as part of `#visitFromClause`...

		// todo (6.0) : what is the correct behavior here?
		appendSql( tableGroup.getPrimaryTableReference().getIdentificationVariable() );
		appendSql( '.' );
		//TODO: pretty sure the typecast to Loadable is quite wrong here

		ModelPartContainer modelPart = tableGroup.getModelPart();
		if ( modelPart instanceof Loadable ) {
			appendSql( ( (Loadable) tableGroup.getModelPart() ).getIdentifierColumnNames()[0] );
		}
		else if ( modelPart instanceof PluralAttributeMapping ) {
			CollectionPart elementDescriptor = ( (PluralAttributeMapping) modelPart ).getElementDescriptor();
			if ( elementDescriptor instanceof BasicValuedCollectionPart ) {
				String mappedColumnExpression = ( (BasicValuedCollectionPart) elementDescriptor ).getSelectionExpression();
				appendSql( mappedColumnExpression );
			}
		}
		else {
			throw new NotYetImplementedFor6Exception( getClass() );
		}
	}

	@Override
	public void visitTableGroupJoin(TableGroupJoin tableGroupJoin) {
		// TableGroup and TableGroupJoin handling should be performed as part of `#visitFromClause`...

		// todo (6.0) : what is the correct behavior here?
		appendSql( tableGroupJoin.getJoinedGroup().getPrimaryTableReference().getIdentificationVariable() );
		appendSql( '.' );
		//TODO: pretty sure the typecast to Loadable is quite wrong here
		appendSql( ( (Loadable) tableGroupJoin.getJoinedGroup().getModelPart() ).getIdentifierColumnNames()[0] );
	}

	@Override
	public void visitTableReference(TableReference tableReference) {
		// nothing to do... handled via TableGroup#render
	}

	@Override
	public void visitTableReferenceJoin(TableReferenceJoin tableReferenceJoin) {
		// nothing to do... handled within TableGroup#render
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Expressions

	@Override
	public void visitColumnReference(ColumnReference columnReference) {
		if ( dmlTargetTableAlias != null && dmlTargetTableAlias.equals( columnReference.getQualifier() ) ) {
			// todo (6.0) : use the Dialect to determine how to handle column references
			//		- specifically should they use the table-alias, the table-expression
			//			or neither for its qualifier

			// for now, use the unqualified form
			appendSql( columnReference.getColumnExpression() );
		}
		else {
			appendSql( columnReference.getExpressionText() );
		}
	}

	@Override
	public void visitExtractUnit(ExtractUnit extractUnit) {
		appendSql( getDialect().translateExtractField( extractUnit.getUnit() ) );
	}

	@Override
	public void visitDurationUnit(DurationUnit unit) {
		appendSql( getDialect().translateDurationField( unit.getUnit() ) );
	}

	@Override
	public void visitFormat(Format format) {
		final String dialectFormat = getDialect().translateDatetimeFormat( format.getFormat() );
		appendSql( "'" );
		appendSql( dialectFormat );
		appendSql( "'" );
	}

	@Override
	public void visitStar(Star star) {
		appendSql( "*" );
	}

	@Override
	public void visitTrimSpecification(TrimSpecification trimSpecification) {
		appendSql( " " );
		appendSql( trimSpecification.getSpecification().toSqlText() );
		appendSql( " " );
	}

	@Override
	public void visitCastTarget(CastTarget castTarget) {
		appendSql(
				getDialect().getCastTypeName(
						castTarget.getExpressionType(),
						castTarget.getLength(),
						castTarget.getPrecision(),
						castTarget.getScale()
				)
		);
	}

	@Override
	public void visitDistinct(Distinct distinct) {
		appendSql( "distinct " );
		distinct.getExpression().accept( this );
	}

	@Override
	public void visitParameter(JdbcParameter jdbcParameter) {
		appendSql( PARAM_MARKER );

		parameterBinders.add( jdbcParameter.getParameterBinder() );
		jdbcParameters.addParameter( jdbcParameter );
	}

	@Override
	public void visitTuple(SqlTuple tuple) {
		String separator = NO_SEPARATOR;

		boolean isCurrentWhereClause = clauseStack.getCurrent() == Clause.WHERE;
		if ( isCurrentWhereClause ) {
			appendSql( OPEN_PARENTHESIS );
		}

		renderCommaSeparated( tuple.getExpressions() );

		if ( isCurrentWhereClause ) {
			appendSql( CLOSE_PARENTHESIS );
		}
	}

	private void renderCommaSeparated(Iterable<? extends Expression> expressions) {
		String separator = NO_SEPARATOR;
		for ( Expression expression : expressions ) {
			appendSql( separator );
			expression.accept( this );
			separator = COMA_SEPARATOR;
		}
	}

	@Override
	public void visitCollate(Collate collate) {
		collate.getExpression().accept( this );
		appendSql( " collate " );
		appendSql( collate.getCollation() );
	}

	@Override
	public void visitSqlSelectionExpression(SqlSelectionExpression expression) {
		final boolean useSelectionPosition = dialect.supportsOrdinalSelectItemReference();

		if ( useSelectionPosition ) {
			appendSql( Integer.toString( expression.getSelection().getJdbcResultSetIndex() ) );
		}
		else {
			expression.getSelection().getExpression().accept( this );
		}
	}


//	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//	// Expression : Function : Non-Standard
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitNonStandardFunctionExpression(NonStandardFunction function) {
//		appendSql( function.getFunctionName() );
//		if ( !function.getArguments().isEmpty() ) {
//			appendSql( OPEN_PARENTHESIS );
//			String separator = NO_SEPARATOR;
//			for ( Expression argumentExpression : function.getArguments() ) {
//				appendSql( separator );
//				argumentExpression.accept( this );
//				separator = COMA_SEPARATOR;
//			}
//			appendSql( CLOSE_PARENTHESIS );
//		}
//	}
//
//
//	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//	// Expression : Function : Standard
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitAbsFunction(AbsFunction function) {
//		appendSql( "abs(" );
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitAvgFunction(AvgFunction function) {
//		appendSql( "avg(" );
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitBitLengthFunction(BitLengthFunction function) {
//		appendSql( "bit_length(" );
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitCastFunction(CastFunction function) {
//		sqlAppender.appendSql( "cast(" );
//		function.getExpressionToCast().accept( this );
//		sqlAppender.appendSql( AS_KEYWORD );
//		sqlAppender.appendSql( determineCastTargetTypeSqlExpression( function ) );
//		sqlAppender.appendSql( CLOSE_PARENTHESIS );
//	}
//
//	private String determineCastTargetTypeSqlExpression(CastFunction castFunction) {
//		if ( castFunction.getExplicitCastTargetTypeSqlExpression() != null ) {
//			return castFunction.getExplicitCastTargetTypeSqlExpression();
//		}
//
//		final SqlExpressableType castResultType = castFunction.getCastResultType();
//
//		if ( castResultType == null ) {
//			throw new SqlTreeException(
//					"CastFunction did not define an explicit cast target SQL expression and its return type was null"
//			);
//		}
//
//		final BasicJavaDescriptor javaTypeDescriptor = castResultType.getJavaTypeDescriptor();
//		return getJdbcServices()
//				.getDialect()
//				.getCastTypeName( javaTypeDescriptor.getJdbcRecommendedSqlType( this ).getJdbcTypeCode() );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitConcatFunction(ConcatFunction function) {
//		appendSql( "concat(" );
//
//		boolean firstPass = true;
//		for ( Expression expression : function.getExpressions() ) {
//			if ( ! firstPass ) {
//				appendSql( COMA_SEPARATOR );
//			}
//			expression.accept( this );
//			firstPass = false;
//		}
//
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitSubstrFunction(SubstrFunction function) {
//		appendSql( "substr(" );
//
//		boolean firstPass = true;
//		for ( Expression expression : function.getExpressions() ) {
//			if ( ! firstPass ) {
//				appendSql( COMA_SEPARATOR );
//			}
//			expression.accept( this );
//			firstPass = false;
//		}
//
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitCountFunction(CountFunction function) {
//		appendSql( "count(" );
//		if ( function.isDistinct() ) {
//			appendSql( DISTINCT_KEYWORD );
//		}
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	public void visitCountStarFunction(CountStarFunction function) {
//		appendSql( "count(" );
//		if ( function.isDistinct() ) {
//			appendSql( DISTINCT_KEYWORD );
//		}
//		appendSql( "*)" );
//	}
//
//	@Override
//	public void visitCurrentDateFunction(CurrentDateFunction function) {
//		appendSql( "current_date" );
//	}
//
//	@Override
//	public void visitCurrentTimeFunction(CurrentTimeFunction function) {
//		appendSql( "current_time" );
//	}
//
//	@Override
//	public void visitCurrentTimestampFunction(CurrentTimestampFunction function) {
//		appendSql( "current_timestamp" );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitExtractFunction(ExtractFunction extractFunction) {
//		appendSql( "extract(" );
//		extractFunction.getUnitToExtract().accept( this );
//		appendSql( FROM_KEYWORD );
//		extractFunction.getExtractionSource().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitLengthFunction(LengthFunction function) {
//		sqlAppender.appendSql( "length(" );
//		function.getArgument().accept( this );
//		sqlAppender.appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitLocateFunction(LocateFunction function) {
//		appendSql( "locate(" );
//		function.getPatternString().accept( this );
//		appendSql( COMA_SEPARATOR );
//		function.getStringToSearch().accept( this );
//		if ( function.getStartPosition() != null ) {
//			appendSql( COMA_SEPARATOR );
//			function.getStartPosition().accept( this );
//		}
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitLowerFunction(LowerFunction function) {
//		appendSql( "lower(" );
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitMaxFunction(MaxFunction function) {
//		appendSql( "max(" );
//		if ( function.isDistinct() ) {
//			appendSql( DISTINCT_KEYWORD );
//		}
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitMinFunction(MinFunction function) {
//		appendSql( "min(" );
//		if ( function.isDistinct() ) {
//			appendSql( DISTINCT_KEYWORD );
//		}
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitModFunction(ModFunction function) {
//		sqlAppender.appendSql( "mod(" );
//		function.getDividend().accept( this );
//		sqlAppender.appendSql( COMA_SEPARATOR );
//		function.getDivisor().accept( this );
//		sqlAppender.appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitSqrtFunction(SqrtFunction function) {
//		appendSql( "sqrt(" );
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitSumFunction(SumFunction function) {
//		appendSql( "sum(" );
//		if ( function.isDistinct() ) {
//			appendSql( DISTINCT_KEYWORD );
//		}
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitTrimFunction(TrimFunction function) {
//		sqlAppender.appendSql( "trim(" );
//		sqlAppender.appendSql( function.getSpecification().toSqlText() );
//		sqlAppender.appendSql( EMPTY_STRING_SEPARATOR );
//		function.getTrimCharacter().accept( this );
//		sqlAppender.appendSql( FROM_KEYWORD );
//		function.getSource().accept( this );
//		sqlAppender.appendSql( CLOSE_PARENTHESIS );
//
//	}
//
//	@Override
//	@SuppressWarnings("unchecked")
//	public void visitUpperFunction(UpperFunction function) {
//		appendSql( "upper(" );
//		function.getArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	public void visitCoalesceFunction(CoalesceFunction coalesceExpression) {
//		appendSql( "coalesce(" );
//		String separator = NO_SEPARATOR;
//		for ( Expression expression : coalesceExpression.getValues() ) {
//			appendSql( separator );
//			expression.accept( this );
//			separator = COMA_SEPARATOR;
//		}
//
//		appendSql( CLOSE_PARENTHESIS );
//	}
//
//	@Override
//	public void visitNullifFunction(NullifFunction function) {
//		appendSql( "nullif(" );
//		function.getFirstArgument().accept( this );
//		appendSql( COMA_SEPARATOR );
//		function.getSecondArgument().accept( this );
//		appendSql( CLOSE_PARENTHESIS );
//	}


	@Override
	public void visitEntityTypeLiteral(EntityTypeLiteral expression) {
		throw new NotYetImplementedFor6Exception( "Mapping model subclass support not yet implemented" );
//		final EntityPersister entityTypeDescriptor = expression.getEntityTypeDescriptor();
//		final DiscriminatorDescriptor<?> discriminatorDescriptor = expression.getDiscriminatorDescriptor();
//
//		final Object discriminatorValue = discriminatorDescriptor.getDiscriminatorMappings()
//				.entityNameToDiscriminatorValue( entityTypeDescriptor.getEntityName() );
//
//		appendSql( discriminatorValue.toString() );
	}

	@Override
	public void visitBinaryArithmeticExpression(BinaryArithmeticExpression arithmeticExpression) {
		appendSql( "(" );
		arithmeticExpression.getLeftHandOperand().accept( this );
		appendSql( arithmeticExpression.getOperator().getOperatorSqlTextString() );
		arithmeticExpression.getRightHandOperand().accept( this );
		appendSql( ")" );
	}

	@Override
	public void visitDuration(Duration duration) {
		duration.getMagnitude().accept( this );
		appendSql(
				duration.getUnit().conversionFactor( NANOSECOND, getDialect() )
		);
	}

	@Override
	public void visitConversion(Conversion conversion) {
		conversion.getDuration().getMagnitude().accept( this );
		appendSql(
				conversion.getDuration().getUnit().conversionFactor(
						conversion.getUnit(), getDialect()
				)
		);
	}

	@Override
	public void visitCaseSearchedExpression(CaseSearchedExpression caseSearchedExpression) {
		dialect.getCaseExpressionWalker().visitCaseSearchedExpression( caseSearchedExpression, sqlBuffer, this );
	}

	@Override
	public void visitCaseSimpleExpression(CaseSimpleExpression caseSimpleExpression) {
		appendSql( "case" );
		caseSimpleExpression.getFixture().accept( this );
		for ( CaseSimpleExpression.WhenFragment whenFragment : caseSimpleExpression.getWhenFragments() ) {
			appendSql( " when " );
			whenFragment.getCheckValue().accept( this );
			appendSql( " then " );
			whenFragment.getResult().accept( this );
		}
		appendSql( " else " );
		caseSimpleExpression.getOtherwise().accept( this );
		appendSql( " end" );
	}

	@Override
	public void visitAny(Any any) {
		appendSql( "some " );
		any.getSubquery().accept( this );
	}

	@Override
	public void visitEvery(Every every) {
		appendSql( "all " );
		every.getSubquery().accept( this );
	}

	@Override
	public void visitSummarization(Summarization every) {
		// nothing to do... handled within #renderGroupByItem
	}

	@Override
	public void visitJdbcLiteral(JdbcLiteral jdbcLiteral) {
		visitLiteral( jdbcLiteral );
	}

	@Override
	public void visitQueryLiteral(QueryLiteral queryLiteral) {
		visitLiteral( queryLiteral );
	}

	@Override
	public void visitNullnessLiteral(NullnessLiteral nullnessLiteral) {
		// todo (6.0) : account for composite nulls?
		appendSql( "null" );
	}

	private void visitLiteral(Literal literal) {
		if ( literal.getLiteralValue() == null ) {
			// todo : not sure we allow this "higher up"
			appendSql( SqlAppender.NULL_KEYWORD );
		}
		else {
			renderLiteral( literal, false );
		}
	}

	protected void renderAsLiteral(JdbcParameter jdbcParameter, Object literalValue) {
		if ( literalValue == null ) {
			appendSql( SqlAppender.NULL_KEYWORD );
		}
		else {
			assert jdbcParameter.getExpressionType().getJdbcTypeCount() == 1;
			final JdbcMapping jdbcMapping = jdbcParameter.getExpressionType().getJdbcMappings().get( 0 );
			final JdbcLiteralFormatter literalFormatter = jdbcMapping.getSqlTypeDescriptor().getJdbcLiteralFormatter( jdbcMapping.getJavaTypeDescriptor() );
			if ( literalFormatter == null ) {
				throw new IllegalArgumentException( "Can't render parameter as literal, no literal formatter found" );
			}
			else {
				appendSql(
						literalFormatter.toJdbcLiteral(
								literalValue,
								dialect,
								getWrapperOptions()
						)
				);
			}
		}
	}

	@Override
	public void visitUnaryOperationExpression(UnaryOperation unaryOperationExpression) {
		if ( unaryOperationExpression.getOperator() == UnaryArithmeticOperator.UNARY_PLUS ) {
			appendSql( UnaryArithmeticOperator.UNARY_PLUS.getOperatorChar() );
		}
		else {
			appendSql( UnaryArithmeticOperator.UNARY_MINUS.getOperatorChar() );
		}

		unaryOperationExpression.getOperand().accept( this );
	}

	@Override
	public void visitSelfRenderingPredicate(SelfRenderingPredicate selfRenderingPredicate) {
		// todo (6.0) render boolean expression as comparison predicate if necessary
		selfRenderingPredicate.getSelfRenderingExpression().renderToSql( this, this, getSessionFactory() );
	}

	@Override
	public void visitSelfRenderingExpression(SelfRenderingExpression expression) {
		expression.renderToSql( this, this, getSessionFactory() );
	}

//	@Override
//	public void visitPluralAttribute(PluralAttributeReference pluralAttributeReference) {
//		// todo (6.0) - is this valid in the general sense?  Or specific to things like order-by rendering?
//		//		long story short... what should we do here?
//	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Predicates

	@Override
	public void visitBetweenPredicate(BetweenPredicate betweenPredicate) {
		betweenPredicate.getExpression().accept( this );
		if ( betweenPredicate.isNegated() ) {
			appendSql( " not" );
		}
		appendSql( " between " );
		betweenPredicate.getLowerBound().accept( this );
		appendSql( " and " );
		betweenPredicate.getUpperBound().accept( this );
	}

	@Override
	public void visitFilterPredicate(FilterPredicate filterPredicate) {
		assert StringHelper.isNotEmpty( filterPredicate.getFilterFragment() );
		appendSql( filterPredicate.getFilterFragment() );
		for ( FilterJdbcParameter filterJdbcParameter : filterPredicate.getFilterJdbcParameters() ) {
			parameterBinders.add( filterJdbcParameter.getBinder() );
			jdbcParameters.addParameter( filterJdbcParameter.getParameter() );
			filterJdbcParameters.add( filterJdbcParameter );
		}
	}

	@Override
	public void visitGroupedPredicate(GroupedPredicate groupedPredicate) {
		if ( groupedPredicate.isEmpty() ) {
			return;
		}

		appendSql( OPEN_PARENTHESIS );
		groupedPredicate.getSubPredicate().accept( this );
		appendSql( CLOSE_PARENTHESIS );
	}

	@Override
	public void visitInListPredicate(InListPredicate inListPredicate) {
		if ( inListPredicate.getListExpressions().isEmpty() ) {
			appendSql( "false" );
			return;
		}
		final SqlTuple lhsTuple;
		if ( ( lhsTuple = getTuple( inListPredicate.getTestExpression() ) ) != null ) {
			if ( lhsTuple.getExpressions().size() == 1 ) {
				// Special case for tuples with arity 1 as any DBMS supports scalar IN predicates
				lhsTuple.getExpressions().get( 0 ).accept( this );
				if ( inListPredicate.isNegated() ) {
					appendSql( " not" );
				}
				appendSql( " in (" );
				String separator = NO_SEPARATOR;
				for ( Expression expression : inListPredicate.getListExpressions() ) {
					appendSql( separator );
					getTuple( expression ).getExpressions().get( 0 ).accept( this );
					separator = COMA_SEPARATOR;
				}
				appendSql( CLOSE_PARENTHESIS );
			}
			else if ( !dialect.supportsRowValueConstructorSyntaxInInList() ) {
				final ComparisonOperator comparisonOperator = inListPredicate.isNegated() ?
						ComparisonOperator.NOT_EQUAL :
						ComparisonOperator.EQUAL;
				// Some DBs like Oracle support tuples only for the IN subquery predicate
				if ( dialect.supportsRowValueConstructorSyntaxInInSubquery() && dialect.supportsUnionAll() ) {
					inListPredicate.getTestExpression().accept( this );
					if ( inListPredicate.isNegated() ) {
						appendSql( " not" );
					}
					appendSql( " in (" );
					String separator = NO_SEPARATOR;
					for ( Expression expression : inListPredicate.getListExpressions() ) {
						appendSql( separator );
						renderExpressionsAsSubquery(
								getTuple( expression ).getExpressions()
						);
						separator = " union all ";
					}
					appendSql( CLOSE_PARENTHESIS );
				}
				else {
					String separator = NO_SEPARATOR;
					for ( Expression expression : inListPredicate.getListExpressions() ) {
						appendSql( separator );
						emulateTupleComparison(
								lhsTuple.getExpressions(),
								getTuple( expression ).getExpressions(),
								comparisonOperator,
								true
						);
						separator = " or ";
					}
				}
			}
			else {
				inListPredicate.getTestExpression().accept( this );
				if ( inListPredicate.isNegated() ) {
					appendSql( " not" );
				}
				appendSql( " in (" );
				renderCommaSeparated( inListPredicate.getListExpressions() );
				appendSql( CLOSE_PARENTHESIS );
			}
		}
		else {
			inListPredicate.getTestExpression().accept( this );
			if ( inListPredicate.isNegated() ) {
				appendSql( " not" );
			}
			appendSql( " in (" );
			renderCommaSeparated( inListPredicate.getListExpressions() );
			appendSql( CLOSE_PARENTHESIS );
		}
	}

	protected final SqlTuple getTuple(Expression expression) {
		if ( expression instanceof SqlTuple ) {
			return (SqlTuple) expression;
		}
		else if ( expression instanceof SqmParameterInterpretation ) {
			final Expression resolvedExpression = ( (SqmParameterInterpretation) expression ).getResolvedExpression();
			if ( resolvedExpression instanceof SqlTuple ) {
				return (SqlTuple) resolvedExpression;
			}
		}
		else if ( expression instanceof EmbeddableValuedPathInterpretation<?> ) {
			return ( (EmbeddableValuedPathInterpretation<?>) expression ).getSqlExpression();
		}
		else if ( expression instanceof NonAggregatedCompositeValuedPathInterpretation<?> ) {
			return ( (NonAggregatedCompositeValuedPathInterpretation<?>) expression ).getSqlExpression();
		}
		return null;
	}

	@Override
	public void visitInSubQueryPredicate(InSubQueryPredicate inSubQueryPredicate) {
		final SqlTuple lhsTuple;
		if ( ( lhsTuple = getTuple( inSubQueryPredicate.getTestExpression() ) ) != null ) {
			if ( lhsTuple.getExpressions().size() == 1 ) {
				// Special case for tuples with arity 1 as any DBMS supports scalar IN predicates
				lhsTuple.getExpressions().get( 0 ).accept( this );
				if ( inSubQueryPredicate.isNegated() ) {
					appendSql( " not" );
				}
				appendSql( " in " );
				inSubQueryPredicate.getSubQuery().accept( this );
			}
			else if ( !dialect.supportsRowValueConstructorSyntaxInInSubquery() ) {
				emulateTupleSubQueryPredicate(
						inSubQueryPredicate,
						inSubQueryPredicate.isNegated(),
						inSubQueryPredicate.getSubQuery(),
						lhsTuple,
						ComparisonOperator.EQUAL
				);
			}
			else {
				inSubQueryPredicate.getTestExpression().accept( this );
				if ( inSubQueryPredicate.isNegated() ) {
					appendSql( " not" );
				}
				appendSql( " in " );
				inSubQueryPredicate.getSubQuery().accept( this );
			}
		}
		else {
			inSubQueryPredicate.getTestExpression().accept( this );
			if ( inSubQueryPredicate.isNegated() ) {
				appendSql( " not" );
			}
			appendSql( " in " );
			inSubQueryPredicate.getSubQuery().accept( this );
		}
	}

	protected void emulateTupleSubQueryPredicate(
			Predicate predicate,
			boolean negated,
			QueryPart queryPart,
			SqlTuple lhsTuple,
			ComparisonOperator tupleComparisonOperator) {
		final QuerySpec subQuery;
		if ( queryPart instanceof QuerySpec && queryPart.getFetchClauseExpression() == null && queryPart.getOffsetClauseExpression() == null ) {
			subQuery = (QuerySpec) queryPart;
			// We can only emulate the tuple sub query predicate as exists predicate when there are no limit/offsets
			if ( negated ) {
				appendSql( "not " );
			}

			final QueryPart queryPartForRowNumbering = this.queryPartForRowNumbering;
			final boolean needsSelectAliases = this.needsSelectAliases;
			try {
				this.queryPartForRowNumbering = null;
				this.needsSelectAliases = false;
				queryPartStack.push( subQuery );
				appendSql( "exists (select 1" );
				visitFromClause( subQuery.getFromClause() );

				if ( !subQuery.getGroupByClauseExpressions()
						.isEmpty() || subQuery.getHavingClauseRestrictions() != null ) {
					// If we have a group by or having clause, we have to move the tuple comparison emulation to the HAVING clause
					visitWhereClause( subQuery );
					visitGroupByClause( subQuery, false );

					appendSql( " having " );
					clauseStack.push( Clause.HAVING );
					try {
						renderSelectTupleComparison(
								subQuery.getSelectClause().getSqlSelections(),
								lhsTuple,
								tupleComparisonOperator
						);
						final Predicate havingClauseRestrictions = subQuery.getHavingClauseRestrictions();
						if ( havingClauseRestrictions != null ) {
							appendSql( " and (" );
							havingClauseRestrictions.accept( this );
							appendSql( ')' );
						}
					}
					finally {
						clauseStack.pop();
					}
				}
				else {
					// If we have no group by or having clause, we can move the tuple comparison emulation to the WHERE clause
					appendSql( " where " );
					clauseStack.push( Clause.WHERE );
					try {
						renderSelectTupleComparison(
								subQuery.getSelectClause().getSqlSelections(),
								lhsTuple,
								tupleComparisonOperator
						);
						final Predicate whereClauseRestrictions = subQuery.getWhereClauseRestrictions();
						if ( whereClauseRestrictions != null ) {
							appendSql( " and (" );
							whereClauseRestrictions.accept( this );
							appendSql( ')' );
						}
					}
					finally {
						clauseStack.pop();
					}
				}

				appendSql( ")" );
			}
			finally {
				queryPartStack.pop();
				this.queryPartForRowNumbering = queryPartForRowNumbering;
				this.needsSelectAliases = needsSelectAliases;
			}
		}
		else {
			// TODO: We could use nested queries and use row numbers to emulate this
			throw new IllegalArgumentException( "Can't emulate in predicate with tuples and limit/offset or set operations: " + predicate );
		}
	}

	/**
	 * An optimized emulation for relational tuple subquery comparisons.
	 * The idea of this method is to use limit 1 to select the max or min tuple and only compare against that.
	 */
	protected void emulateQuantifiedTupleSubQueryPredicate(
			Predicate predicate,
			QueryPart queryPart,
			SqlTuple lhsTuple,
			ComparisonOperator tupleComparisonOperator) {
		final QuerySpec subQuery;
		if ( queryPart instanceof QuerySpec && queryPart.getFetchClauseExpression() == null && queryPart.getOffsetClauseExpression() == null ) {
			subQuery = (QuerySpec) queryPart;
			// We can only emulate the tuple sub query predicate as exists predicate when there are no limit/offsets
			lhsTuple.accept( this );
			appendSql( " " );
			appendSql( tupleComparisonOperator.sqlText() );
			appendSql( " " );

			final QueryPart queryPartForRowNumbering = this.queryPartForRowNumbering;
			final boolean needsSelectAliases = this.needsSelectAliases;
			try {
				this.queryPartForRowNumbering = null;
				this.needsSelectAliases = false;
				queryPartStack.push( subQuery );
				appendSql( "(" );
				visitSelectClause( subQuery.getSelectClause() );
				visitFromClause( subQuery.getFromClause() );
				visitWhereClause( subQuery );
				visitGroupByClause( subQuery, dialect.supportsSelectAliasInGroupByClause() );
				visitHavingClause( subQuery );

				appendSql( " order by " );
				final List<SqlSelection> sqlSelections = subQuery.getSelectClause().getSqlSelections();
				final String order;
				if ( tupleComparisonOperator == ComparisonOperator.LESS_THAN || tupleComparisonOperator == ComparisonOperator.LESS_THAN_OR_EQUAL ) {
					// Default order is asc so we don't need to specify the order explicitly
					order = "";
				}
				else {
					order = " desc";
				}
				appendSql( "1" );
				appendSql( order );
				for ( int i = 1; i < sqlSelections.size(); i++ ) {
					appendSql( COMA_SEPARATOR );
					appendSql( Integer.toString( i + 1 ) );
					appendSql( order );
				}
				renderFetch( ONE_LITERAL, null, FetchClauseType.ROWS_ONLY );
				appendSql( ")" );
			}
			finally {
				queryPartStack.pop();
				this.queryPartForRowNumbering = queryPartForRowNumbering;
				this.needsSelectAliases = needsSelectAliases;
			}
		}
		else {
			// TODO: We could use nested queries and use row numbers to emulate this
			throw new IllegalArgumentException( "Can't emulate in predicate with tuples and limit/offset or set operations: " + predicate );
		}
	}

	@Override
	public void visitExistsPredicate(ExistsPredicate existsPredicate) {
		appendSql( "exists " );
		existsPredicate.getExpression().accept( this );
	}

	@Override
	public void visitJunction(Junction junction) {
		if ( junction.isEmpty() ) {
			return;
		}

		String separator = NO_SEPARATOR;
		for ( Predicate predicate : junction.getPredicates() ) {
			appendSql( separator );
			predicate.accept( this );
			if ( separator == NO_SEPARATOR ) {
				separator = junction.getNature() == Junction.Nature.CONJUNCTION
						? " and "
						: " or ";
			}
		}
	}

	@Override
	public void visitLikePredicate(LikePredicate likePredicate) {
		likePredicate.getMatchExpression().accept( this );
		if ( likePredicate.isNegated() ) {
			appendSql( " not" );
		}
		appendSql( " like " );
		likePredicate.getPattern().accept( this );
		if ( likePredicate.getEscapeCharacter() != null ) {
			appendSql( " escape " );
			likePredicate.getEscapeCharacter().accept( this );
		}
	}

	@Override
	public void visitNegatedPredicate(NegatedPredicate negatedPredicate) {
		if ( negatedPredicate.isEmpty() ) {
			return;
		}

		appendSql( "not (" );
		negatedPredicate.getPredicate().accept( this );
		appendSql( ")" );
	}

	@Override
	public void visitNullnessPredicate(NullnessPredicate nullnessPredicate) {
		final Expression expression = nullnessPredicate.getExpression();
		final String predicateValue;
		if ( nullnessPredicate.isNegated() ) {
			predicateValue = " is not null";
		}
		else {
			predicateValue = " is null";
		}
		final SqlTuple tuple;
		if ( ( tuple = getTuple( expression ) ) != null ) {
			String separator = NO_SEPARATOR;
			for ( Expression exp : tuple.getExpressions() ) {
				appendSql( separator );
				exp.accept( this );
				appendSql( predicateValue );
				separator = " and ";
			}
		}
		else {
			expression.accept( this );
			appendSql( predicateValue );
		}
	}

	@Override
	public void visitRelationalPredicate(ComparisonPredicate comparisonPredicate) {
		// todo (6.0) : do we want to allow multi-valued parameters in a relational predicate?
		//		yes means we'd have to support dynamically converting this predicate into
		//		an IN predicate or an OR predicate
		//
		//		NOTE: JPA does not define support for multi-valued parameters here.
		//
		// If we decide to support that ^^  we should validate that *both* sides of the
		//		predicate are multi-valued parameters.  because...
		//		well... its stupid :)
//		if ( relationalPredicate.getLeftHandExpression() instanceof GenericParameter ) {
//			final GenericParameter lhs =
//			// transform this into a
//		}
//
		final SqlTuple lhsTuple;
		final SqlTuple rhsTuple;
		if ( ( lhsTuple = getTuple( comparisonPredicate.getLeftHandExpression() ) ) != null ) {
			final Expression rhsExpression = comparisonPredicate.getRightHandExpression();
			final boolean all;
			final QueryPart subquery;

			// Handle emulation of quantified comparison
			if ( rhsExpression instanceof QueryPart ) {
				subquery = (QueryPart) rhsExpression;
				all = true;
			}
			else if ( rhsExpression instanceof Every ) {
				subquery = ( (Every) rhsExpression ).getSubquery();
				all = true;
			}
			else if ( rhsExpression instanceof Any ) {
				subquery = ( (Any) rhsExpression ).getSubquery();
				all = false;
			}
			else {
				subquery = null;
				all = false;
			}

			final ComparisonOperator operator = comparisonPredicate.getOperator();
			if ( lhsTuple.getExpressions().size() == 1 ) {
				// Special case for tuples with arity 1 as any DBMS supports scalar IN predicates
				lhsTuple.getExpressions().get( 0 ).accept( this );
				appendSql( " " );
				appendSql( operator.sqlText() );
				appendSql( " " );
				if ( subquery == null ) {
					getTuple( comparisonPredicate.getRightHandExpression() ).getExpressions().get( 0 ).accept( this );
				}
				else {
					rhsExpression.accept( this );
				}
			}
			else if ( subquery != null && !dialect.supportsRowValueConstructorSyntaxInQuantifiedPredicates() ) {
				// For quantified relational comparisons, we can do an optimized emulation
				if ( all && operator != ComparisonOperator.EQUAL && operator != ComparisonOperator.NOT_EQUAL && dialect.supportsRowValueConstructorSyntax() ) {
					emulateQuantifiedTupleSubQueryPredicate(
							comparisonPredicate,
							subquery,
							lhsTuple,
							operator
					);
				}
				else {
					emulateTupleSubQueryPredicate(
							comparisonPredicate,
							all,
							subquery,
							lhsTuple,
							all ? operator.negated() : operator
					);
				}
			}
			else if ( !dialect.supportsRowValueConstructorSyntax() ) {
				rhsTuple = getTuple( rhsExpression );
				assert rhsTuple != null;
				// Some DBs like Oracle support tuples only for the IN subquery predicate
				if ( ( operator == ComparisonOperator.EQUAL || operator == ComparisonOperator.NOT_EQUAL ) && dialect.supportsRowValueConstructorSyntaxInInSubquery() ) {
					comparisonPredicate.getLeftHandExpression().accept( this );
					if ( operator == ComparisonOperator.NOT_EQUAL ) {
						appendSql( " not" );
					}
					appendSql( " in (" );
					renderExpressionsAsSubquery( rhsTuple.getExpressions() );
					appendSql( CLOSE_PARENTHESIS );
				}
				else {
					emulateTupleComparison(
							lhsTuple.getExpressions(),
							rhsTuple.getExpressions(),
							operator,
							true
					);
				}
			}
			else {
				comparisonPredicate.getLeftHandExpression().accept( this );
				appendSql( " " );
				appendSql( operator.sqlText() );
				appendSql( " " );
				rhsExpression.accept( this );
			}
		}
		else if ( ( rhsTuple = getTuple( comparisonPredicate.getRightHandExpression() ) ) != null ) {
			final Expression lhsExpression = comparisonPredicate.getLeftHandExpression();

			if ( lhsExpression instanceof QueryGroup ) {
				final QueryGroup subquery = (QueryGroup) lhsExpression;

				if ( rhsTuple.getExpressions().size() == 1 ) {
					// Special case for tuples with arity 1 as any DBMS supports scalar IN predicates
					lhsExpression.accept( this );
					appendSql( " " );
					appendSql( comparisonPredicate.getOperator().sqlText() );
					appendSql( " " );
					rhsTuple.getExpressions().get( 0 ).accept( this );
				}
				else if ( dialect.supportsRowValueConstructorSyntax() ) {
					lhsExpression.accept( this );
					appendSql( " " );
					appendSql( comparisonPredicate.getOperator().sqlText() );
					appendSql( " " );
					comparisonPredicate.getRightHandExpression().accept( this );
				}
				else {
					emulateTupleSubQueryPredicate(
							comparisonPredicate,
							false,
							subquery,
							rhsTuple,
							// Since we switch the order of operands, we have to invert the operator
							comparisonPredicate.getOperator().invert()
					);
				}
			}
			else {
				throw new IllegalStateException(
						"Unsupported tuple comparison combination. LHS is neither a tuple nor a tuple subquery but RHS is a tuple: " + comparisonPredicate );
			}
		}
		else {
			comparisonPredicate.getLeftHandExpression().accept( this );
			appendSql( " " );
			appendSql( comparisonPredicate.getOperator().sqlText() );
			appendSql( " " );
			comparisonPredicate.getRightHandExpression().accept( this );
		}
	}

}
