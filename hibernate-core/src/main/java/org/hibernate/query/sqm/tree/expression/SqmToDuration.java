/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.expression;

import org.hibernate.metamodel.model.domain.AllowableFunctionReturnType;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SemanticQueryWalker;

/**
 * @author Gavin King
 */
public class SqmToDuration<T> extends AbstractSqmExpression<T> {
	private final SqmExpression<?> magnitude;
	private final SqmDurationUnit<?> unit;

	public SqmToDuration(
			SqmExpression<?> magnitude,
			SqmDurationUnit<?> unit,
			AllowableFunctionReturnType<T> type,
			NodeBuilder nodeBuilder) {
		super( type, nodeBuilder );
		this.magnitude = magnitude;
		this.unit = unit;
	}

	public SqmExpression<?> getMagnitude() {
		return magnitude;
	}

	public SqmDurationUnit<?> getUnit() {
		return unit;
	}

	@Override
	public <T> T accept(SemanticQueryWalker<T> walker) {
		return walker.visitToDuration( this );
	}

	@Override
	public String asLoggableText() {
		return magnitude.asLoggableText() + " " + unit.getUnit();
	}
}


