/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect.function;

import org.hibernate.metamodel.model.domain.spi.AllowableFunctionReturnType;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.query.sqm.produce.function.internal.SelfRenderingSqmFunction;
import org.hibernate.query.sqm.produce.function.spi.AbstractSqmFunctionTemplate;
import org.hibernate.query.sqm.produce.function.spi.SelfRenderingFunctionSupport;
import org.hibernate.query.sqm.tree.SqmTypedNode;
import org.hibernate.sql.ast.consume.spi.SqlAppender;
import org.hibernate.sql.ast.consume.spi.SqlAstWalker;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.ExtractUnit;
import org.hibernate.type.spi.StandardSpiBasicTypes;

import java.util.List;

/**
 * HSQL datediff() does not support 'microsecond' as an argument,
 * so use timestampdiff(SQL_TSI_FRAC_SECOND) which extracts
 * nanoseconds.
 *
 * @author Gavin King
 */
public class HSQLTimestampdiffEmulation
		extends AbstractSqmFunctionTemplate implements SelfRenderingFunctionSupport {

	public HSQLTimestampdiffEmulation() {
		super(
				StandardArgumentsValidators.exactly( 3 ),
				StandardFunctionReturnTypeResolvers.invariant( StandardSpiBasicTypes.LONG )
		);
	}

	@Override
	public void render(
			SqlAppender sqlAppender,
			List<SqlAstNode> arguments,
			SqlAstWalker walker) {
		ExtractUnit field = (ExtractUnit) arguments.get(0);
		Expression datetime1 = (Expression) arguments.get(1);
		Expression datetime2 = (Expression) arguments.get(2);
		String fieldName = field.getName();
		if ( "microsecond".equals( fieldName ) ) {
			sqlAppender.appendSql("timestampdiff(SQL_TSI_FRAC_SECOND");
		}
		else {
			sqlAppender.appendSql("datediff(");
			sqlAppender.appendSql( fieldName );
		}
		sqlAppender.appendSql(",");
		datetime1.accept(walker);
		sqlAppender.appendSql(",");
		datetime2.accept(walker);
		sqlAppender.appendSql(")");
		if ( "microsecond".equals( fieldName ) ) {
			sqlAppender.appendSql("/1e3");
		}
	}

	@Override
	protected <T> SelfRenderingSqmFunction<T> generateSqmFunctionExpression(
			List<SqmTypedNode<?>> arguments,
			AllowableFunctionReturnType<T> impliedResultType,
			QueryEngine queryEngine) {
		return new SelfRenderingSqmFunction<T>(
				this,
				arguments,
				impliedResultType,
				queryEngine.getCriteriaBuilder(),
				"timestampdiff"
		);
	}

}
