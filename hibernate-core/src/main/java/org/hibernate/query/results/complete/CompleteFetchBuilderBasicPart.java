/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.results.complete;

import java.util.function.BiFunction;

import org.hibernate.LockMode;
import org.hibernate.engine.FetchTiming;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.results.DomainResultCreationStateImpl;
import org.hibernate.query.results.MissingSqlSelectionException;
import org.hibernate.query.results.PositionalSelectionsNotAllowedException;
import org.hibernate.query.results.SqlSelectionImpl;
import org.hibernate.query.results.dynamic.DynamicFetchBuilderLegacy;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMetadata;

import static org.hibernate.query.results.ResultsHelper.impl;
import static org.hibernate.query.results.ResultsHelper.jdbcPositionToValuesArrayPosition;
import static org.hibernate.sql.ast.spi.SqlExpressionResolver.createColumnReferenceKey;

/**
 * @author Steve Ebersole
 */
public class CompleteFetchBuilderBasicPart implements CompleteFetchBuilder, ModelPartReferenceBasic {
	private final NavigablePath navigablePath;
	private final BasicValuedModelPart referencedModelPart;
	private final String selectionAlias;

	public CompleteFetchBuilderBasicPart(
			NavigablePath navigablePath,
			BasicValuedModelPart referencedModelPart,
			String selectionAlias) {
		this.navigablePath = navigablePath;
		this.referencedModelPart = referencedModelPart;
		this.selectionAlias = selectionAlias;
	}

	@Override
	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	@Override
	public BasicValuedModelPart getReferencedPart() {
		return referencedModelPart;
	}

	@Override
	public Fetch buildFetch(
			FetchParent parent,
			NavigablePath fetchPath,
			JdbcValuesMetadata jdbcResultsMetadata,
			BiFunction<String, String, DynamicFetchBuilderLegacy> legacyFetchResolver,
			DomainResultCreationState domainResultCreationState) {
		final DomainResultCreationStateImpl creationState = impl( domainResultCreationState );

		final String mappedTable = referencedModelPart.getContainingTableExpression();
		final String mappedColumn = referencedModelPart.getSelectionExpression();

		final TableGroup tableGroup = creationState.getFromClauseAccess().getTableGroup( parent.getNavigablePath() );
		final TableReference tableReference = tableGroup.getTableReference( mappedTable );

		final String selectedAlias;
		final int jdbcPosition;

		if ( selectionAlias != null ) {
			try {
				jdbcPosition = jdbcResultsMetadata.resolveColumnPosition( selectionAlias );
			}
			catch (Exception e) {
				throw new MissingSqlSelectionException(
						"ResultSet mapping specified selected-alias `" + selectionAlias
								+ "` which was not part of the ResultSet",
						e
				);
			}
			selectedAlias = selectionAlias;
		}
		else {
			if ( ! creationState.arePositionalSelectionsAllowed() ) {
				throw new PositionalSelectionsNotAllowedException(
						"Positional SQL selection resolution not allowed"
				);
			}
			jdbcPosition = creationState.getNumberOfProcessedSelections();
			selectedAlias = jdbcResultsMetadata.resolveColumnName( jdbcPosition );
		}

		final int valuesArrayPosition = jdbcPositionToValuesArrayPosition( jdbcPosition );

		// we just care about the registration here.  The ModelPart will find it later
		creationState.resolveSqlExpression(
				createColumnReferenceKey( tableReference, mappedColumn ),
				processingState -> new SqlSelectionImpl( valuesArrayPosition, referencedModelPart )
		);

		return referencedModelPart.generateFetch(
				parent,
				fetchPath,
				FetchTiming.IMMEDIATE,
				true,
				LockMode.READ,
				selectedAlias,
				domainResultCreationState
		);
	}
}
