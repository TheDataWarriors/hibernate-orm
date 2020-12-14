/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.sql.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.internal.FilterHelper;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.Joinable;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.spi.QueryParameterBindings;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.sql.BaseSqmToSqlAstConverter;
import org.hibernate.query.sqm.sql.SimpleSqmUpdateTranslation;
import org.hibernate.query.sqm.sql.SimpleSqmUpdateTranslator;
import org.hibernate.query.sqm.tree.cte.SqmCteStatement;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.query.sqm.tree.predicate.SqmWhereClause;
import org.hibernate.query.sqm.tree.update.SqmAssignment;
import org.hibernate.query.sqm.tree.update.SqmSetClause;
import org.hibernate.query.sqm.tree.update.SqmUpdateStatement;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlTreeCreationLogger;
import org.hibernate.sql.ast.spi.SqlAliasBase;
import org.hibernate.sql.ast.spi.SqlAliasBaseGenerator;
import org.hibernate.sql.ast.spi.SqlAstCreationContext;
import org.hibernate.sql.ast.spi.SqlAstProcessingState;
import org.hibernate.sql.ast.spi.SqlAstTreeHelper;
import org.hibernate.sql.ast.tree.cte.CteStatement;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.predicate.FilterPredicate;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.ast.tree.update.Assignment;
import org.hibernate.sql.ast.tree.update.UpdateStatement;
import org.hibernate.sql.exec.internal.JdbcParameterImpl;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * @author Steve Ebersole
 */
