/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.sql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.persistence.TemporalType;

import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.NotYetImplementedFor6Exception;
import org.hibernate.dialect.function.TimestampaddFunction;
import org.hibernate.dialect.function.TimestampdiffFunction;
import org.hibernate.engine.FetchTiming;
import org.hibernate.engine.profile.FetchProfile;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.graph.spi.AppliedGraph;
import org.hibernate.internal.FilterHelper;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.internal.util.collections.Stack;
import org.hibernate.internal.util.collections.StandardStack;
import org.hibernate.loader.MultipleBagFetchException;
import org.hibernate.metamodel.CollectionClassification;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.Association;
import org.hibernate.metamodel.mapping.BasicValuedMapping;
import org.hibernate.metamodel.mapping.CollectionPart;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.ForeignKeyDescriptor;
import org.hibernate.metamodel.mapping.MappingModelExpressable;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.ModelPartContainer;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.mapping.internal.EmbeddedCollectionPart;
import org.hibernate.metamodel.mapping.internal.EntityCollectionPart;
import org.hibernate.metamodel.mapping.ordering.OrderByFragment;
import org.hibernate.metamodel.model.domain.AllowableFunctionReturnType;
import org.hibernate.metamodel.model.domain.AllowableParameterType;
import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.metamodel.model.domain.PluralPersistentAttribute;
import org.hibernate.metamodel.model.domain.internal.CompositeSqmPathSource;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.Joinable;
import org.hibernate.query.BinaryArithmeticOperator;
import org.hibernate.query.ComparisonOperator;
import org.hibernate.query.DynamicInstantiationNature;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.QueryLogging;
import org.hibernate.query.SemanticException;
import org.hibernate.query.TemporalUnit;
import org.hibernate.query.UnaryArithmeticOperator;
import org.hibernate.query.internal.QueryHelper;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.spi.QueryParameterBinding;
import org.hibernate.query.spi.QueryParameterBindings;
import org.hibernate.query.spi.QueryParameterImplementor;
import org.hibernate.query.sqm.InterpretationException;
import org.hibernate.query.sqm.SqmExpressable;
import org.hibernate.query.sqm.SqmPathSource;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.internal.SqmMappingModelHelper;
import org.hibernate.query.sqm.spi.BaseSemanticQueryWalker;
import org.hibernate.query.sqm.sql.internal.BasicValuedPathInterpretation;
import org.hibernate.query.sqm.sql.internal.DomainResultProducer;
import org.hibernate.query.sqm.sql.internal.EmbeddableValuedPathInterpretation;
import org.hibernate.query.sqm.sql.internal.EntityValuedPathInterpretation;
import org.hibernate.query.sqm.sql.internal.NonAggregatedCompositeValuedPathInterpretation;
import org.hibernate.query.sqm.sql.internal.PluralValuedSimplePathInterpretation;
import org.hibernate.query.sqm.sql.internal.SqlAstProcessingStateImpl;
import org.hibernate.query.sqm.sql.internal.SqlAstQueryPartProcessingStateImpl;
import org.hibernate.query.sqm.sql.internal.SqmParameterInterpretation;
import org.hibernate.query.sqm.sql.internal.SqmPathInterpretation;
import org.hibernate.query.sqm.tree.SqmStatement;
import org.hibernate.query.sqm.tree.cte.SqmCteContainer;
import org.hibernate.query.sqm.tree.cte.SqmCteStatement;
import org.hibernate.query.sqm.tree.cte.SqmCteTable;
import org.hibernate.query.sqm.tree.cte.SqmCteTableColumn;
import org.hibernate.query.sqm.tree.cte.SqmSearchClauseSpecification;
import org.hibernate.query.sqm.tree.delete.SqmDeleteStatement;
import org.hibernate.query.sqm.tree.domain.NonAggregatedCompositeSimplePath;
import org.hibernate.query.sqm.tree.domain.SqmBasicValuedSimplePath;
import org.hibernate.query.sqm.tree.domain.SqmEmbeddedValuedSimplePath;
import org.hibernate.query.sqm.tree.domain.SqmEntityValuedSimplePath;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.hibernate.query.sqm.tree.domain.SqmPluralValuedSimplePath;
import org.hibernate.query.sqm.tree.domain.SqmTreatedPath;
import org.hibernate.query.sqm.tree.expression.Conversion;
import org.hibernate.query.sqm.tree.expression.JpaCriteriaParameter;
import org.hibernate.query.sqm.tree.expression.SqmAny;
import org.hibernate.query.sqm.tree.expression.SqmBinaryArithmetic;
import org.hibernate.query.sqm.tree.expression.SqmByUnit;
import org.hibernate.query.sqm.tree.expression.SqmCaseSearched;
import org.hibernate.query.sqm.tree.expression.SqmCaseSimple;
import org.hibernate.query.sqm.tree.expression.SqmCastTarget;
import org.hibernate.query.sqm.tree.expression.SqmCollate;
import org.hibernate.query.sqm.tree.expression.SqmDistinct;
import org.hibernate.query.sqm.tree.expression.SqmDurationUnit;
import org.hibernate.query.sqm.tree.expression.SqmEnumLiteral;
import org.hibernate.query.sqm.tree.expression.SqmEvery;
import org.hibernate.query.sqm.tree.expression.SqmExpression;
import org.hibernate.query.sqm.tree.expression.SqmExtractUnit;
import org.hibernate.query.sqm.tree.expression.SqmFieldLiteral;
import org.hibernate.query.sqm.tree.expression.SqmFormat;
import org.hibernate.query.sqm.tree.expression.SqmFunction;
import org.hibernate.query.sqm.tree.expression.SqmJpaCriteriaParameterWrapper;
import org.hibernate.query.sqm.tree.expression.SqmLiteral;
import org.hibernate.query.sqm.tree.expression.SqmLiteralEntityType;
import org.hibernate.query.sqm.tree.expression.SqmLiteralNull;
import org.hibernate.query.sqm.tree.expression.SqmNamedParameter;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.query.sqm.tree.expression.SqmPathEntityType;
import org.hibernate.query.sqm.tree.expression.SqmPositionalParameter;
import org.hibernate.query.sqm.tree.expression.SqmStar;
import org.hibernate.query.sqm.tree.expression.SqmSummarization;
import org.hibernate.query.sqm.tree.expression.SqmToDuration;
import org.hibernate.query.sqm.tree.expression.SqmTrimSpecification;
import org.hibernate.query.sqm.tree.expression.SqmTuple;
import org.hibernate.query.sqm.tree.expression.SqmUnaryOperation;
import org.hibernate.query.sqm.tree.from.SqmAttributeJoin;
import org.hibernate.query.sqm.tree.from.SqmCrossJoin;
import org.hibernate.query.sqm.tree.from.SqmEntityJoin;
import org.hibernate.query.sqm.tree.from.SqmFrom;
import org.hibernate.query.sqm.tree.from.SqmFromClause;
import org.hibernate.query.sqm.tree.from.SqmJoin;
import org.hibernate.query.sqm.tree.from.SqmRoot;
import org.hibernate.query.sqm.tree.insert.SqmInsertSelectStatement;
import org.hibernate.query.sqm.tree.insert.SqmInsertValuesStatement;
import org.hibernate.query.sqm.tree.insert.SqmValues;
import org.hibernate.query.sqm.tree.predicate.SqmAndPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmBetweenPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmBooleanExpressionPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmComparisonPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmEmptinessPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmExistsPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmGroupedPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmInListPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmInSubQueryPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmLikePredicate;
import org.hibernate.query.sqm.tree.predicate.SqmMemberOfPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmNegatedPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmNullnessPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmOrPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmWhereClause;
import org.hibernate.query.sqm.tree.select.SqmDynamicInstantiation;
import org.hibernate.query.sqm.tree.select.SqmDynamicInstantiationArgument;
import org.hibernate.query.sqm.tree.select.SqmDynamicInstantiationTarget;
import org.hibernate.query.sqm.tree.select.SqmOrderByClause;
import org.hibernate.query.sqm.tree.select.SqmQueryGroup;
import org.hibernate.query.sqm.tree.select.SqmQueryPart;
import org.hibernate.query.sqm.tree.select.SqmQuerySpec;
import org.hibernate.query.sqm.tree.select.SqmSelectClause;
import org.hibernate.query.sqm.tree.select.SqmSelectStatement;
import org.hibernate.query.sqm.tree.select.SqmSelection;
import org.hibernate.query.sqm.tree.select.SqmSortSpecification;
import org.hibernate.query.sqm.tree.select.SqmSubQuery;
import org.hibernate.query.sqm.tree.update.SqmAssignment;
import org.hibernate.query.sqm.tree.update.SqmSetClause;
import org.hibernate.query.sqm.tree.update.SqmUpdateStatement;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlAstJoinType;
import org.hibernate.sql.ast.SqlTreeCreationLogger;
import org.hibernate.sql.ast.spi.FromClauseAccess;
import org.hibernate.sql.ast.spi.SqlAliasBase;
import org.hibernate.sql.ast.spi.SqlAliasBaseGenerator;
import org.hibernate.sql.ast.spi.SqlAliasBaseManager;
import org.hibernate.sql.ast.spi.SqlAstCreationContext;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlAstProcessingState;
import org.hibernate.sql.ast.spi.SqlAstQueryPartProcessingState;
import org.hibernate.sql.ast.spi.SqlAstTreeHelper;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.cte.CteColumn;
import org.hibernate.sql.ast.tree.cte.CteStatement;
import org.hibernate.sql.ast.tree.cte.CteTable;
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
import org.hibernate.sql.ast.tree.expression.QueryLiteral;
import org.hibernate.sql.ast.tree.expression.SelfRenderingExpression;
import org.hibernate.sql.ast.tree.expression.SqlSelectionExpression;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.Star;
import org.hibernate.sql.ast.tree.expression.Summarization;
import org.hibernate.sql.ast.tree.expression.TrimSpecification;
import org.hibernate.sql.ast.tree.expression.UnaryOperation;
import org.hibernate.sql.ast.tree.from.CorrelatedTableGroup;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.from.TableGroupJoinProducer;
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
import org.hibernate.sql.ast.tree.update.Assignable;
import org.hibernate.sql.ast.tree.update.Assignment;
import org.hibernate.sql.ast.tree.update.UpdateStatement;
import org.hibernate.sql.exec.internal.JdbcParameterImpl;
import org.hibernate.sql.exec.internal.JdbcParametersImpl;
import org.hibernate.sql.exec.spi.JdbcParameters;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.EntityGraphTraversalState;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.graph.Fetchable;
import org.hibernate.sql.results.graph.entity.EntityResultGraphNode;
import org.hibernate.sql.results.graph.instantiation.internal.DynamicInstantiation;
import org.hibernate.sql.results.internal.SqlSelectionImpl;
import org.hibernate.sql.results.internal.StandardEntityGraphTraversalStateImpl;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.spi.TypeConfiguration;

import org.jboss.logging.Logger;

import static org.hibernate.internal.util.NullnessHelper.coalesceSuppliedValues;
import static org.hibernate.query.BinaryArithmeticOperator.MULTIPLY;
import static org.hibernate.query.BinaryArithmeticOperator.SUBTRACT;
import static org.hibernate.query.TemporalUnit.DAY;
import static org.hibernate.query.TemporalUnit.NANOSECOND;
import static org.hibernate.query.TemporalUnit.NATIVE;
import static org.hibernate.query.UnaryArithmeticOperator.UNARY_MINUS;
import static org.hibernate.type.spi.TypeConfiguration.isDuration;

/**
 * @author Steve Ebersole
 */
