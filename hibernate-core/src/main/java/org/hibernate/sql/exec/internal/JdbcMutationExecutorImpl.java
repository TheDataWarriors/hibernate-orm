/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.exec.internal;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.resource.jdbc.spi.LogicalConnectionImplementor;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcMutation;
import org.hibernate.sql.exec.spi.JdbcMutationExecutor;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * @author Steve Ebersole
 */
public class JdbcMutationExecutorImpl implements JdbcMutationExecutor {

	private final boolean callAfterStatement;

	public JdbcMutationExecutorImpl(boolean callAfterStatement) {
		this.callAfterStatement = callAfterStatement;
	}

	@Override
	public int execute(
			JdbcMutation jdbcMutation,
			ExecutionContext executionContext,
			Function<String, PreparedStatement> statementCreator,
			BiConsumer<Integer, PreparedStatement> expectationCkeck) {
		final LogicalConnectionImplementor logicalConnection = executionContext.getSession()
				.getJdbcCoordinator()
				.getLogicalConnection();

		final JdbcServices jdbcServices = executionContext.getSession().getFactory().getServiceRegistry().getService(
				JdbcServices.class );

		final String sql = jdbcMutation.getSql();
		try {
			// prepare the query
			final PreparedStatement preparedStatement = statementCreator.apply( sql );

			try {
				if ( executionContext.getQueryOptions().getTimeout() != null ) {
					preparedStatement.setQueryTimeout( executionContext.getQueryOptions().getTimeout() );
				}

				// bind parameters
				// 		todo : validate that all query parameters were bound?
				int paramBindingPosition = 1;
				for ( JdbcParameterBinder parameterBinder : jdbcMutation.getParameterBinders() ) {
					paramBindingPosition += parameterBinder.bindParameterValue(
							preparedStatement,
							paramBindingPosition,
							executionContext
					);
				}
				int rows = preparedStatement.executeUpdate();
				expectationCkeck.accept( rows, preparedStatement );
				return rows;
			}
			finally {
				logicalConnection.getResourceRegistry().release( preparedStatement );
			}
		}
		catch (SQLException e) {
			throw jdbcServices.getSqlExceptionHelper().convert(
					e,
					"JDBC exception executing SQL [" + sql + "]"
			);
		}
		finally {
			if ( callAfterStatement ) {
				logicalConnection.afterStatement();
			}
		}
	}

	@Override
	public int execute(
			JdbcMutation jdbcMutation,
			ExecutionContext executionContext,
			Function<String, PreparedStatement> statementCreator) {
		return execute(
				jdbcMutation, executionContext,
				statementCreator,
				(integer, preparedStatement) -> {
				}
		);
	}

	public int execute(
			JdbcMutation jdbcMutation,
			ExecutionContext executionContext,
			BiConsumer<Integer, PreparedStatement> expectationCkeck) {
		return execute(
				jdbcMutation,
				executionContext,
				(sql) -> executionContext.getSession()
						.getJdbcCoordinator()
						.getStatementPreparer()
						.prepareStatement( sql ),
				expectationCkeck
		);
	}

	public int execute(JdbcMutation jdbcMutation, ExecutionContext executionContext) {
		return execute(
				jdbcMutation,
				executionContext,
				(sql) -> executionContext.getSession()
						.getJdbcCoordinator()
						.getStatementPreparer()
						.prepareStatement( sql )
		);
	}
}
