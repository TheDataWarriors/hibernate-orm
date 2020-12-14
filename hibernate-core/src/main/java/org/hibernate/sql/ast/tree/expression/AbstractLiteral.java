/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.tree.expression;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.hibernate.metamodel.mapping.BasicValuedMapping;
import org.hibernate.query.sqm.sql.internal.DomainResultProducer;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.basic.BasicResult;
import org.hibernate.type.BasicType;

/**
 * We classify literals different based on their source so that we can handle then differently
 * when rendering SQL.  This class offers convenience for those implementations
 * <p/>
 * Can function as a ParameterBinder for cases where we want to treat literals using bind parameters.
 *
 * @author Steve Ebersole
 */
public abstract class AbstractLiteral<T>
		implements JdbcParameterBinder, Expression, DomainResultProducer<T> {
	private final Object value;
	private final BasicValuedMapping type;
	private final Clause clause;

	public AbstractLiteral(Object value, BasicValuedMapping type, Clause clause) {
		this.value = value;
		this.type = type;
		this.clause = clause;
	}

	public Object getValue() {
		return value;
	}

	public boolean isInSelect() {
		return clause == Clause.SELECT;
	}

	@Override
	public BasicValuedMapping getExpressionType() {
		return type;
	}

	@Override
	public DomainResult<T> createDomainResult(
			String resultVariable,
			DomainResultCreationState creationState) {
		final SqlExpressionResolver sqlExpressionResolver = creationState.getSqlAstCreationState().getSqlExpressionResolver();
		final SqlSelection sqlSelection = sqlExpressionResolver.resolveSqlSelection(
				this,
				type.getMappedType().getMappedJavaTypeDescriptor(),
				creationState.getSqlAstCreationState()
						.getCreationContext()
						.getSessionFactory()
						.getTypeConfiguration()
		);

		//noinspection unchecked
		return new BasicResult<>(
				sqlSelection.getValuesArrayPosition(),
				resultVariable,
				type.getMappedType().getMappedJavaTypeDescriptor()
		);
	}

	@Override
	public void bindParameterValue(
			PreparedStatement statement,
			int startPosition,
			JdbcParameterBindings jdbcParameterBindings,
			ExecutionContext executionContext) throws SQLException {
		//noinspection unchecked
		( ( BasicType<?> ) getExpressionType() ).getJdbcValueBinder().bind(
				statement,
				getValue(),
				startPosition,
				executionContext.getSession()
		);
	}
}
