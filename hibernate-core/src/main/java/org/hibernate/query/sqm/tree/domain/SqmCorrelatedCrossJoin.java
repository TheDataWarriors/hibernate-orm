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
import org.hibernate.query.sqm.tree.from.SqmCrossJoin;
import org.hibernate.query.sqm.tree.from.SqmRoot;

/**
 * @author Christian Beikov
 */
public class SqmCorrelatedCrossJoin<T> extends SqmCrossJoin<T> implements SqmCorrelation<T, T> {

	private final SqmCorrelatedRootJoin<T> correlatedRootJoin;
	private final SqmCrossJoin<T> correlationParent;

	public SqmCorrelatedCrossJoin(SqmCrossJoin<T> correlationParent) {
		super(
				correlationParent.getReferencedPathSource(),
				null,
				correlationParent.getRoot()
		);
		this.correlatedRootJoin = SqmCorrelatedRootJoin.create( correlationParent, this );
		this.correlationParent = correlationParent;
	}

	private SqmCorrelatedCrossJoin(
			EntityDomainType<T> joinedEntityDescriptor,
			String alias,
			SqmRoot sqmRoot,
			SqmCorrelatedRootJoin<T> correlatedRootJoin,
			SqmCrossJoin<T> correlationParent) {
		super( joinedEntityDescriptor, alias, sqmRoot );
		this.correlatedRootJoin = correlatedRootJoin;
		this.correlationParent = correlationParent;
	}

	@Override
	public SqmCrossJoin<T> getCorrelationParent() {
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
	public SqmCorrelatedCrossJoin<T> makeCopy(SqmCreationProcessingState creationProcessingState) {
		final SqmPathRegistry pathRegistry = creationProcessingState.getPathRegistry();
		//noinspection unchecked
		return new SqmCorrelatedCrossJoin<>(
				getReferencedPathSource(),
				getExplicitAlias(),
				(SqmRoot<?>) pathRegistry.findFromByPath( getRoot().getNavigablePath() ),
				(SqmCorrelatedRootJoin<T>) pathRegistry.findFromByPath( correlatedRootJoin.getNavigablePath() ),
				(SqmCrossJoin<T>) pathRegistry.findFromByPath( correlationParent.getNavigablePath() )
		);
	}
}
