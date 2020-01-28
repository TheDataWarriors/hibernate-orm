/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect.function;

import org.hibernate.dialect.Dialect;
import org.hibernate.metamodel.model.domain.AllowableFunctionReturnType;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.sqm.function.AbstractSqmFunctionDescriptor;
import org.hibernate.query.sqm.function.SelfRenderingFunctionSqlAstExpression;
import org.hibernate.query.sqm.function.SelfRenderingSqlFunctionExpression;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.query.sqm.produce.function.internal.PatternRenderer;
import org.hibernate.query.sqm.tree.SqmTypedNode;
import org.hibernate.query.sqm.tree.expression.SqmExpression;
import org.hibernate.query.sqm.tree.expression.SqmExtractUnit;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.expression.DurationUnit;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.type.spi.TypeConfiguration;

import java.util.List;

import static java.util.Arrays.asList;
import static org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers.useArgType;
import static org.hibernate.type.spi.TypeConfiguration.isSqlTimestampType;

/**
 * @author Gavin King
 */
public class TimestampaddFunction
		extends AbstractSqmFunctionDescriptor {

	private Dialect dialect;

	public TimestampaddFunction(Dialect dialect) {
		super(
				"timestampadd",
				StandardArgumentsValidators.exactly( 3 ),
				StandardFunctionReturnTypeResolvers.useArgType( 3 )
		);
		this.dialect = dialect;
	}

	@Override
	protected <T> SelfRenderingSqlFunctionExpression<T> generateSqmFunctionExpression(
			List<SqmTypedNode<?>> arguments,
			AllowableFunctionReturnType<T> impliedResultType,
			QueryEngine queryEngine,
			TypeConfiguration typeConfiguration) {
		SqmExtractUnit<?> field = (SqmExtractUnit<?>) arguments.get(0);
		SqmExpression<?> to = (SqmExpression<?>) arguments.get(2);
		return queryEngine.getSqmFunctionRegistry()
				.patternDescriptorBuilder(
						"timestampadd",
						dialect.timestampaddPattern(
								field.getUnit(),
								typeConfiguration.isTimestampType( to.getNodeType() )
						)
				)
				.setExactArgumentCount( 3 )
				.setReturnTypeResolver( useArgType( 3 ) )
				.descriptor()
				.generateSqmExpression(
						arguments,
						impliedResultType,
						queryEngine,
						typeConfiguration
				);
	}

	public SelfRenderingFunctionSqlAstExpression expression(
			AllowableFunctionReturnType<?> impliedResultType,
			SqlAstNode... sqlAstArguments) {
		DurationUnit field = (DurationUnit) sqlAstArguments[0];
		Expression to = (Expression) sqlAstArguments[2];
		return new SelfRenderingFunctionSqlAstExpression(
				new PatternRenderer(
						dialect.timestampaddPattern(
								field.getUnit(),
								isSqlTimestampType( to.getExpressionType() )
						)
				)::render,
				asList( sqlAstArguments ),
				impliedResultType,
				to.getExpressionType()
		);
	}

	@Override
	public String getArgumentListSignature() {
		return "(field, magnitude, datetime)";
	}

}
