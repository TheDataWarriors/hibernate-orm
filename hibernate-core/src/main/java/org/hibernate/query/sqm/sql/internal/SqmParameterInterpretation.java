/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.sql.internal;

import java.util.List;
import java.util.function.Function;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.DiscriminatedAssociationModelPart;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.EntityAssociationMapping;
import org.hibernate.metamodel.mapping.EntityValuedModelPart;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.MappingModelExpressible;
import org.hibernate.metamodel.model.convert.spi.BasicValueConverter;
import org.hibernate.query.BindableType;
import org.hibernate.query.SemanticException;
import org.hibernate.query.spi.QueryParameterBinding;
import org.hibernate.query.spi.QueryParameterImplementor;
import org.hibernate.query.sqm.SqmExpressible;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.SqlTupleContainer;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.basic.BasicResult;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * @author Steve Ebersole
 */
public class SqmParameterInterpretation implements Expression, DomainResultProducer, SqlTupleContainer {
	private final SqmParameter<?> sqmParameter;
	private final QueryParameterImplementor<?> queryParameter;
	private final MappingModelExpressible<?> valueMapping;
	private final Function<QueryParameterImplementor<?>, QueryParameterBinding<?>> queryParameterBindingResolver;
	private final List<JdbcParameter> jdbcParameters;
	private Expression resolvedExpression;

	public SqmParameterInterpretation(
			SqmParameter<?> sqmParameter,
			QueryParameterImplementor<?> queryParameter,
			List<JdbcParameter> jdbcParameters,
			MappingModelExpressible<?> valueMapping,
			Function<QueryParameterImplementor<?>, QueryParameterBinding<?>> queryParameterBindingResolver) {
		this.sqmParameter = sqmParameter;
		this.queryParameter = queryParameter;
		this.queryParameterBindingResolver = queryParameterBindingResolver;

		if ( valueMapping instanceof EntityAssociationMapping ) {
			final EntityAssociationMapping mapping = (EntityAssociationMapping) valueMapping;
			this.valueMapping = mapping.getForeignKeyDescriptor().getPart( mapping.getSideNature() );
		}
		else if ( valueMapping instanceof EntityValuedModelPart ) {
			this.valueMapping = ( (EntityValuedModelPart) valueMapping ).getEntityMappingType().getIdentifierMapping();
		}
		else {
			this.valueMapping = valueMapping;
		}

		assert jdbcParameters != null;
		assert jdbcParameters.size() > 0;

		this.jdbcParameters = jdbcParameters;
	}

	private Expression determineResolvedExpression(List<JdbcParameter> jdbcParameters, MappingModelExpressible<?> valueMapping) {
		if ( valueMapping instanceof EmbeddableValuedModelPart
				|| valueMapping instanceof DiscriminatedAssociationModelPart ) {
			return new SqlTuple( jdbcParameters, valueMapping );
		}

		assert jdbcParameters.size() == 1;
		return jdbcParameters.get( 0 );
	}

	public Expression getResolvedExpression() {
		// We need to defer the resolution because the JdbcParameter might be replaced in BaseSqmToSqlAstConverter#replaceJdbcParametersType
		if ( resolvedExpression == null ) {
			return this.resolvedExpression = determineResolvedExpression( jdbcParameters, this.valueMapping );
		}
		return resolvedExpression;
	}

	@Override
	public void accept(SqlAstWalker sqlTreeWalker) {
		getResolvedExpression().accept( sqlTreeWalker );
	}

	@Override
	public MappingModelExpressible<?> getExpressionType() {
		return valueMapping;
	}

	@Override
	public DomainResult<?> createDomainResult(
			String resultVariable,
			DomainResultCreationState creationState) {
		final Expression resolvedExpression = getResolvedExpression();
		if ( resolvedExpression instanceof SqlTuple ) {
			throw new SemanticException( "Composite query parameter cannot be used in select" );
		}

		BindableType<?> nodeType = sqmParameter.getNodeType();
		if ( nodeType == null ) {
			final QueryParameterBinding<?> binding = queryParameterBindingResolver.apply( queryParameter );
			nodeType = binding.getBindType();
		}
		final SessionFactoryImplementor sessionFactory = creationState.getSqlAstCreationState()
				.getCreationContext()
				.getSessionFactory();

		final SqmExpressible<?> sqmExpressible = nodeType.resolveExpressible( sessionFactory );
		final JavaType<?> jdbcJavaType;
		final BasicValueConverter<?, ?> converter;
		if ( sqmExpressible instanceof JdbcMapping ) {
			final JdbcMapping jdbcMapping = (JdbcMapping) sqmExpressible;
			jdbcJavaType = jdbcMapping.getJdbcJavaType();
			converter = jdbcMapping.getValueConverter();
		}
		else {
			jdbcJavaType = sqmExpressible.getExpressibleJavaType();
			converter = null;
		}

		final SqlSelection sqlSelection = creationState.getSqlAstCreationState().getSqlExpressionResolver().resolveSqlSelection(
				resolvedExpression,
				jdbcJavaType,
				null,
				sessionFactory.getTypeConfiguration()
		);

		return new BasicResult(
				sqlSelection.getValuesArrayPosition(),
				resultVariable,
				sqmExpressible.getExpressibleJavaType(),
				converter
		);
	}

	@Override
	public SqlTuple getSqlTuple() {
		final Expression resolvedExpression = getResolvedExpression();
		return resolvedExpression instanceof SqlTuple
				? (SqlTuple) resolvedExpression
				: null;
	}

	@Override
	public void applySqlSelections(DomainResultCreationState creationState) {
		resolveSqlSelection( creationState );
	}

	public SqlSelection resolveSqlSelection(DomainResultCreationState creationState) {
		final Expression resolvedExpression = getResolvedExpression();
		if ( resolvedExpression instanceof SqlTuple ) {
			throw new SemanticException( "Composite query parameter cannot be used in select" );
		}

		BindableType<?> nodeType = sqmParameter.getNodeType();
		if ( nodeType == null ) {
			final QueryParameterBinding<?> binding = queryParameterBindingResolver.apply( queryParameter );
			nodeType = binding.getBindType();
		}

		final SessionFactoryImplementor sessionFactory = creationState.getSqlAstCreationState()
				.getCreationContext()
				.getSessionFactory();

		final SqmExpressible<?> sqmExpressible = nodeType.resolveExpressible( sessionFactory );
		final JavaType<?> jdbcJavaType;
		if ( sqmExpressible instanceof JdbcMapping ) {
			final JdbcMapping jdbcMapping = (JdbcMapping) sqmExpressible;
			jdbcJavaType = jdbcMapping.getJdbcJavaType();
		}
		else {
			jdbcJavaType = sqmExpressible.getExpressibleJavaType();
		}

		return creationState.getSqlAstCreationState().getSqlExpressionResolver().resolveSqlSelection(
				resolvedExpression,
				jdbcJavaType,
				null,
				sessionFactory.getTypeConfiguration()
		);
	}
}
