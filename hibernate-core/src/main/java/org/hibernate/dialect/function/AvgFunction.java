/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect.function;

import java.util.Arrays;
import java.util.List;

import org.hibernate.dialect.Dialect;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor;
import org.hibernate.query.sqm.function.FunctionKind;
import org.hibernate.query.sqm.produce.function.ArgumentTypesValidator;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.sql.ast.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.expression.CastTarget;
import org.hibernate.sql.ast.tree.expression.Distinct;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.type.BasicType;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.spi.TypeConfiguration;

import static org.hibernate.query.sqm.produce.function.FunctionParameterType.NUMERIC;

/**
 * @author Christian Beikov
 */
public class AvgFunction extends AbstractSqmSelfRenderingFunctionDescriptor {

	private final SqlAstNodeRenderingMode defaultArgumentRenderingMode;
	private final CastFunction castFunction;
	private final BasicType<Double> doubleType;

	public AvgFunction(
			Dialect dialect,
			TypeConfiguration typeConfiguration,
			SqlAstNodeRenderingMode defaultArgumentRenderingMode) {
		super(
				"avg",
				FunctionKind.AGGREGATE,
				new ArgumentTypesValidator( StandardArgumentsValidators.exactly( 1 ), NUMERIC ),
				StandardFunctionReturnTypeResolvers.invariant(
						typeConfiguration.getBasicTypeRegistry().resolve( StandardBasicTypes.DOUBLE )
				)
		);
		this.defaultArgumentRenderingMode = defaultArgumentRenderingMode;
		doubleType = typeConfiguration.getBasicTypeRegistry().resolve( StandardBasicTypes.DOUBLE );
		//This is kinda wrong, we're supposed to use findFunctionDescriptor("cast"), not instantiate CastFunction
		//However, since no Dialects currently override the cast() function, it's OK for now
		castFunction = new CastFunction( dialect, dialect.getPreferredSqlTypeCodeForBoolean() );
	}

	@Override
	public void render(SqlAppender sqlAppender, List<? extends SqlAstNode> sqlAstArguments, SqlAstTranslator<?> walker) {
		render( sqlAppender, sqlAstArguments, null, walker );
	}

	@Override
	public void render(
			SqlAppender sqlAppender,
			List<? extends SqlAstNode> sqlAstArguments,
			Predicate filter,
			SqlAstTranslator<?> translator) {
		final boolean caseWrapper = filter != null && !translator.supportsFilterClause();
		sqlAppender.appendSql( "avg(" );
		final Expression arg;
		if ( sqlAstArguments.get( 0 ) instanceof Distinct ) {
			sqlAppender.appendSql( "distinct " );
			arg = ( (Distinct) sqlAstArguments.get( 0 ) ).getExpression();
		}
		else {
			arg = (Expression) sqlAstArguments.get( 0 );
		}
		if ( caseWrapper ) {
			sqlAppender.appendSql( "case when " );
			filter.accept( translator );
			sqlAppender.appendSql( " then " );
			renderArgument( sqlAppender, translator, arg );
			sqlAppender.appendSql( " else null end)" );
		}
		else {
			renderArgument( sqlAppender, translator, arg );
			sqlAppender.appendSql( ')' );
			if ( filter != null ) {
				sqlAppender.appendSql( " filter (where " );
				filter.accept( translator );
				sqlAppender.appendSql( ')' );
			}
		}
	}

	private void renderArgument(SqlAppender sqlAppender, SqlAstTranslator<?> translator, Expression realArg) {
		final JdbcMapping sourceMapping = realArg.getExpressionType().getJdbcMappings().get( 0 );
		// Only cast to float/double if this is an integer
		if ( sourceMapping.getJdbcType().isInteger() ) {
			castFunction.render( sqlAppender, Arrays.asList( realArg, new CastTarget(doubleType) ), translator );
		}
		else {
			translator.render( realArg, defaultArgumentRenderingMode );
		}
	}

	@Override
	public String getArgumentListSignature() {
		return "(NUMERIC arg)";
	}

}
