/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

/**
 * An SQL Dialect for PostgreSQL 9.3 and later.
 * Adds support for Materialized view.
 *
 * @author Dionis Argiri
 *
 * @deprecated use {@code PostgreSQLDialect(810)}
 */
@Deprecated
public class PostgreSQL93Dialect extends PostgreSQLDialect {

	public PostgreSQL93Dialect() {
		super(930);
	}

}
