/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.expression;

import org.hibernate.metamodel.model.domain.PluralPersistentAttribute;
import org.hibernate.query.AllowableParameterType;
import org.hibernate.query.internal.QueryHelper;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SqmExpressable;

/**
 * Common support for SqmParameter impls
 *
 * @author Steve Ebersole
 */
public abstract class AbstractSqmParameter<T> extends AbstractSqmExpression<T> implements SqmParameter<T> {
	private final boolean canBeMultiValued;

	public AbstractSqmParameter(
			boolean canBeMultiValued,
			SqmExpressable<T> inherentType,
			NodeBuilder nodeBuilder) {
		super( inherentType, nodeBuilder );
		this.canBeMultiValued = canBeMultiValued;
	}

	@Override
	public void applyInferableType(SqmExpressable<?> type) {
		if ( type == null ) {
			return;
		}
		else if ( type instanceof PluralPersistentAttribute<?, ?, ?> ) {
			type = ( (PluralPersistentAttribute<?, ?, ?>) type ).getElementType();
		}
		final SqmExpressable<T> oldType = getNodeType();

		final SqmExpressable<?> newType = QueryHelper.highestPrecedenceType( oldType, type );
		if ( newType != null && newType != oldType ) {
			internalApplyInferableType( newType );
		}
	}

	@Override
	public String getName() {
		return null;
	}

	@Override
	public Integer getPosition() {
		return null;
	}

	@Override
	public boolean allowMultiValuedBinding() {
		return canBeMultiValued;
	}

	@Override
	public AllowableParameterType<T> getAnticipatedType() {
		return this.getNodeType();
	}

	@Override
	public Class<T> getParameterType() {
		return this.getNodeType().getExpressableJavaTypeDescriptor().getJavaTypeClass();
	}
}
