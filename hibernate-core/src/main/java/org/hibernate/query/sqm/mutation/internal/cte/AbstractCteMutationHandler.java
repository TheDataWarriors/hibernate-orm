/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.mutation.internal.cte;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.SqlExpressable;
import org.hibernate.metamodel.model.domain.BasicDomainType;
import org.hibernate.query.spi.SqlOmittingQueryOptions;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.internal.SqmUtil;
import org.hibernate.query.sqm.mutation.internal.MatchingIdSelectionHelper;
import org.hibernate.query.sqm.mutation.internal.MultiTableSqmMutationConverter;
import org.hibernate.query.sqm.mutation.spi.AbstractMutationHandler;
import org.hibernate.query.sqm.sql.BaseSqmToSqlAstConverter;
import org.hibernate.query.sqm.tree.SqmDeleteOrUpdateStatement;
import org.hibernate.query.sqm.tree.cte.SqmCteTable;
import org.hibernate.query.sqm.tree.expression.SqmExpression;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.query.sqm.tree.expression.SqmStar;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.tree.cte.CteColumn;
import org.hibernate.sql.ast.tree.cte.CteContainer;
import org.hibernate.sql.ast.tree.cte.CteStatement;
import org.hibernate.sql.ast.tree.cte.CteTableGroup;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.predicate.InSubQueryPredicate;
import org.hibernate.sql.ast.tree.predicate.Junction;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.exec.spi.JdbcSelect;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.basic.BasicResult;
import org.hibernate.sql.results.internal.SqlSelectionImpl;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Defines how identifier values are selected from the updatable/deletable tables.
 *
 * @author Christian Beikov
 */
public abstract class AbstractCteMutationHandler extends AbstractMutationHandler {

	public static final String DML_RESULT_TABLE_NAME_PREFIX = "dml_cte_";
	public static final String CTE_TABLE_IDENTIFIER = "id";

	private final SqmCteTable cteTable;
	private final DomainParameterXref domainParameterXref;
	private final CteStrategy strategy;

	public AbstractCteMutationHandler(
			SqmCteTable cteTable,
			SqmDeleteOrUpdateStatement sqmStatement,
			DomainParameterXref domainParameterXref,
			CteStrategy strategy,
			SessionFactoryImplementor sessionFactory) {
		super( sqmStatement, sessionFactory );
		this.cteTable = cteTable;
		this.domainParameterXref = domainParameterXref;

		this.strategy = strategy;
	}

	public SqmCteTable getCteTable() {
		return cteTable;
	}

	public DomainParameterXref getDomainParameterXref() {
		return domainParameterXref;
	}

	public CteStrategy getStrategy() {
		return strategy;
	}

