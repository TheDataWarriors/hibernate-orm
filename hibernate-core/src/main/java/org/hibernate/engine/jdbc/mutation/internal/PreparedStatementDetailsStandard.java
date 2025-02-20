/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.engine.jdbc.mutation.internal;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Supplier;

import org.hibernate.engine.jdbc.mutation.group.PreparedStatementDetails;
import org.hibernate.engine.jdbc.mutation.group.PreparedStatementGroup;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.jdbc.Expectation;
import org.hibernate.sql.model.PreparableMutationOperation;
import org.hibernate.sql.model.TableMapping;

/**
 * Describes a particular {@link PreparedStatement} within a {@linkplain PreparedStatementGroup group}
 *
 * @author Steve Ebersole
 */
public class PreparedStatementDetailsStandard implements PreparedStatementDetails {
	private final TableMapping mutatingTableDetails;
	private final String sql;
	private final Supplier<PreparedStatement> jdbcStatementCreator;
	private final Expectation expectation;
	private final JdbcServices jdbcServices;

	private PreparedStatement statement;

	public PreparedStatementDetailsStandard(
			PreparableMutationOperation tableMutation,
			Supplier<PreparedStatement> jdbcStatementCreator,
			JdbcServices jdbcServices) {
		this(
				tableMutation,
				tableMutation.getSqlString(),
				jdbcStatementCreator,
				tableMutation.getExpectation(),
				jdbcServices
		);
	}

	public PreparedStatementDetailsStandard(
			PreparableMutationOperation tableMutation,
			String sql,
			Supplier<PreparedStatement> jdbcStatementCreator,
			Expectation expectation,
			JdbcServices jdbcServices) {
		this.mutatingTableDetails = tableMutation.getTableDetails();
		this.sql = sql;
		this.jdbcStatementCreator = jdbcStatementCreator;
		this.expectation = expectation;
		this.jdbcServices = jdbcServices;
	}

	@Override
	public TableMapping getMutatingTableDetails() {
		return mutatingTableDetails;
	}

	@Override
	public void releaseStatement(SharedSessionContractImplementor session) {
		if ( statement != null ) {
			session.getJdbcCoordinator().getLogicalConnection().getResourceRegistry().release( statement );
			statement = null;
		}
	}

	@Override
	public String getSqlString() {
		return sql;
	}

	@Override
	public PreparedStatement getStatement() {
		return statement;
	}

	@Override
	public PreparedStatement resolveStatement() {
		if ( statement == null ) {
			statement = jdbcStatementCreator.get();
			try {
				expectation.prepare( statement );
			}
			catch (SQLException e) {
				throw jdbcServices.getSqlExceptionHelper().convert(
						e,
						"Unable to prepare for expectation",
						sql
				);
			}
		}
		return statement;
	}

	@Override
	public Expectation getExpectation() {
		return expectation;
	}

	@Override
	public String toString() {
		return "PreparedStatementDetails(" + sql + ")";
	}
}
