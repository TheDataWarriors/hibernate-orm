/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.tree.expression;

import java.util.List;

import org.hibernate.metamodel.mapping.MappingModelExpressable;
import org.hibernate.sql.ast.SqlAstWalker;

/**
 * @author Christian Beikov
 */
public class Summarization implements Expression {

	private final Kind kind;
	private final List<Expression> groupings;

	public Summarization(Kind kind, List<Expression> groupings) {
		this.kind = kind;
		this.groupings = groupings;
	}

	public Kind getKind() {
		return kind;
	}

	public List<Expression> getGroupings() {
		return groupings;
	}

	@Override
	public MappingModelExpressable getExpressionType() {
		return null;
	}

	@Override
	public void accept(SqlAstWalker walker) {
		walker.visitSummarization( this );
	}

	public enum Kind {
		ROLLUP,
		CUBE
	}
}
