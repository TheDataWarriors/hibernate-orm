/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.spi.AbstractSqlAstTranslator;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.cte.CteStatement;
import org.hibernate.sql.ast.tree.select.QueryPart;
import org.hibernate.sql.exec.spi.JdbcOperation;

/**
 * A SQL AST translator for MimerSQL.
 *
 * @author Christian Beikov
 */
public class MimerSQLSqlAstTranslator<T extends JdbcOperation> extends AbstractSqlAstTranslator<T> {

	public MimerSQLSqlAstTranslator(SessionFactoryImplementor sessionFactory, Statement statement) {
		super( sessionFactory, statement );
	}

	@Override
	public void visitOffsetFetchClause(QueryPart queryPart) {
		assertRowsOnlyFetchClauseType( queryPart );
		renderOffsetFetchClause( queryPart, true );
	}

	@Override
	protected void renderSearchClause(CteStatement cte) {
		// MimerSQL does not support this, but it's just a hint anyway
	}

	@Override
	protected void renderCycleClause(CteStatement cte) {
		// MimerSQL does not support this, but it can be emulated
	}
}
