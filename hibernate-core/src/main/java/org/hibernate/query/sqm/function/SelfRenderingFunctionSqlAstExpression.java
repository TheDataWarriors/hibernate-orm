/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.function;

import java.util.List;

import org.hibernate.NotYetImplementedFor6Exception;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.mapping.IndexedConsumer;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.Table;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.JdbcMappingContainer;
import org.hibernate.metamodel.mapping.SqlExpressable;
import org.hibernate.metamodel.model.domain.AllowableFunctionReturnType;
import org.hibernate.query.SemanticException;
import org.hibernate.query.sqm.sql.internal.DomainResultProducer;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.expression.FunctionExpression;
import org.hibernate.sql.ast.tree.expression.SelfRenderingExpression;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.basic.BasicResult;
import org.hibernate.sql.results.internal.SqlSelectionImpl;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Representation of a function call in the SQL AST for impls that know how to
 * render themselves.
 *
 * @author Steve Ebersole
 */
public class SelfRenderingFunctionSqlAstExpression
		implements SelfRenderingExpression, Selectable, SqlExpressable, DomainResultProducer, FunctionExpression {
	private final String functionName;
	private final FunctionRenderingSupport renderer;
	private final List<? extends SqlAstNode> sqlAstArguments;
	private final AllowableFunctionReturnType<?> type;
	private final JdbcMappingContainer expressable;

	public SelfRenderingFunctionSqlAstExpression(
			String functionName,
			FunctionRenderingSupport renderer,
			List<? extends SqlAstNode> sqlAstArguments,
			AllowableFunctionReturnType<?> type,
			JdbcMappingContainer expressable) {
		this.functionName = functionName;
		this.renderer = renderer;
		this.sqlAstArguments = sqlAstArguments;
		this.type = type;
		//might be null due to code in SelfRenderingFunctionSqlAstExpression
		this.expressable = expressable;
	}

	@Override
	public String getFunctionName() {
		return functionName;
	}

	@Override
	public List<? extends SqlAstNode> getArguments() {
		return sqlAstArguments;
	}

	@Override
	public JdbcMappingContainer getExpressionType() {
		if ( type instanceof SqlExpressable ) {
			return (JdbcMappingContainer) type;
		}
		return expressable;
	}

	protected FunctionRenderingSupport getRenderer() {
		return renderer;
	}

	@Override
	public SqlSelection createSqlSelection(
			int jdbcPosition,
			int valuesArrayPosition,
			JavaType javaTypeDescriptor,
			TypeConfiguration typeConfiguration) {
		return new SqlSelectionImpl(
				jdbcPosition,
				valuesArrayPosition,
				this
		);
	}

	@Override
	public DomainResult createDomainResult(
			String resultVariable,
			DomainResultCreationState creationState) {
		return new BasicResult(
				creationState.getSqlAstCreationState().getSqlExpressionResolver()
						.resolveSqlSelection(
								this,
								type.getExpressableJavaTypeDescriptor(),
								creationState.getSqlAstCreationState().getCreationContext()
										.getDomainModel().getTypeConfiguration()
						)
						.getValuesArrayPosition(),
				resultVariable,
				type.getExpressableJavaTypeDescriptor()
		);
	}

	@Override
	public void renderToSql(
			SqlAppender sqlAppender,
			SqlAstTranslator<?> walker,
			SessionFactoryImplementor sessionFactory) {
		renderer.render( sqlAppender, sqlAstArguments, walker );
	}

	@Override
	public String getAlias(Dialect dialect) {
		return null;
	}

	@Override
	public String getAlias(Dialect dialect, Table table) {
		return null;
	}

	@Override
	public boolean isFormula() {
		return false;
	}

	@Override
	public String getTemplate(Dialect dialect, SqmFunctionRegistry functionRegistry) {
		return null;
	}

	@Override
	public String getText(Dialect dialect) {
		return null;
	}

	@Override
	public String getText() {
		return null;
	}

	@Override
	public String getCustomReadExpression() {
		return null;
	}

	@Override
	public String getCustomWriteExpression() {
		return null;
	}

	@Override
	public JdbcMapping getJdbcMapping() {
		if ( type instanceof SqlExpressable ) {
			return ( (SqlExpressable) type ).getJdbcMapping();
		}
		else {
			return ( (SqlExpressable) expressable ).getJdbcMapping();
		}
	}

	@Override
	public void applySqlSelections(DomainResultCreationState creationState) {
		final SqlAstCreationState sqlAstCreationState = creationState.getSqlAstCreationState();
		final SqlExpressionResolver sqlExpressionResolver = sqlAstCreationState.getSqlExpressionResolver();

		sqlExpressionResolver.resolveSqlSelection(
				this,
				type.getExpressableJavaTypeDescriptor(),
				sqlAstCreationState.getCreationContext().getDomainModel().getTypeConfiguration()
		);
	}

	@Override
	public int forEachJdbcType(int offset, IndexedConsumer<JdbcMapping> action) {
		throw new NotYetImplementedFor6Exception( getClass() );
	}
}
