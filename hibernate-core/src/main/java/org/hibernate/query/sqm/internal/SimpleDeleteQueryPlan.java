/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.internal;

import java.util.List;
import java.util.Map;

import org.hibernate.action.internal.BulkOperationCleanupAction;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.ForeignKeyDescriptor;
import org.hibernate.metamodel.mapping.MappingModelHelper;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.spi.NonSelectQueryPlan;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.spi.QueryParameterImplementor;
import org.hibernate.query.spi.SqlOmittingQueryOptions;
import org.hibernate.query.sqm.mutation.internal.SqmMutationStrategyHelper;
import org.hibernate.query.sqm.sql.SqmTranslation;
import org.hibernate.query.sqm.sql.SqmTranslator;
import org.hibernate.query.sqm.sql.SqmTranslatorFactory;
import org.hibernate.query.sqm.tree.delete.SqmDeleteStatement;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.tree.delete.DeleteStatement;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.from.MutatingTableReferenceGroupWrapper;
import org.hibernate.sql.ast.tree.predicate.InSubQueryPredicate;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcDelete;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.results.internal.SqlSelectionImpl;

/**
 * @author Steve Ebersole
 */
public class SimpleDeleteQueryPlan implements NonSelectQueryPlan {
	private final EntityMappingType entityDescriptor;
	private final SqmDeleteStatement sqmDelete;
	private final DomainParameterXref domainParameterXref;

	private JdbcDelete jdbcDelete;
	private SqmTranslation<DeleteStatement> sqmInterpretation;
	private Map<QueryParameterImplementor<?>, Map<SqmParameter, List<List<JdbcParameter>>>> jdbcParamsXref;

	public SimpleDeleteQueryPlan(
			EntityMappingType entityDescriptor,
			SqmDeleteStatement sqmDelete,
			DomainParameterXref domainParameterXref) {
		assert entityDescriptor.getEntityName().equals( sqmDelete.getTarget().getEntityName() );

		this.entityDescriptor = entityDescriptor;
		this.sqmDelete = sqmDelete;
		this.domainParameterXref = domainParameterXref;
	}

	private SqlAstTranslator<JdbcDelete> createDeleteTranslator(ExecutionContext executionContext) {
		final SessionFactoryImplementor factory = executionContext.getSession().getFactory();
		final QueryEngine queryEngine = factory.getQueryEngine();

		final SqmTranslatorFactory translatorFactory = queryEngine.getSqmTranslatorFactory();
		final SqmTranslator<DeleteStatement> translator = translatorFactory.createSimpleDeleteTranslator(
				sqmDelete,
				executionContext.getQueryOptions(),
				domainParameterXref,
				executionContext.getQueryParameterBindings(),
				executionContext.getLoadQueryInfluencers(),
				factory
		);

		sqmInterpretation = translator.translate();

		this.jdbcParamsXref = SqmUtil.generateJdbcParamsXref(
				domainParameterXref,
				sqmInterpretation::getJdbcParamsBySqmParam
		);

		return factory.getJdbcServices().getJdbcEnvironment().getSqlAstTranslatorFactory()
				.buildDeleteTranslator( factory, sqmInterpretation.getSqlAst() );
	}

	@Override
	public int executeUpdate(ExecutionContext executionContext) {
		BulkOperationCleanupAction.schedule( executionContext, sqmDelete );
		final SharedSessionContractImplementor session = executionContext.getSession();
		final SessionFactoryImplementor factory = session.getFactory();
		final JdbcServices jdbcServices = factory.getJdbcServices();
		SqlAstTranslator<JdbcDelete> deleteTranslator = null;
		if ( jdbcDelete == null ) {
			deleteTranslator = createDeleteTranslator( executionContext );
		}

		final JdbcParameterBindings jdbcParameterBindings = SqmUtil.createJdbcParameterBindings(
				executionContext.getQueryParameterBindings(),
				domainParameterXref,
				jdbcParamsXref,
				factory.getDomainModel(),
				sqmInterpretation.getFromClauseAccess()::findTableGroup,
				session
		);

		if ( jdbcDelete != null && !jdbcDelete.isCompatibleWith(
				jdbcParameterBindings,
				executionContext.getQueryOptions()
		) ) {
			deleteTranslator = createDeleteTranslator( executionContext );
		}

		if ( deleteTranslator != null ) {
			jdbcDelete = deleteTranslator.translate( jdbcParameterBindings, executionContext.getQueryOptions() );
		}
		else {
			jdbcDelete.bindFilterJdbcParameters( jdbcParameterBindings );
		}

		final boolean missingRestriction = sqmDelete.getWhereClause() == null
				|| sqmDelete.getWhereClause().getPredicate() == null;
		if ( missingRestriction ) {
			assert domainParameterXref.getSqmParameterCount() == 0;
			assert jdbcParamsXref.isEmpty();
		}

		SqmMutationStrategyHelper.cleanUpCollectionTables(
				entityDescriptor,
				(tableReference, attributeMapping) -> {
					if ( missingRestriction ) {
						return null;
					}

					final ForeignKeyDescriptor fkDescriptor = attributeMapping.getKeyDescriptor();
					final Expression fkColumnExpression = MappingModelHelper.buildColumnReferenceExpression(
							fkDescriptor,
							null,
							factory
					);

					final QuerySpec matchingIdSubQuery = new QuerySpec( false );

					final Expression fkTargetColumnExpression = MappingModelHelper.buildColumnReferenceExpression(
							fkDescriptor,
							sqmInterpretation.getSqlExpressionResolver(),
							factory
					);
					matchingIdSubQuery.getSelectClause().addSqlSelection( new SqlSelectionImpl( 1, 0, fkTargetColumnExpression ) );

					matchingIdSubQuery.getFromClause().addRoot(
							new MutatingTableReferenceGroupWrapper(
									new NavigablePath( attributeMapping.getRootPathName() ),
									attributeMapping,
									sqmInterpretation.getSqlAst().getTargetTable()
							)
					);

					matchingIdSubQuery.applyPredicate( sqmInterpretation.getSqlAst().getRestriction() );

					return new InSubQueryPredicate( fkColumnExpression, matchingIdSubQuery, false );
				},
				missingRestriction ? JdbcParameterBindings.NO_BINDINGS : jdbcParameterBindings,
				executionContext
		);

		return jdbcServices.getJdbcMutationExecutor().execute(
				jdbcDelete,
				jdbcParameterBindings,
				sql -> session
						.getJdbcCoordinator()
						.getStatementPreparer()
						.prepareStatement( sql ),
				(integer, preparedStatement) -> {},
				SqlOmittingQueryOptions.omitSqlQueryOptions( executionContext )
		);
	}
}
