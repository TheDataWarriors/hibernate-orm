/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.expression;

import org.hibernate.metamodel.model.domain.AllowableFunctionReturnType;
import org.hibernate.query.TemporalUnit;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SemanticQueryWalker;
import org.hibernate.query.sqm.SqmExpressable;
import org.hibernate.query.sqm.tree.AbstractSqmNode;
import org.hibernate.query.sqm.tree.SqmTypedNode;
import org.hibernate.query.sqm.tree.SqmVisitableNode;

/**
 * @author Gavin King
 */
public class SqmExtractUnit<T> extends AbstractSqmNode implements SqmTypedNode<T>, SqmVisitableNode {
	private final TemporalUnit unit;
	private final AllowableFunctionReturnType type;

	public SqmExtractUnit(TemporalUnit unit, AllowableFunctionReturnType<T> type, NodeBuilder nodeBuilder) {
		super( nodeBuilder );
		this.unit = unit;
		this.type = type;
	}

	public TemporalUnit getUnit() {
		return unit;
	}

	public AllowableFunctionReturnType getType() {
		return type;
	}

	@Override
	public <X> X accept(SemanticQueryWalker<X> walker) {
		return walker.visitExtractUnit( this );
	}

	@Override
	public SqmExpressable<T> getNodeType() {
		return type;
	}
}


