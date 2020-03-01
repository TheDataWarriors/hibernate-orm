/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.mutation.spi;

import org.hibernate.engine.jdbc.connections.spi.JdbcConnectionAccess;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.internal.MappingModelCreationProcess;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.mutation.internal.SqmMutationStrategyHelper;
import org.hibernate.query.sqm.tree.delete.SqmDeleteStatement;
import org.hibernate.query.sqm.tree.update.SqmUpdateStatement;
import org.hibernate.sql.exec.spi.ExecutionContext;

/**
 * Pluggable strategy for defining how mutation (`UPDATE` or `DELETE`) queries should be handled when the target
 * entity is mapped to multiple tables via secondary tables or certain inheritance strategies.
 *
 * The main contracts here are {@link #executeUpdate} and {@link #executeDelete}.
 *
 * {@link #prepare} and {@link #release} allow the strategy to perform any one time preparation and cleanup.
 *
 * @apiNote See {@link SqmMutationStrategyHelper#resolveStrategy} for standard resolution of the strategy to use
 * for each hierarchy
 *
 * @author Steve Ebersole
 */
public interface SqmMultiTableMutationStrategy {

	/**
	 * Prepare the strategy for use.  Called one time as the SessionFactory
	 * is being built.
	 */
	default void prepare(
			MappingModelCreationProcess mappingModelCreationProcess,
			JdbcConnectionAccess connectionAccess) {
		// by default, nothing to do...
	}

	/**
	 * Release the strategy.   Called one time as the SessionFactory is
	 * being shut down.
	 */
	default void release(SessionFactoryImplementor sessionFactory, JdbcConnectionAccess connectionAccess) {
		// by default, nothing to do...
	}

	/**
	 * Execute the multi-table update indicated by the passed SqmUpdateStatement
	 *
	 * @return The number of rows affected
	 */
	int executeUpdate(
			SqmUpdateStatement sqmUpdateStatement,
			DomainParameterXref domainParameterXref,
			ExecutionContext context);

	/**
	 * Execute the multi-table update indicated by the passed SqmUpdateStatement
	 *
	 * @return The number of rows affected
	 */
	int executeDelete(
			SqmDeleteStatement sqmDeleteStatement,
			DomainParameterXref domainParameterXref,
			ExecutionContext context);
}
