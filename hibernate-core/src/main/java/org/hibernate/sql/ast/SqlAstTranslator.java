/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast;

import java.util.Set;

import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptorIndicators;

/**
 * @author Steve Ebersole
 */
public interface SqlAstTranslator<T extends JdbcOperation> extends SqlAstWalker, SqlTypeDescriptorIndicators {
	/**
	 * Not the best spot for this.  Its the table names collected while walking the SQL AST.
	 * Its ok here because the translator is consider a one-time-use.  It just needs to be called
	 * after translation.
	 *
	 * A better option is probably to have "translation" objects that expose the affected table-names.
	 */
	Set<String> getAffectedTableNames();

	T translate(JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions);

}
