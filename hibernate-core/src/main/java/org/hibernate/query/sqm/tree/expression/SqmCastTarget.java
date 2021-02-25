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
import org.hibernate.query.sqm.SqmExpressable;
import org.hibernate.query.sqm.tree.AbstractSqmNode;
import org.hibernate.query.sqm.tree.SqmTypedNode;
import org.hibernate.query.sqm.tree.SqmVisitableNode;


/**
 * @author Gavin King
 */
public class SqmCastTarget<T> extends AbstractSqmNode implements SqmTypedNode<T>, SqmVisitableNode {
	private final AllowableFunctionReturnType<T> type;
	private final Long length;
	private final Integer precision;
	private final Integer scale;

	public Long getLength() {
		return length;
	}

	public Integer getPrecision() {
		return precision;
	}

	public Integer getScale() {
		return scale;
	}

	public SqmCastTarget(
			AllowableFunctionReturnType<T> type,
			NodeBuilder nodeBuilder) {
		this( type, null, nodeBuilder );
	}

	public SqmCastTarget(
			AllowableFunctionReturnType<T> type,
			Long length,
			NodeBuilder nodeBuilder) {
		this( type, length, null, null, nodeBuilder );
	}

	public SqmCastTarget(
			AllowableFunctionReturnType<T> type,
			Integer precision,
			Integer scale,
			NodeBuilder nodeBuilder) {
		this( type, null, precision, scale, nodeBuilder );
	}

	public SqmCastTarget(
			AllowableFunctionReturnType<T> type,
			Long length,
			Integer precision,
			Integer scale,
			NodeBuilder nodeBuilder) {
		super( nodeBuilder );
		this.type = type;
		this.length = length;
		this.precision = precision;
		this.scale = scale;
	}

	public AllowableFunctionReturnType<T> getType() {
		return type;
	}

	@Override
	public <T> T accept(SemanticQueryWalker<T> walker) {
		return walker.visitCastTarget(this);
	}

	@Override
	public SqmExpressable getNodeType() {
		return type;
	}
}
