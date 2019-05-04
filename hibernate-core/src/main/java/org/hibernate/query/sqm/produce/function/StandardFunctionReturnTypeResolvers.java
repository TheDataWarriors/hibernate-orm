/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.produce.function;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.hibernate.metamodel.model.domain.spi.AllowableFunctionReturnType;
import org.hibernate.QueryException;
import org.hibernate.query.sqm.tree.SqmTypedNode;
import org.hibernate.query.sqm.tree.expression.SqmExpression;
import org.hibernate.sql.ast.produce.metamodel.spi.ExpressableType;

/**
 * @author Steve Ebersole
 */
public class StandardFunctionReturnTypeResolvers {
	/**
	 * Disallow instantiation
	 */
	private StandardFunctionReturnTypeResolvers() {
	}

	/**
	 * A resolver that defines an invariant result type.  E.g. `substring` always
	 * returns a String.  Note however that to account for attribute converters and
	 * such, this resolver allows the context-impled expression type to be the
	 * return type so long as the Java types are compatible.
	 */
	public static FunctionReturnTypeResolver invariant(AllowableFunctionReturnType invariantType) {
		if ( invariantType == null ) {
			throw new IllegalArgumentException( "Passed `invariantType` for function return cannot be null" );
		}

		return new FunctionReturnTypeResolver() {
			@Override
			public <T> AllowableFunctionReturnType<T> resolveFunctionReturnType(
					AllowableFunctionReturnType<T> impliedType,
					List<SqmTypedNode> arguments) {
				return useImpliedTypeIfPossible( invariantType, impliedType );
			}
		};
	}

	public static FunctionReturnTypeResolver useArgType(int argPosition) {
		return new FunctionReturnTypeResolver() {
			@Override
			public <T> AllowableFunctionReturnType<T> resolveFunctionReturnType(
					AllowableFunctionReturnType<T> impliedType,
					List<SqmTypedNode> arguments) {
				final AllowableFunctionReturnType specifiedArgType = extractArgumentType( arguments, argPosition );
				return useImpliedTypeIfPossible( specifiedArgType, impliedType );
			}
		};
	}

	public static FunctionReturnTypeResolver useFirstNonNull() {
		return new FunctionReturnTypeResolver() {
			@Override
			public <T> AllowableFunctionReturnType<T> resolveFunctionReturnType(
					AllowableFunctionReturnType<T> impliedType,
					List<SqmTypedNode> arguments) {
				final Optional<SqmTypedNode> firstNonNull = arguments.stream()
						.filter(
								arg -> arg instanceof SqmExpression
										&& ((SqmExpression) arg).getExpressableType() instanceof AllowableFunctionReturnType
						)
						.findFirst();
				if ( firstNonNull.isPresent() ) {
					SqmExpression arg = (SqmExpression) firstNonNull.get();
					AllowableFunctionReturnType argType = (AllowableFunctionReturnType) arg.getExpressableType();
					return useImpliedTypeIfPossible( argType, impliedType );
				}
				else {
					return impliedType;
				}
			}
		};
	}



	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Internal helpers

	@SuppressWarnings("unchecked")
	private static <T> AllowableFunctionReturnType<T> useImpliedTypeIfPossible(
			AllowableFunctionReturnType found,
			AllowableFunctionReturnType implied) {
		return areCompatible( found, implied ) ? implied : found;
	}

	@SuppressWarnings({"unchecked", "SimplifiableIfStatement"})
	private static boolean areCompatible(AllowableFunctionReturnType expected, AllowableFunctionReturnType found) {
		if ( expected == null || found == null ) {
			return false;
		}

		return expected.getJavaType().isAssignableFrom( found.getJavaType() );
	}

	private static AllowableFunctionReturnType extractArgumentType(List<SqmTypedNode> arguments, int position) {
		final SqmExpression specifiedArgument = (SqmExpression) arguments.get( position-1 );
		final ExpressableType specifiedArgType = specifiedArgument.getExpressableType();
		if ( !(specifiedArgType instanceof AllowableFunctionReturnType) ) {
			throw new QueryException(
					String.format(
							Locale.ROOT,
							"Function argument [%s] at specified position [%d] in call arguments was not typed as an allowable function return type",
							specifiedArgument,
							position
					)
			);
		}

		return (AllowableFunctionReturnType) specifiedArgType;
	}
}
