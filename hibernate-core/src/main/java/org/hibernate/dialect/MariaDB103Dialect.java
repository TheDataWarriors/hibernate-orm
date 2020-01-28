/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

/**
 * An SQL dialect for MariaDB 10.3 and later, provides sequence support, lock-timeouts, etc.
 * 
 * @author Philippe Marschall
 *
 * @deprecated use {@code MariaDBDialect(1030)}
 */
@Deprecated
public class MariaDB103Dialect extends MariaDBDialect {

	public MariaDB103Dialect() {
		super(1030);
	}

}
