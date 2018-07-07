/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.loader.plan.exec.internal;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.QueryParameters;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.loader.MultipleBagFetchException;
import org.hibernate.loader.plan.build.spi.LoadPlanTreePrinter;
import org.hibernate.loader.plan.exec.process.internal.ResultSetProcessorImpl;
import org.hibernate.loader.plan.exec.process.spi.CollectionReferenceInitializer;
import org.hibernate.loader.plan.exec.process.spi.EntityReferenceInitializer;
import org.hibernate.loader.plan.exec.process.spi.ReaderCollector;
import org.hibernate.loader.plan.exec.process.spi.ResultSetProcessor;
import org.hibernate.loader.plan.exec.query.internal.SelectStatementBuilder;
import org.hibernate.loader.plan.exec.query.spi.QueryBuildingParameters;
import org.hibernate.loader.plan.exec.spi.AliasResolutionContext;
import org.hibernate.loader.plan.exec.spi.LoadQueryDetails;
import org.hibernate.loader.plan.spi.CollectionAttributeFetch;
import org.hibernate.loader.plan.spi.CollectionReturn;
import org.hibernate.loader.plan.spi.FetchSource;
import org.hibernate.loader.plan.spi.LoadPlan;
import org.hibernate.loader.plan.spi.QuerySpace;
import org.hibernate.loader.plan.spi.Return;
import org.hibernate.sql.ConditionFragment;
import org.hibernate.sql.DisjunctionFragment;
import org.hibernate.sql.InFragment;
import org.hibernate.type.AbstractStandardBasicType;
import org.hibernate.type.ArrayType;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

/**
 * @author Gail Badner
 */
public abstract class AbstractLoadQueryDetails implements LoadQueryDetails {

	private final LoadPlan loadPlan;
	private final String[] keyColumnNames;
	private final Return rootReturn;
	private final LoadQueryJoinAndFetchProcessor queryProcessor;
	private final QueryBuildingParameters buildingParameters;
	private String sqlStatement;
	private ResultSetProcessor resultSetProcessor;
	private boolean hasArrayRestriction;

	/**
	 * @param rootReturn The root return reference we are processing
	 * @param factory The SessionFactory
	 * @param buildingParameters The query building context
	 */
	protected AbstractLoadQueryDetails(
			LoadPlan loadPlan,
			AliasResolutionContextImpl aliasResolutionContext,
			QueryBuildingParameters buildingParameters,
			String[] keyColumnNames,
			Return rootReturn,
			SessionFactoryImplementor factory) {
		this.keyColumnNames = keyColumnNames;
		this.rootReturn = rootReturn;
		this.loadPlan = loadPlan;
		this.queryProcessor = new LoadQueryJoinAndFetchProcessor(
				aliasResolutionContext, buildingParameters.getQueryInfluencers(), factory
		);
		this.buildingParameters = buildingParameters;
	}

	/**
	 * Constructs an AbstractLoadQueryDetails object from an initial object and new building parameters,
	 * with the guarantee that only batch size changed between the initial parameters and the new ones.
	 *
	 * @param initialLoadQueryDetails The initial object to be copied
	 * @param buildingParameters The new building parameters, with only the batch size being different
	 * from the parameters used in the initial object.
	 */
	protected AbstractLoadQueryDetails(
			AbstractLoadQueryDetails initialLoadQueryDetails,
			QueryBuildingParameters buildingParameters) {
		this.keyColumnNames = initialLoadQueryDetails.keyColumnNames;
		this.rootReturn = initialLoadQueryDetails.rootReturn;
		this.loadPlan = initialLoadQueryDetails.loadPlan;
		this.queryProcessor = new LoadQueryJoinAndFetchProcessor(
				initialLoadQueryDetails.queryProcessor, buildingParameters.getQueryInfluencers()
		);
		this.buildingParameters = buildingParameters;
	}

	protected QuerySpace getQuerySpace(String querySpaceUid) {
		return loadPlan.getQuerySpaces().getQuerySpaceByUid( querySpaceUid );
	}

	@Override
	public String getSqlStatement() {
		return sqlStatement;
	}

	@Override
	public ResultSetProcessor getResultSetProcessor() {
		return resultSetProcessor;
	}

	protected final Return getRootReturn() {
		return rootReturn;
	}

	protected final AliasResolutionContext getAliasResolutionContext() {
		return queryProcessor.getAliasResolutionContext();
	}

	protected final QueryBuildingParameters getQueryBuildingParameters() {
		return buildingParameters;
	}

	protected final SessionFactoryImplementor getSessionFactory() {
		return queryProcessor.getSessionFactory();
	}

	protected LoadPlan getLoadPlan() {
		return loadPlan;
	}

	protected String[] getKeyColumnNames() {
		return keyColumnNames;
	}

