/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.sql.model.jdbc;

import java.util.List;

import org.hibernate.engine.jdbc.mutation.ParameterUsage;
import org.hibernate.engine.jdbc.mutation.internal.JdbcValueDescriptorImpl;
import org.hibernate.engine.jdbc.mutation.spi.BindingGroup;
import org.hibernate.jdbc.Expectation;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.model.MutationTarget;
import org.hibernate.sql.model.TableMapping;

import static org.hibernate.internal.util.collections.CollectionHelper.arrayList;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractJdbcMutation implements JdbcMutationOperation {
	private final TableMapping tableDetails;
	private final MutationTarget<?> mutationTarget;
	private final String sql;
	private final boolean callable;
	private final Expectation expectation;

	private final List<JdbcValueDescriptor> jdbcValueDescriptors;
	private final List<? extends JdbcParameterBinder> parameterBinders;

	public AbstractJdbcMutation(
			TableMapping tableDetails,
			MutationTarget<?> mutationTarget,
			String sql,
			boolean callable,
			Expectation expectation,
			List<? extends JdbcParameterBinder> parameterBinders) {
		this.tableDetails = tableDetails;
		this.mutationTarget = mutationTarget;
		this.sql = sql;
		this.callable = callable;
		this.expectation = expectation;
		this.parameterBinders = parameterBinders;

		this.jdbcValueDescriptors = arrayList( parameterBinders.size() );
		for ( int i = 0; i < parameterBinders.size(); i++ ) {
			final JdbcValueDescriptorImpl parameterDescriptor = new JdbcValueDescriptorImpl(
					parameterBinders.get( i ),
					expectation.getNumberOfParametersUsed() + i + 1
			);
			this.jdbcValueDescriptors.add( parameterDescriptor );
		}
	}

	@Override
	public TableMapping getTableDetails() {
		return tableDetails;
	}

	@Override
	public MutationTarget<?> getMutationTarget() {
		return mutationTarget;
	}

	@Override
	public String getSqlString() {
		return sql;
	}

	@Override
	public List<JdbcParameterBinder> getParameterBinders() {
		//noinspection unchecked
		return (List<JdbcParameterBinder>) parameterBinders;
	}

	@Override
	public boolean bindValues(BindingGroup bindingGroup, String columnName, ParameterUsage usage, Object value) {
		boolean found = false;
		for ( int i = 0; i < jdbcValueDescriptors.size(); i++ ) {
			final JdbcValueDescriptor descriptor = jdbcValueDescriptors.get( i );
			if ( descriptor.getColumnName().equals( columnName )
					&& descriptor.getUsage() == usage ) {
				found = true;
				bindingGroup.bindValue( columnName, value, descriptor );
			}
		}
		return found;
	}

	@Override
	public boolean isCallable() {
		return callable;
	}

	@Override
	public Expectation getExpectation() {
		return expectation;
	}
}
