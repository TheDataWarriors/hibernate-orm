/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.mutation.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.MappingModelExpressable;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.query.spi.DomainQueryExecutionContext;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.internal.SqmJdbcExecutionContextAdapter;
import org.hibernate.query.sqm.internal.SqmUtil;
import org.hibernate.query.sqm.spi.SqmParameterMappingModelResolutionAccess;
import org.hibernate.query.sqm.sql.internal.SqlAstQueryPartProcessingStateImpl;
import org.hibernate.query.sqm.tree.SqmDeleteOrUpdateStatement;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.sql.ast.SqlAstJoinType;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.exec.spi.JdbcSelect;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.basic.BasicResult;
import org.hibernate.sql.results.internal.SqlSelectionImpl;
import org.hibernate.sql.results.spi.ListResultsConsumer;

import org.jboss.logging.Logger;

/**
 * Helper used to generate the SELECT for selection of an entity's identifier, here specifically intended to be used
 * as the SELECT portion of a multi-table SQM mutation
 *
 * @author Steve Ebersole
 */
public class MatchingIdSelectionHelper {
	private static final Logger log = Logger.getLogger( MatchingIdSelectionHelper.class );

	/**
	 * @asciidoc
	 *
	 * Generates a query-spec for selecting all ids matching the restriction defined as part
	 * of the user's update/delete query.  This query-spec is generally used:
	 *
	 * 		* to select all the matching ids via JDBC - see {@link MatchingIdSelectionHelper#selectMatchingIds}
	 * 		* as a sub-query restriction to insert rows into an "id table"
	 */
	public static SelectStatement generateMatchingIdSelectStatement(
			EntityMappingType targetEntityDescriptor,
			SqmDeleteOrUpdateStatement sqmStatement,
			boolean queryRoot,
			Predicate restriction,
			MultiTableSqmMutationConverter sqmConverter,
			DomainQueryExecutionContext executionContext,
			SessionFactoryImplementor sessionFactory) {
		final EntityDomainType entityDomainType = sqmStatement.getTarget().getModel();
		if ( log.isTraceEnabled() ) {
			log.tracef(
					"Starting generation of entity-id SQM selection - %s",
					entityDomainType.getHibernateEntityName()
			);
		}

		final QuerySpec idSelectionQuery = new QuerySpec( queryRoot, 1 );
		idSelectionQuery.applyPredicate( restriction );

		final TableGroup mutatingTableGroup = sqmConverter.getMutatingTableGroup();
		idSelectionQuery.getFromClause().addRoot( mutatingTableGroup );

		final List<DomainResult<?>> domainResults = new ArrayList<>();
		sqmConverter.getProcessingStateStack().push(
				new SqlAstQueryPartProcessingStateImpl(
						idSelectionQuery,
						sqmConverter.getCurrentProcessingState(),
						sqmConverter.getSqlAstCreationState(),
						sqmConverter.getCurrentClauseStack()::getCurrent
				)
		);
		targetEntityDescriptor.getIdentifierMapping().applySqlSelections(
				mutatingTableGroup.getNavigablePath(),
				mutatingTableGroup,
				sqmConverter,
				(selection, jdbcMapping) -> {
					domainResults.add(
							new BasicResult<>(
									selection.getValuesArrayPosition(),
									null,
									jdbcMapping.getJavaTypeDescriptor()
							)
					);
				}
		);
		sqmConverter.getProcessingStateStack().pop();

		targetEntityDescriptor.getEntityPersister().applyBaseRestrictions(
				idSelectionQuery::applyPredicate,
				mutatingTableGroup,
				true,
				executionContext.getSession().getLoadQueryInfluencers().getEnabledFilters(),
				null,
				sqmConverter
		);

		return new SelectStatement( idSelectionQuery, domainResults );
	}
	/**
	 * @asciidoc
	 *
	 * Generates a query-spec for selecting all ids matching the restriction defined as part
	 * of the user's update/delete query.  This query-spec is generally used:
	 *
	 * 		* to select all the matching ids via JDBC - see {@link MatchingIdSelectionHelper#selectMatchingIds}
	 * 		* as a sub-query restriction to insert rows into an "id table"
	 */
	public static QuerySpec generateMatchingIdSelectQuery(
			EntityMappingType targetEntityDescriptor,
			SqmDeleteOrUpdateStatement sqmStatement,
			DomainParameterXref domainParameterXref,
			Predicate restriction,
			MultiTableSqmMutationConverter sqmConverter,
			SessionFactoryImplementor sessionFactory) {
		final EntityDomainType entityDomainType = sqmStatement.getTarget().getModel();
		if ( log.isTraceEnabled() ) {
			log.tracef(
					"Starting generation of entity-id SQM selection - %s",
					entityDomainType.getHibernateEntityName()
			);
		}

		final QuerySpec idSelectionQuery = new QuerySpec( true, 1 );

		final TableGroup mutatingTableGroup = sqmConverter.getMutatingTableGroup();
		idSelectionQuery.getFromClause().addRoot( mutatingTableGroup );

		targetEntityDescriptor.getIdentifierMapping().forEachSelectable(
				(position, selection) -> {
					final TableReference tableReference = mutatingTableGroup.resolveTableReference(
							mutatingTableGroup.getNavigablePath(),
							selection.getContainingTableExpression()
					);
					final Expression expression = sqmConverter.getSqlExpressionResolver().resolveSqlExpression(
							SqlExpressionResolver.createColumnReferenceKey( tableReference, selection.getSelectionExpression() ),
							sqlAstProcessingState -> new ColumnReference(
									tableReference,
									selection,
									sessionFactory
							)
					);
					idSelectionQuery.getSelectClause().addSqlSelection(
							new SqlSelectionImpl(
									position,
									position + 1,
									expression
							)
					);
				}
		);

		idSelectionQuery.applyPredicate( restriction );

		return idSelectionQuery;
	}

