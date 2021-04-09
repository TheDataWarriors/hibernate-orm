/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.tree.expression;

import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.JdbcMappingContainer;
import org.hibernate.metamodel.mapping.SqlExpressable;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.tree.SqlAstNode;

/**
 * @author Christian Beikov
 */
public class Collate implements Expression, SqlExpressable, SqlAstNode {

	private final Expression expression;
	private final String collation;

	public Collate(Expression expression, String collation) {
		this.expression = expression;
		this.collation = collation;
	}

	public Expression getExpression() {
		return expression;
	}

	public String getCollation() {
		return collation;
	}

	@Override
	public JdbcMapping getJdbcMapping() {
		if ( expression instanceof SqlExpressable ) {
			return ( (SqlExpressable) expression ).getJdbcMapping();
		}

		if ( getExpressionType() instanceof SqlExpressable ) {
			return ( (SqlExpressable) getExpressionType() ).getJdbcMapping();
		}

		if ( getExpressionType() != null ) {
			final JdbcMappingContainer mappingContainer = getExpressionType();
			assert mappingContainer.getJdbcTypeCount() == 1;
			return mappingContainer.getJdbcMappings().get( 0 );
		}

		return null;
	}

	@Override
	public JdbcMappingContainer getExpressionType() {
		return expression.getExpressionType();
	}

	@Override
	public void accept(SqlAstWalker sqlTreeWalker) {
		sqlTreeWalker.visitCollate( this );
	}
}
