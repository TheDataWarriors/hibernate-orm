/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.function;

import org.hibernate.metamodel.model.domain.AllowableFunctionReturnType;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.sqm.tree.SqmTypedNode;
import org.hibernate.type.spi.TypeConfiguration;

import java.util.List;

/**
 * Acts as a wrapper to another {@link SqmFunctionDescriptor}, rendering the
 * standard JDBC escape sequence {@code {fn f(x, y)}} around the invocation
 * syntax generated by its delegate.
 *
 * @author Steve Ebersole
 * @author Gavin King
 */
public class JdbcEscapeFunctionDescriptor
		extends AbstractSqmFunctionDescriptor {
	private final SqmFunctionDescriptor wrapped;

	public JdbcEscapeFunctionDescriptor(String name, SqmFunctionDescriptor wrapped) {
		super(name);
		this.wrapped = wrapped;
	}

	@Override
	protected <T> SelfRenderingSqmFunction<T> generateSqmFunctionExpression(
			List<? extends SqmTypedNode<?>> arguments,
			AllowableFunctionReturnType<T> impliedResultType,
			QueryEngine queryEngine,
			TypeConfiguration typeConfiguration) {

		final SelfRenderingSqmFunction<T> delegate =
				wrapped.generateSqmExpression(
						arguments,
						impliedResultType,
						queryEngine,
						typeConfiguration
				);

		return new SelfRenderingSqmFunction<>(
				JdbcEscapeFunctionDescriptor.this,
				(sqlAppender, sqlAstArguments, walker) -> {
					sqlAppender.appendSql("{fn ");
					delegate.getRenderingSupport()
							.render(sqlAppender, sqlAstArguments, walker);
					sqlAppender.appendSql("}");
				},
				arguments,
				impliedResultType,
				getReturnTypeResolver(),
				queryEngine.getCriteriaBuilder(),
				getName());
	}
}