	/**
	 * Centralized selection of ids matching the restriction of the DELETE
	 * or UPDATE SQM query
	 */
	public static List<Object> selectMatchingIds(
			SqmDeleteOrUpdateStatement sqmMutationStatement,
			DomainParameterXref domainParameterXref,
			DomainQueryExecutionContext executionContext) {
		final SessionFactoryImplementor factory = executionContext.getSession().getFactory();

		final EntityMappingType entityDescriptor = factory.getDomainModel()
				.getEntityDescriptor( sqmMutationStatement.getTarget().getModel().getHibernateEntityName() );

		final MultiTableSqmMutationConverter sqmConverter = new MultiTableSqmMutationConverter(
				entityDescriptor,
				sqmMutationStatement,
				sqmMutationStatement.getTarget(),
				domainParameterXref,
				executionContext.getQueryOptions(),
				executionContext.getSession().getLoadQueryInfluencers(),
				executionContext.getQueryParameterBindings(),
				factory
		);


		final Map<SqmParameter, List<JdbcParameter>> parameterResolutions;

		if ( domainParameterXref.getSqmParameterCount() == 0 ) {
			parameterResolutions = Collections.emptyMap();
		}
		else {
			parameterResolutions = new IdentityHashMap<>();
		}

		final Predicate restriction = sqmConverter.visitWhereClause(
				sqmMutationStatement.getWhereClause(),
				columnReference -> {},
				(sqmParam, mappingType, jdbcParameters) -> parameterResolutions.put( sqmParam, jdbcParameters )
		);

		final SelectStatement matchingIdSelection = generateMatchingIdSelectStatement(
				entityDescriptor,
				sqmMutationStatement,
				true,
				restriction,
				sqmConverter,
				executionContext,
				factory
		);

		sqmConverter.getProcessingStateStack().push(
				new SqlAstQueryPartProcessingStateImpl(
						matchingIdSelection.getQuerySpec(),
						sqmConverter.getCurrentProcessingState(),
						sqmConverter.getSqlAstCreationState(),
						sqmConverter.getCurrentClauseStack()::getCurrent
				)
		);
		entityDescriptor.visitSubTypeAttributeMappings(
				attribute -> {
					if ( attribute instanceof PluralAttributeMapping ) {
						final PluralAttributeMapping pluralAttribute = (PluralAttributeMapping) attribute;

						if ( pluralAttribute.getSeparateCollectionTable() != null ) {
							// Ensure that the FK target columns are available
							final boolean useFkTarget = !( pluralAttribute.getKeyDescriptor()
									.getTargetPart() instanceof EntityIdentifierMapping );
							if ( useFkTarget ) {
								final TableGroup mutatingTableGroup = sqmConverter.getMutatingTableGroup();
								pluralAttribute.getKeyDescriptor().getTargetPart().applySqlSelections(
										mutatingTableGroup.getNavigablePath(),
										mutatingTableGroup,
										sqmConverter,
										(selection, jdbcMapping) -> {
											matchingIdSelection.getDomainResultDescriptors().add(
													new BasicResult<>(
															selection.getValuesArrayPosition(),
															null,
															jdbcMapping.getJavaTypeDescriptor()
													)
											);
										}
								);
							}
						}
					}
				}
		);
		sqmConverter.getProcessingStateStack().pop();

		final JdbcServices jdbcServices = factory.getJdbcServices();
		final JdbcEnvironment jdbcEnvironment = jdbcServices.getJdbcEnvironment();
		final SqlAstTranslator<JdbcSelect> sqlAstSelectTranslator = jdbcEnvironment
				.getSqlAstTranslatorFactory()
				.buildSelectTranslator( factory, matchingIdSelection );

		final JdbcParameterBindings jdbcParameterBindings = SqmUtil.createJdbcParameterBindings(
				executionContext.getQueryParameterBindings(),
				domainParameterXref,
				SqmUtil.generateJdbcParamsXref(domainParameterXref, sqmConverter),
				factory.getDomainModel(),
				navigablePath -> sqmConverter.getMutatingTableGroup(),
				new SqmParameterMappingModelResolutionAccess() {
					@Override @SuppressWarnings("unchecked")
					public <T> MappingModelExpressable<T> getResolvedMappingModelType(SqmParameter<T> parameter) {
						return (MappingModelExpressable<T>) sqmConverter.getSqmParameterMappingModelExpressableResolutions().get(parameter);
					}
				}
				,
				executionContext.getSession()
		);
		final LockOptions lockOptions = executionContext.getQueryOptions().getLockOptions();
		final LockMode lockMode = lockOptions.getLockMode();
		// Acquire a WRITE lock for the rows that are about to be modified
		lockOptions.setLockMode( LockMode.WRITE );
		// Visit the table joins and reset the lock mode if we encounter OUTER joins that are not supported
		if ( !jdbcEnvironment.getDialect().supportsOuterJoinForUpdate() ) {
			matchingIdSelection.getQuerySpec().getFromClause().visitTableJoins(
					tableJoin -> {
						if ( tableJoin.getJoinType() != SqlAstJoinType.INNER ) {
							lockOptions.setLockMode( lockMode );
						}
					}
			);
		}
		final JdbcSelect idSelectJdbcOperation = sqlAstSelectTranslator.translate(
				jdbcParameterBindings,
				executionContext.getQueryOptions()
		);
		lockOptions.setLockMode( lockMode );

		return jdbcServices.getJdbcSelectExecutor().list(
				idSelectJdbcOperation,
				jdbcParameterBindings,
				SqmJdbcExecutionContextAdapter.omittingLockingAndPaging( executionContext ),
				row -> row[0],
				ListResultsConsumer.UniqueSemantic.FILTER
		);
	}
}
