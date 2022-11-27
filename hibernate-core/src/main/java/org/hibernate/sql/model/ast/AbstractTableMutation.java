/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.sql.model.ast;

import java.util.List;
import java.util.function.BiConsumer;

import org.hibernate.engine.jdbc.mutation.internal.MutationQueryOptions;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.model.MutationOperation;
import org.hibernate.sql.model.MutationTarget;
import org.hibernate.sql.model.TableMapping;
import org.hibernate.sql.model.ValuesAnalysis;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;

/**
 * Base TableMutation support
 *
 * @author Steve Ebersole
 */
public abstract class AbstractTableMutation<O extends MutationOperation>
		implements TableMutation<O> {
	private final MutatingTableReference mutatingTable;
	private final MutationTarget<?> mutationTarget;
	private final String sqlComment;

	private final List<ColumnValueParameter> parameters;

	public AbstractTableMutation(
			MutatingTableReference mutatingTable,
			MutationTarget<?> mutationTarget,
			String sqlComment,
			List<ColumnValueParameter> parameters) {
		this.mutatingTable = mutatingTable;
		this.mutationTarget = mutationTarget;
		this.sqlComment = sqlComment;
		this.parameters = parameters;
	}

	@Override
	public MutatingTableReference getMutatingTable() {
		return mutatingTable;
	}

	public MutationTarget<?> getMutationTarget() {
		return mutationTarget;
	}

	@Override
	public String getMutationComment() {
		return sqlComment;
	}

	@Override
	public List<ColumnValueParameter> getParameters() {
		return parameters;
	}

	protected <T> void forEachThing(List<T> list, BiConsumer<Integer,T> action) {
		for ( int i = 0; i < list.size(); i++ ) {
			action.accept( i, list.get( i ) );
		}
	}

	@Override
	public O createMutationOperation(ValuesAnalysis valuesAnalysis, SessionFactoryImplementor factory) {
		final SqlAstTranslatorFactory sqlAstTranslatorFactory = factory
				.getJdbcServices()
				.getJdbcEnvironment()
				.getSqlAstTranslatorFactory();
		//noinspection unchecked
		final SqlAstTranslator<JdbcMutationOperation> translator = sqlAstTranslatorFactory.buildModelMutationTranslator(
				(TableMutation<JdbcMutationOperation>) this,
				factory
		);

		//noinspection unchecked
		return (O) translator.translate( null, MutationQueryOptions.INSTANCE );
	}

	/**
	 * Intended for use from {@link SqlAstTranslator}
	 */
	@Override
	public final O createMutationOperation(String sql, List<JdbcParameterBinder> parameterBinders) {
		return createMutationOperation( getMutatingTable().getTableMapping(), sql, parameterBinders );
	}

	/**
	 * Intended for use from {@link SqlAstTranslator}
	 *
	 * @param effectiveBinders The parameter binders effective for this table mutation
	 */
	protected abstract O createMutationOperation(
			TableMapping tableDetails,
			String sql,
			List<JdbcParameterBinder> effectiveBinders);
}
