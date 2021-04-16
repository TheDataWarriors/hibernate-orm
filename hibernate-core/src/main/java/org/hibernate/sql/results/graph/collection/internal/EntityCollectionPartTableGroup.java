/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.graph.collection.internal;

import java.util.List;
import java.util.function.Consumer;

import org.hibernate.LockMode;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.internal.EntityCollectionPart;
import org.hibernate.query.NavigablePath;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.from.TableReferenceJoin;

/**
 * @author Steve Ebersole
 */
public class EntityCollectionPartTableGroup implements TableGroup {
	private final NavigablePath collectionPartPath;
	private final TableGroup collectionTableGroup;
	private final EntityCollectionPart collectionPart;

	public EntityCollectionPartTableGroup(
			NavigablePath collectionPartPath,
			TableGroup collectionTableGroup,
			EntityCollectionPart collectionPart) {
		this.collectionPartPath = collectionPartPath;
		this.collectionTableGroup = collectionTableGroup;
		this.collectionPart = collectionPart;
	}

	@Override
	public NavigablePath getNavigablePath() {
		return collectionPartPath;
	}

	@Override
	public ModelPart getExpressionType() {
		return getModelPart();
	}

	@Override
	public String getGroupAlias() {
		return null;
	}

	@Override
	public EntityCollectionPart getModelPart() {
		return collectionPart;
	}

	@Override
	public LockMode getLockMode() {
		return collectionTableGroup.getLockMode();
	}

	@Override
	public List<TableGroupJoin> getTableGroupJoins() {
		return collectionTableGroup.getTableGroupJoins();
	}

	@Override
	public boolean hasTableGroupJoins() {
		return collectionTableGroup.hasTableGroupJoins();
	}

	@Override
	public void addTableGroupJoin(TableGroupJoin join) {
		collectionTableGroup.addTableGroupJoin( join );
	}

	@Override
	public void visitTableGroupJoins(Consumer<TableGroupJoin> consumer) {
		collectionTableGroup.visitTableGroupJoins( consumer );
	}

	@Override
	public void applyAffectedTableNames(Consumer<String> nameCollector) {
		collectionTableGroup.applyAffectedTableNames( nameCollector );
	}

	@Override
	public TableReference getPrimaryTableReference() {
		return collectionTableGroup.getPrimaryTableReference();
	}

	@Override
	public List<TableReferenceJoin> getTableReferenceJoins() {
		return collectionTableGroup.getTableReferenceJoins();
	}

	@Override
	public boolean isInnerJoinPossible() {
		return collectionTableGroup.isInnerJoinPossible();
	}

	@Override
	public TableReference getTableReference(NavigablePath navigablePath, String tableExpression) {
		return collectionTableGroup.getTableReference( navigablePath, tableExpression );
	}

	@Override
	public TableReference resolveTableReference(NavigablePath navigablePath, String tableExpression) {
		return collectionTableGroup.resolveTableReference( navigablePath, tableExpression );
	}

	@Override
	public void accept(SqlAstWalker sqlTreeWalker) {
		// do nothing
	}
}