public class StandardSqmUpdateTranslator
		extends BaseSqmToSqlAstConverter
		implements SimpleSqmUpdateTranslator {

	public StandardSqmUpdateTranslator(
			SqlAstCreationContext creationContext,
			QueryOptions queryOptions,
			LoadQueryInfluencers loadQueryInfluencers,
			DomainParameterXref domainParameterXref,
			QueryParameterBindings domainParameterBindings) {
		super( creationContext, queryOptions, loadQueryInfluencers, domainParameterXref, domainParameterBindings );
	}

	@Override
	public CteStatement translate(SqmCteStatement sqmCte) {
		return visitCteStatement( sqmCte );
	}

	@Override
	public SimpleSqmUpdateTranslation translate(SqmUpdateStatement<?> sqmUpdate) {
		final UpdateStatement sqlUpdateAst = visitUpdateStatement( sqmUpdate );
		return new SimpleSqmUpdateTranslation(
				sqlUpdateAst,
				getJdbcParamsBySqmParam()
		);
	}

	@Override
	public UpdateStatement visitUpdateStatement(SqmUpdateStatement sqmStatement) {
		final String entityName = sqmStatement.getTarget().getEntityName();
		final EntityPersister entityDescriptor = getCreationContext().getDomainModel().getEntityDescriptor( entityName );
		assert entityDescriptor != null;

		getProcessingStateStack().push(
				new SqlAstProcessingStateImpl(
						null,
						this,
						getCurrentClauseStack()::getCurrent
				)
		);

		try {
			final NavigablePath rootPath = sqmStatement.getTarget().getNavigablePath();
			final TableGroup rootTableGroup = entityDescriptor.createRootTableGroup(
					rootPath,
					sqmStatement.getRoot().getAlias(),
					false,
					LockMode.WRITE,
					getSqlAliasBaseGenerator(),
					getSqlExpressionResolver(),
					() -> predicate -> additionalRestrictions = predicate,
					getCreationContext()
			);

			if ( ! rootTableGroup.getTableReferenceJoins().isEmpty() ) {
				throw new HibernateException( "Not expecting multiple table references for an SQM DELETE" );
			}

			getFromClauseIndex().registerTableGroup( rootPath, rootTableGroup );

			final List<Assignment> assignments = visitSetClause( sqmStatement.getSetClause() );

			final FilterPredicate filterPredicate = FilterHelper.createFilterPredicate(
					getLoadQueryInfluencers(),
					(Joinable) entityDescriptor
			);
			if ( filterPredicate != null ) {
				additionalRestrictions = SqlAstTreeHelper.combinePredicates( additionalRestrictions, filterPredicate );
			}

			Predicate suppliedPredicate = null;
			final SqmWhereClause whereClause = sqmStatement.getWhereClause();
			if ( whereClause != null && whereClause.getPredicate() != null ) {
				getCurrentClauseStack().push( Clause.WHERE );
				try {
					suppliedPredicate = (Predicate) whereClause.getPredicate().accept( this );
				}
				finally {
					getCurrentClauseStack().pop();
				}
			}

			return new UpdateStatement(
					rootTableGroup.getPrimaryTableReference(),
					assignments,
					SqlAstTreeHelper.combinePredicates( suppliedPredicate, additionalRestrictions )
			);
		}
		finally {
			getProcessingStateStack().pop();
		}
	}


	@Override
	public SqlAliasBaseGenerator getSqlAliasBaseGenerator() {
		return SQL_ALIAS_BASE_GENERATOR;
	}

	private static final SqlAliasBaseGenerator SQL_ALIAS_BASE_GENERATOR = new SqlAliasBaseGenerator() {
		private final SqlAliasBase sqlAliasBase = new SqlAliasBase() {
			@Override
			public String getAliasStem() {
				return null;
			}

			@Override
			public String generateNewAlias() {
				return null;
			}
		};

		@Override
		public SqlAliasBase createSqlAliasBase(String stem) {
			return sqlAliasBase;
		}
	};

	@Override
	public List<Assignment> visitSetClause(SqmSetClause setClause) {
		final List<Assignment> assignments = new ArrayList<>();

		for ( SqmAssignment sqmAssignment : setClause.getAssignments() ) {
			final List<ColumnReference> targetColumnReferences = new ArrayList<>();

			getProcessingStateStack().push(
					new SqlAstProcessingStateImpl(
							getProcessingStateStack().getCurrent(),
							this,
							getCurrentClauseStack()::getCurrent
					) {
						@Override
						public Expression resolveSqlExpression(
								String key,
								Function<SqlAstProcessingState, Expression> creator) {
							final Expression expression = getParentState().getSqlExpressionResolver().resolveSqlExpression( key, creator );
							assert expression instanceof ColumnReference;

							targetColumnReferences.add( (ColumnReference) expression );

							return expression;
						}
					}
			);

			final SqmPathInterpretation assignedPathInterpretation;
			try {
				assignedPathInterpretation = (SqmPathInterpretation) sqmAssignment.getTargetPath().accept( this );
			}
			finally {
				getProcessingStateStack().pop();
			}

			inferableTypeAccessStack.push( assignedPathInterpretation::getExpressionType );

			final List<ColumnReference> valueColumnReferences = new ArrayList<>();
			getProcessingStateStack().push(
					new SqlAstProcessingStateImpl(
							getProcessingStateStack().getCurrent(),
							this,
							getCurrentClauseStack()::getCurrent
					) {
						@Override
						public Expression resolveSqlExpression(
								String key,
								Function<SqlAstProcessingState, Expression> creator) {
							final Expression expression = getParentState().getSqlExpressionResolver().resolveSqlExpression( key, creator );
							assert expression instanceof ColumnReference;

							valueColumnReferences.add( (ColumnReference) expression );

							return expression;
						}
					}
			);

			try {

				if ( sqmAssignment.getValue() instanceof SqmParameter ) {
					final SqmParameter sqmParameter = (SqmParameter) sqmAssignment.getValue();
					final List<JdbcParameter> jdbcParametersForSqm = new ArrayList<>();

					// create one JdbcParameter for each column in the assigned path
					assignedPathInterpretation.getExpressionType().forEachSelection(
							(columnIndex, selection) -> {
								final JdbcParameter jdbcParameter = new JdbcParameterImpl( selection.getJdbcMapping() );
								jdbcParametersForSqm.add( jdbcParameter );
								assignments.add(
										new Assignment(
												new ColumnReference(
														// we do not want a qualifier (table alias) here
														(String) null,
														selection,
														getCreationContext().getSessionFactory()
												),
												jdbcParameter
										)
								);
							}
					);

					getJdbcParamsBySqmParam().put( sqmParameter, jdbcParametersForSqm );
				}
				else {
					final MappingMetamodel domainModel = getCreationContext().getDomainModel();
					final TypeConfiguration typeConfiguration = domainModel.getTypeConfiguration();

					final Expression valueExpression = (Expression) sqmAssignment.getValue().accept( this );

					final int valueExprJdbcCount = valueExpression.getExpressionType().getJdbcTypeCount();
					final int assignedPathJdbcCount = assignedPathInterpretation.getExpressionType().getJdbcTypeCount();

					if ( valueExprJdbcCount != assignedPathJdbcCount ) {
						SqlTreeCreationLogger.LOGGER.debugf(
								"JDBC type count does not match in UPDATE assignment between the assigned-path and the assigned-value; " +
										"this will likely lead to problems executing the query"
						);
					}

					assert assignedPathJdbcCount == valueExprJdbcCount;

					for (ColumnReference columnReference : targetColumnReferences) {
						assignments.add(
								new Assignment( columnReference, valueExpression )
						);
					}
				}
			}
			finally {
				getProcessingStateStack().pop();
				inferableTypeAccessStack.pop();
			}

		}

		return assignments;
	}

}
