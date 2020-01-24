/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect.function;

import java.util.List;
import java.util.function.Supplier;

import org.hibernate.metamodel.mapping.MappingModelExpressable;
import org.hibernate.query.TemporalUnit;
import org.hibernate.query.sqm.function.SqmFunctionDescriptor;
import org.hibernate.query.sqm.produce.function.ArgumentsValidator;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.sql.SqmToSqlAstConverter;
import org.hibernate.query.sqm.tree.SqmVisitableNode;
import org.hibernate.query.sqm.tree.expression.SqmExtractUnit;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.tree.expression.Expression;

import static org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers.useArgType;

/**
 * Oracle supports a limited list of temporal fields in the
 * extract() function, but we can emulate some of them by
 * using to_char() with a format string instead of extract().
 *
 * Thus, the additional supported fields are
 * {@link TemporalUnit#DAY_OF_YEAR},
 * {@link TemporalUnit#DAY_OF_MONTH},
 * {@link TemporalUnit#DAY_OF_YEAR},
 * and {@link TemporalUnit#WEEK}.
 *
 * @author Gavin King
 */
public class OracleExtractEmulation	implements SqmFunctionDescriptor {
	private static final ArgumentsValidator ARGS_VALIDATOR = StandardArgumentsValidators.exactly( 2 );

	@Override
	public Expression generateSqlExpression(
			String functionName,
			List<? extends SqmVisitableNode> arguments,
			Supplier<MappingModelExpressable> inferableTypeAccess,
			SqmToSqlAstConverter converter,
			SqlAstCreationState creationState) {
		ARGS_VALIDATOR.validate( arguments );

		final SqmExtractUnit<?> extractUnit = (SqmExtractUnit<?>) arguments.get( 0 );
		final TemporalUnit unit = extractUnit.getTemporalUnit();
		final String pattern;

		switch (unit) {
			case DAY_OF_WEEK: {
				pattern = "to_number(to_char(?2,'D'))";
				break;
			}
			case DAY_OF_MONTH: {
				pattern = "to_number(to_char(?2,'DD'))";
				break;
			}
			case DAY_OF_YEAR: {
				pattern = "to_number(to_char(?2,'DDD'))";
				break;
			}
			case WEEK: {
				pattern = "to_number(to_char(?2,'IW'))"; //the ISO week number
				break;
			}
			default: {
				pattern = "extract(?1 from ?2)";
				break;
			}
		}

		final SqmFunctionDescriptor sqmPattern = converter.getCreationContext().getSessionFactory()
				.getQueryEngine()
				.getSqmFunctionRegistry()
				.patternDescriptorBuilder( functionName, pattern )
				.setReturnTypeResolver( useArgType( 1 ) )
				.build();

		return sqmPattern.generateSqlExpression( functionName, arguments, inferableTypeAccess, converter, creationState );
	}
}
