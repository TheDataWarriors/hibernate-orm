/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.tree.expression;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.MappingModelExpressable;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.spi.SqlAppender;

/**
 * A wrapper for a literal to render as parameter through a cast function.
 *
 * @see org.hibernate.sql.ast.spi.AbstractSqlAstWalker
 *
 * @author Christian beikov
 */
public class LiteralAsParameter<T> implements SelfRenderingExpression {

	private final Literal literal;

	public LiteralAsParameter(Literal literal) {
		this.literal = literal;
	}

	@Override
	public void renderToSql(SqlAppender sqlAppender, SqlAstWalker walker, SessionFactoryImplementor sessionFactory) {
		sqlAppender.appendSql( "?" );
	}

	@Override
	public MappingModelExpressable getExpressionType() {
		return literal.getExpressionType();
	}

	public Literal getLiteral() {
		return literal;
	}
}
