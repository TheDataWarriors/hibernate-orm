/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.function;

import org.hibernate.metamodel.model.domain.AllowableFunctionReturnType;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.sqm.produce.function.ArgumentsValidator;
import org.hibernate.query.sqm.produce.function.FunctionReturnTypeResolver;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.query.sqm.sql.SqmToSqlAstConverter;
import org.hibernate.query.sqm.tree.SqmTypedNode;
import org.hibernate.query.sqm.tree.SqmVisitableNode;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.type.spi.TypeConfiguration;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractSqmFunctionDescriptor implements SqmFunctionDescriptor {
	private final ArgumentsValidator argumentsValidator;
	private final FunctionReturnTypeResolver returnTypeResolver;
	private final String name;

	protected FunctionReturnTypeResolver getReturnTypeResolver() {
		return returnTypeResolver;
	}

	public AbstractSqmFunctionDescriptor(String name) {
		this( name, null, null );
	}

	public AbstractSqmFunctionDescriptor(String name, ArgumentsValidator argumentsValidator) {
		this( name, argumentsValidator, null );
	}

	public AbstractSqmFunctionDescriptor(
			String name,
			ArgumentsValidator argumentsValidator,
			FunctionReturnTypeResolver returnTypeResolver) {
		this.name = name;
		this.argumentsValidator = argumentsValidator == null
				? StandardArgumentsValidators.NONE
				: argumentsValidator;
		this.returnTypeResolver = returnTypeResolver == null
				? StandardFunctionReturnTypeResolvers.useFirstNonNull()
				: returnTypeResolver;
	}

	public String getName() {
		return name;
	}

	public String getSignature(String name) {
		return getReturnSignature() + name + getArgumentListSignature();
	}

	public String getReturnSignature() {
		String result = returnTypeResolver.getReturnType();
		return result.isEmpty() ? "" : result + " ";
	}

	public String getArgumentListSignature() {
		String args = argumentsValidator.getSignature();
		return alwaysIncludesParentheses() ? args : "()".equals(args) ? "" : "[" + args + "]";
	}

	private static SqlAstNode toSqlAstNode(Object arg, SqmToSqlAstConverter walker) {
		return (SqlAstNode) arg;
	}


	public static List<SqlAstNode> resolveSqlAstArguments(List<SqmTypedNode<?>> sqmArguments, SqmToSqlAstConverter walker) {
		if ( sqmArguments == null || sqmArguments.isEmpty() ) {
			return emptyList();
		}

		final ArrayList<SqlAstNode> sqlAstArguments = new ArrayList<>();
		for ( SqmTypedNode sqmArgument : sqmArguments ) {
			sqlAstArguments.add( toSqlAstNode( ((SqmVisitableNode) sqmArgument).accept( walker ), walker ) );
		}
		return sqlAstArguments;
	}

	@Override
	public final <T> SelfRenderingSqmFunction<T> generateSqmExpression(
			List<SqmTypedNode<?>> arguments,
			AllowableFunctionReturnType<T> impliedResultType,
			QueryEngine queryEngine,
			TypeConfiguration typeConfiguration) {
		argumentsValidator.validate( arguments );

		return generateSqmFunctionExpression(
				arguments,
				impliedResultType,
				queryEngine,
				typeConfiguration
		);
	}

	/**
	 * Return an SQM node or subtree representing an invocation of this function
	 * with the given arguments. This method may be overridden in the case of
	 * function descriptors that wish to customize creation of the node.
	 *
	 * @param arguments the arguments of the function invocation
	 * @param impliedResultType the function return type as inferred from its usage
	 */
	protected abstract <T> SelfRenderingSqmFunction<T> generateSqmFunctionExpression(
			List<SqmTypedNode<?>> arguments,
			AllowableFunctionReturnType<T> impliedResultType,
			QueryEngine queryEngine,
			TypeConfiguration typeConfiguration);
}

