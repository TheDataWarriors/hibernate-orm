/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.tree.from;

import org.hibernate.spi.NavigablePath;
import org.hibernate.query.sqm.sql.internal.DomainResultProducer;
import org.hibernate.sql.ast.SqlAstJoinType;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.SqlTreeCreationLogger;
import org.hibernate.sql.ast.spi.SqlAstTreeHelper;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;

/**
 * @author Steve Ebersole
 */
public class TableGroupJoin implements TableJoin, DomainResultProducer {
	private final NavigablePath navigablePath;
	private final TableGroup joinedGroup;

	private SqlAstJoinType joinType;
	private Predicate predicate;

	private boolean implicit;

	public TableGroupJoin(
			NavigablePath navigablePath,
			SqlAstJoinType joinType,
			TableGroup joinedGroup) {
		this( navigablePath, joinType, joinedGroup, null );
	}

	public TableGroupJoin(
			NavigablePath navigablePath,
			SqlAstJoinType joinType,
			TableGroup joinedGroup,
			Predicate predicate) {
		assert !joinedGroup.isLateral() || ( joinType == SqlAstJoinType.INNER
				|| joinType == SqlAstJoinType.LEFT
				|| joinType == SqlAstJoinType.CROSS )
				: "Lateral is only allowed with inner, left or cross joins";
		this.navigablePath = navigablePath;
		this.joinType = joinType;
		this.joinedGroup = joinedGroup;
		this.predicate = predicate;
	}

	@Override
	public SqlAstJoinType getJoinType() {
		return joinType;
	}

	public void setJoinType(SqlAstJoinType joinType) {
		SqlTreeCreationLogger.LOGGER.debugf(
				"Adjusting join-type for TableGroupJoin(%s) : %s -> %s",
				navigablePath,
				this.joinType,
				joinType
		);
		this.joinType = joinType;
	}

	public TableGroup getJoinedGroup() {
		return joinedGroup;
	}

	@Override
	public SqlAstNode getJoinedNode() {
		return joinedGroup;
	}

	@Override
	public Predicate getPredicate() {
		return predicate;
	}

	public void applyPredicate(Predicate predicate) {
		this.predicate = SqlAstTreeHelper.combinePredicates( this.predicate, predicate );
	}

	@Override
	public void accept(SqlAstWalker sqlTreeWalker) {
		sqlTreeWalker.visitTableGroupJoin( this );
	}

	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	public void setImplicit(){
		this.implicit = true;
	}

	public boolean isImplicit(){
		return implicit;
	}

	@Override
	public DomainResult createDomainResult(
			String resultVariable,
			DomainResultCreationState creationState) {
		return getJoinedGroup().createDomainResult( resultVariable, creationState );
	}

	@Override
	public void applySqlSelections(DomainResultCreationState creationState) {
		getJoinedGroup().applySqlSelections( creationState );
	}
}
