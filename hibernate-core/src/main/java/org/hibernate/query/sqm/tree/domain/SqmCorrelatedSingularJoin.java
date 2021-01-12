/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.domain;

import org.hibernate.metamodel.model.domain.SingularPersistentAttribute;
import org.hibernate.query.hql.spi.SqmCreationProcessingState;
import org.hibernate.query.hql.spi.SqmPathRegistry;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.tree.SqmJoinType;
import org.hibernate.query.sqm.tree.from.SqmFrom;
import org.hibernate.query.sqm.tree.from.SqmRoot;

/**
 * @author Christian Beikov
 */
public class SqmCorrelatedSingularJoin<O, T> extends SqmSingularJoin<O, T> implements SqmCorrelation<O, T> {

	private final SqmCorrelatedRootJoin<O> correlatedRootJoin;
	private final SqmSingularJoin<O, T> correlationParent;

	public SqmCorrelatedSingularJoin(SqmSingularJoin<O, T> correlationParent) {
		super(
				correlationParent.getLhs(),
				correlationParent.getAttribute(),
				null,
				SqmJoinType.INNER,
				false,
				correlationParent.nodeBuilder()
		);
		this.correlatedRootJoin = SqmCorrelatedRootJoin.create( correlationParent, this );
		this.correlationParent = correlationParent;
	}

	private SqmCorrelatedSingularJoin(
			SqmFrom<?, O> lhs,
			SingularPersistentAttribute<O, T> joinedNavigable,
			String alias,
			SqmJoinType joinType,
			boolean fetched,
			NodeBuilder nodeBuilder,
			SqmCorrelatedRootJoin<O> correlatedRootJoin,
			SqmSingularJoin<O, T> correlationParent) {
		super( lhs, joinedNavigable, alias, joinType, fetched, nodeBuilder );
		this.correlatedRootJoin = correlatedRootJoin;
		this.correlationParent = correlationParent;
	}

	@Override
	public SqmSingularJoin<O, T> getCorrelationParent() {
		return correlationParent;
	}

	@Override
	public SqmPath<T> getWrappedPath() {
		return correlationParent;
	}

	@Override
	public boolean isCorrelated() {
		return true;
	}

	@Override
	public SqmRoot<O> getCorrelatedRoot() {
		return correlatedRootJoin;
	}

	@Override
	public SqmCorrelatedSingularJoin<O, T> makeCopy(SqmCreationProcessingState creationProcessingState) {
		final SqmPathRegistry pathRegistry = creationProcessingState.getPathRegistry();
		//noinspection unchecked
		return new SqmCorrelatedSingularJoin<>(
				pathRegistry.findFromByPath( getLhs().getNavigablePath() ),
				getReferencedPathSource(),
				getExplicitAlias(),
				getSqmJoinType(),
				isFetched(),
				nodeBuilder(),
				(SqmCorrelatedRootJoin<O>) pathRegistry.findFromByPath( correlatedRootJoin.getNavigablePath() ),
				(SqmSingularJoin<O, T>) pathRegistry.findFromByPath( correlationParent.getNavigablePath() )
		);
	}
}
