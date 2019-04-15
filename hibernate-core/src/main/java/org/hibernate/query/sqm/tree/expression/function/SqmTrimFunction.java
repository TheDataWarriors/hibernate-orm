/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.expression.function;

import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.sql.ast.produce.metamodel.spi.BasicValuedExpressableType;
import org.hibernate.query.sqm.consume.spi.SemanticQueryWalker;
import org.hibernate.query.sqm.tree.expression.SqmExpression;
import org.hibernate.sql.TrimSpecification;

/**
 * @author Steve Ebersole
 */
public class SqmTrimFunction<T> extends AbstractSqmFunction<T> {
	public static final String NAME = "trim";

	private final TrimSpecification specification;
	private final SqmExpression trimCharacter;
	private final SqmExpression source;

	public SqmTrimFunction(
			TrimSpecification specification,
			SqmExpression<?> trimCharacter,
			SqmExpression<?> source,
			BasicValuedExpressableType<T> resultType,
			NodeBuilder nodeBuilder) {
		super( resultType, nodeBuilder );
		this.specification = specification;
		this.trimCharacter = trimCharacter;
		this.source = source;

		assert specification != null;
		assert trimCharacter != null;
		assert source != null;
	}

	public TrimSpecification getSpecification() {
		return specification;
	}

	public SqmExpression getTrimCharacter() {
		return trimCharacter;
	}

	public SqmExpression getSource() {
		return source;
	}

	@Override
	public String getFunctionName() {
		return NAME;
	}

	@Override
	public boolean hasArguments() {
		return true;
	}

	@Override
	public <X> X accept(SemanticQueryWalker<X> walker) {
		return walker.visitTrimFunction( this );
	}

	@Override
	public String asLoggableText() {
		return "TRIM(" + specification.name() +
				" '" + trimCharacter.asLoggableText() +
				"' FROM " + source.asLoggableText() + ")";
	}
}
