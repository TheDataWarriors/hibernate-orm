/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.tree.expression;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.IndexedConsumer;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.MappingModelExpressable;
import org.hibernate.query.sqm.sql.internal.DomainResultProducer;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.basic.BasicResult;

/**
 * Represents a literal in the SQL AST.  This form accepts a {@link JdbcMapping} and acts
 * as its own MappingModelExpressable.
 *
 * @see QueryLiteral
 *
 * @author Steve Ebersole
 */
public class JdbcLiteral<T> implements Literal, MappingModelExpressable<T>, DomainResultProducer<T> {
	private final T literalValue;
	private final JdbcMapping jdbcMapping;

	public JdbcLiteral(T literalValue, JdbcMapping jdbcMapping) {
		this.literalValue = literalValue;
		this.jdbcMapping = jdbcMapping;
	}

	@Override
	public Object getLiteralValue() {
		return literalValue;
	}

	@Override
	public JdbcMapping getJdbcMapping() {
		return jdbcMapping;
	}

	@Override
	public void accept(SqlAstWalker sqlTreeWalker) {
		sqlTreeWalker.visitJdbcLiteral( this );
	}

	@Override
	public void bindParameterValue(
			PreparedStatement statement,
			int startPosition,
			JdbcParameterBindings jdbcParameterBindings,
			ExecutionContext executionContext) throws SQLException {
		//noinspection unchecked
		jdbcMapping.getJdbcValueBinder().bind(
				statement,
				literalValue,
				startPosition,
				executionContext.getSession()
		);
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// MappingModelExpressable

	@Override
	public MappingModelExpressable getExpressionType() {
		return this;
	}

	@Override
	public int getJdbcTypeCount() {
		return 1;
	}

	@Override
	public List<JdbcMapping> getJdbcMappings() {
		return Collections.singletonList( jdbcMapping );
	}

	@Override
	public int forEachJdbcType(int offset, IndexedConsumer<JdbcMapping> action) {
		action.accept( offset, jdbcMapping );
		return getJdbcTypeCount();
	}

	@Override
	public Object disassemble(Object value, SharedSessionContractImplementor session) {
		return value;
	}

	@Override
	public int forEachDisassembledJdbcValue(
			Object value,
			Clause clause,
			int offset,
			JdbcValuesConsumer valuesConsumer,
			SharedSessionContractImplementor session) {
		valuesConsumer.consume( offset, value, jdbcMapping );
		return getJdbcTypeCount();
	}

	@Override
	public int forEachJdbcValue(
			Object value,
			Clause clause,
			int offset,
			JdbcValuesConsumer valuesConsumer,
			SharedSessionContractImplementor session) {
		valuesConsumer.consume( offset, value, jdbcMapping );
		return getJdbcTypeCount();
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// DomainResultProducer

	@Override
	public DomainResult<T> createDomainResult(String resultVariable, DomainResultCreationState creationState) {
		final SqlAstCreationState sqlAstCreationState = creationState.getSqlAstCreationState();
		final SqlExpressionResolver sqlExpressionResolver = sqlAstCreationState.getSqlExpressionResolver();

		final SqlSelection sqlSelection = sqlExpressionResolver.resolveSqlSelection(
				this,
				jdbcMapping.getJavaTypeDescriptor(),
				sqlAstCreationState.getCreationContext().getDomainModel().getTypeConfiguration()
		);

		//noinspection unchecked
		return new BasicResult( sqlSelection.getValuesArrayPosition(), resultVariable, jdbcMapping.getJavaTypeDescriptor() );
	}

	@Override
	public void applySqlSelections(DomainResultCreationState creationState) {
		final SqlAstCreationState sqlAstCreationState = creationState.getSqlAstCreationState();
		final SqlExpressionResolver sqlExpressionResolver = sqlAstCreationState.getSqlExpressionResolver();

		sqlExpressionResolver.resolveSqlSelection(
				this,
				jdbcMapping.getJavaTypeDescriptor(),
				sqlAstCreationState.getCreationContext().getDomainModel().getTypeConfiguration()
		);
	}

}
