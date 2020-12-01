/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.sql.internal;

import java.util.List;
import java.util.function.Function;

import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.MappingModelExpressable;
import org.hibernate.metamodel.model.domain.AllowableParameterType;
import org.hibernate.query.SemanticException;
import org.hibernate.query.spi.QueryParameterBinding;
import org.hibernate.query.spi.QueryParameterImplementor;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.results.graph.basic.BasicResult;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;

/**
 * @author Steve Ebersole
 */
public class SqmParameterInterpretation implements Expression, DomainResultProducer {
	private final SqmParameter sqmParameter;
	private final QueryParameterImplementor<?> queryParameter;
	private final MappingModelExpressable valueMapping;
	private final Function<QueryParameterImplementor, QueryParameterBinding> queryParameterBindingResolver;

	private final Expression resolvedExpression;

	public SqmParameterInterpretation(
			SqmParameter sqmParameter,
			QueryParameterImplementor<?> queryParameter,
			List<JdbcParameter> jdbcParameters,
			MappingModelExpressable valueMapping,
			Function<QueryParameterImplementor, QueryParameterBinding> queryParameterBindingResolver) {
		this.sqmParameter = sqmParameter;
		this.queryParameter = queryParameter;
		this.valueMapping = valueMapping;
		this.queryParameterBindingResolver = queryParameterBindingResolver;

		assert jdbcParameters != null;
		assert jdbcParameters.size() > 0;

		this.resolvedExpression = valueMapping instanceof EmbeddableValuedModelPart
				? new SqlTuple( jdbcParameters, valueMapping )
				: jdbcParameters.get( 0 );
	}

	public Expression getResolvedExpression() {
		return resolvedExpression;
	}

	@Override
	public void accept(SqlAstWalker sqlTreeWalker) {
		resolvedExpression.accept( sqlTreeWalker );
	}

	@Override
	public MappingModelExpressable getExpressionType() {
		return valueMapping;
	}

	@Override
	public DomainResult createDomainResult(
			String resultVariable,
			DomainResultCreationState creationState) {
		if ( resolvedExpression instanceof SqlTuple ) {
			throw new SemanticException( "Composite query parameter cannot be used in select" );
		}

		AllowableParameterType nodeType = sqmParameter.getNodeType();
		if ( nodeType == null ) {
			final QueryParameterBinding<?> binding = queryParameterBindingResolver.apply( queryParameter );
			nodeType = binding.getBindType();
		}

		final SqlSelection sqlSelection = creationState.getSqlAstCreationState().getSqlExpressionResolver().resolveSqlSelection(
				resolvedExpression,
				nodeType.getExpressableJavaTypeDescriptor(),
				creationState.getSqlAstCreationState()
						.getCreationContext()
						.getSessionFactory()
						.getTypeConfiguration()
		);

		//noinspection unchecked
		return new BasicResult(
				sqlSelection.getValuesArrayPosition(),
				resultVariable,
				nodeType.getExpressableJavaTypeDescriptor()
		);
	}
}
