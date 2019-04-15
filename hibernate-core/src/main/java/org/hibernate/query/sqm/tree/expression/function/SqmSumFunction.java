/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.expression.function;

import org.hibernate.metamodel.model.domain.spi.AllowableFunctionReturnType;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.consume.spi.SemanticQueryWalker;
import org.hibernate.query.sqm.tree.expression.SqmExpression;

/**
 * @author Steve Ebersole
 */
public class SqmSumFunction<T>
		extends AbstractSqmAggregateFunction<T>
		implements SqmAggregateFunction<T> {
	public static final String NAME = "sum";

	public SqmSumFunction(SqmExpression<?> argument, AllowableFunctionReturnType<T> resultType, NodeBuilder nodeBuilder) {
		super( argument, resultType, nodeBuilder );
	}

	@Override
	public String getFunctionName() {
		return NAME;
	}

	@Override
	public <X> X accept(SemanticQueryWalker<X> walker) {
		return walker.visitSumFunction( this );
	}

	@Override
	public String asLoggableText() {
		return "SUM(" + getArgument().asLoggableText() + ")";
	}
}
