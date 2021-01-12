/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.domain;

import org.hibernate.metamodel.model.domain.MapPersistentAttribute;
import org.hibernate.query.hql.spi.SqmCreationProcessingState;
import org.hibernate.query.hql.spi.SqmPathRegistry;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.tree.SqmJoinType;
import org.hibernate.query.sqm.tree.from.SqmFrom;
import org.hibernate.query.sqm.tree.from.SqmRoot;

/**
 * @author Christian Beikov
 */
public class SqmCorrelatedMapJoin<O, K, V> extends SqmMapJoin<O, K, V> implements SqmCorrelation<O, V> {

	private final SqmCorrelatedRootJoin<O> correlatedRootJoin;
	private final SqmMapJoin<O, K, V> correlationParent;

	public SqmCorrelatedMapJoin(SqmMapJoin<O, K, V> correlationParent) {
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

	private SqmCorrelatedMapJoin(
			SqmFrom<?, O> lhs,
			MapPersistentAttribute<O,K,V> attribute,
			String alias,
			SqmJoinType sqmJoinType,
			boolean fetched,
			NodeBuilder nodeBuilder,
			SqmCorrelatedRootJoin<O> correlatedRootJoin,
			SqmMapJoin<O, K, V> correlationParent) {
		super( lhs, attribute, alias, sqmJoinType, fetched, nodeBuilder );
		this.correlatedRootJoin = correlatedRootJoin;
		this.correlationParent = correlationParent;
	}

	@Override
	public SqmMapJoin<O, K, V> getCorrelationParent() {
		return correlationParent;
	}

	@Override
	public SqmPath<V> getWrappedPath() {
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
	public SqmCorrelatedMapJoin<O, K, V> makeCopy(SqmCreationProcessingState creationProcessingState) {
		final SqmPathRegistry pathRegistry = creationProcessingState.getPathRegistry();
		//noinspection unchecked
		return new SqmCorrelatedMapJoin<>(
				pathRegistry.findFromByPath( getLhs().getNavigablePath() ),
				getReferencedPathSource(),
				getExplicitAlias(),
				getSqmJoinType(),
				isFetched(),
				nodeBuilder(),
				(SqmCorrelatedRootJoin<O>) pathRegistry.findFromByPath( correlatedRootJoin.getNavigablePath() ),
				(SqmMapJoin<O, K, V>) pathRegistry.findFromByPath( correlationParent.getNavigablePath() )
		);
	}
}
