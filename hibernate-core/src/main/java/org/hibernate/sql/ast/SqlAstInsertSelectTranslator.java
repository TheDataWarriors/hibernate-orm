/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast;

import org.hibernate.sql.ast.tree.insert.InsertStatement;
import org.hibernate.sql.exec.spi.JdbcInsert;

/**
 * @author Steve Ebersole
 */
public interface SqlAstInsertSelectTranslator extends SqlAstTranslator {
	JdbcInsert translate(InsertStatement sqlAst);
}
