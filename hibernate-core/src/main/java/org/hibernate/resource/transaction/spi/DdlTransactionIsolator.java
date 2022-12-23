/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.resource.transaction.spi;

import java.sql.Connection;

import org.hibernate.tool.schema.internal.exec.JdbcContext;

/**
 * Provides access to a Connection that is isolated from
 * any "current transaction" with the designed purpose of
 * performing DDL commands
 *
 * @author Steve Ebersole
 */
public interface DdlTransactionIsolator {
	JdbcContext getJdbcContext();

	/**
	 * Returns a {@link Connection} that is usable within the bounds of the
	 * {@link TransactionCoordinatorBuilder#buildDdlTransactionIsolator}
	 * and {@link #release} calls, with autocommit mode enabled. Further,
	 * this {@code Connection} will be isolated (transactionally) from any
	 * transaction in effect prior to the call to
	 * {@code buildDdlTransactionIsolator}.
	 *
	 * @return The Connection.
	 */
	Connection getIsolatedConnection();

	/**
	 * Returns a {@link Connection} that is usable within the bounds of the
	 * {@link TransactionCoordinatorBuilder#buildDdlTransactionIsolator}
	 * and {@link #release} calls, with the given autocommit mode. Further,
	 * this {@code Connection} will be isolated (transactionally) from any
	 * transaction in effect prior to the call to
	 * {@code buildDdlTransactionIsolator}.
	 *
	 * @return The Connection.
	 */
	Connection getIsolatedConnection(boolean autocommit);

	void release();
}