public abstract class BaseSqmToSqlAstConverter<T extends Statement> extends BaseSemanticQueryWalker
		implements SqmTranslator<T>, DomainResultCreationState {

	private static final Logger log = Logger.getLogger( BaseSqmToSqlAstConverter.class );

	protected enum Shallowness {
		NONE,
		CTOR,
		FUNCTION,
		SUBQUERY
	}

	private final SqlAstCreationContext creationContext;
	private final SqmStatement<?> statement;

	private final QueryOptions queryOptions;
	private final LoadQueryInfluencers loadQueryInfluencers;

	private final DomainParameterXref domainParameterXref;
	private final QueryParameterBindings domainParameterBindings;
	private final Map<JpaCriteriaParameter<?>, Supplier<SqmJpaCriteriaParameterWrapper<?>>> jpaCriteriaParamResolutions;
	private final List<DomainResult> domainResults;
	private final EntityGraphTraversalState entityGraphTraversalState;

	private int fetchDepth;

	private Map<String, FilterPredicate> collectionFilterPredicates;
	private OrderByFragmentConsumer orderByFragmentConsumer;

	private Map<String, NavigablePath> joinPathBySqmJoinFullPath = new HashMap<>();

	private final SqlAliasBaseManager sqlAliasBaseManager = new SqlAliasBaseManager();
	private final Stack<SqlAstProcessingState> processingStateStack = new StandardStack<>();
	private final Stack<FromClauseIndex> fromClauseIndexStack = new StandardStack<>();
	private SqlAstProcessingState lastPoppedProcessingState;
	private FromClauseIndex lastPoppedFromClauseIndex;

	private final Stack<Clause> currentClauseStack = new StandardStack<>();
	private final Stack<Shallowness> shallownessStack = new StandardStack<>( Shallowness.NONE );

	private SqmByUnit appliedByUnit;
	private Expression adjustedTimestamp;
	private SqmExpressable<?> adjustedTimestampType; //TODO: remove this once we can get a Type directly from adjustedTimestamp
	private Expression adjustmentScale;
	private boolean negativeAdjustment;

	public BaseSqmToSqlAstConverter(
			SqlAstCreationContext creationContext,
			SqmStatement<?> statement,
			QueryOptions queryOptions,
			LoadQueryInfluencers loadQueryInfluencers,
			DomainParameterXref domainParameterXref,
			QueryParameterBindings domainParameterBindings) {
		super( creationContext.getServiceRegistry() );

		this.creationContext = creationContext;
		this.statement = statement;

		if ( statement instanceof SqmSelectStatement<?> ) {
			this.domainResults = new ArrayList<>(
					( (SqmSelectStatement<?>) statement ).getQuerySpec()
							.getSelectClause()
							.getSelectionItems()
							.size()
			);

			final AppliedGraph appliedGraph = queryOptions.getAppliedGraph();
			if ( appliedGraph != null && appliedGraph.getSemantic() != null && appliedGraph.getGraph() != null ) {
				this.entityGraphTraversalState = new StandardEntityGraphTraversalStateImpl(
						appliedGraph.getSemantic(), appliedGraph.getGraph() );
			}
			else {
				this.entityGraphTraversalState = null;
			}
		}
		else if ( statement instanceof SqmInsertSelectStatement ) {
			this.domainResults = new ArrayList<>(
					( (SqmInsertSelectStatement<?>) statement ).getSelectQueryPart()
							.getFirstQuerySpec()
							.getSelectClause()
							.getSelectionItems()
							.size()
			);
			this.entityGraphTraversalState = null;
		}
		else {
			this.domainResults = null;
			this.entityGraphTraversalState = null;
		}

		this.queryOptions = queryOptions;
		this.loadQueryInfluencers = loadQueryInfluencers;
		this.domainParameterXref = domainParameterXref;
		this.domainParameterBindings = domainParameterBindings;
		this.jpaCriteriaParamResolutions = domainParameterXref.getParameterResolutions()
				.getJpaCriteriaParamResolutions();
	}

	protected Stack<SqlAstProcessingState> getProcessingStateStack() {
		return processingStateStack;
	}

	protected void pushProcessingState(SqlAstProcessingState processingState) {
		pushProcessingState( processingState, new FromClauseIndex( getFromClauseIndex() ) );
	}

	protected void pushProcessingState(SqlAstProcessingState processingState, FromClauseIndex fromClauseIndex) {
		fromClauseIndexStack.push( fromClauseIndex );
		processingStateStack.push( processingState );
	}
	
	protected void popProcessingStateStack() {
		lastPoppedFromClauseIndex = fromClauseIndexStack.pop();
		lastPoppedProcessingState = processingStateStack.pop();
	}

	protected SqmStatement<?> getStatement() {
		return statement;
	}
	
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// FromClauseAccess

	@Override
	public TableGroup findTableGroup(NavigablePath navigablePath) {
		return getFromClauseAccess().findTableGroup( navigablePath );
	}

	@Override
	public ModelPart resolveModelPart(NavigablePath navigablePath) {
		// again, assume that the path refers to a TableGroup
		return getFromClauseAccess().findTableGroup( navigablePath ).getModelPart();
	}

	@Override
	public void registerTableGroup(NavigablePath navigablePath, TableGroup tableGroup) {
		throw new UnsupportedOperationException();
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SqlAstCreationState

	@Override
	public SqlAstCreationContext getCreationContext() {
		return creationContext;
	}

	@Override
	public SqlAstProcessingState getCurrentProcessingState() {
		return processingStateStack.getCurrent();
	}

	@Override
	public SqlExpressionResolver getSqlExpressionResolver() {
		return getCurrentProcessingState().getSqlExpressionResolver();
	}

	@Override
	public SqlAliasBaseGenerator getSqlAliasBaseGenerator() {
		return sqlAliasBaseManager;
	}

	@Override
	public LockMode determineLockMode(String identificationVariable) {
		final LockOptions lockOptions = getQueryOptions().getLockOptions();
		return lockOptions.getScope() || identificationVariable == null
				? lockOptions.getLockMode()
				: lockOptions.getEffectiveLockMode( identificationVariable );
	}

	public QueryOptions getQueryOptions() {
		return queryOptions;
	}

	public LoadQueryInfluencers getLoadQueryInfluencers() {
		return loadQueryInfluencers;
	}

	public FromClauseIndex getFromClauseIndex() {
		return (FromClauseIndex) getFromClauseAccess();
	}

	@Override
	public FromClauseAccess getFromClauseAccess() {
		final FromClauseIndex fromClauseIndex = fromClauseIndexStack.getCurrent();
		if ( fromClauseIndex == null ) {
			return lastPoppedFromClauseIndex;
		}
		else {
			return fromClauseIndex;
		}
	}

	@Override
	public Stack<Clause> getCurrentClauseStack() {
		return currentClauseStack;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Statements

	@Override
	public SqmTranslation<T> translate() {
		final SqmStatement<?> sqmStatement = getStatement();
		final T statement = (T) sqmStatement.accept( this );
		return new StandardSqmTranslation<>(
				statement,
				getJdbcParamsBySqmParam(),
				lastPoppedProcessingState.getSqlExpressionResolver(),
				getFromClauseAccess()
		);
	}

	@Override
	public Statement visitStatement(SqmStatement<?> sqmStatement) {
		return (Statement) sqmStatement.accept( this );
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Update statement

	@Override
	public UpdateStatement visitUpdateStatement(SqmUpdateStatement sqmStatement) {
		Map<String, CteStatement> cteStatements = this.visitCteContainer( sqmStatement );

		final String entityName = sqmStatement.getTarget().getEntityName();
		final EntityPersister entityDescriptor = getCreationContext().getDomainModel()
				.getEntityDescriptor( entityName );
		assert entityDescriptor != null;

		pushProcessingState(
				new SqlAstProcessingStateImpl(
						getCurrentProcessingState(),
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

			if ( !rootTableGroup.getTableReferenceJoins().isEmpty() ) {
				throw new HibernateException( "Not expecting multiple table references for an SQM DELETE" );
			}

			getFromClauseAccess().registerTableGroup( rootPath, rootTableGroup );

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
					sqmStatement.isWithRecursive(), cteStatements,
					rootTableGroup.getPrimaryTableReference(),
					assignments,
					SqlAstTreeHelper.combinePredicates( suppliedPredicate, additionalRestrictions ),
					Collections.emptyList()
			);
		}
		finally {
			popProcessingStateStack();
		}
	}

	@Override
	public List<Assignment> visitSetClause(SqmSetClause setClause) {
		final List<Assignment> assignments = new ArrayList<>();

		for ( SqmAssignment sqmAssignment : setClause.getAssignments() ) {
			final List<ColumnReference> targetColumnReferences = new ArrayList<>();

			pushProcessingState(
					new SqlAstProcessingStateImpl(
							getCurrentProcessingState(),
							this,
							getCurrentClauseStack()::getCurrent
					) {
						@Override
						public Expression resolveSqlExpression(
								String key,
								Function<SqlAstProcessingState, Expression> creator) {
							final Expression expression = getParentState().getSqlExpressionResolver()
									.resolveSqlExpression( key, creator );
							assert expression instanceof ColumnReference;

							targetColumnReferences.add( (ColumnReference) expression );

							return expression;
						}
					},
					getFromClauseIndex()
			);

			final SqmPathInterpretation assignedPathInterpretation;
			try {
				assignedPathInterpretation = (SqmPathInterpretation) sqmAssignment.getTargetPath().accept( this );
			}
			finally {
				popProcessingStateStack();
			}

			inferableTypeAccessStack.push( assignedPathInterpretation::getExpressionType );

			final List<ColumnReference> valueColumnReferences = new ArrayList<>();
			pushProcessingState(
					new SqlAstProcessingStateImpl(
							getCurrentProcessingState(),
							this,
							getCurrentClauseStack()::getCurrent
					) {
						@Override
						public Expression resolveSqlExpression(
								String key,
								Function<SqlAstProcessingState, Expression> creator) {
							final Expression expression = getParentState().getSqlExpressionResolver()
									.resolveSqlExpression( key, creator );
							assert expression instanceof ColumnReference;

							valueColumnReferences.add( (ColumnReference) expression );

							return expression;
						}
					},
					getFromClauseIndex()
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

					for ( ColumnReference columnReference : targetColumnReferences ) {
						assignments.add(
								new Assignment( columnReference, valueExpression )
						);
					}
				}
			}
			finally {
				popProcessingStateStack();
				inferableTypeAccessStack.pop();
			}

		}

		return assignments;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Delete statement

	@Override
	public DeleteStatement visitDeleteStatement(SqmDeleteStatement<?> statement) {
		Map<String, CteStatement> cteStatements = this.visitCteContainer( statement );

		final String entityName = statement.getTarget().getEntityName();
		final EntityPersister entityDescriptor = getCreationContext().getDomainModel()
				.getEntityDescriptor( entityName );
		assert entityDescriptor != null;

		pushProcessingState(
				new SqlAstProcessingStateImpl(
						getCurrentProcessingState(),
						this,
						getCurrentClauseStack()::getCurrent
				)
		);

		try {
			final NavigablePath rootPath = statement.getTarget().getNavigablePath();
			final TableGroup rootTableGroup = entityDescriptor.createRootTableGroup(
					rootPath,
					statement.getRoot().getAlias(),
					false,
					LockMode.WRITE,
					stem -> getSqlAliasBaseGenerator().createSqlAliasBase( stem ),
					getSqlExpressionResolver(),
					() -> predicate -> additionalRestrictions = predicate,
					getCreationContext()
			);
			getFromClauseAccess().registerTableGroup( rootPath, rootTableGroup );

			if ( !rootTableGroup.getTableReferenceJoins().isEmpty() ) {
				throw new HibernateException( "Not expecting multiple table references for an SQM DELETE" );
			}

			final FilterPredicate filterPredicate = FilterHelper.createFilterPredicate(
					getLoadQueryInfluencers(),
					(Joinable) entityDescriptor
			);
			if ( filterPredicate != null ) {
				additionalRestrictions = SqlAstTreeHelper.combinePredicates( additionalRestrictions, filterPredicate );
			}

			Predicate suppliedPredicate = null;
			final SqmWhereClause whereClause = statement.getWhereClause();
			if ( whereClause != null && whereClause.getPredicate() != null ) {
				getCurrentClauseStack().push( Clause.WHERE );
				try {
					suppliedPredicate = (Predicate) whereClause.getPredicate().accept( this );
				}
				finally {
					getCurrentClauseStack().pop();
				}
			}

			return new DeleteStatement(
					statement.isWithRecursive(),
					cteStatements,
					rootTableGroup.getPrimaryTableReference(),
					SqlAstTreeHelper.combinePredicates( suppliedPredicate, additionalRestrictions ),
					Collections.emptyList()
			);
		}
		finally {
			popProcessingStateStack();
		}
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Insert-select statement

	@Override
	public InsertStatement visitInsertSelectStatement(SqmInsertSelectStatement<?> sqmStatement) {
		Map<String, CteStatement> cteStatements = this.visitCteContainer( sqmStatement );

		final String entityName = sqmStatement.getTarget().getEntityName();
		final EntityPersister entityDescriptor = getCreationContext().getDomainModel()
				.getEntityDescriptor( entityName );
		assert entityDescriptor != null;

		SqmQueryPart<?> selectQueryPart = sqmStatement.getSelectQueryPart();
		pushProcessingState(
				new SqlAstProcessingStateImpl(
						null,
						this,
						r -> new SqlSelectionForSqmSelectionResolver(
								r,
								selectQueryPart.getFirstQuerySpec()
										.getSelectClause()
										.getSelectionItems()
										.size()
						),
						getCurrentClauseStack()::getCurrent
				)
		);

		try {
			final NavigablePath rootPath = sqmStatement.getTarget().getNavigablePath();
			final TableGroup rootTableGroup = entityDescriptor.createRootTableGroup(
					rootPath,
					sqmStatement.getTarget().getExplicitAlias(),
					false,
					LockMode.WRITE,
					stem -> getSqlAliasBaseGenerator().createSqlAliasBase( stem ),
					getSqlExpressionResolver(),
					() -> predicate -> additionalRestrictions = predicate,
					getCreationContext()
			);

			if ( !rootTableGroup.getTableReferenceJoins().isEmpty()
					|| !rootTableGroup.getTableGroupJoins().isEmpty() ) {
				throw new HibernateException( "Not expecting multiple table references for an SQM INSERT-SELECT" );
			}

			getFromClauseAccess().registerTableGroup( rootPath, rootTableGroup );

			final InsertStatement insertStatement = new InsertStatement(
					sqmStatement.isWithRecursive(),
					cteStatements,
					rootTableGroup.getPrimaryTableReference(),
					Collections.emptyList()
			);

			List<SqmPath> targetPaths = sqmStatement.getInsertionTargetPaths();
			for ( SqmPath target : targetPaths ) {
				Assignable assignable = (Assignable) target.accept( this );
				insertStatement.addTargetColumnReferences( assignable.getColumnReferences() );
			}

			insertStatement.setSourceSelectStatement(
					visitQueryPart( selectQueryPart )
			);

			return insertStatement;
		}
		finally {
			popProcessingStateStack();
		}
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Insert-values statement

	@Override
	public InsertStatement visitInsertValuesStatement(SqmInsertValuesStatement<?> sqmStatement) {
		Map<String, CteStatement> cteStatements = this.visitCteContainer( sqmStatement );
		final String entityName = sqmStatement.getTarget().getEntityName();
		final EntityPersister entityDescriptor = getCreationContext().getDomainModel()
				.getEntityDescriptor( entityName );
		assert entityDescriptor != null;

		pushProcessingState(
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
					sqmStatement.getTarget().getExplicitAlias(),
					false,
					LockMode.WRITE,
					stem -> getSqlAliasBaseGenerator().createSqlAliasBase( stem ),
					getSqlExpressionResolver(),
					() -> predicate -> additionalRestrictions = predicate,
					getCreationContext()
			);

			if ( !rootTableGroup.getTableReferenceJoins().isEmpty()
					|| !rootTableGroup.getTableGroupJoins().isEmpty() ) {
				throw new HibernateException( "Not expecting multiple table references for an SQM INSERT-SELECT" );
			}

			getFromClauseAccess().registerTableGroup( rootPath, rootTableGroup );

			final InsertStatement insertValuesStatement = new InsertStatement(
					sqmStatement.isWithRecursive(),
					cteStatements,
					rootTableGroup.getPrimaryTableReference(),
					Collections.emptyList()
			);

			List<SqmPath> targetPaths = sqmStatement.getInsertionTargetPaths();
			for ( SqmPath target : targetPaths ) {
				Assignable assignable = (Assignable) target.accept( this );
				insertValuesStatement.addTargetColumnReferences( assignable.getColumnReferences() );
			}

			List<SqmValues> valuesList = sqmStatement.getValuesList();
			for ( SqmValues sqmValues : valuesList ) {
				insertValuesStatement.getValuesList().add( visitValues( sqmValues ) );
			}

			return insertValuesStatement;
		}
		finally {
			popProcessingStateStack();
		}
	}

	@Override
	public Values visitValues(SqmValues sqmValues) {
		Values values = new Values();
		//noinspection rawtypes
		for ( SqmExpression expression : sqmValues.getExpressions() ) {
			values.getExpressions().add( (Expression) expression.accept( this ) );
		}
		return values;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Select statement

	@Override
	public SelectStatement visitSelectStatement(SqmSelectStatement<?> statement) {
		Map<String, CteStatement> cteStatements = this.visitCteContainer( statement );
		final QueryPart queryPart = visitQueryPart( statement.getQueryPart() );
		return new SelectStatement( statement.isWithRecursive(), cteStatements, queryPart, domainResults );
	}

	@Override
	public DynamicInstantiation<?> visitDynamicInstantiation(SqmDynamicInstantiation<?> sqmDynamicInstantiation) {
		final SqmDynamicInstantiationTarget<?> instantiationTarget = sqmDynamicInstantiation.getInstantiationTarget();
		final DynamicInstantiationNature instantiationNature = instantiationTarget.getNature();
		final JavaTypeDescriptor<Object> targetTypeDescriptor = interpretInstantiationTarget( instantiationTarget );

		final DynamicInstantiation<?> dynamicInstantiation = new DynamicInstantiation<>(
				instantiationNature,
				targetTypeDescriptor
		);

		for ( SqmDynamicInstantiationArgument<?> sqmArgument : sqmDynamicInstantiation.getArguments() ) {
			final DomainResultProducer<?> argumentResultProducer = (DomainResultProducer<?>) sqmArgument.getSelectableNode()
					.accept( this );
			dynamicInstantiation.addArgument(
					sqmArgument.getAlias(),
					argumentResultProducer
			);
		}

		dynamicInstantiation.complete();

		return dynamicInstantiation;
	}

	@SuppressWarnings("unchecked")
	private <T> JavaTypeDescriptor<T> interpretInstantiationTarget(SqmDynamicInstantiationTarget<?> instantiationTarget) {
		final Class<T> targetJavaType;

		if ( instantiationTarget.getNature() == DynamicInstantiationNature.LIST ) {
			targetJavaType = (Class<T>) List.class;
		}
		else if ( instantiationTarget.getNature() == DynamicInstantiationNature.MAP ) {
			targetJavaType = (Class<T>) Map.class;
		}
		else {
			targetJavaType = instantiationTarget.getJavaType();
		}

		return getCreationContext().getDomainModel()
				.getTypeConfiguration()
				.getJavaTypeDescriptorRegistry()
				.getDescriptor( targetJavaType );
	}

	@Override
	public CteStatement visitCteStatement(SqmCteStatement<?> sqmCteStatement) {
		final CteTable cteTable = createCteTable(
				sqmCteStatement.getCteTable(),
				getCreationContext().getSessionFactory()
		);

		return new CteStatement(
				cteTable,
				visitStatement( sqmCteStatement.getCteDefinition() ),
				sqmCteStatement.getSearchClauseKind(),
				visitSearchBySpecifications( cteTable, sqmCteStatement.getSearchBySpecifications() ),
				visitCycleColumns( cteTable, sqmCteStatement.getCycleColumns() ),
				findCteColumn( cteTable, sqmCteStatement.getCycleMarkColumn() ),
				sqmCteStatement.getCycleValue(),
				sqmCteStatement.getNoCycleValue()
		);
	}

	protected List<SearchClauseSpecification> visitSearchBySpecifications(
			CteTable cteTable,
			List<SqmSearchClauseSpecification> searchBySpecifications) {
		if ( searchBySpecifications == null || searchBySpecifications.isEmpty() ) {
			return null;
		}
		final int size = searchBySpecifications.size();
		final List<SearchClauseSpecification> searchClauseSpecifications = new ArrayList<>( size );
		for ( int i = 0; i < size; i++ ) {
			final SqmSearchClauseSpecification specification = searchBySpecifications.get( i );
			forEachCteColumn(
					cteTable,
					specification.getCteColumn(),
					cteColumn -> {
						searchClauseSpecifications.add(
								new SearchClauseSpecification(
										cteColumn,
										specification.getSortOrder(),
										specification.getNullPrecedence()
								)
						);
					}
			);
		}

		return searchClauseSpecifications;
	}

	protected CteColumn findCteColumn(CteTable cteTable, SqmCteTableColumn cteColumn) {
		if ( cteColumn == null ) {
			return null;
		}
		final List<CteColumn> cteColumns = cteTable.getCteColumns();
		final int size = cteColumns.size();
		for ( int i = 0; i < size; i++ ) {
			final CteColumn column = cteColumns.get( i );
			if ( cteColumn.getColumnName().equals( column.getColumnExpression() ) ) {
				return column;
			}
		}
		throw new IllegalArgumentException(
				String.format(
						"Couldn't find cte column %s in cte %s!",
						cteColumn.getColumnName(),
						cteTable.getTableExpression()
				)
		);
	}

	protected void forEachCteColumn(CteTable cteTable, SqmCteTableColumn cteColumn, Consumer<CteColumn> consumer) {
		final List<CteColumn> cteColumns = cteTable.getCteColumns();
		final int size = cteColumns.size();
		for ( int i = 0; i < size; i++ ) {
			final CteColumn column = cteColumns.get( i );
			if ( cteColumn.getColumnName().equals( column.getColumnExpression() ) ) {
				consumer.accept( column );
			}
		}
	}

	protected List<CteColumn> visitCycleColumns(CteTable cteTable, List<SqmCteTableColumn> cycleColumns) {
		if ( cycleColumns == null || cycleColumns.isEmpty() ) {
			return null;
		}
		final int size = cycleColumns.size();
		final List<CteColumn> columns = new ArrayList<>( size );
		for ( int i = 0; i < size; i++ ) {
			forEachCteColumn(
					cteTable,
					cycleColumns.get( i ),
					columns::add
			);
		}
		return columns;
	}

	public static CteTable createCteTable(SqmCteTable sqmCteTable, SessionFactoryImplementor factory) {
		final List<SqmCteTableColumn> sqmCteColumns = sqmCteTable.getColumns();
		final List<CteColumn> sqlCteColumns = new ArrayList<>( sqmCteColumns.size() );

		for ( int i = 0; i < sqmCteColumns.size(); i++ ) {
			final SqmCteTableColumn sqmCteTableColumn = sqmCteColumns.get( i );
			ModelPart modelPart = sqmCteTableColumn.getType();
			if ( modelPart instanceof Association ) {
				modelPart = ( (Association) modelPart ).getForeignKeyDescriptor();
			}
			if ( modelPart instanceof EmbeddableValuedModelPart ) {
				modelPart.forEachJdbcType(
						(index, jdbcMapping) -> {
							sqlCteColumns.add(
									new CteColumn(
											sqmCteTableColumn.getColumnName() + "_" + index,
											jdbcMapping
									)
							);
						}
				);
			}
			else {
				sqlCteColumns.add(
						new CteColumn(
								sqmCteTableColumn.getColumnName(),
								( (BasicValuedMapping) modelPart ).getJdbcMapping()
						)
				);
			}
		}

		return new CteTable(
				sqmCteTable.getCteName(),
				sqlCteColumns,
				factory
		);
	}

	@Override
	public Map<String, CteStatement> visitCteContainer(SqmCteContainer consumer) {
		final Collection<SqmCteStatement<?>> sqmCteStatements = consumer.getCteStatements();
		final Map<String, CteStatement> cteStatements = new LinkedHashMap<>( sqmCteStatements.size() );
		for ( SqmCteStatement<?> sqmCteStatement : sqmCteStatements ) {
			final CteStatement cteStatement = visitCteStatement( sqmCteStatement );
			cteStatements.put( cteStatement.getCteTable().getTableExpression(), cteStatement );
		}
		return cteStatements;
	}

	@Override
	public QueryPart visitQueryPart(SqmQueryPart<?> queryPart) {
		return (QueryPart) super.visitQueryPart( queryPart );
	}

	@Override
	public QueryGroup visitQueryGroup(SqmQueryGroup<?> queryGroup) {
		final List<? extends SqmQueryPart<?>> queryParts = queryGroup.getQueryParts();
		final int size = queryParts.size();
		final List<QueryPart> newQueryParts = new ArrayList<>( size );
		final QueryGroup group = new QueryGroup(
				getProcessingStateStack().isEmpty(),
				queryGroup.getSetOperator(),
				newQueryParts
		);
		final SqlAstQueryPartProcessingStateImpl processingState = new SqlAstQueryPartProcessingStateImpl(
				group,
				getCurrentProcessingState(),
				this,
				DelegatingSqlSelectionForSqmSelectionCollector::new,
				currentClauseStack::getCurrent
		);
		final DelegatingSqlSelectionForSqmSelectionCollector collector = (DelegatingSqlSelectionForSqmSelectionCollector) processingState
				.getSqlExpressionResolver();
		pushProcessingState( processingState );

		try {
			newQueryParts.add( visitQueryPart( queryParts.get( 0 ) ) );
			collector.setSqlSelectionForSqmSelectionCollector(
					(SqlSelectionForSqmSelectionCollector) lastPoppedProcessingState.getSqlExpressionResolver()
			);
			for ( int i = 1; i < size; i++ ) {
				newQueryParts.add( visitQueryPart( queryParts.get( i ) ) );
			}
			visitOrderByOffsetAndFetch( queryGroup, group );
			return group;
		}
		finally {
			popProcessingStateStack();
		}
	}

	@Override
	public QuerySpec visitQuerySpec(SqmQuerySpec<?> sqmQuerySpec) {
		final QuerySpec sqlQuerySpec = new QuerySpec(
				getProcessingStateStack().isEmpty(),
				sqmQuerySpec.getFromClause().getNumberOfRoots()
		);
		final SqmSelectClause selectClause = sqmQuerySpec.getSelectClause();

		Predicate originalAdditionalRestrictions = additionalRestrictions;
		additionalRestrictions = null;

		pushProcessingState(
				new SqlAstQueryPartProcessingStateImpl(
						sqlQuerySpec,
						getCurrentProcessingState(),
						this,
						r -> new SqlSelectionForSqmSelectionResolver(
								r,
								selectClause.getSelectionItems()
										.size()
						),
						currentClauseStack::getCurrent
				)
		);

		try {
			final boolean topLevel = sqlQuerySpec.isRoot();
			if ( topLevel ) {
				orderByFragmentConsumer = new StandardOrderByFragmentConsumer();
			}

			// we want to visit the from-clause first
			visitFromClause( sqmQuerySpec.getFromClause() );

			visitSelectClause( selectClause );

			final SqmWhereClause whereClause = sqmQuerySpec.getWhereClause();
			if ( whereClause != null && whereClause.getPredicate() != null ) {
				currentClauseStack.push( Clause.WHERE );
				try {
					sqlQuerySpec.applyPredicate( (Predicate) whereClause.getPredicate().accept( this ) );
				}
				finally {
					currentClauseStack.pop();
				}
			}

			sqlQuerySpec.setGroupByClauseExpressions( visitGroupByClause( sqmQuerySpec.getGroupByClauseExpressions() ) );
			if ( sqmQuerySpec.getHavingClausePredicate() != null ) {
				sqlQuerySpec.setHavingClauseRestrictions( visitHavingClause( sqmQuerySpec.getHavingClausePredicate() ) );
			}

			visitOrderByOffsetAndFetch( sqmQuerySpec, sqlQuerySpec );

			if ( topLevel && statement instanceof SqmSelectStatement<?> ) {
				orderByFragmentConsumer.visitFragments(
						(orderByFragment, tableGroup) -> {
							orderByFragment.apply( sqlQuerySpec, tableGroup, this );
						}
				);
				orderByFragmentConsumer = null;
				applyCollectionFilterPredicates( sqlQuerySpec );
			}

			joinPathBySqmJoinFullPath.clear();
			return sqlQuerySpec;
		}
		finally {
			if ( additionalRestrictions != null ) {
				sqlQuerySpec.applyPredicate( additionalRestrictions );
			}
			additionalRestrictions = originalAdditionalRestrictions;
			popProcessingStateStack();
		}
	}

	protected void visitOrderByOffsetAndFetch(SqmQueryPart<?> sqmQueryPart, QueryPart sqlQueryPart) {
		if ( sqmQueryPart.getOrderByClause() != null ) {
			currentClauseStack.push( Clause.ORDER );
			try {
				for ( SqmSortSpecification sortSpecification : sqmQueryPart.getOrderByClause()
						.getSortSpecifications() ) {
					sqlQueryPart.addSortSpecification( visitSortSpecification( sortSpecification ) );
				}
			}
			finally {
				currentClauseStack.pop();
			}
		}

		sqlQueryPart.setOffsetClauseExpression( visitOffsetExpression( sqmQueryPart.getOffsetExpression() ) );
		sqlQueryPart.setFetchClauseExpression(
				visitFetchExpression( sqmQueryPart.getFetchExpression() ),
				sqmQueryPart.getFetchClauseType()
		);
	}

	private interface OrderByFragmentConsumer {
		void accept(OrderByFragment orderByFragment, TableGroup tableGroup);

		void visitFragments(BiConsumer<OrderByFragment,TableGroup> consumer);
	}

	private static class StandardOrderByFragmentConsumer implements OrderByFragmentConsumer {
		private Map<OrderByFragment, TableGroup> fragments;

		@Override
		public void accept(OrderByFragment orderByFragment, TableGroup tableGroup) {
			if ( fragments == null ) {
				fragments = new LinkedHashMap<>();
			}
			fragments.put( orderByFragment, tableGroup );
		}

		@Override
		public void visitFragments(BiConsumer<OrderByFragment, TableGroup> consumer) {
			if ( fragments == null || fragments.isEmpty() ) {
				return;
			}

			fragments.forEach( consumer );
		}
	}

	protected void applyCollectionFilterPredicates(QuerySpec sqlQuerySpec) {
		final List<TableGroup> roots = sqlQuerySpec.getFromClause().getRoots();
		if ( roots != null && roots.size() == 1 ) {
			final TableGroup root = roots.get( 0 );
			final ModelPartContainer modelPartContainer = root.getModelPart();
			final EntityPersister entityPersister = modelPartContainer.findContainingEntityMapping().getEntityPersister();
			assert entityPersister instanceof Joinable;
			final FilterPredicate filterPredicate = FilterHelper.createFilterPredicate(
					getLoadQueryInfluencers(), (Joinable) entityPersister, root
			);
			if ( filterPredicate != null ) {
				sqlQuerySpec.applyPredicate( filterPredicate );
			}
			if ( CollectionHelper.isNotEmpty( collectionFilterPredicates ) ) {
				root.getTableGroupJoins().forEach(
						tableGroupJoin -> {
							collectionFilterPredicates.forEach( (alias, predicate) -> {
								if ( tableGroupJoin.getJoinedGroup().getGroupAlias().equals( alias ) ) {
									tableGroupJoin.applyPredicate( predicate );
								}
							} );
						}
				);
			}
		}
	}

	@Override
	public SelectClause visitSelectClause(SqmSelectClause selectClause) {
		currentClauseStack.push( Clause.SELECT );
		shallownessStack.push( Shallowness.SUBQUERY );
		try {
			super.visitSelectClause( selectClause );

			final SelectClause sqlSelectClause = currentQuerySpec().getSelectClause();
			sqlSelectClause.makeDistinct( selectClause.isDistinct() );
			return sqlSelectClause;
		}
		finally {
			shallownessStack.pop();
			currentClauseStack.pop();
		}
	}

	@Override
	public Void visitSelection(SqmSelection sqmSelection) {
		currentSqlSelectionCollector().next();
		final DomainResultProducer resultProducer = resolveDomainResultProducer( sqmSelection );

		if ( domainResults != null ) {
			final Stack<SqlAstProcessingState> processingStateStack = getProcessingStateStack();
			final boolean collectDomainResults;
			if ( processingStateStack.depth() == 1) {
				collectDomainResults = true;
			}
			else {
				final SqlAstProcessingState current = processingStateStack.getCurrent();
				// Since we only want to create domain results for the first/left-most query spec within query groups,
				// we have to check if the current query spec is the left-most.
				// This is the case when all upper level in-flight query groups are still empty
				collectDomainResults = processingStateStack.findCurrentFirst(
						processingState -> {
							if ( !( processingState instanceof SqlAstQueryPartProcessingState ) ) {
								return Boolean.FALSE;
							}
							if ( processingState == current ) {
								return null;
							}
							final QueryPart part = ( (SqlAstQueryPartProcessingState) processingState ).getInflightQueryPart();
							if ( part instanceof QueryGroup ) {
								if ( ( (QueryGroup) part ).getQueryParts().isEmpty() ) {
									return null;
								}
							}
							return Boolean.FALSE;
						}
				) == null;
			}
			if ( collectDomainResults ) {
				final DomainResult domainResult = resultProducer.createDomainResult(
						sqmSelection.getAlias(),
						this
				);

				domainResults.add( domainResult );
			}
			else {
				resultProducer.applySqlSelections( this );
			}
		}
		return null;
	}

	private DomainResultProducer resolveDomainResultProducer(SqmSelection sqmSelection) {
		return (DomainResultProducer) sqmSelection.getSelectableNode().accept( this );
	}

	protected Expression resolveGroupOrOrderByExpression(SqmExpression<?> groupByClauseExpression) {
		if ( groupByClauseExpression instanceof SqmLiteral<?> ) {
			Object literal = ( (SqmLiteral<?>) groupByClauseExpression ).getLiteralValue();
			if ( literal instanceof Integer ) {
				// Integer literals have a special meaning in the GROUP BY and ORDER BY clause i.e. they refer to a select item
				final int sqmPosition = (Integer) literal;
				final List<SqlSelection> selections = currentSqlSelectionCollector().getSelections( sqmPosition );
				final List<Expression> expressions = new ArrayList<>( selections.size() );
				for ( SqlSelection selection : selections ) {
					expressions.add( new SqlSelectionExpression( selection ) );
				}
				return new SqlTuple( expressions, null );
			}
		}
		return (Expression) groupByClauseExpression.accept( this );
	}

	@Override
	public List<Expression> visitGroupByClause(List<SqmExpression<?>> groupByClauseExpressions) {
		if ( !groupByClauseExpressions.isEmpty() ) {
			currentClauseStack.push( Clause.GROUP );
			try {
				final List<Expression> expressions = new ArrayList<>( groupByClauseExpressions.size() );
				for ( SqmExpression<?> groupByClauseExpression : groupByClauseExpressions ) {
					expressions.add( resolveGroupOrOrderByExpression( groupByClauseExpression ) );
				}
				return expressions;
			}
			finally {
				currentClauseStack.pop();
			}
		}
		return Collections.emptyList();
	}

	@Override
	public Predicate visitHavingClause(SqmPredicate sqmPredicate) {
		if ( sqmPredicate == null ) {
			return null;
		}
		currentClauseStack.push( Clause.HAVING );
		try {
			return (Predicate) sqmPredicate.accept( this );
		}
		finally {
			currentClauseStack.pop();
		}
	}

	@Override
	public Void visitOrderByClause(SqmOrderByClause orderByClause) {
		super.visitOrderByClause( orderByClause );
		return null;
	}

	@Override
	public SortSpecification visitSortSpecification(SqmSortSpecification sortSpecification) {
		return new SortSpecification(
				resolveGroupOrOrderByExpression( sortSpecification.getSortExpression() ),
				null,
				sortSpecification.getSortOrder(),
				sortSpecification.getNullPrecedence()
		);
	}

	public QuerySpec visitOffsetAndFetchExpressions(QuerySpec sqlQuerySpec, SqmQuerySpec<?> sqmQuerySpec) {
		final Expression offsetExpression = visitOffsetExpression( sqmQuerySpec.getOffsetExpression() );
		final Expression fetchExpression = visitFetchExpression( sqmQuerySpec.getFetchExpression() );
		sqlQuerySpec.setOffsetClauseExpression( offsetExpression );
		sqlQuerySpec.setFetchClauseExpression( fetchExpression, sqmQuerySpec.getFetchClauseType() );
		return sqlQuerySpec;
	}

	@Override
	public Expression visitOffsetExpression(SqmExpression<?> expression) {
		if ( expression == null ) {
			return null;
		}

		currentClauseStack.push( Clause.OFFSET );
		try {
			return (Expression) expression.accept( this );
		}
		finally {
			currentClauseStack.pop();
		}
	}

	@Override
	public Expression visitFetchExpression(SqmExpression<?> expression) {
		if ( expression == null ) {
			return null;
		}

		currentClauseStack.push( Clause.FETCH );
		try {
			return (Expression) expression.accept( this );
		}
		finally {
			currentClauseStack.pop();
		}
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// FROM clause

	@Override
	public Void visitFromClause(SqmFromClause sqmFromClause) {
		currentClauseStack.push( Clause.FROM );

		try {
			sqmFromClause.visitRoots( this::consumeFromClauseRoot );
		}
		finally {
			currentClauseStack.pop();
		}

		return null;
	}

	protected Predicate additionalRestrictions;

	@SuppressWarnings("WeakerAccess")
	protected void consumeFromClauseRoot(SqmRoot<?> sqmRoot) {
		log.tracef( "Resolving SqmRoot [%s] to TableGroup", sqmRoot );
		final FromClauseIndex fromClauseIndex = getFromClauseIndex();
		if ( fromClauseIndex.isResolved( sqmRoot ) ) {
			log.tracef( "Already resolved SqmRoot [%s] to TableGroup", sqmRoot );
		}
		final SqlExpressionResolver sqlExpressionResolver = getSqlExpressionResolver();
		final TableGroup tableGroup;
		if ( sqmRoot.isCorrelated() ) {
			final SessionFactoryImplementor sessionFactory = creationContext.getSessionFactory();
			final TableGroup parentTableGroup = fromClauseIndex.findTableGroup(
					sqmRoot.getCorrelationParent().getNavigablePath()
			);
			final EntityPersister entityDescriptor = resolveEntityPersister( sqmRoot.getReferencedPathSource() );
			if ( sqmRoot.containsOnlyInnerJoins() ) {
				// If we have just inner joins against a correlated root, we can render the joins as references
				final SqlAliasBase sqlAliasBase = sqlAliasBaseManager.createSqlAliasBase( parentTableGroup.getGroupAlias() );
				tableGroup = new CorrelatedTableGroup(
						parentTableGroup,
						sqlAliasBase,
						currentQuerySpec(),
						predicate -> additionalRestrictions = SqlAstTreeHelper.combinePredicates(
								additionalRestrictions,
								predicate
						),
						sessionFactory
				);

				log.tracef( "Resolved SqmRoot [%s] to correlated TableGroup [%s]", sqmRoot, tableGroup );

				consumeExplicitJoins( sqmRoot, tableGroup );
				consumeImplicitJoins( sqmRoot, tableGroup );
				return;
			}
			else {
				// If we have non-inner joins against a correlated root, we must render the root with a correlation predicate
				tableGroup = entityDescriptor.createRootTableGroup(
						sqmRoot.getNavigablePath(),
						sqmRoot.getExplicitAlias(),
						true,
						LockMode.NONE,
						sqlAliasBaseManager,
						sqlExpressionResolver,
						() -> predicate -> {},
						creationContext
				);
				final EntityIdentifierMapping identifierMapping = entityDescriptor.getIdentifierMapping();
				final int jdbcTypeCount = identifierMapping.getJdbcTypeCount();
				if ( jdbcTypeCount == 1 ) {
					identifierMapping.forEachSelection(
							(selectionIndex, selectionMapping) -> {
								additionalRestrictions = SqlAstTreeHelper.combinePredicates(
										additionalRestrictions,
										new ComparisonPredicate(
												new ColumnReference(
														parentTableGroup.getTableReference( selectionMapping.getContainingTableExpression() ),
														selectionMapping,
														sessionFactory
												),
												ComparisonOperator.EQUAL,
												new ColumnReference(
														tableGroup.getTableReference( selectionMapping.getContainingTableExpression() ),
														selectionMapping,
														sessionFactory
												)
										)
								);
							}
					);
				}
				else {
					final List<Expression> lhs = new ArrayList<>( jdbcTypeCount );
					final List<Expression> rhs = new ArrayList<>( jdbcTypeCount );
					identifierMapping.forEachSelection(
							(selectionIndex, selectionMapping) -> {
								lhs.add(
										new ColumnReference(
												parentTableGroup.getTableReference( selectionMapping.getContainingTableExpression() ),
												selectionMapping,
												sessionFactory
										)
								);
								rhs.add(
										new ColumnReference(
												tableGroup.getTableReference( selectionMapping.getContainingTableExpression() ),
												selectionMapping,
												sessionFactory
										)
								);
							}
					);
					additionalRestrictions = SqlAstTreeHelper.combinePredicates(
							additionalRestrictions,
							new ComparisonPredicate(
									new SqlTuple( lhs, identifierMapping ),
									ComparisonOperator.EQUAL,
									new SqlTuple( rhs, identifierMapping )
							)
					);
				}
			}
		}
		else {
			final EntityPersister entityDescriptor = resolveEntityPersister( sqmRoot.getReferencedPathSource() );
			tableGroup = entityDescriptor.createRootTableGroup(
					sqmRoot.getNavigablePath(),
					sqmRoot.getExplicitAlias(),
					true,
					LockMode.NONE,
					sqlAliasBaseManager,
					sqlExpressionResolver,
					() -> predicate -> additionalRestrictions = SqlAstTreeHelper.combinePredicates(
							additionalRestrictions,
							predicate
					),
					creationContext
			);
		}

		log.tracef( "Resolved SqmRoot [%s] to new TableGroup [%s]", sqmRoot, tableGroup );

		fromClauseIndex.register( sqmRoot, tableGroup );
		currentQuerySpec().getFromClause().addRoot( tableGroup );

		consumeExplicitJoins( sqmRoot, tableGroup );
		consumeImplicitJoins( sqmRoot, tableGroup );
	}

	private EntityPersister resolveEntityPersister(EntityDomainType<?> entityDomainType) {
		return creationContext.getDomainModel().getEntityDescriptor( entityDomainType.getHibernateEntityName() );
	}

	protected void consumeExplicitJoins(SqmFrom<?, ?> sqmFrom, TableGroup lhsTableGroup) {
		if ( log.isTraceEnabled() ) {
			log.tracef( "Visiting explicit joins for `%s`", sqmFrom.getNavigablePath() );
		}
		sqmFrom.visitSqmJoins(
				sqmJoin -> consumeExplicitJoin( sqmJoin, lhsTableGroup )
		);
	}

	@SuppressWarnings("WeakerAccess")
	protected void consumeExplicitJoin(SqmJoin<?, ?> sqmJoin, TableGroup lhsTableGroup) {
		if ( sqmJoin instanceof SqmAttributeJoin ) {
			consumeAttributeJoin( ( (SqmAttributeJoin) sqmJoin ), lhsTableGroup );
		}
		else if ( sqmJoin instanceof SqmCrossJoin ) {
			consumeCrossJoin( ( (SqmCrossJoin) sqmJoin ), lhsTableGroup );
		}
		else if ( sqmJoin instanceof SqmEntityJoin ) {
			consumeEntityJoin( ( (SqmEntityJoin) sqmJoin ), lhsTableGroup );
		}
		else {
			throw new InterpretationException( "Could not resolve SqmJoin [" + sqmJoin.getNavigablePath() + "] to TableGroupJoin" );
		}
	}

	private void consumeAttributeJoin(SqmAttributeJoin<?, ?> sqmJoin, TableGroup lhsTableGroup) {

		final SqmPathSource<?> pathSource = sqmJoin.getReferencedPathSource();

		final TableGroupJoin joinedTableGroupJoin;
		final TableGroup joinedTableGroup;

		final NavigablePath sqmJoinNavigablePath = sqmJoin.getNavigablePath();
		final NavigablePath parentNavigablePath = sqmJoinNavigablePath.getParent();

		final ModelPart modelPart = lhsTableGroup.getModelPart().findSubPart(
				pathSource.getPathName(),
				SqmMappingModelHelper.resolveExplicitTreatTarget( sqmJoin, this )
		);

		final NavigablePath joinPath;
		if ( pathSource instanceof PluralPersistentAttribute ) {
			assert modelPart instanceof PluralAttributeMapping;

			final PluralAttributeMapping pluralAttributeMapping = (PluralAttributeMapping) modelPart;

			joinPath = getJoinNavigablePath(
					sqmJoinNavigablePath,
					parentNavigablePath,
					pluralAttributeMapping.getPartName()
			);

			joinPathBySqmJoinFullPath.put(
					sqmJoin.getNavigablePath().getFullPath(),
					joinPath.append( CollectionPart.Nature.ELEMENT.getName() )
			);

			joinedTableGroupJoin = pluralAttributeMapping.createTableGroupJoin(
					joinPath,
					lhsTableGroup,
					sqmJoin.getExplicitAlias(),
					sqmJoin.getSqmJoinType().getCorrespondingSqlJoinType(),
					determineLockMode( sqmJoin.getExplicitAlias() ),
					this
			);
		}
		else {
			assert modelPart instanceof TableGroupJoinProducer;

			joinPath = getJoinNavigablePath( sqmJoinNavigablePath, parentNavigablePath, modelPart.getPartName() );

			joinedTableGroupJoin = ( (TableGroupJoinProducer) modelPart ).createTableGroupJoin(
					joinPath,
					lhsTableGroup,
					sqmJoin.getExplicitAlias(),
					sqmJoin.getSqmJoinType().getCorrespondingSqlJoinType(),
					determineLockMode( sqmJoin.getExplicitAlias() ),
					this
			);
		}

		joinedTableGroup = joinedTableGroupJoin.getJoinedGroup();
		lhsTableGroup.addTableGroupJoin( joinedTableGroupJoin );

		getFromClauseIndex().register( sqmJoin, joinedTableGroup, joinPath );

		// add any additional join restrictions
		if ( sqmJoin.getJoinPredicate() != null ) {
			if ( sqmJoin.isFetched() ) {
				QueryLogging.QUERY_MESSAGE_LOGGER.debugf( "Join fetch [" + sqmJoinNavigablePath + "] is restricted" );
			}

			if ( joinedTableGroupJoin == null ) {
				throw new IllegalStateException();
			}

			joinedTableGroupJoin.applyPredicate(
					(Predicate) sqmJoin.getJoinPredicate().accept( this )
			);
		}

		consumeExplicitJoins( sqmJoin, joinedTableGroup );
		consumeImplicitJoins( sqmJoin, joinedTableGroup );
	}

	private NavigablePath getJoinNavigablePath(
			NavigablePath sqmJoinNavigablePath,
			NavigablePath parentNavigablePath, String partName) {
		if ( parentNavigablePath == null ) {
			return sqmJoinNavigablePath;
		}
		else {
			final NavigablePath elementNavigablePath = joinPathBySqmJoinFullPath.get( parentNavigablePath.getFullPath() );
			if ( elementNavigablePath == null ) {
				return sqmJoinNavigablePath;
			}
			else {
				return elementNavigablePath.append( partName );
			}
		}
	}

	private void consumeCrossJoin(SqmCrossJoin sqmJoin, TableGroup lhsTableGroup) {
		final EntityPersister entityDescriptor = resolveEntityPersister( sqmJoin.getReferencedPathSource() );

		final TableGroup tableGroup = entityDescriptor.createRootTableGroup(
				sqmJoin.getNavigablePath(),
				sqmJoin.getExplicitAlias(),
				true,
				determineLockMode( sqmJoin.getExplicitAlias() ),
				sqlAliasBaseManager,
				getSqlExpressionResolver(),
				() -> predicate -> additionalRestrictions = SqlAstTreeHelper.combinePredicates(
						additionalRestrictions,
						predicate
				),
				getCreationContext()
		);

		final TableGroupJoin tableGroupJoin = new TableGroupJoin(
				sqmJoin.getNavigablePath(),
				SqlAstJoinType.CROSS,
				tableGroup
		);

		lhsTableGroup.addTableGroupJoin( tableGroupJoin );

		getFromClauseIndex().register( sqmJoin, tableGroup );

		consumeExplicitJoins( sqmJoin, tableGroupJoin.getJoinedGroup() );
		consumeImplicitJoins( sqmJoin, tableGroupJoin.getJoinedGroup() );
	}

	private void consumeEntityJoin(SqmEntityJoin sqmJoin, TableGroup lhsTableGroup) {
		final EntityPersister entityDescriptor = resolveEntityPersister( sqmJoin.getReferencedPathSource() );

		final TableGroup tableGroup = entityDescriptor.createRootTableGroup(
				sqmJoin.getNavigablePath(),
				sqmJoin.getExplicitAlias(),
				true,
				determineLockMode( sqmJoin.getExplicitAlias() ),
				sqlAliasBaseManager,
				getSqlExpressionResolver(),
				() -> predicate -> additionalRestrictions = SqlAstTreeHelper.combinePredicates(
						additionalRestrictions,
						predicate
				),
				getCreationContext()
		);
		getFromClauseIndex().register( sqmJoin, tableGroup );

		final TableGroupJoin tableGroupJoin = new TableGroupJoin(
				sqmJoin.getNavigablePath(),
				sqmJoin.getSqmJoinType().getCorrespondingSqlJoinType(),
				tableGroup,
				null
		);
		lhsTableGroup.addTableGroupJoin( tableGroupJoin );

		// add any additional join restrictions
		if ( sqmJoin.getJoinPredicate() != null ) {
			tableGroupJoin.applyPredicate(
					(Predicate) sqmJoin.getJoinPredicate().accept( this )
			);
		}

		consumeExplicitJoins( sqmJoin, tableGroupJoin.getJoinedGroup() );
		consumeImplicitJoins( sqmJoin, tableGroupJoin.getJoinedGroup() );
	}

	private void consumeImplicitJoins(SqmPath<?> sqmPath, TableGroup tableGroup) {
		if ( log.isTraceEnabled() ) {
			log.tracef( "Visiting implicit joins for `%s`", sqmPath.getNavigablePath() );
		}

		sqmPath.visitImplicitJoinPaths(
				joinedPath -> {
					if ( log.isTraceEnabled() ) {
						log.tracef( "Starting implicit join handling for `%s`", joinedPath.getNavigablePath() );
					}
					final FromClauseIndex fromClauseIndex = getFromClauseIndex();
					assert fromClauseIndex.findTableGroup( joinedPath.getLhs().getNavigablePath() ) == tableGroup;

					final ModelPart subPart = tableGroup.getModelPart().findSubPart(
							joinedPath.getReferencedPathSource().getPathName(),
							sqmPath instanceof SqmTreatedPath
									? resolveEntityPersister( ( (SqmTreatedPath) sqmPath ).getTreatTarget() )
									: null
					);

					assert subPart instanceof TableGroupJoinProducer;
					final TableGroupJoinProducer joinProducer = (TableGroupJoinProducer) subPart;
					final TableGroupJoin tableGroupJoin = joinProducer.createTableGroupJoin(
							joinedPath.getNavigablePath(),
							tableGroup,
							null,
							tableGroup.isInnerJoinPossible() ? SqlAstJoinType.INNER : SqlAstJoinType.LEFT,
							null,
							this
					);

					fromClauseIndex.register( joinedPath, tableGroupJoin.getJoinedGroup() );

					consumeImplicitJoins( joinedPath, tableGroupJoin.getJoinedGroup() );
				}
		);
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SqmPath handling
	//		- Note that SqmFrom references defined in the FROM-clause are already
	//			handled during `#visitFromClause`

	@Override
	public TableGroup visitRootPath(SqmRoot<?> sqmRoot) {
		final TableGroup resolved = getFromClauseAccess().findTableGroup( sqmRoot.getNavigablePath() );
		if ( resolved != null ) {
			log.tracef( "SqmRoot [%s] resolved to existing TableGroup [%s]", sqmRoot, resolved );
			return resolved;
		}

		throw new InterpretationException( "SqmRoot not yet resolved to TableGroup" );
	}

	@Override
	public TableGroup visitQualifiedAttributeJoin(SqmAttributeJoin<?, ?> sqmJoin) {
		// todo (6.0) : have this resolve to TableGroup instead?
		//		- trying to remove tracking of TableGroupJoin in the x-refs

		final TableGroup existing = getFromClauseAccess().findTableGroup( sqmJoin.getNavigablePath() );
		if ( existing != null ) {
			log.tracef( "SqmAttributeJoin [%s] resolved to existing TableGroup [%s]", sqmJoin, existing );
			return existing;
		}

		throw new InterpretationException( "SqmAttributeJoin not yet resolved to TableGroup" );
	}

	private QuerySpec currentQuerySpec() {
		return currentQueryPart().getLastQuerySpec();
	}

	private QueryPart currentQueryPart() {
		final SqlAstQueryPartProcessingState processingState = (SqlAstQueryPartProcessingState) getProcessingStateStack()
				.getCurrent();
		return processingState.getInflightQueryPart();
	}

	protected SqlSelectionForSqmSelectionCollector currentSqlSelectionCollector() {
		return (SqlSelectionForSqmSelectionCollector) getCurrentProcessingState().getSqlExpressionResolver();
	}

	@Override
	public TableGroup visitCrossJoin(SqmCrossJoin<?> sqmJoin) {
		// todo (6.0) : have this resolve to TableGroup instead?
		//		- trying to remove tracking of TableGroupJoin in the x-refs

		final TableGroup existing = getFromClauseAccess().findTableGroup( sqmJoin.getNavigablePath() );
		if ( existing != null ) {
			log.tracef( "SqmCrossJoin [%s] resolved to existing TableGroup [%s]", sqmJoin, existing );
			return existing;
		}

		throw new InterpretationException( "SqmCrossJoin not yet resolved to TableGroup" );
	}

	@Override
	public TableGroup visitQualifiedEntityJoin(SqmEntityJoin sqmJoin) {
		// todo (6.0) : have this resolve to TableGroup instead?
		//		- trying to remove tracking of TableGroupJoin in the x-refs

		final TableGroup existing = getFromClauseAccess().findTableGroup( sqmJoin.getNavigablePath() );
		if ( existing != null ) {
			log.tracef( "SqmEntityJoin [%s] resolved to existing TableGroup [%s]", sqmJoin, existing );
			return existing;
		}

		throw new InterpretationException( "SqmEntityJoin not yet resolved to TableGroup" );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SqmPath

	@Override
	public Expression visitBasicValuedPath(SqmBasicValuedSimplePath<?> sqmPath) {
		BasicValuedPathInterpretation<?> path = BasicValuedPathInterpretation.from( sqmPath, this, this );

		if ( isDuration( sqmPath.getNodeType() ) ) {

			// Durations are stored (at least by default)
			// in a BIGINTEGER column full of nanoseconds
			// which we need to convert to the given unit
			//
			// This does not work at all for a Duration
			// mapped to a VARCHAR column, in which case
			// we would need to parse the weird format
			// defined by java.time.Duration (a bit hard
			// to do without some custom function).
			// Nor does it work for databases which have
			// a well-defined INTERVAL type, but that is
			// something we could implement.

			//first let's apply the propagated scale
			Expression scaledExpression = applyScale( toSqlExpression( path ) );

			// we use NANOSECOND, not NATIVE, as the unit
			// because that's how a Duration is persisted
			// in a database table column, and how it's
			// returned to a Java client

			if ( adjustedTimestamp != null ) {
				if ( appliedByUnit != null ) {
					throw new IllegalStateException();
				}
				// we're adding this variable duration to the
				// given date or timestamp, producing an
				// adjusted date or timestamp
				return timestampadd().expression(
						(AllowableFunctionReturnType<?>) adjustedTimestampType,
						new DurationUnit( NANOSECOND, basicType( Long.class ) ),
						scaledExpression,
						adjustedTimestamp
				);
			}
			else if ( appliedByUnit != null ) {
				// we're applying the 'by unit' operator,
				// producing a literal scalar value, so
				// we must convert this duration from
				// nanoseconds to the given unit

				MappingModelExpressable durationType = scaledExpression.getExpressionType();
				Duration duration = new Duration( scaledExpression, NANOSECOND, (BasicValuedMapping) durationType );

				TemporalUnit appliedUnit = appliedByUnit.getUnit().getUnit();
				BasicValuedMapping scalarType = (BasicValuedMapping) appliedByUnit.getNodeType();
				return new Conversion( duration, appliedUnit, scalarType );
			}
			else {
				// a "bare" Duration value in nanoseconds
				return scaledExpression;
			}
		}

		return path;
	}

	@Override
	public SqmPathInterpretation<?> visitEmbeddableValuedPath(SqmEmbeddedValuedSimplePath<?> sqmPath) {
		return EmbeddableValuedPathInterpretation.from( sqmPath, this, this );
	}

	@Override
	public Object visitNonAggregatedCompositeValuedPath(NonAggregatedCompositeSimplePath sqmPath) {
		return NonAggregatedCompositeValuedPathInterpretation.from( sqmPath, this, this );
	}

	@Override
	public SqmPathInterpretation<?> visitEntityValuedPath(SqmEntityValuedSimplePath sqmPath) {
		return EntityValuedPathInterpretation.from( sqmPath, this );
	}

	@Override
	public SqmPathInterpretation<?> visitPluralValuedPath(SqmPluralValuedSimplePath sqmPath) {
		return PluralValuedSimplePathInterpretation.from( sqmPath, this );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// General expressions

	@Override
	public Expression visitLiteral(SqmLiteral<?> literal) {
		if ( literal instanceof SqmLiteralNull ) {
			return new QueryLiteral<>( null, (BasicValuedMapping) inferableTypeAccessStack.getCurrent().get() );
		}

		return new QueryLiteral<>(
				literal.getLiteralValue(),
				(BasicValuedMapping) SqmMappingModelHelper.resolveMappingModelExpressable(
						literal,
						getCreationContext().getDomainModel(),
						getFromClauseAccess()::findTableGroup
				)
		);
	}

	private final Map<SqmParameter, List<JdbcParameter>> jdbcParamsBySqmParam = new IdentityHashMap<>();
	private final JdbcParameters jdbcParameters = new JdbcParametersImpl();

	@Override
	public Map<SqmParameter, List<JdbcParameter>> getJdbcParamsBySqmParam() {
		return jdbcParamsBySqmParam;
	}

	@Override
	public Expression visitNamedParameterExpression(SqmNamedParameter expression) {
		return consumeSqmParameter( expression );
	}


	protected Expression consumeSqmParameter(SqmParameter sqmParameter) {
		final MappingModelExpressable valueMapping = determineValueMapping( sqmParameter );
		final List<JdbcParameter> jdbcParametersForSqm = new ArrayList<>();

		resolveSqmParameter(
				sqmParameter,
				valueMapping,
				jdbcParametersForSqm::add
		);

		this.jdbcParameters.addParameters( jdbcParametersForSqm );
		this.jdbcParamsBySqmParam.put( sqmParameter, jdbcParametersForSqm );

		final QueryParameterImplementor<?> queryParameter = domainParameterXref.getQueryParameter( sqmParameter );
		final QueryParameterBinding<?> binding = domainParameterBindings.getBinding( queryParameter );
		binding.setType( valueMapping );
		return new SqmParameterInterpretation(
				sqmParameter,
				queryParameter,
				jdbcParametersForSqm,
				valueMapping,
				qp -> binding
		);
	}

	protected MappingModelExpressable<?> resolveMappingExpressable(SqmExpressable<?> nodeType) {
		final MappingModelExpressable valueMapping = getCreationContext().getDomainModel().resolveMappingExpressable(
				nodeType );

		if ( valueMapping == null ) {
			final Supplier<MappingModelExpressable> currentExpressableSupplier = inferableTypeAccessStack.getCurrent();
			if ( currentExpressableSupplier != null ) {
				return currentExpressableSupplier.get();
			}
		}

		if ( valueMapping == null ) {
			throw new ConversionException( "Could not determine ValueMapping for SqmExpressable: " + nodeType );
		}

		return valueMapping;
	}

	protected MappingModelExpressable<?> determineValueMapping(SqmExpression<?> sqmExpression) {
		if ( sqmExpression instanceof SqmParameter ) {
			return determineValueMapping( (SqmParameter) sqmExpression );
		}

		if ( sqmExpression instanceof SqmPath ) {
			log.debugf( "Determining mapping-model type for SqmPath : %s ", sqmExpression );
			return SqmMappingModelHelper.resolveMappingModelExpressable(
					sqmExpression,
					getCreationContext().getDomainModel(),
					getFromClauseAccess()::findTableGroup
			);
		}

		// The model type of an enum literal is always inferred
		if ( sqmExpression instanceof SqmEnumLiteral<?> ) {
			final Supplier<MappingModelExpressable> currentExpressableSupplier = inferableTypeAccessStack.getCurrent();
			if ( currentExpressableSupplier != null ) {
				return currentExpressableSupplier.get();
			}
		}

		log.debugf( "Determining mapping-model type for generalized SqmExpression : %s", sqmExpression );
		final SqmExpressable<?> nodeType = sqmExpression.getNodeType();
		final MappingModelExpressable valueMapping = getCreationContext().getDomainModel().resolveMappingExpressable(
				nodeType );

		if ( valueMapping == null ) {
			final Supplier<MappingModelExpressable> currentExpressableSupplier = inferableTypeAccessStack.getCurrent();
			if ( currentExpressableSupplier != null ) {
				return currentExpressableSupplier.get();
			}
		}

		if ( valueMapping == null ) {
			throw new ConversionException( "Could not determine ValueMapping for SqmExpression: " + sqmExpression );
		}

		return valueMapping;
	}

	@SuppressWarnings("WeakerAccess")
	protected MappingModelExpressable<?> determineValueMapping(SqmParameter<?> sqmParameter) {
		log.debugf( "Determining mapping-model type for SqmParameter : %s", sqmParameter );

		final QueryParameterImplementor<?> queryParameter = domainParameterXref.getQueryParameter( sqmParameter );
		final QueryParameterBinding<?> binding = domainParameterBindings.getBinding( queryParameter );

		if ( sqmParameter.getAnticipatedType() == null ) {
			// this should indicate the condition that the user query did not define an
			// explicit type in regard to this parameter.  Here we should prefer the
			// inferable type and fallback to the binding type
			final Supplier<MappingModelExpressable> currentExpressableSupplier = inferableTypeAccessStack.getCurrent();
			if ( currentExpressableSupplier != null ) {
				final MappingModelExpressable inferredMapping = currentExpressableSupplier.get();
				if ( inferredMapping != null ) {
					return inferredMapping;
				}
			}
		}

		AllowableParameterType<?> parameterSqmType = binding.getBindType();
		if ( parameterSqmType == null ) {
			parameterSqmType = queryParameter.getHibernateType();
			if ( parameterSqmType == null ) {
				parameterSqmType = sqmParameter.getAnticipatedType();
			}
		}

		assert parameterSqmType != null;

		if ( parameterSqmType instanceof BasicValuedMapping ) {
			return (BasicValuedMapping) parameterSqmType;
		}

		if ( parameterSqmType instanceof CompositeSqmPathSource ) {
			throw new NotYetImplementedFor6Exception( "Support for embedded-valued parameters not yet implemented" );
		}

		throw new ConversionException( "Could not determine ValueMapping for SqmParameter: " + sqmParameter );
	}

	protected final Stack<Supplier<MappingModelExpressable>> inferableTypeAccessStack = new StandardStack<>(
			() -> null
	);

	private void resolveSqmParameter(
			SqmParameter expression,
			MappingModelExpressable valueMapping,
			Consumer<JdbcParameter> jdbcParameterConsumer) {
		if ( valueMapping instanceof Association ) {
			( (Association) valueMapping ).getForeignKeyDescriptor().forEachJdbcType(
					(index, jdbcMapping) -> jdbcParameterConsumer.accept( new JdbcParameterImpl( jdbcMapping ) )
			);
		}
		else {
			valueMapping.forEachJdbcType(
					(index, jdbcMapping) -> jdbcParameterConsumer.accept( new JdbcParameterImpl( jdbcMapping ) )
			);
		}
	}

	@Override
	public Object visitPositionalParameterExpression(SqmPositionalParameter expression) {
		return consumeSqmParameter( expression );
	}

	@Override
	public Object visitJpaCriteriaParameter(JpaCriteriaParameter<?> expression) {
		if ( jpaCriteriaParamResolutions == null ) {
			throw new IllegalStateException( "No JpaCriteriaParameter resolutions registered" );
		}

		final Supplier<SqmJpaCriteriaParameterWrapper<?>> supplier = jpaCriteriaParamResolutions.get( expression );
		if ( supplier == null ) {
			throw new IllegalStateException( "Criteria parameter [" + expression + "] not known to be a parameter of the processing tree" );
		}

		return consumeSqmParameter( supplier.get() );
	}

	@Override
	public Object visitTuple(SqmTuple<?> sqmTuple) {
		final List<SqmExpression<?>> groupedExpressions = sqmTuple.getGroupedExpressions();
		final int size = groupedExpressions.size();
		final List<Expression> expressions = new ArrayList<>( size );
		for ( int i = 0; i < size; i++ ) {
			expressions.add( (Expression) groupedExpressions.get( i ).accept( this ) );
		}
		return new SqlTuple( expressions, null );
	}

	@Override
	public Object visitCollate(SqmCollate<?> sqmCollate) {
		return new Collate(
				(Expression) sqmCollate.getExpression().accept( this ),
				sqmCollate.getCollation()
		);
	}

	@Override
	public Expression visitFunction(SqmFunction sqmFunction) {
		shallownessStack.push( Shallowness.FUNCTION );
		try {
			//noinspection unchecked
			return sqmFunction.convertToSqlAst( this );
		}
		finally {
			shallownessStack.pop();
		}
	}

	@Override
	public Star visitStar(SqmStar sqmStar) {
		return new Star();
	}

	@Override
	public Object visitDistinct(SqmDistinct sqmDistinct) {
		return new Distinct( (Expression) sqmDistinct.getExpression().accept( this ) );
	}

	@Override
	public Object visitTrimSpecification(SqmTrimSpecification specification) {
		shallownessStack.push( Shallowness.FUNCTION );
		try {
			return new TrimSpecification( specification.getSpecification() );
		}
		finally {
			shallownessStack.pop();
		}
	}

	@Override
	public Object visitCastTarget(SqmCastTarget target) {
		shallownessStack.push( Shallowness.FUNCTION );
		try {
			return new CastTarget(
					(BasicValuedMapping) target.getType(),
					target.getLength(),
					target.getPrecision(),
					target.getScale()
			);
		}
		finally {
			shallownessStack.pop();
		}
	}

	@Override
	public Object visitExtractUnit(SqmExtractUnit unit) {
		shallownessStack.push( Shallowness.FUNCTION );
		try {
			return new ExtractUnit(
					unit.getUnit(),
					(BasicValuedMapping) unit.getType()
			);
		}
		finally {
			shallownessStack.pop();
		}
	}

	@Override
	public Object visitDurationUnit(SqmDurationUnit unit) {
		shallownessStack.push( Shallowness.FUNCTION );
		try {
			return new DurationUnit(
					unit.getUnit(),
					(BasicValuedMapping) unit.getType()
			);
		}
		finally {
			shallownessStack.pop();
		}
	}

	@Override
	public Object visitFormat(SqmFormat sqmFormat) {
		return new Format(
				sqmFormat.getLiteralValue(),
				(BasicValuedMapping) sqmFormat.getNodeType()
		);
	}

	//	@Override
//	public Object visitAbsFunction(SqmAbsFunction function) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new AbsFunction( (Expression) function.getArgument().accept( this ) );
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public AvgFunction visitAvgFunction(SqmAvgFunction expression) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new AvgFunction(
//					(Expression) expression.getArgument().accept( this ),
//					expression.isDistinct(),
//					expression.getJdbcMapping().getSqlExpressableType()
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public Object visitBitLengthFunction(SqmBitLengthFunction function) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new BitLengthFunction(
//					(Expression) function.getArgument().accept( this ),
//					( (BasicValuedExpressableType) function.getJdbcMapping() ).getSqlExpressableType()
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public Object visitCastFunction(SqmCastFunction expression) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new CastFunction(
//					(Expression) expression.getExpressionToCast().accept( this ),
//					( (BasicValuedExpressableType) expression.getJdbcMapping() ).getSqlExpressableType(),
//					expression.getExplicitSqlCastTarget()
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public CountFunction visitCountFunction(SqmCountFunction expression) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new CountFunction(
//					toSqlExpression( expression.getArgument().accept( this ) ),
//					expression.isDistinct(),
//					getCreationContext().getDomainModel().getTypeConfiguration()
//							.getBasicTypeRegistry()
//							.getBasicType( Long.class )
//							.getSqlExpressableType( getCreationContext().getDomainModel().getTypeConfiguration() )
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public ConcatFunction visitConcatFunction(SqmConcatFunction function) {
//		return new ConcatFunction(
//				collectionExpressions( function.getExpressions() ),
//				getCreationContext().getDomainModel().getTypeConfiguration()
//						.getBasicTypeRegistry()
//						.getBasicType( String.class )
//						.getSqlExpressableType( getCreationContext().getDomainModel().getTypeConfiguration() )
//		);
//	}
//
//	private List<Expression> collectionExpressions(List<SqmExpression> sqmExpressions) {
//		if ( sqmExpressions == null || sqmExpressions.isEmpty() ) {
//			return Collections.emptyList();
//		}
//
//		if ( sqmExpressions.size() == 1 ) {
//			return Collections.singletonList( (Expression) sqmExpressions.get( 0 ).accept( this ) );
//		}
//
//		final List<Expression> results = new ArrayList<>();
//
//		sqmExpressions.forEach( sqmExpression -> {
//
//			final Object expression = sqmExpression.accept( this );
//			if ( expression instanceof BasicValuedNavigableReference ) {
//				final BasicValuedNavigableReference navigableReference = (BasicValuedNavigableReference) expression;
//				results.add(
//						getSqlExpressionResolver().resolveSqlExpression(
//								fromClauseIndex.getTableGroup( navigableReference.getNavigablePath().getParent() ),
//								navigableReference.getNavigable().getBoundColumn()
//						)
//				);
//			}
//			else {
//				results.add( (Expression) expression );
//			}
//		} );
//		return results;
//	}
//
//	@Override
//	public CurrentDateFunction visitCurrentDateFunction(SqmCurrentDateFunction function) {
//		return new CurrentDateFunction(
//				( (BasicValuedExpressableType) function.getJdbcMapping() ).getSqlExpressableType()
//		);
//	}
//
//	@Override
//	public CurrentTimeFunction visitCurrentTimeFunction(SqmCurrentTimeFunction function) {
//		return new CurrentTimeFunction(
//				( (BasicValuedExpressableType) function.getJdbcMapping() ).getSqlExpressableType()
//		);
//	}
//
//	@Override
//	public CurrentTimestampFunction visitCurrentTimestampFunction(SqmCurrentTimestampFunction function) {
//		return new CurrentTimestampFunction(
//				( (BasicValuedExpressableType) function.getJdbcMapping() ).getSqlExpressableType()
//		);
//	}
//
//	@Override
//	public ExtractFunction visitExtractFunction(SqmExtractFunction function) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new ExtractFunction(
//					(Expression) function.getUnitToExtract().accept( this ),
//					(Expression) function.getExtractionSource().accept( this ),
//					( (BasicValuedExpressableType) function.getJdbcMapping() ).getSqlExpressableType()
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public CountStarFunction visitCountStarFunction(SqmCountStarFunction expression) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new CountStarFunction(
//					expression.isDistinct(),
//					getCreationContext().getDomainModel().getTypeConfiguration()
//							.getBasicTypeRegistry()
//							.getBasicType( Long.class )
//							.getSqlExpressableType( getCreationContext().getDomainModel().getTypeConfiguration() )
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public LengthFunction visitLengthFunction(SqmLengthFunction function) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new LengthFunction(
//					toSqlExpression( function.getArgument().accept( this ) ),
//					getCreationContext().getDomainModel().getTypeConfiguration()
//							.getBasicTypeRegistry()
//							.getBasicType( Long.class )
//							.getSqlExpressableType( getCreationContext().getDomainModel().getTypeConfiguration() )
//
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public LocateFunction visitLocateFunction(SqmLocateFunction function) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new LocateFunction(
//					(Expression) function.getPatternString().accept( this ),
//					(Expression) function.getStringToSearch().accept( this ),
//					function.getStartPosition() == null
//							? null
//							: (Expression) function.getStartPosition().accept( this )
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public Object visitLowerFunction(SqmLowerFunction function) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new LowerFunction(
//					toSqlExpression( function.getArgument().accept( this ) ),
//					( (BasicValuedExpressableType) function.getJdbcMapping() ).getSqlExpressableType()
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public MaxFunction visitMaxFunction(SqmMaxFunction expression) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new MaxFunction(
//					toSqlExpression( expression.getArgument().accept( this ) ),
//					expression.isDistinct(),
//					expression.getJdbcMapping().getSqlExpressableType()
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public MinFunction visitMinFunction(SqmMinFunction expression) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new MinFunction(
//					toSqlExpression( expression.getArgument().accept( this ) ),
//					expression.isDistinct(),
//					expression.getJdbcMapping().getSqlExpressableType()
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public Object visitModFunction(SqmModFunction function) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		final Expression dividend = (Expression) function.getDividend().accept( this );
//		final Expression divisor = (Expression) function.getDivisor().accept( this );
//		try {
//			return new ModFunction(
//					dividend,
//					divisor,
//					( (BasicValuedExpressableType) function.getJdbcMapping() ).getSqlExpressableType()
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public Object visitSubstringFunction(SqmSubstringFunction expression) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			List<Expression> expressionList = new ArrayList<>();
//			expressionList.add( toSqlExpression( expression.getSource().accept( this ) ) );
//			expressionList.add( toSqlExpression( expression.getStartPosition().accept( this ) ) );
//			expressionList.add( toSqlExpression( expression.getLength().accept( this ) ) );
//
//			return new SubstrFunction(
//					expression.getFunctionName(),
//					expressionList,
//					( (BasicValuedExpressableType) expression.getJdbcMapping() ).getSqlExpressableType()
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public Object visitStrFunction(SqmStrFunction expression) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new CastFunction(
//					toSqlExpression( expression.getArgument().accept( this ) ),
//					( (BasicValuedExpressableType) expression.getJdbcMapping() ).getSqlExpressableType(),
//					null
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public SumFunction visitSumFunction(SqmSumFunction expression) {
//		shallownessStack.push( Shallowness.FUNCTION );
//
//		try {
//			return new SumFunction(
//					toSqlExpression( expression.getArgument().accept( this ) ),
//					expression.isDistinct(),
//					expression.getJdbcMapping().getSqlExpressableType()
//
//			);
//		}
//		finally {
//			shallownessStack.pop();
//		}
//	}
//
//	@Override
//	public CoalesceFunction visitCoalesceFunction(SqmCoalesceFunction expression) {
//		final CoalesceFunction result = new CoalesceFunction();
//		for ( SqmExpression value : expression.getArguments() ) {
//			result.value( (Expression) value.accept( this ) );
//		}
//
//		return result;
//	}
//	@Override
//	public NullifFunction visitNullifFunction(SqmNullifFunction expression) {
//		return new NullifFunction(
//				(Expression) expression.getFirstArgument().accept( this ),
//				(Expression) expression.getSecondArgument().accept( this ),
//				( (BasicValuedExpressableType) expression.getJdbcMapping() ).getSqlExpressableType()
//		);
//	}
//
//	@Override
//	public Object visitTrimFunction(SqmTrimFunction expression) {
//		return new TrimFunction(
//				expression.getSpecification(),
//				(Expression) expression.getTrimCharacter().accept( this ),
//				(Expression) expression.getSource().accept( this ),
//				getCreationContext()
//		);
//	}
//
//	@Override
//	public Object visitUpperFunction(SqmUpperFunction sqmFunction) {
//		return new UpperFunction(
//				toSqlExpression( sqmFunction.getArgument().accept( this ) ),
//				( (BasicValuedExpressableType) sqmFunction.getJdbcMapping() ).getSqlExpressableType()
//		);
//
//	}
//
//	@Override
//	public ConcatFunction visitConcatExpression(SqmConcat expression) {
//		return new ConcatFunction(
//				Arrays.asList(
//						(Expression)expression.getLeftHandOperand().accept( this ),
//						(Expression) expression.getRightHandOperand().accept( this )
//				),
//				expression.getJdbcMapping().getSqlExpressableType()
//		);
//	}

	@Override
	public Object visitUnaryOperationExpression(SqmUnaryOperation expression) {
		shallownessStack.push( Shallowness.NONE );

		try {
			return new UnaryOperation(
					interpret( expression.getOperation() ),
					toSqlExpression( expression.getOperand().accept( this ) ),
					(BasicValuedMapping) determineValueMapping( expression.getOperand() )
			);
		}
		finally {
			shallownessStack.pop();
		}
	}

	private UnaryArithmeticOperator interpret(UnaryArithmeticOperator operator) {
		return operator;
	}

	@Override
	public Object visitBinaryArithmeticExpression(SqmBinaryArithmetic expression) {
		shallownessStack.push( Shallowness.NONE );

		try {
			SqmExpression leftOperand = expression.getLeftHandOperand();
			SqmExpression rightOperand = expression.getRightHandOperand();

			boolean durationToRight = TypeConfiguration.isDuration( rightOperand.getNodeType() );
			TypeConfiguration typeConfiguration = getCreationContext().getDomainModel().getTypeConfiguration();
			TemporalType temporalTypeToLeft = typeConfiguration.getSqlTemporalType( leftOperand.getNodeType() );
			TemporalType temporalTypeToRight = typeConfiguration.getSqlTemporalType( rightOperand.getNodeType() );
			boolean temporalTypeSomewhereToLeft = adjustedTimestamp != null || temporalTypeToLeft != null;

			if ( temporalTypeToLeft != null && durationToRight ) {
				if ( adjustmentScale != null || negativeAdjustment ) {
					//we can't distribute a scale over a date/timestamp
					throw new SemanticException( "scalar multiplication of temporal value" );
				}
			}

			if ( durationToRight && temporalTypeSomewhereToLeft ) {
				return transformDurationArithmetic( expression );
			}
			else if ( temporalTypeToLeft != null && temporalTypeToRight != null ) {
				return transformDatetimeArithmetic( expression );
			}
			else if ( durationToRight && appliedByUnit != null ) {
				return new BinaryArithmeticExpression(
						toSqlExpression( leftOperand.accept( this ) ),
						expression.getOperator(),
						toSqlExpression( rightOperand.accept( this ) ),
						//after distributing the 'by unit' operator
						//we always get a Long value back
						(BasicValuedMapping) appliedByUnit.getNodeType()
				);
			}
			else {
				return new BinaryArithmeticExpression(
						toSqlExpression( leftOperand.accept( this ) ),
						expression.getOperator(),
						toSqlExpression( rightOperand.accept( this ) ),
						getExpressionType( expression )
				);
			}
		}
		finally {
			shallownessStack.pop();
		}
	}

	private BasicValuedMapping getExpressionType(SqmBinaryArithmetic expression) {
		SqmExpressable leftHandOperandType = expression.getLeftHandOperand().getNodeType();
		if ( leftHandOperandType instanceof BasicValuedMapping ) {
			return (BasicValuedMapping) leftHandOperandType;
		}
		else {
			return (BasicValuedMapping) expression.getRightHandOperand().getNodeType();
		}
	}

	private Expression toSqlExpression(Object value) {
		return (Expression) value;
	}

	private Object transformDurationArithmetic(SqmBinaryArithmetic<?> expression) {
		BinaryArithmeticOperator operator = expression.getOperator();

		// we have a date or timestamp somewhere to
		// the right of us, so we need to restructure
		// the expression tree
		switch ( operator ) {
			case ADD:
			case SUBTRACT:
				// the only legal binary operations involving
				// a duration with a date or timestamp are
				// addition and subtraction with the duration
				// on the right and the date or timestamp on
				// the left, producing a date or timestamp
				//
				// ts + d or ts - d
				//
				// the only legal binary operations involving
				// two durations are addition and subtraction,
				// producing a duration
				//
				// d1 + d2

				// re-express addition of non-leaf duration
				// expressions to a date or timestamp as
				// addition of leaf durations to a date or
				// timestamp
				// ts + x * (d1 + d2) => (ts + x * d1) + x * d2
				// ts - x * (d1 + d2) => (ts - x * d1) - x * d2
				// ts + x * (d1 - d2) => (ts + x * d1) - x * d2
				// ts - x * (d1 - d2) => (ts - x * d1) + x * d2

				Expression timestamp = adjustedTimestamp;
				SqmExpressable<?> timestampType = adjustedTimestampType;
				adjustedTimestamp = toSqlExpression( expression.getLeftHandOperand().accept( this ) );
				MappingModelExpressable type = adjustedTimestamp.getExpressionType();
				if ( type instanceof SqmExpressable ) {
					adjustedTimestampType = (SqmExpressable) type;
				}
//				else if (type instanceof BasicValuedMapping) {
//					adjustedTimestampType = ((BasicValuedMapping) type).getBasicType();
//				}
				else {
					// else we know it has not been transformed
					adjustedTimestampType = expression.getLeftHandOperand().getNodeType();
				}
				if ( operator == SUBTRACT ) {
					negativeAdjustment = !negativeAdjustment;
				}
				try {
					return expression.getRightHandOperand().accept( this );
				}
				finally {
					if ( operator == SUBTRACT ) {
						negativeAdjustment = !negativeAdjustment;
					}
					adjustedTimestamp = timestamp;
					adjustedTimestampType = timestampType;
				}
			case MULTIPLY:
				// finally, we can multiply a duration on the
				// right by a scalar value on the left
				// scalar multiplication produces a duration
				// x * d

				// distribute scalar multiplication over the
				// terms, not forgetting the propagated scale
				// x * (d1 + d2) => x * d1 + x * d2
				// x * (d1 - d2) => x * d1 - x * d2
				// -x * (d1 + d2) => - x * d1 - x * d2
				// -x * (d1 - d2) => - x * d1 + x * d2
				Expression duration = toSqlExpression( expression.getLeftHandOperand().accept( this ) );
				Expression scale = adjustmentScale;
				boolean negate = negativeAdjustment;
				adjustmentScale = applyScale( duration );
				negativeAdjustment = false; //was sucked into the scale
				try {
					return expression.getRightHandOperand().accept( this );
				}
				finally {
					adjustmentScale = scale;
					negativeAdjustment = negate;
				}
			default:
				throw new SemanticException( "illegal operator for a duration " + operator );
		}
	}

	private Object transformDatetimeArithmetic(SqmBinaryArithmetic expression) {
		BinaryArithmeticOperator operator = expression.getOperator();

		// the only kind of algebra we know how to
		// do on dates/timestamps is subtract them,
		// producing a duration - all other binary
		// operator expressions with two dates or
		// timestamps are ill-formed
		if ( operator != SUBTRACT ) {
			throw new SemanticException( "illegal operator for temporal type: " + operator );
		}

		// a difference between two dates or two
		// timestamps is a leaf duration, so we
		// must apply the scale, and the 'by unit'
		// ts1 - ts2

		Expression left = cleanly( () -> toSqlExpression( expression.getLeftHandOperand().accept( this ) ) );
		Expression right = cleanly( () -> toSqlExpression( expression.getRightHandOperand().accept( this ) ) );

		TypeConfiguration typeConfiguration = getCreationContext().getDomainModel().getTypeConfiguration();
		TemporalType leftTimestamp = typeConfiguration.getSqlTemporalType( expression.getLeftHandOperand().getNodeType() );
		TemporalType rightTimestamp = typeConfiguration.getSqlTemporalType( expression.getRightHandOperand().getNodeType() );

		// when we're dealing with Dates, we use
		// DAY as the smallest unit, otherwise we
		// use a platform-specific granularity

		TemporalUnit baseUnit = ( rightTimestamp == TemporalType.TIMESTAMP || leftTimestamp == TemporalType.TIMESTAMP ) ?
				NATIVE :
				DAY;

		if ( adjustedTimestamp != null ) {
			if ( appliedByUnit != null ) {
				throw new IllegalStateException();
			}
			// we're using the resulting duration to
			// adjust a date or timestamp on the left

			// baseUnit is the finest resolution for the
			// temporal type, so we must use it for both
			// the diff, and then the subsequent add

			DurationUnit unit = new DurationUnit( baseUnit, basicType( Integer.class ) );
			Expression scaledMagnitude = applyScale( timestampdiff().expression(
					(AllowableFunctionReturnType<?>) expression.getNodeType(),
					unit, right, left
			) );
			return timestampadd().expression(
					(AllowableFunctionReturnType<?>) adjustedTimestampType, //TODO should be adjustedTimestamp.getType()
					unit, scaledMagnitude, adjustedTimestamp
			);
		}
		else if ( appliedByUnit != null ) {
			// we're immediately converting the resulting
			// duration to a scalar in the given unit

			DurationUnit unit = (DurationUnit) appliedByUnit.getUnit().accept( this );
			return applyScale( timestampdiff().expression(
					(AllowableFunctionReturnType<?>) expression.getNodeType(),
					unit, right, left
			) );
		}
		else {
			// a plain "bare" Duration
			DurationUnit unit = new DurationUnit( baseUnit, basicType( Integer.class ) );
			BasicValuedMapping durationType = (BasicValuedMapping) expression.getNodeType();
			Expression scaledMagnitude = applyScale( timestampdiff().expression(
					(AllowableFunctionReturnType<?>) expression.getNodeType(),
					unit, right, left
			) );
			return new Duration( scaledMagnitude, baseUnit, durationType );
		}
	}

	private <J> BasicValuedMapping basicType(Class<J> javaType) {
		return creationContext.getDomainModel().getTypeConfiguration().getBasicTypeForJavaType( javaType );
	}

	private TimestampaddFunction timestampadd() {
		return (TimestampaddFunction)
				getCreationContext().getSessionFactory()
						.getQueryEngine().getSqmFunctionRegistry()
						.findFunctionDescriptor( "timestampadd" );
	}

	private TimestampdiffFunction timestampdiff() {
		return (TimestampdiffFunction)
				getCreationContext().getSessionFactory()
						.getQueryEngine().getSqmFunctionRegistry()
						.findFunctionDescriptor( "timestampdiff" );
	}

	private <T> T cleanly(Supplier<T> supplier) {
		SqmByUnit byUnit = appliedByUnit;
		Expression timestamp = adjustedTimestamp;
		SqmExpressable<?> timestampType = adjustedTimestampType;
		Expression scale = adjustmentScale;
		boolean negate = negativeAdjustment;
		adjustmentScale = null;
		negativeAdjustment = false;
		appliedByUnit = null;
		adjustedTimestamp = null;
		adjustedTimestampType = null;
		try {
			return supplier.get();
		}
		finally {
			appliedByUnit = byUnit;
			adjustedTimestamp = timestamp;
			adjustedTimestampType = timestampType;
			adjustmentScale = scale;
			negativeAdjustment = negate;
		}
	}

	Expression applyScale(Expression magnitude) {
		boolean negate = negativeAdjustment;

		if ( magnitude instanceof UnaryOperation ) {
			UnaryOperation unary = (UnaryOperation) magnitude;
			if ( unary.getOperator() == UNARY_MINUS ) {
				// if it's already negated, don't
				// wrap it in another unary minus,
				// just throw away the one we have
				// (OTOH, if it *is* negated, shift
				// the operator to left of scale)
				negate = !negate;
			}
			magnitude = unary.getOperand();
		}

		if ( adjustmentScale != null ) {
			if ( isOne( adjustmentScale ) ) {
				//no work to do
			}
			else {
				if ( isOne( magnitude ) ) {
					magnitude = adjustmentScale;
				}
				else {
					magnitude = new BinaryArithmeticExpression(
							adjustmentScale,
							MULTIPLY,
							magnitude,
							(BasicValuedMapping) magnitude.getExpressionType()
					);
				}
			}
		}

		if ( negate ) {
			magnitude = new UnaryOperation(
					UNARY_MINUS,
					magnitude,
					(BasicValuedMapping) magnitude.getExpressionType()
			);
		}

		return magnitude;
	}

	@SuppressWarnings("unchecked")
	static boolean isOne(Expression scale) {
		return scale instanceof QueryLiteral
				&& ( (QueryLiteral<Number>) scale ).getLiteralValue().longValue() == 1L;
	}

	@Override
	public Object visitToDuration(SqmToDuration toDuration) {
		//TODO: do we need to temporarily set appliedByUnit
		//      to null before we recurse down the tree?
		//      and what about scale?
		Expression magnitude = toSqlExpression( toDuration.getMagnitude().accept( this ) );
		DurationUnit unit = (DurationUnit) toDuration.getUnit().accept( this );

		// let's start by applying the propagated scale
		// so we don't forget to do it in what follows
		Expression scaledMagnitude = applyScale( magnitude );

		if ( adjustedTimestamp != null ) {
			// we're adding this literal duration to the
			// given date or timestamp, producing an
			// adjusted date or timestamp
			if ( appliedByUnit != null ) {
				throw new IllegalStateException();
			}
			return timestampadd().expression(
					(AllowableFunctionReturnType<?>) adjustedTimestampType, //TODO should be adjustedTimestamp.getType()
					unit, scaledMagnitude, adjustedTimestamp
			);
		}
		else {
			BasicValuedMapping durationType = (BasicValuedMapping) toDuration.getNodeType();
			Duration duration = new Duration( scaledMagnitude, unit.getUnit(), durationType );

			if ( appliedByUnit != null ) {
				// we're applying the 'by unit' operator,
				// producing a literal scalar value in
				// the given unit
				TemporalUnit appliedUnit = appliedByUnit.getUnit().getUnit();
				BasicValuedMapping scalarType = (BasicValuedMapping) appliedByUnit.getNodeType();
				return new Conversion( duration, appliedUnit, scalarType );
			}
			else {
				// a "bare" Duration value (gets rendered as nanoseconds)
				return duration;
			}
		}
	}

	@Override
	public Object visitByUnit(SqmByUnit byUnit) {
		SqmByUnit outer = appliedByUnit;
		appliedByUnit = byUnit;
		try {
			return byUnit.getDuration().accept( this );
		}
		finally {
			appliedByUnit = outer;
		}
	}

	@Override
	public QueryPart visitSubQueryExpression(SqmSubQuery sqmSubQuery) {
		return visitQueryPart( sqmSubQuery.getQueryPart() );
//
//		final ExpressableType<?> expressableType = determineExpressableType( sqmSubQuery );
//
//		return new SubQuery(
//				subQuerySpec,
//				expressableType instanceof BasicValuedExpressableType<?>
//						? ( (BasicValuedExpressableType) expressableType ).getSqlExpressableType( getTypeConfiguration() )
//						: null,
//				expressableType
//		);
	}

	@Override
	public CaseSimpleExpression visitSimpleCaseExpression(SqmCaseSimple<?, ?> expression) {
		SqmExpressable<?> resultType = expression.getNodeType();
		List<CaseSimpleExpression.WhenFragment> whenFragments = new ArrayList<>( expression.getWhenFragments().size() );
		for ( SqmCaseSimple.WhenFragment<?, ?> whenFragment : expression.getWhenFragments() ) {
			resultType = QueryHelper.highestPrecedenceType2( resultType, whenFragment.getResult().getNodeType() );
			whenFragments.add(
					new CaseSimpleExpression.WhenFragment(
							(Expression) whenFragment.getCheckValue().accept( this ),
							(Expression) whenFragment.getResult().accept( this )
					)
			);
		}

		Expression otherwise = null;
		if ( expression.getOtherwise() != null ) {
			resultType = QueryHelper.highestPrecedenceType2( resultType, expression.getOtherwise().getNodeType() );
			otherwise = (Expression) expression.getOtherwise().accept( this );
		}

		final CaseSimpleExpression result = new CaseSimpleExpression(
				resolveMappingExpressable( resultType ),
				(Expression) expression.getFixture().accept( this ),
				whenFragments,
				otherwise
		);

		return result;
	}

	@Override
	public CaseSearchedExpression visitSearchedCaseExpression(SqmCaseSearched<?> expression) {
		SqmExpressable<?> resultType = expression.getNodeType();
		List<CaseSearchedExpression.WhenFragment> whenFragments = new ArrayList<>( expression.getWhenFragments().size() );
		for ( SqmCaseSearched.WhenFragment<?> whenFragment : expression.getWhenFragments() ) {
			resultType = QueryHelper.highestPrecedenceType2( resultType, whenFragment.getResult().getNodeType() );
			whenFragments.add(
					new CaseSearchedExpression.WhenFragment(
							(Predicate) whenFragment.getPredicate().accept( this ),
							(Expression) whenFragment.getResult().accept( this )
					)
			);
		}

		Expression otherwise = null;
		if ( expression.getOtherwise() != null ) {
			resultType = QueryHelper.highestPrecedenceType2( resultType, expression.getOtherwise().getNodeType() );
			otherwise = (Expression) expression.getOtherwise().accept( this );
		}

		final CaseSearchedExpression result = new CaseSearchedExpression(
				resolveMappingExpressable( resultType ),
				whenFragments,
				otherwise
		);

		return result;
	}

	@Override
	public Object visitAny(SqmAny<?> sqmAny) {
		return new Any(
				visitSubQueryExpression( sqmAny.getSubquery() ),
				null //resolveMappingExpressable( sqmAny.getNodeType() )
		);
	}

	@Override
	public Object visitEvery(SqmEvery<?> sqmEvery) {
		return new Every(
				visitSubQueryExpression( sqmEvery.getSubquery() ),
				null //resolveMappingExpressable( sqmEvery.getNodeType() )
		);
	}

	@Override
	public Object visitSummarization(SqmSummarization<?> sqmSummarization) {
		final List<SqmExpression<?>> groupingExpressions = sqmSummarization.getGroupings();
		final int size = groupingExpressions.size();
		final List<Expression> expressions = new ArrayList<>( size );
		for ( int i = 0; i < size; i++ ) {
			expressions.add( (Expression) groupingExpressions.get( i ).accept( this ) );
		}
		return new Summarization(
				getSummarizationKind( sqmSummarization.getKind() ),
				expressions
		);
	}

	private Summarization.Kind getSummarizationKind(SqmSummarization.Kind kind) {
		switch ( kind ) {
			case CUBE:
				return Summarization.Kind.CUBE;
			case ROLLUP:
				return Summarization.Kind.ROLLUP;
		}
		throw new UnsupportedOperationException( "Unsupported summarization: " + kind );
	}

	@Override
	public Expression visitEntityTypeLiteralExpression(SqmLiteralEntityType sqmExpression) {
		final EntityDomainType<?> nodeType = sqmExpression.getNodeType();
		final EntityPersister mappingDescriptor = getCreationContext().getDomainModel()
				.getEntityDescriptor( nodeType.getHibernateEntityName() );

		return new EntityTypeLiteral( mappingDescriptor );
	}

	@Override
	public Expression visitSqmPathEntityTypeExpression(SqmPathEntityType<?> sqmExpression) {
		return BasicValuedPathInterpretation.from(
				sqmExpression,
				this,
				this
		);
	}

	@Override
	public Object visitEnumLiteral(SqmEnumLiteral sqmEnumLiteral) {
		return new QueryLiteral<>(
				sqmEnumLiteral.getEnumValue(),
				(BasicValuedMapping) determineValueMapping( sqmEnumLiteral )
		);
	}

	@Override
	public Object visitFieldLiteral(SqmFieldLiteral sqmFieldLiteral) {
		return new QueryLiteral<>(
				sqmFieldLiteral.getValue(),
				(BasicValuedMapping) determineValueMapping( sqmFieldLiteral )
		);
	}


//	@Override
//	public Object visitPluralAttributeElementBinding(PluralAttributeElementBinding binding) {
//		final TableGroup resolvedTableGroup = fromClauseIndex.findResolvedTableGroup( binding.getFromElement() );
//
//		return getCurrentDomainReferenceExpressionBuilder().buildPluralAttributeElementReferenceExpression(
//				binding,
//				resolvedTableGroup,
//				PersisterHelper.convert( binding.getNavigablePath() )
//		);
//	}
//
//	@Override
//	public ColumnReference visitExplicitColumnReference(SqmColumnReference sqmColumnReference) {
//		final TableGroup tableGroup = fromClauseIndex.findTableGroup(
//				sqmColumnReference.getSqmFromBase().getNavigablePath()
//		);
//
//		final ColumnReference columnReference = tableGroup.locateColumnReferenceByName( sqmColumnReference.getColumnName() );
//
//		if ( columnReference == null ) {
//			throw new HibernateException( "Could not resolve ColumnReference" );
//		}
//
//		return columnReference;
//	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Predicates


	@Override
	public GroupedPredicate visitGroupedPredicate(SqmGroupedPredicate predicate) {
		return new GroupedPredicate( (Predicate) predicate.getSubPredicate().accept( this ) );
	}

	@Override
	public Junction visitAndPredicate(SqmAndPredicate predicate) {
		final Junction conjunction = new Junction( Junction.Nature.CONJUNCTION );
		conjunction.add( (Predicate) predicate.getLeftHandPredicate().accept( this ) );
		conjunction.add( (Predicate) predicate.getRightHandPredicate().accept( this ) );
		return conjunction;
	}

	@Override
	public Junction visitOrPredicate(SqmOrPredicate predicate) {
		final Junction disjunction = new Junction( Junction.Nature.DISJUNCTION );
		disjunction.add( (Predicate) predicate.getLeftHandPredicate().accept( this ) );
		disjunction.add( (Predicate) predicate.getRightHandPredicate().accept( this ) );
		return disjunction;
	}

	@Override
	public InSubQueryPredicate visitMemberOfPredicate(SqmMemberOfPredicate predicate) {
		final SqmPath<?> pluralPath = predicate.getPluralPath();
		final PluralAttributeMapping mappingModelExpressable = (PluralAttributeMapping) determineValueMapping(
				pluralPath );

		if ( mappingModelExpressable.getElementDescriptor() instanceof EntityCollectionPart ) {
			inferableTypeAccessStack.push(
					() -> ( (EntityCollectionPart) mappingModelExpressable.getElementDescriptor() ).getKeyTargetMatchPart() );
		}
		else if ( mappingModelExpressable.getElementDescriptor() instanceof EmbeddedCollectionPart ) {
			inferableTypeAccessStack.push(
					() -> mappingModelExpressable.getElementDescriptor() );
		}
		else {
			inferableTypeAccessStack.push( () -> mappingModelExpressable );
		}

		final Expression lhs;
		try {
			lhs = (Expression) predicate.getLeftHandExpression().accept( this );
		}
		finally {
			inferableTypeAccessStack.pop();
		}

		return new InSubQueryPredicate(
				lhs,
				createMemberOfSubQuery( pluralPath, mappingModelExpressable ),
				predicate.isNegated()
		);
	}

	private QueryPart createMemberOfSubQuery(SqmPath<?> pluralPath, PluralAttributeMapping mappingModelExpressable) {
		final FromClauseAccess parentFromClauseAccess = getFromClauseAccess();
		final QuerySpec querySpec = new QuerySpec( false );
		pushProcessingState(
				new SqlAstQueryPartProcessingStateImpl(
						querySpec,
						getCurrentProcessingState(),
						this,
						currentClauseStack::getCurrent
				)
		);
		try {

			final TableGroup rootTableGroup = mappingModelExpressable.createRootTableGroup(
					pluralPath.getNavigablePath(),
					null,
					true,
					LockOptions.NONE.getLockMode(),
					sqlAliasBaseManager,
					getSqlExpressionResolver(),
					() -> querySpec::applyPredicate,
					creationContext
			);

			getFromClauseAccess().registerTableGroup( pluralPath.getNavigablePath(), rootTableGroup );

			querySpec.getFromClause().addRoot( rootTableGroup );

			final CollectionPart elementDescriptor = mappingModelExpressable.getElementDescriptor();

			elementDescriptor.createDomainResult(
					pluralPath.getNavigablePath(),
					rootTableGroup,
					null,
					this
			);

			final Predicate predicate = mappingModelExpressable.getKeyDescriptor().generateJoinPredicate(
					parentFromClauseAccess.findTableGroup( pluralPath.getNavigablePath().getParent() ),
					rootTableGroup,
					null,
					getSqlExpressionResolver(),
					creationContext
			);
			querySpec.applyPredicate( predicate );
		}
		finally {
			popProcessingStateStack();
		}

		return querySpec;
	}

	@Override
	public NegatedPredicate visitNegatedPredicate(SqmNegatedPredicate predicate) {
		return new NegatedPredicate(
				(Predicate) predicate.getWrappedPredicate().accept( this )
		);
	}

	@Override
	public ComparisonPredicate visitComparisonPredicate(SqmComparisonPredicate predicate) {
		inferableTypeAccessStack.push( () -> determineValueMapping( predicate.getRightHandExpression() ) );

		final Expression lhs;
		try {
			lhs = (Expression) predicate.getLeftHandExpression().accept( this );
		}
		finally {
			inferableTypeAccessStack.pop();
		}

		inferableTypeAccessStack.push( () -> determineValueMapping( predicate.getLeftHandExpression() ) );

		final Expression rhs;
		try {
			rhs = (Expression) predicate.getRightHandExpression().accept( this );
		}
		finally {
			inferableTypeAccessStack.pop();
		}

		return new ComparisonPredicate( lhs, predicate.getSqmOperator(), rhs );
	}

	@Override
	public Object visitIsEmptyPredicate(SqmEmptinessPredicate predicate) {
		final QuerySpec subQuerySpec = new QuerySpec( false, 1 );

		final FromClauseAccess parentFromClauseAccess = getFromClauseAccess();
		final SqlAstProcessingStateImpl subQueryState = new SqlAstProcessingStateImpl(
				getCurrentProcessingState(),
				this,
				currentClauseStack::getCurrent
		);

		pushProcessingState( subQueryState );
		try {
			final SqmPluralValuedSimplePath<?> sqmPluralPath = predicate.getPluralPath();

			final NavigablePath pluralPathNavPath = sqmPluralPath.getNavigablePath();
			final NavigablePath parentNavPath = pluralPathNavPath.getParent();
			assert parentNavPath != null;

			final TableGroup parentTableGroup = parentFromClauseAccess.getTableGroup( parentNavPath );

			subQueryState.getSqlAstCreationState().getFromClauseAccess().registerTableGroup(
					parentNavPath,
					parentTableGroup
			);

			final SqmPathInterpretation<?> sqmPathInterpretation = visitPluralValuedPath( sqmPluralPath );


			final PluralAttributeMapping pluralAttributeMapping = (PluralAttributeMapping) sqmPathInterpretation.getExpressionType();

			// note : do not add to `parentTableGroup` as a join

			final TableGroupJoin tableGroupJoin = pluralAttributeMapping.createTableGroupJoin(
					pluralPathNavPath,
					parentTableGroup,
					sqmPluralPath.getExplicitAlias(),
					SqlAstJoinType.LEFT,
					LockMode.NONE,
					sqlAliasBaseManager,
					subQueryState,
					creationContext
			);

			final TableGroup collectionTableGroup = tableGroupJoin.getJoinedGroup();

			subQuerySpec.getFromClause().addRoot( collectionTableGroup );
			subQuerySpec.applyPredicate( tableGroupJoin.getPredicate() );

			final ForeignKeyDescriptor collectionKeyDescriptor = pluralAttributeMapping.getKeyDescriptor();
			final int jdbcTypeCount = collectionKeyDescriptor.getJdbcTypeCount();
			assert jdbcTypeCount > 0;

			final JdbcLiteral<Integer> jdbcLiteral = new JdbcLiteral<>( 1, StandardBasicTypes.INTEGER );
			subQuerySpec.getSelectClause().addSqlSelection(
					new SqlSelectionImpl( 1, 0, jdbcLiteral )
			);

			return new ExistsPredicate( subQuerySpec );
		}
		finally {
			popProcessingStateStack();
		}
	}

	@Override
	public BetweenPredicate visitBetweenPredicate(SqmBetweenPredicate predicate) {
		final Expression expression;
		final Expression lowerBound;
		final Expression upperBound;

		inferableTypeAccessStack.push(
				() -> coalesceSuppliedValues(
						() -> determineValueMapping( predicate.getLowerBound() ),
						() -> determineValueMapping( predicate.getUpperBound() )
				)
		);

		try {
			expression = (Expression) predicate.getExpression().accept( this );
		}
		finally {
			inferableTypeAccessStack.pop();
		}

		inferableTypeAccessStack.push(
				() -> coalesceSuppliedValues(
						() -> determineValueMapping( predicate.getExpression() ),
						() -> determineValueMapping( predicate.getUpperBound() )
				)
		);
		try {
			lowerBound = (Expression) predicate.getLowerBound().accept( this );
		}
		finally {
			inferableTypeAccessStack.pop();
		}

		inferableTypeAccessStack.push(
				() -> coalesceSuppliedValues(
						() -> determineValueMapping( predicate.getExpression() ),
						() -> determineValueMapping( predicate.getLowerBound() )
				)
		);
		try {
			upperBound = (Expression) predicate.getUpperBound().accept( this );
		}
		finally {
			inferableTypeAccessStack.pop();
		}

		return new BetweenPredicate(
				expression,
				lowerBound,
				upperBound,
				predicate.isNegated()
		);
	}

	@Override
	public LikePredicate visitLikePredicate(SqmLikePredicate predicate) {
		final Expression escapeExpression = predicate.getEscapeCharacter() == null
				? null
				: (Expression) predicate.getEscapeCharacter().accept( this );

		return new LikePredicate(
				(Expression) predicate.getMatchExpression().accept( this ),
				(Expression) predicate.getPattern().accept( this ),
				escapeExpression,
				predicate.isNegated()
		);
	}

	@Override
	public NullnessPredicate visitIsNullPredicate(SqmNullnessPredicate predicate) {
		return new NullnessPredicate(
				(Expression) predicate.getExpression().accept( this ),
				predicate.isNegated()
		);
	}

	@Override
	public InListPredicate visitInListPredicate(SqmInListPredicate<?> predicate) {
		// special case: if there is just a single "value" element and it is a parameter
		//		and the binding for that parameter is multi-valued we need special
		//		handling for "expansion"
		if ( predicate.getListExpressions().size() == 1 ) {
			final SqmExpression sqmExpression = predicate.getListExpressions().get( 0 );
			if ( sqmExpression instanceof SqmParameter ) {
				final SqmParameter sqmParameter = (SqmParameter) sqmExpression;

				if ( sqmParameter.allowMultiValuedBinding() ) {
					final InListPredicate specialCase = processInListWithSingleParameter( predicate, sqmParameter );
					if ( specialCase != null ) {
						return specialCase;
					}
				}
			}
		}

		// otherwise - no special case...

		final InListPredicate inPredicate = new InListPredicate(
				(Expression) predicate.getTestExpression().accept( this ),
				predicate.isNegated()
		);

		inferableTypeAccessStack.push( () -> determineValueMapping( predicate.getTestExpression() ) );

		try {
			for ( SqmExpression expression : predicate.getListExpressions() ) {
				inPredicate.addExpression( (Expression) expression.accept( this ) );
			}
		}
		finally {
			inferableTypeAccessStack.pop();
		}

		return inPredicate;
	}

	private InListPredicate processInListWithSingleParameter(
			SqmInListPredicate<?> sqmPredicate,
			SqmParameter sqmParameter) {
		assert sqmParameter.allowMultiValuedBinding();

		if ( sqmParameter instanceof JpaCriteriaParameter ) {
			return processInSingleCriteriaParameter( sqmPredicate, (JpaCriteriaParameter) sqmParameter );
		}

		return processInSingleHqlParameter( sqmPredicate, sqmParameter );
	}

	private InListPredicate processInSingleHqlParameter(SqmInListPredicate<?> sqmPredicate, SqmParameter sqmParameter) {
		final QueryParameterImplementor<?> domainParam = domainParameterXref.getQueryParameter( sqmParameter );
		final QueryParameterBinding domainParamBinding = domainParameterBindings.getBinding( domainParam );

		if ( !domainParamBinding.isMultiValued() ) {
			// triggers normal processing
			return null;
		}

		final InListPredicate inListPredicate = new InListPredicate(
				(Expression) sqmPredicate.getTestExpression().accept( this )
		);

		inferableTypeAccessStack.push(
				() -> determineValueMapping( sqmPredicate.getTestExpression() )
		);

		try {
			boolean first = true;
			for ( Object bindValue : domainParamBinding.getBindValues() ) {
				final SqmParameter sqmParamToConsume;
				// for each bind value create an "expansion"
				if ( first ) {
					sqmParamToConsume = sqmParameter;
					first = false;
				}
				else {
					sqmParamToConsume = sqmParameter.copy();
					domainParameterXref.addExpansion( domainParam, sqmParameter, sqmParamToConsume );
				}

				inListPredicate.addExpression( consumeSqmParameter( sqmParamToConsume ) );
			}
		}
		finally {
			inferableTypeAccessStack.pop();
		}

		return inListPredicate;
	}

	private InListPredicate processInSingleCriteriaParameter(
			SqmInListPredicate<?> sqmPredicate,
			JpaCriteriaParameter jpaCriteriaParameter) {
		assert jpaCriteriaParameter.allowsMultiValuedBinding();

		final QueryParameterBinding domainParamBinding = domainParameterBindings.getBinding( jpaCriteriaParameter );
		if ( !domainParamBinding.isMultiValued() ) {
			return null;
		}

		final InListPredicate inListPredicate = new InListPredicate(
				(Expression) sqmPredicate.getTestExpression().accept( this )
		);

		inferableTypeAccessStack.push(
				() -> determineValueMapping( sqmPredicate.getTestExpression() )
		);

		final SqmJpaCriteriaParameterWrapper<?> sqmWrapper = jpaCriteriaParamResolutions.get( jpaCriteriaParameter )
				.get();

		try {
			boolean first = true;
			for ( Object bindValue : domainParamBinding.getBindValues() ) {
				final SqmParameter sqmParamToConsume;
				// for each bind value create an "expansion"
				if ( first ) {
					sqmParamToConsume = sqmWrapper;
					first = false;
				}
				else {
					sqmParamToConsume = sqmWrapper.copy();
					domainParameterXref.addExpansion( jpaCriteriaParameter, sqmWrapper, sqmParamToConsume );
				}

				inListPredicate.addExpression( consumeSqmParameter( sqmParamToConsume ) );
			}
		}
		finally {
			inferableTypeAccessStack.pop();
		}

		return inListPredicate;
	}

	@Override
	public InSubQueryPredicate visitInSubQueryPredicate(SqmInSubQueryPredicate predicate) {
		return new InSubQueryPredicate(
				(Expression) predicate.getTestExpression().accept( this ),
				(QueryPart) predicate.getSubQueryExpression().accept( this ),
				predicate.isNegated()
		);
	}

	@Override
	public Object visitBooleanExpressionPredicate(SqmBooleanExpressionPredicate predicate) {
		Object booleanExpression = predicate.getBooleanExpression().accept( this );
		if ( booleanExpression instanceof SelfRenderingExpression ) {
			return new SelfRenderingPredicate( (SelfRenderingExpression) booleanExpression );
		}
		else {
			return new ComparisonPredicate(
					(Expression) booleanExpression,
					ComparisonOperator.EQUAL,
					new QueryLiteral<>( true, basicType( Boolean.class ) )
			);
		}
	}

	@Override
	public Object visitExistsPredicate(SqmExistsPredicate predicate) {
		return new ExistsPredicate( (QueryPart) predicate.getExpression().accept( this ) );
	}

	@Override
	public SqlAstCreationState getSqlAstCreationState() {
		return this;
	}

	@Override
	public Object visitFullyQualifiedClass(Class namedClass) {
		throw new NotYetImplementedFor6Exception();

		// what exactly is the expected end result here?

//		final MetamodelImplementor metamodel = getSessionFactory().getMetamodel();
//		final TypeConfiguration typeConfiguration = getSessionFactory().getTypeConfiguration();
//
//		// see if it is an entity-type
//		final EntityTypeDescriptor entityDescriptor = metamodel.findEntityDescriptor( namedClass );
//		if ( entityDescriptor != null ) {
//			throw new NotYetImplementedFor6Exception( "Add support for entity type literals as SqlExpression" );
//		}
//
//
//		final JavaTypeDescriptor jtd = typeConfiguration
//				.getJavaTypeDescriptorRegistry()
//				.getOrMakeJavaDescriptor( namedClass );
	}

	@Override
	public List<Fetch> visitFetches(FetchParent fetchParent) {
		final List<Fetch> fetches = CollectionHelper.arrayList( fetchParent.getReferencedMappingType().getNumberOfFetchables() );
		final List<String> bagRoles = new ArrayList<>();

		final BiConsumer<Fetchable, Boolean> fetchableBiConsumer = (fetchable, isKeyFetchable) -> {
			final NavigablePath fetchablePath = fetchParent.getNavigablePath().append( fetchable.getFetchableName() );

			final Fetch biDirectionalFetch = fetchable.resolveCircularFetch(
					fetchablePath,
					fetchParent,
					this
			);

			if ( biDirectionalFetch != null ) {
				fetches.add( biDirectionalFetch );
				return;
			}

			final boolean incrementFetchDepth = fetchable.incrementFetchDepth();
			try {
				if ( incrementFetchDepth ) {
					fetchDepth++;
				}
				final Fetch fetch = buildFetch( fetchablePath, fetchParent, fetchable, isKeyFetchable );

				if ( fetch != null ) {
					if ( fetch.getTiming() == FetchTiming.IMMEDIATE && fetchable instanceof PluralAttributeMapping ) {
						final PluralAttributeMapping pluralAttributeMapping = (PluralAttributeMapping) fetchable;
						final CollectionClassification collectionClassification = pluralAttributeMapping.getMappedType()
								.getCollectionSemantics()
								.getCollectionClassification();
						if ( collectionClassification == CollectionClassification.BAG ) {
							bagRoles.add( fetchable.getNavigableRole().getNavigableName() );
						}
					}

					fetches.add( fetch );
				}
			}
			finally {
				if ( incrementFetchDepth ) {
					fetchDepth--;
				}
			}
		};

// todo (6.0) : determine how to best handle TREAT
//		fetchParent.getReferencedMappingContainer().visitKeyFetchables( fetchableBiConsumer, treatTargetType );
//		fetchParent.getReferencedMappingContainer().visitFetchables( fetchableBiConsumer, treatTargetType );
		fetchParent.getReferencedMappingContainer().visitKeyFetchables( fetchable -> fetchableBiConsumer.accept( fetchable, true ), null );
		fetchParent.getReferencedMappingContainer().visitFetchables( fetchable -> fetchableBiConsumer.accept( fetchable, false ), null );
		if ( bagRoles.size() > 1 ) {
			throw new MultipleBagFetchException( bagRoles );
		}
		return fetches;
	}

	private Fetch buildFetch(NavigablePath fetchablePath, FetchParent fetchParent, Fetchable fetchable, boolean isKeyFetchable) {
		// fetch has access to its parent in addition to the parent having its fetches.
		//
		// we could sever the parent -> fetch link ... it would not be "seen" while walking
		// but it would still have access to its parent info - and be able to access its
		// "initializing" state as part of AfterLoadAction

		final String alias;
		LockMode lockMode = LockMode.READ;
		FetchTiming fetchTiming = fetchable.getMappedFetchOptions().getTiming();
		boolean joined = false;

		EntityGraphTraversalState.TraversalResult traversalResult = null;
		final FromClauseIndex fromClauseIndex = getFromClauseIndex();
		final SqmAttributeJoin fetchedJoin = fromClauseIndex.findFetchedJoinByPath( fetchablePath );

		if ( fetchedJoin != null ) {
			// there was an explicit fetch in the SQM
			//		there should be a TableGroupJoin registered for this `fetchablePath` already
			//		because it
			assert fromClauseIndex.getTableGroup( fetchablePath ) != null;

//
			if ( fetchedJoin.isFetched() ) {
				fetchTiming = FetchTiming.IMMEDIATE;
			}
			joined = true;
			alias = fetchedJoin.getExplicitAlias();
			lockMode = determineLockMode( alias );
		}
		else {
			// there was not an explicit fetch in the SQM
			alias = null;

			if ( !( fetchable instanceof CollectionPart ) ) {
				if ( entityGraphTraversalState != null ) {
					traversalResult = entityGraphTraversalState.traverse( fetchParent, fetchable, isKeyFetchable );
					fetchTiming = traversalResult.getFetchStrategy();
					joined = traversalResult.isJoined();
				}
				else if ( getLoadQueryInfluencers().hasEnabledFetchProfiles() ) {
					if ( fetchParent instanceof EntityResultGraphNode ) {
						final EntityResultGraphNode entityFetchParent = (EntityResultGraphNode) fetchParent;
						final EntityMappingType entityMappingType = entityFetchParent.getEntityValuedModelPart()
								.getEntityMappingType();
						final String fetchParentEntityName = entityMappingType.getEntityName();
						final String fetchableRole = fetchParentEntityName + "." + fetchable.getFetchableName();

						for ( String enabledFetchProfileName : getLoadQueryInfluencers().getEnabledFetchProfileNames() ) {
							final FetchProfile enabledFetchProfile = getCreationContext().getSessionFactory()
									.getFetchProfile( enabledFetchProfileName );
							final org.hibernate.engine.profile.Fetch profileFetch = enabledFetchProfile.getFetchByRole(
									fetchableRole );

							fetchTiming = FetchTiming.IMMEDIATE;
							joined = joined || profileFetch.getStyle() == org.hibernate.engine.profile.Fetch.Style.JOIN;
						}
					}
				}
			}

			final TableGroup existingJoinedGroup = fromClauseIndex.findTableGroup( fetchablePath );
			if ( existingJoinedGroup !=  null ) {
				// we can use this to trigger the fetch from the joined group.

				// todo (6.0) : do we want to do this though?
				//  	On the positive side it would allow EntityGraph to use the existing TableGroup.  But that ties in
				//  	to the discussion above regarding how to handle eager and EntityGraph (JOIN versus SELECT).
				//		Can be problematic if the existing one is restricted
				//fetchTiming = FetchTiming.IMMEDIATE;
			}

			// lastly, account for any app-defined max-fetch-depth
			final Integer maxDepth = getCreationContext().getMaximumFetchDepth();
			if ( maxDepth != null ) {
				if ( fetchDepth >= maxDepth ) {
					joined = false;
				}
			}

			if ( joined && fetchable instanceof TableGroupJoinProducer ) {
				fromClauseIndex.resolveTableGroup(
						fetchablePath,
						np -> {
							// generate the join
							final TableGroup lhs = fromClauseIndex.getTableGroup( fetchParent.getNavigablePath() );
							final TableGroupJoin tableGroupJoin = ( (TableGroupJoinProducer) fetchable ).createTableGroupJoin(
									fetchablePath,
									lhs,
									alias,
									SqlAstJoinType.LEFT,
									LockMode.NONE,
									this
							);
							return tableGroupJoin.getJoinedGroup();
						}
				);

			}
		}

		try {
			final Fetch fetch = fetchable.generateFetch(
					fetchParent,
					fetchablePath,
					fetchTiming,
					joined,
					lockMode,
					alias,
					this
			);

			if ( fetchable instanceof PluralAttributeMapping && fetch.getTiming() == FetchTiming.IMMEDIATE && joined ) {
				final PluralAttributeMapping pluralAttributeMapping = (PluralAttributeMapping) fetchable;

				final Joinable joinable = pluralAttributeMapping
						.getCollectionDescriptor()
						.getCollectionType()
						.getAssociatedJoinable( getCreationContext().getSessionFactory() );
				final TableGroup tableGroup = fromClauseIndex.getTableGroup( fetchablePath );
				final FilterPredicate collectionFieldFilterPredicate = FilterHelper.createFilterPredicate(
						getLoadQueryInfluencers(),
						joinable,
						tableGroup
				);
				if ( collectionFieldFilterPredicate != null ) {
					if ( collectionFilterPredicates == null ) {
						collectionFilterPredicates = new HashMap<>();
					}
					collectionFilterPredicates.put( tableGroup.getGroupAlias(), collectionFieldFilterPredicate );
				}
				if ( pluralAttributeMapping.getCollectionDescriptor().isManyToMany() ) {
					assert joinable instanceof CollectionPersister;
					final Predicate manyToManyFilterPredicate = FilterHelper.createManyToManyFilterPredicate(
							getLoadQueryInfluencers(),
							( CollectionPersister) joinable,
							tableGroup
					);
					if ( manyToManyFilterPredicate != null ) {
						assert tableGroup.getTableReferenceJoins() != null &&
								tableGroup.getTableReferenceJoins().size() == 1;
						tableGroup.getTableReferenceJoins().get( 0 ).applyPredicate( manyToManyFilterPredicate );
					}
				}

				if ( orderByFragmentConsumer != null ) {
					assert tableGroup.getModelPart() == pluralAttributeMapping;

					if ( pluralAttributeMapping.getOrderByFragment() != null ) {
						orderByFragmentConsumer.accept( pluralAttributeMapping.getOrderByFragment(), tableGroup );
					}

					if ( pluralAttributeMapping.getManyToManyOrderByFragment() != null ) {
						orderByFragmentConsumer.accept( pluralAttributeMapping.getManyToManyOrderByFragment(), tableGroup );
					}
				}
			}

			return fetch;
		}
		catch (RuntimeException e) {
			throw new HibernateException(
					String.format(
							Locale.ROOT,
							"Could not generate fetch : %s -> %s",
							fetchParent.getNavigablePath(),
							fetchable.getFetchableName()
					),
					e
			);
		}
		finally {
			if ( entityGraphTraversalState != null && traversalResult != null ) {
				entityGraphTraversalState.backtrack( traversalResult.getPreviousContext() );
			}
		}
	}

	protected interface SqlSelectionForSqmSelectionCollector {
		void next();
		List<SqlSelection> getSelections(int position);
	}

	protected static class DelegatingSqlSelectionForSqmSelectionCollector implements SqlExpressionResolver, SqlSelectionForSqmSelectionCollector {

		private final SqlExpressionResolver delegate;
		private SqlSelectionForSqmSelectionCollector sqlSelectionForSqmSelectionCollector;

		public DelegatingSqlSelectionForSqmSelectionCollector(SqlExpressionResolver delegate) {
			this.delegate = delegate;
		}

		@Override
		public void next() {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<SqlSelection> getSelections(int position) {
			return sqlSelectionForSqmSelectionCollector.getSelections( position );
		}

		@Override
		public Expression resolveSqlExpression(String key, Function<SqlAstProcessingState, Expression> creator) {
			return delegate.resolveSqlExpression( key, creator );
		}

		@Override
		public SqlSelection resolveSqlSelection(
				Expression expression,
				JavaTypeDescriptor javaTypeDescriptor,
				TypeConfiguration typeConfiguration) {
			return delegate.resolveSqlSelection( expression, javaTypeDescriptor, typeConfiguration );
		}

		public SqlSelectionForSqmSelectionCollector getSqlSelectionForSqmSelectionCollector() {
			return sqlSelectionForSqmSelectionCollector;
		}

		public void setSqlSelectionForSqmSelectionCollector(SqlSelectionForSqmSelectionCollector sqlSelectionForSqmSelectionCollector) {
			this.sqlSelectionForSqmSelectionCollector = sqlSelectionForSqmSelectionCollector;
		}
	}

	protected static class SqlSelectionForSqmSelectionResolver implements SqlExpressionResolver, SqlSelectionForSqmSelectionCollector {

		private final SqlExpressionResolver delegate;
		private final List<SqlSelection>[] sqlSelectionsForSqmSelection;
		private int index = -1;

		public SqlSelectionForSqmSelectionResolver(SqlExpressionResolver delegate, int sqmSelectionCount) {
			this.delegate = delegate;
			sqlSelectionsForSqmSelection = new List[sqmSelectionCount];
		}

		@Override
		public void next() {
			index++;
		}

		@Override
		public List<SqlSelection> getSelections(int position) {
			return sqlSelectionsForSqmSelection[position - 1];
		}

		@Override
		public Expression resolveSqlExpression(String key, Function<SqlAstProcessingState, Expression> creator) {
			return delegate.resolveSqlExpression( key, creator );
		}

		@Override
		public SqlSelection resolveSqlSelection(
				Expression expression,
				JavaTypeDescriptor javaTypeDescriptor,
				TypeConfiguration typeConfiguration) {
			SqlSelection selection = delegate.resolveSqlSelection( expression, javaTypeDescriptor, typeConfiguration );
			List<SqlSelection> sqlSelectionList = sqlSelectionsForSqmSelection[index];
			if ( sqlSelectionList == null ) {
				sqlSelectionsForSqmSelection[index] = sqlSelectionList = new ArrayList<>();
			}
			sqlSelectionList.add( selection );
			return selection;
		}
	}
}
