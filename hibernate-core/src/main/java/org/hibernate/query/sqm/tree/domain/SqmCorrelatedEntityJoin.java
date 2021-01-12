/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.domain;

import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.query.hql.spi.SqmCreationProcessingState;
import org.hibernate.query.hql.spi.SqmPathRegistry;
import org.hibernate.query.sqm.tree.SqmJoinType;
import org.hibernate.query.sqm.tree.from.SqmEntityJoin;
import org.hibernate.query.sqm.tree.from.SqmRoot;

/**
 * @author Christian Beikov
 */
public class SqmCorrelatedEntityJoin<T> extends SqmEntityJoin<T> implements SqmCorrelation<T, T> {

	private final SqmCorrelatedRootJoin<T> correlatedRootJoin;
	private final SqmEntityJoin<T> correlationParent;

	public SqmCorrelatedEntityJoin(SqmEntityJoin<T> correlationParent) {
		super(
				correlationParent.getReferencedPathSource(),
				null,
				SqmJoinType.INNER,
				correlationParent.getRoot()
		);
		this.correlatedRootJoin = SqmCorrelatedRootJoin.create( correlationParent, this );
		this.correlationParent = correlationParent;
	}

	public SqmCorrelatedEntityJoin(
			EntityDomainType<T> joinedEntityDescriptor,
			String alias,
			SqmJoinType joinType,
			SqmRoot sqmRoot,
			SqmCorrelatedRootJoin<T> correlatedRootJoin,
			SqmEntityJoin<T> correlationParent) {
		super( joinedEntityDescriptor, alias, joinType, sqmRoot );
		this.correlatedRootJoin = correlatedRootJoin;
		this.correlationParent = correlationParent;
	}

	@Override
	public SqmEntityJoin<T> getCorrelationParent() {
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
	public SqmRoot<T> getCorrelatedRoot() {
		return correlatedRootJoin;
	}

	@Override
	public SqmCorrelatedEntityJoin<T> makeCopy(SqmCreationProcessingState creationProcessingState) {
		final SqmPathRegistry pathRegistry = creationProcessingState.getPathRegistry();
		//noinspection unchecked
		return new SqmCorrelatedEntityJoin<>(
				getReferencedPathSource(),
				getExplicitAlias(),
				getSqmJoinType(),
				(SqmRoot<?>) pathRegistry.findFromByPath( getRoot().getNavigablePath() ),
				(SqmCorrelatedRootJoin<T>) pathRegistry.findFromByPath( correlatedRootJoin.getNavigablePath() ),
				(SqmEntityJoin<T>) pathRegistry.findFromByPath( correlationParent.getNavigablePath() )
		);
	}
}
