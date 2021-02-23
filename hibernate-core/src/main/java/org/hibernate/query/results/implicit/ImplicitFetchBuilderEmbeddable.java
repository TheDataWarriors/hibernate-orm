/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.results.implicit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.hibernate.LockMode;
import org.hibernate.engine.FetchTiming;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.results.Builders;
import org.hibernate.query.results.DomainResultCreationStateImpl;
import org.hibernate.query.results.FetchBuilder;
import org.hibernate.query.results.SqlSelectionImpl;
import org.hibernate.query.results.dynamic.DynamicFetchBuilderLegacy;
import org.hibernate.sql.ast.SqlAstJoinType;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.graph.embeddable.EmbeddableValuedFetchable;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMetadata;

import static org.hibernate.query.results.ResultsHelper.impl;
import static org.hibernate.query.results.ResultsHelper.jdbcPositionToValuesArrayPosition;
import static org.hibernate.sql.ast.spi.SqlExpressionResolver.createColumnReferenceKey;

/**
 * @author Steve Ebersole
 */
public class ImplicitFetchBuilderEmbeddable implements ImplicitFetchBuilder {
	private final NavigablePath fetchPath;
	private final EmbeddableValuedFetchable fetchable;
	private final Map<NavigablePath, FetchBuilder> fetchBuilders;

	public ImplicitFetchBuilderEmbeddable(
			NavigablePath fetchPath,
			EmbeddableValuedFetchable fetchable,
			DomainResultCreationState creationState) {
		this.fetchPath = fetchPath;
		this.fetchable = fetchable;
		final DomainResultCreationStateImpl creationStateImpl = impl( creationState );
		final NavigablePath relativePath = creationStateImpl.getCurrentRelativePath();
		final Function<String, FetchBuilder> fetchBuilderResolver = creationStateImpl.getCurrentExplicitFetchMementoResolver();
		final Map<NavigablePath, FetchBuilder> fetchBuilders = new LinkedHashMap<>( fetchable.getNumberOfFetchables() );
		fetchable.visitFetchables(
				subFetchable -> {
					final NavigablePath subFetchPath = relativePath.append( subFetchable.getFetchableName() );
					final FetchBuilder explicitFetchBuilder = fetchBuilderResolver
							.apply( subFetchPath.getFullPath() );
					if ( explicitFetchBuilder == null ) {
						fetchBuilders.put(
								subFetchPath,
								Builders.implicitFetchBuilder( fetchPath, subFetchable, creationStateImpl )
						);
					}
					else {
						fetchBuilders.put( subFetchPath, explicitFetchBuilder );
					}
				},
				null
		);
		this.fetchBuilders = fetchBuilders;
	}

	@Override
	public Fetch buildFetch(
			FetchParent parent,
			NavigablePath fetchPath,
			JdbcValuesMetadata jdbcResultsMetadata,
			BiFunction<String, String, DynamicFetchBuilderLegacy> legacyFetchResolver,
			DomainResultCreationState creationState) {
		final DomainResultCreationStateImpl creationStateImpl = impl( creationState );

		final TableGroup tableGroup = creationStateImpl.getFromClauseAccess().resolveTableGroup(
				fetchPath,
				navigablePath -> {
					final TableGroup parentTableGroup = creationStateImpl
							.getFromClauseAccess()
							.getTableGroup( parent.getNavigablePath() );
					final TableGroupJoin tableGroupJoin = fetchable.createTableGroupJoin(
							fetchPath,
							parentTableGroup,
							null,
							SqlAstJoinType.INNER,
							LockMode.READ,
							creationStateImpl
					);
					return tableGroupJoin.getJoinedGroup();
				}
		);

		final Fetch fetch = fetchable.generateFetch(
				parent,
				fetchPath,
				FetchTiming.IMMEDIATE,
				true,
				LockMode.READ,
				null,
				creationState
		);
		final FetchParent fetchParent = (FetchParent) fetch;
		fetchBuilders.forEach(
				(subFetchPath, fetchBuilder) -> fetchBuilder.buildFetch(
						fetchParent,
						subFetchPath,
						jdbcResultsMetadata,
						legacyFetchResolver,
						creationState
				)
		);

		return fetch;
	}

	@Override
	public String toString() {
		return "ImplicitFetchBuilderEmbeddable(" + fetchPath + ")";
	}
}
