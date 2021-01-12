/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SqmQuerySource;
import org.hibernate.query.sqm.tree.cte.SqmCteStatement;
import org.hibernate.query.sqm.tree.from.SqmRoot;
import org.hibernate.query.sqm.tree.select.SqmSubQuery;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractSqmDmlStatement<E>
		extends AbstractSqmStatement<E>
		implements SqmDmlStatement<E> {
	private final Map<String, SqmCteStatement<?>> cteStatements = new LinkedHashMap<>();
	private boolean withRecursiveCte;
	private SqmRoot<E> target;

	public AbstractSqmDmlStatement(SqmQuerySource querySource, NodeBuilder nodeBuilder) {
		super( querySource, nodeBuilder );
	}

	public AbstractSqmDmlStatement(SqmRoot<E> target, SqmQuerySource querySource, NodeBuilder nodeBuilder) {
		this( querySource, nodeBuilder );
		this.target = target;
	}

	@Override
	public boolean isWithRecursive() {
		return withRecursiveCte;
	}

	@Override
	public void setWithRecursive(boolean withRecursiveCte) {
		this.withRecursiveCte = withRecursiveCte;
	}

	@Override
	public Collection<SqmCteStatement<?>> getCteStatements() {
		return cteStatements.values();
	}

	@Override
	public SqmCteStatement<?> getCteStatement(String cteLabel) {
		return cteStatements.get( cteLabel );
	}

	@Override
	public void addCteStatement(SqmCteStatement<?> cteStatement) {
		if ( cteStatements.putIfAbsent( cteStatement.getCteTable().getCteName(), cteStatement ) != null ) {
			throw new IllegalArgumentException( "A CTE with the label " + cteStatement.getCteTable().getCteName() + " already exists!" );
		}
	}

	@Override
	public SqmRoot<E> getTarget() {
		return target;
	}

	@Override
	public void setTarget(SqmRoot<E> root) {
		this.target = root;
	}

	@Override
	public <U> SqmSubQuery<U> subquery(Class<U> type) {
		return new SqmSubQuery<>( this, type, nodeBuilder() );
	}
}
