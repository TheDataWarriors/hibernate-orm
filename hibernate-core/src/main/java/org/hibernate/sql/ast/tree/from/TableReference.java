/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.tree.from;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.expression.ColumnReference;

/**
 * Represents a reference to a table (derived or physical) in a query's from clause.
 *
 * @author Steve Ebersole
 */
public class TableReference implements SqlAstNode, ColumnReferenceQualifier {
	private final String tableExpression;
	private final String identificationVariable;

	private final boolean isOptional;
	private final SessionFactoryImplementor sessionFactory;
	private boolean forceRendering;

	private final Map<String, ColumnReference> columnReferenceResolutionMap = new HashMap<>();

	public TableReference(
			String tableExpression,
			String identificationVariable,
			boolean isOptional,
			SessionFactoryImplementor sessionFactory) {
		this.tableExpression = tableExpression;
		this.identificationVariable = identificationVariable;
		this.isOptional = isOptional;
		this.sessionFactory = sessionFactory;
	}

	public boolean isForceRendering() {
		return forceRendering;
	}

	public void forceRendering() {
		this.forceRendering = true;
	}

	public String getTableExpression() {
		return tableExpression;
	}

	public String getIdentificationVariable() {
		return identificationVariable;
	}

	public boolean isOptional() {
		return isOptional;
	}

	@Override
	public void accept(SqlAstWalker sqlTreeWalker) {
		sqlTreeWalker.visitTableReference( this );
	}

	@Override
	public TableReference resolveTableReference(String tableExpression, Supplier<TableReference> creator) {
		throw new UnsupportedOperationException( "Cannot create a TableReference relative to a TableReference" );
	}

	@Override
	public TableReference resolveTableReference(String tableExpression) {
		if ( tableExpression.equals( getTableExpression() ) ) {
			return this;
		}
		return null;
	}

	@Override
	public String toString() {
		return getTableExpression() + "(" + getIdentificationVariable() + ')';
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}
		TableReference that = (TableReference) o;
		return Objects.equals( identificationVariable, that.identificationVariable );
	}

	@Override
	public int hashCode() {
		return Objects.hash( identificationVariable );
	}
}
