/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.select;

import org.hibernate.NullPrecedence;
import org.hibernate.SortOrder;
import org.hibernate.query.criteria.JpaExpression;
import org.hibernate.query.criteria.JpaOrder;
import org.hibernate.query.sqm.tree.expression.SqmExpression;

/**
 * @author Steve Ebersole
 */
public class SqmSortSpecification implements JpaOrder {
	private final SqmExpression sortExpression;
	private final SortOrder sortOrder;

	private NullPrecedence nullPrecedence;

	public SqmSortSpecification(
			SqmExpression sortExpression,
			SortOrder sortOrder,
			NullPrecedence nullPrecedence) {
		this.sortExpression = sortExpression;
		this.sortOrder = sortOrder;
		this.nullPrecedence = nullPrecedence;
	}

	public SqmSortSpecification(SqmExpression sortExpression) {
		this( sortExpression, SortOrder.ASCENDING, null );
	}

	public SqmSortSpecification(SqmExpression sortExpression, SortOrder sortOrder) {
		this( sortExpression, sortOrder, null );
	}

	public SqmExpression getSortExpression() {
		return sortExpression;
	}

	public SortOrder getSortOrder() {
		return sortOrder;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// JPA

	@Override
	public JpaOrder nullPrecedence(NullPrecedence nullPrecedence) {
		this.nullPrecedence = nullPrecedence;
		return this;
	}

	@Override
	public NullPrecedence getNullPrecedence() {
		return nullPrecedence;
	}

	@Override
	public JpaOrder reverse() {
		SortOrder newSortOrder = this.sortOrder == null ? SortOrder.DESCENDING : sortOrder.reverse();
		return new SqmSortSpecification( sortExpression, newSortOrder, nullPrecedence );
	}

	@Override
	public JpaExpression<?> getExpression() {
		return getSortExpression();
	}

	@Override
	public boolean isAscending() {
		return sortOrder == SortOrder.ASCENDING;
	}
}