	/**
	 * Main entry point for properly handling the FROM clause and and joins and restrictions
	 *
	 */
	protected void generate() {
		// There are 2 high-level requirements to perform here:
		// 	1) Determine the SQL required to carry out the given LoadPlan (and fulfill
		// 		{@code LoadQueryDetails#getSqlStatement()}).  SelectStatementBuilder collects the ongoing efforts to
		//		build the needed SQL.
		// 	2) Determine how to read information out of the ResultSet resulting from executing the indicated SQL
		//		(the SQL aliases).  ReaderCollector and friends are where this work happens, ultimately
		//		producing a ResultSetProcessor

		final Dialect dialect = queryProcessor.getSessionFactory().getDialect();
		final SelectStatementBuilder select = new SelectStatementBuilder( dialect );

		// LoadPlan is broken down into 2 high-level pieces that we need to process here.
		//
		// First is the QuerySpaces, which roughly equates to the SQL FROM-clause.  We'll cycle through
		// those first, generating aliases into the AliasContext in addition to writing SQL FROM-clause information
		// into SelectStatementBuilder.  The AliasContext is populated here and the reused while process the SQL
		// SELECT-clause into the SelectStatementBuilder and then again also to build the ResultSetProcessor

		applyRootReturnTableFragments( select );

		String arrayRestriction = dialect.getArrayRestriction(getRootTableAlias(), keyColumnNames[0], getQueryBuildingParameters().getBatchSize());
		hasArrayRestriction = arrayRestriction != null;
		if ( shouldApplyRootReturnFilterBeforeKeyRestriction() ) {
			applyRootReturnFilterRestrictions( select );
			// add restrictions...
			// first, the load key restrictions (which entity(s)/collection(s) do we want to load?)
			applyKeyRestriction(
					select,
					getRootTableAlias(),
					keyColumnNames,
					getQueryBuildingParameters().getBatchSize(),
					arrayRestriction
			);
		}
		else {
			// add restrictions...
			// first, the load key restrictions (which entity(s)/collection(s) do we want to load?)
			applyKeyRestriction(
					select,
					getRootTableAlias(),
					keyColumnNames,
					getQueryBuildingParameters().getBatchSize(),
					arrayRestriction
			);
			applyRootReturnFilterRestrictions( select );
		}


		applyRootReturnWhereJoinRestrictions( select );

		applyRootReturnOrderByFragments( select );
		// then move on to joins...

		applyRootReturnSelectFragments( select );

		queryProcessor.processQuerySpaceJoins( getRootQuerySpace(), select );

		// Next, we process the Returns and Fetches building the SELECT clause and at the same time building
		// Readers for reading the described results out of a SQL ResultSet

		FetchStats fetchStats = null;
		if ( FetchSource.class.isInstance( rootReturn ) ) {
			fetchStats = queryProcessor.processFetches(
					(FetchSource) rootReturn,
					select,
					getReaderCollector()
			);
		}
		else if ( CollectionReturn.class.isInstance( rootReturn ) ) {
			final CollectionReturn collectionReturn = (CollectionReturn) rootReturn;
			if ( collectionReturn.getElementGraph() != null ) {
				fetchStats = queryProcessor.processFetches(
						collectionReturn.getElementGraph(),
						select,
						getReaderCollector()
				);
			}
			// TODO: what about index???
		}

		if ( fetchStats != null && fetchStats.getJoinedBagAttributeFetches().size() > 1 ) {
			final List<String> bagRoles = new ArrayList<>();
			for ( CollectionAttributeFetch bagFetch : fetchStats.getJoinedBagAttributeFetches() ) {
				bagRoles.add( bagFetch.getCollectionPersister().getRole() );
			}
			throw new MultipleBagFetchException( bagRoles );
		}

		LoadPlanTreePrinter.INSTANCE.logTree( loadPlan, queryProcessor.getAliasResolutionContext() );

		this.sqlStatement = select.toStatementString();
		this.resultSetProcessor = new ResultSetProcessorImpl(
				loadPlan,
				queryProcessor.getAliasResolutionContext(),
				getReaderCollector().buildRowReader(),
				shouldUseOptionalEntityInstance(),
				isSubselectLoadingEnabled( fetchStats )
		);
	}

	@Override
	public void modifyQueryParameters(QueryParameters qp) {
		if (hasArrayRestriction) {
			Type elementType = qp.getPositionalParameterTypes()[0];
			if (elementType instanceof AbstractStandardBasicType<?>) {
				SqlTypeDescriptor sqlType = ((AbstractStandardBasicType<?>)elementType).getSqlTypeDescriptor();
				Object[] elementValues = qp.getPositionalParameterValues();

				Type arrayType = new ArrayType(sqlType, queryProcessor.getSessionFactory().getDialect(), elementType.getReturnedClass());
				Type[] arrayTypes = { arrayType };
				Object[] arrayValues = { elementValues };
				qp.setPositionalParameterTypes(arrayTypes);
				qp.setPositionalParameterValues(arrayValues);
			}
		}
	}

