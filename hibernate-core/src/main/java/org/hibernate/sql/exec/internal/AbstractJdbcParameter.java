/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.exec.internal;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.IndexedConsumer;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.MappingModelExpressable;
import org.hibernate.metamodel.mapping.SqlExpressable;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.exec.ExecutionException;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBinding;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.results.internal.SqlSelectionImpl;
import org.hibernate.type.BasicType;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractJdbcParameter
		implements JdbcParameter, JdbcParameterBinder, MappingModelExpressable, SqlExpressable {

	private final JdbcMapping jdbcMapping;

	public AbstractJdbcParameter(JdbcMapping jdbcMapping) {
		this.jdbcMapping = jdbcMapping;
	}

	@Override
	public JdbcParameterBinder getParameterBinder() {
		return this;
	}

	@Override
	public JdbcMapping getJdbcMapping() {
		return jdbcMapping;
	}

	@Override
	public SqlSelection createSqlSelection(
			int jdbcPosition,
			int valuesArrayPosition,
			JavaTypeDescriptor javaTypeDescriptor,
			TypeConfiguration typeConfiguration) {
		// todo (6.0) : investigate "virtual" or "static" selections
		//		- anything that is the same for each row always - parameter, literal, etc;
		//			the idea would be to write the value directly into the JdbcValues array
		//			and not generating a SQL selection in the query sent to DB
		return new SqlSelectionImpl(
				jdbcPosition,
				valuesArrayPosition,
				this
		);
	}

	@Override
	public void bindParameterValue(
			PreparedStatement statement,
			int startPosition,
			JdbcParameterBindings jdbcParamBindings,
			ExecutionContext executionContext) throws SQLException {
		final JdbcParameterBinding binding = jdbcParamBindings.getBinding( AbstractJdbcParameter.this );
		if ( binding == null ) {
			throw new ExecutionException( "JDBC parameter value not bound - " + this );
		}

		JdbcMapping jdbcMapping = binding.getBindType();

		if ( jdbcMapping == null ) {
			jdbcMapping = this.jdbcMapping;
		}

		if ( jdbcMapping == null ) {
			jdbcMapping = guessBindType( executionContext, binding );
		}

		final Object bindValue = binding.getBindValue();

		//noinspection unchecked
		jdbcMapping.getJdbcValueBinder().bind(
				statement,
				bindValue,
				startPosition,
				executionContext.getSession()
		);
	}

	private JdbcMapping guessBindType(ExecutionContext executionContext, JdbcParameterBinding binding) {
		final BasicType<?> basicType = executionContext.getSession()
				.getFactory()
				.getTypeConfiguration()
				.getBasicTypeRegistry()
				.getRegisteredType( binding.getBindValue().getClass() );

		return basicType.getJdbcMapping();
	}

	@Override
	public MappingModelExpressable getExpressionType() {
		return this;
	}

	@Override
	public int getJdbcTypeCount() {
		return 1;
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
}
