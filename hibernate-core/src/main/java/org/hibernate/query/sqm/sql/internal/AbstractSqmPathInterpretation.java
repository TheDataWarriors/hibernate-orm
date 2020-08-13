/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.sql.internal;

import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;

/**
 * @author Andrea Boriero
 */
public abstract class AbstractSqmPathInterpretation<T> implements SqmPathInterpretation<T> {

	private final SqmPath<T> sqmPath;
	private final ModelPart mapping;
	private final TableGroup tableGroup;

	public AbstractSqmPathInterpretation(
			SqmPath<T> sqmPath,
			ModelPart mapping,
			TableGroup tableGroup) {
		assert sqmPath != null;
		assert mapping != null;
		assert tableGroup != null;

		this.sqmPath = sqmPath;
		this.mapping = mapping;
		this.tableGroup = tableGroup;
	}

	@Override
	public NavigablePath getNavigablePath() {
		return sqmPath.getNavigablePath();
	}

	@Override
	public ModelPart getExpressionType() {
		return mapping;
	}

	@Override
	public DomainResult<T> createDomainResult(
			String resultVariable,
			DomainResultCreationState creationState) {
		return mapping.createDomainResult(
				getNavigablePath(),
				tableGroup,
				resultVariable,
				creationState
		);
	}

	@Override
	public void applySqlSelections(DomainResultCreationState creationState) {
		mapping.applySqlSelections( getNavigablePath(), tableGroup, creationState );
	}
}