	/**
	 * Is subselect loading enabled?
	 *
	 * @param fetchStats the fetch stats; may be null
	 * @return {@code true} if subselect loading is enabled; {@code false} otherwise.
	 */
	protected abstract boolean isSubselectLoadingEnabled(FetchStats fetchStats);

	protected abstract boolean shouldUseOptionalEntityInstance();
	protected abstract ReaderCollector getReaderCollector();
	protected abstract QuerySpace getRootQuerySpace();
	protected abstract String getRootTableAlias();
	protected abstract boolean shouldApplyRootReturnFilterBeforeKeyRestriction();
	protected abstract void applyRootReturnSelectFragments(SelectStatementBuilder selectStatementBuilder );
	protected abstract void applyRootReturnTableFragments(SelectStatementBuilder selectStatementBuilder);
	protected abstract void applyRootReturnFilterRestrictions(SelectStatementBuilder selectStatementBuilder);
	protected abstract void applyRootReturnWhereJoinRestrictions(SelectStatementBuilder selectStatementBuilder);
	protected abstract void applyRootReturnOrderByFragments(SelectStatementBuilder selectStatementBuilder);


	private static void applyKeyRestriction(
			SelectStatementBuilder select,
			String alias,
			String[] keyColumnNames,
			int batchSize,
			String arrayRestriction) {
		if ( keyColumnNames.length==1 ) {
				// NOT A COMPOSITE KEY
				//	 	for batching, use "foo in (?, ?, ?)" for batching
				//		for no batching, use "foo = ?"
				// (that distinction is handled inside InFragment)
				if (arrayRestriction != null) {
					select.appendRestrictions(arrayRestriction);
				}
				else {
					final InFragment in = new InFragment().setColumn( alias, keyColumnNames[0] );
					for ( int i = 0; i < batchSize; i++ ) {
						in.addValue( "?" );
					}
					select.appendRestrictions( in.toFragmentString() );
				}
		}
		else {
			// A COMPOSITE KEY...
			final ConditionFragment keyRestrictionBuilder = new ConditionFragment()
					.setTableAlias( alias )
					.setCondition( keyColumnNames, "?" );
			final String keyRestrictionFragment = keyRestrictionBuilder.toFragmentString();

			StringBuilder restrictions = new StringBuilder();
			if ( batchSize==1 ) {
				// for no batching, use "foo = ? and bar = ?"
				restrictions.append( keyRestrictionFragment );
			}
			else {
				// for batching, use "( (foo = ? and bar = ?) or (foo = ? and bar = ?) )"
				restrictions.append( '(' );
				DisjunctionFragment df = new DisjunctionFragment();
				for ( int i=0; i<batchSize; i++ ) {
					df.addCondition( keyRestrictionFragment );
				}
				restrictions.append( df.toFragmentString() );
				restrictions.append( ')' );
			}
			select.appendRestrictions( restrictions.toString() );
		}
	}

	protected abstract static class ReaderCollectorImpl implements ReaderCollector {
		private final List<EntityReferenceInitializer> entityReferenceInitializers = new ArrayList<EntityReferenceInitializer>();
		private List<CollectionReferenceInitializer> arrayReferenceInitializers;
		private List<CollectionReferenceInitializer> collectionReferenceInitializers;

		@Override
		public void add(CollectionReferenceInitializer collectionReferenceInitializer) {
			if ( collectionReferenceInitializer.getCollectionReference().getCollectionPersister().isArray() ) {
				if ( arrayReferenceInitializers == null ) {
					arrayReferenceInitializers = new ArrayList<CollectionReferenceInitializer>();
				}
				arrayReferenceInitializers.add( collectionReferenceInitializer );
			}
			else {
				if ( collectionReferenceInitializers == null ) {
					collectionReferenceInitializers = new ArrayList<CollectionReferenceInitializer>();
				}
				collectionReferenceInitializers.add( collectionReferenceInitializer );
			}
		}

		@Override
		public void add(EntityReferenceInitializer entityReferenceInitializer) {
			entityReferenceInitializers.add( entityReferenceInitializer );
		}

		@Override
		public final List<EntityReferenceInitializer> getEntityReferenceInitializers() {
			return entityReferenceInitializers;
		}

		@Override
		public List<CollectionReferenceInitializer> getArrayReferenceInitializers() {
			return arrayReferenceInitializers;

		}

		@Override
		public List<CollectionReferenceInitializer> getNonArrayCollectionReferenceInitializers() {
			return collectionReferenceInitializers;
		}
	}
}