	@Override
	public int execute(ExecutionContext executionContext) {
		final SqmDeleteOrUpdateStatement sqmMutationStatement = getSqmDeleteOrUpdateStatement();
		final SessionFactoryImplementor factory = executionContext.getSession().getFactory();
		final EntityMappingType entityDescriptor = getEntityDescriptor();

		final MultiTableSqmMutationConverter sqmConverter = new MultiTableSqmMutationConverter(
				entityDescriptor,
				sqmMutationStatement.getTarget().getExplicitAlias(),
				domainParameterXref,
				executionContext.getQueryOptions(),
				executionContext.getLoadQueryInfluencers(),
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
				parameterResolutions::put
		);

		final CteStatement idSelectCte = new CteStatement(
				BaseSqmToSqlAstConverter.createCteTable( getCteTable(), factory ),
				MatchingIdSelectionHelper.generateMatchingIdSelectStatement(
						entityDescriptor,
						sqmMutationStatement,
						restriction,
						sqmConverter,
						executionContext,
						factory
				)
		);

		// Create the main query spec that will return the count of
		final QuerySpec querySpec = new QuerySpec( true, 1 );
		final List<DomainResult> domainResults = new ArrayList<>( 1 );
		final SelectStatement statement = new SelectStatement( querySpec, domainResults );
		final JdbcServices jdbcServices = factory.getJdbcServices();
		final SqlAstTranslator<JdbcSelect> translator = jdbcServices.getJdbcEnvironment()
				.getSqlAstTranslatorFactory()
				.buildSelectTranslator( factory, statement );

		final Expression count = createCountStart( factory, sqmConverter );
		domainResults.add(
				new BasicResult<>(
						0,
						null,
						( (SqlExpressable) count).getJdbcMapping().getJavaTypeDescriptor()
				)
		);
		querySpec.getSelectClause().addSqlSelection( new SqlSelectionImpl( 1, 0, count ) );
		querySpec.getFromClause().addRoot(
				new CteTableGroup(
						new TableReference(
								idSelectCte.getCteTable().getTableExpression(),
								CTE_TABLE_IDENTIFIER,
								false,
								factory
						)
				)
		);

		// Add all CTEs
		statement.addCteStatement( idSelectCte );
		addDmlCtes( statement, idSelectCte, sqmConverter, parameterResolutions, factory );

		final JdbcParameterBindings jdbcParameterBindings = SqmUtil.createJdbcParameterBindings(
				executionContext.getQueryParameterBindings(),
				domainParameterXref,
				SqmUtil.generateJdbcParamsXref( domainParameterXref, sqmConverter ),
				factory.getDomainModel(),
				navigablePath -> sqmConverter.getMutatingTableGroup(),
				executionContext.getSession()
		);
		final JdbcSelect select = translator.translate( jdbcParameterBindings, executionContext.getQueryOptions() );
		List<Object> list = jdbcServices.getJdbcSelectExecutor().list(
				select,
				jdbcParameterBindings,
				SqlOmittingQueryOptions.omitSqlQueryOptions( executionContext, select ),
				row -> row[0],
				false
		);
		return ( (Number) list.get( 0 ) ).intValue();
	}

	private Expression createCountStart(
			SessionFactoryImplementor factory,
			MultiTableSqmMutationConverter sqmConverter) {
		final SqmExpression<?> arg = new SqmStar( factory.getNodeBuilder() );
		final TypeConfiguration typeConfiguration = factory.getJpaMetamodel().getTypeConfiguration();
		final BasicDomainType<Long> type = typeConfiguration.standardBasicTypeForJavaType( Long.class );
		return factory.getQueryEngine().getSqmFunctionRegistry().findFunctionDescriptor("count").generateSqmExpression(
				arg,
				type,
				factory.getQueryEngine(),
				typeConfiguration
		).convertToSqlAst( sqmConverter );
	}

	protected Predicate createIdSubQueryPredicate(
			List<? extends Expression> lhsExpressions,
			CteStatement idSelectCte,
			SessionFactoryImplementor factory) {
		final TableReference idSelectTableReference = new TableReference(
				idSelectCte.getCteTable().getTableExpression(),
				CTE_TABLE_IDENTIFIER,
				false,
				factory
		);
		final Junction predicate = new Junction( Junction.Nature.CONJUNCTION );
		final List<CteColumn> cteColumns = idSelectCte.getCteTable().getCteColumns();
		final int size = cteColumns.size();
		final QuerySpec subQuery = new QuerySpec( false, 1 );
		subQuery.getFromClause().addRoot( new CteTableGroup( idSelectTableReference ) );
		final SelectClause subQuerySelectClause = subQuery.getSelectClause();
		for ( int i = 0; i < size; i++ ) {
			final CteColumn cteColumn = cteColumns.get( i );
			subQuerySelectClause.addSqlSelection(
					new SqlSelectionImpl(
							i + 1,
							i,
							new ColumnReference(
									idSelectTableReference,
									cteColumn.getColumnExpression(),
									cteColumn.getJdbcMapping(),
									factory
							)
					)
			);
		}
		final Expression lhs;
		if ( lhsExpressions.size() == 1 ) {
			lhs = lhsExpressions.get( 0 );
		}
		else {
			lhs = new SqlTuple( lhsExpressions, null );
		}
		predicate.add(
				new InSubQueryPredicate(
						lhs,
						subQuery,
						false
				)
		);
		return predicate;
	}

	protected abstract void addDmlCtes(
			CteContainer statement,
			CteStatement idSelectCte,
			MultiTableSqmMutationConverter sqmConverter,
			Map<SqmParameter, List<JdbcParameter>> parameterResolutions,
			SessionFactoryImplementor factory);

}
