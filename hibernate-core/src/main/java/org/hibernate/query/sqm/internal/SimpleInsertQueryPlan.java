/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.internal;

import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.query.spi.NonSelectQueryPlan;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.spi.QueryParameterImplementor;
import org.hibernate.query.spi.SqlOmittingQueryOptions;
import org.hibernate.query.sqm.sql.SqmTranslation;
import org.hibernate.query.sqm.sql.SqmTranslator;
import org.hibernate.query.sqm.sql.SqmTranslatorFactory;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.query.sqm.tree.insert.SqmInsertStatement;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.FromClauseAccess;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.insert.InsertStatement;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcInsert;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;

import java.util.List;
import java.util.Map;

/**
 * @author Gavin King
 */
public class SimpleInsertQueryPlan implements NonSelectQueryPlan {
	private final SqmInsertStatement sqmInsert;
	private final DomainParameterXref domainParameterXref;

	private JdbcInsert jdbcInsert;
	private FromClauseAccess tableGroupAccess;
	private Map<QueryParameterImplementor<?>, Map<SqmParameter, List<List<JdbcParameter>>>> jdbcParamsXref;

	public SimpleInsertQueryPlan(
			SqmInsertStatement sqmInsert,
			DomainParameterXref domainParameterXref) {
		this.sqmInsert = sqmInsert;
		this.domainParameterXref = domainParameterXref;
	}

	private SqlAstTranslator<JdbcInsert> createInsertTranslator(ExecutionContext executionContext) {
		final SessionFactoryImplementor factory = executionContext.getSession().getFactory();
		final QueryEngine queryEngine = factory.getQueryEngine();

		final SqmTranslatorFactory translatorFactory = queryEngine.getSqmTranslatorFactory();
		final SqmTranslator<InsertStatement> translator = translatorFactory.createInsertTranslator(
				sqmInsert,
				executionContext.getQueryOptions(),
				domainParameterXref,
				executionContext.getQueryParameterBindings(),
				executionContext.getLoadQueryInfluencers(),
				factory
		);

		final SqmTranslation<InsertStatement> sqmInterpretation = translator.translate();

		tableGroupAccess = sqmInterpretation.getFromClauseAccess();

		this.jdbcParamsXref = SqmUtil.generateJdbcParamsXref(
				domainParameterXref,
				sqmInterpretation::getJdbcParamsBySqmParam
		);

		return factory.getJdbcServices().getJdbcEnvironment().getSqlAstTranslatorFactory()
				.buildInsertTranslator( factory, sqmInterpretation.getSqlAst() );
	}

	@Override
	public int executeUpdate(ExecutionContext executionContext) {
		final SharedSessionContractImplementor session = executionContext.getSession();
		final SessionFactoryImplementor factory = session.getFactory();
		final JdbcServices jdbcServices = factory.getJdbcServices();
		SqlAstTranslator<JdbcInsert> insertTranslator = null;
		if ( jdbcInsert == null ) {
			insertTranslator = createInsertTranslator( executionContext );
		}

		final JdbcParameterBindings jdbcParameterBindings = SqmUtil.createJdbcParameterBindings(
				executionContext.getQueryParameterBindings(),
				domainParameterXref,
				jdbcParamsXref,
				factory.getDomainModel(),
				tableGroupAccess::findTableGroup,
				session
		);

		if ( jdbcInsert != null && !jdbcInsert.isCompatibleWith(
				jdbcParameterBindings,
				executionContext.getQueryOptions()
		) ) {
			insertTranslator = createInsertTranslator( executionContext );
		}

		if ( insertTranslator != null ) {
			jdbcInsert = insertTranslator.translate( jdbcParameterBindings, executionContext.getQueryOptions() );
		}
		else {
			jdbcInsert.bindFilterJdbcParameters( jdbcParameterBindings );
		}

		return jdbcServices.getJdbcMutationExecutor().execute(
				jdbcInsert,
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
