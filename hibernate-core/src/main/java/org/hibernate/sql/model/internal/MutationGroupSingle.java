/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.sql.model.internal;

import java.util.Locale;
import java.util.function.BiConsumer;

import org.hibernate.sql.model.MutationTarget;
import org.hibernate.sql.model.MutationType;
import org.hibernate.sql.model.ast.MutationGroup;
import org.hibernate.sql.model.ast.TableMutation;

/**
 * MutationGroup implementation for cases where we have a
 * single table operation
 *
 * @author Steve Ebersole
 */
public class MutationGroupSingle implements MutationGroup {
	private final MutationType mutationType;
	private final MutationTarget<?> mutationTarget;
	private final TableMutation<?> tableMutation;

	public MutationGroupSingle(
			MutationType mutationType,
			MutationTarget<?> mutationTarget,
			TableMutation<?> tableMutation) {
		this.mutationType = mutationType;
		this.mutationTarget = mutationTarget;
		this.tableMutation = tableMutation;
	}

	@Override
	public MutationType getMutationType() {
		return mutationType;
	}

	@Override
	public MutationTarget<?> getMutationTarget() {
		return mutationTarget;
	}

	@Override
	public int getNumberOfTableMutations() {
		return 1;
	}

	@Override
	public TableMutation getSingleTableMutation() {
		return tableMutation;
	}

	@Override
	public TableMutation getTableMutation(String tableName) {
		assert tableMutation.getMutatingTable().getTableName().equals( tableName );
		return tableMutation;
	}

	@Override
	public void forEachTableMutation(BiConsumer<Integer, TableMutation> action) {
		action.accept( 0, tableMutation );
	}

	@Override
	public String toString() {
		return String.format(
				Locale.ROOT,
				"MutationSqlGroup( %s:`%s` )",
				mutationType.name(),
				mutationTarget.getNavigableRole().getFullPath()
		);
	}
}
