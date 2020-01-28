/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

/**
 * An SQL dialect for Postgres 8.2 and later, adds support for "if exists" when dropping tables
 * 
 * @author edalquist
 *
 * @deprecated use {@code PostgreSQLDialect(820)}
 */
@Deprecated
public class PostgreSQL82Dialect extends PostgreSQLDialect {

	public PostgreSQL82Dialect() {
		super(820);
	}

}
