/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

/**
 * A SQL dialect for Ingres 10 and later versions.
 * <p/>
 * Changes:
 * <ul>
 * <li>Add native BOOLEAN type support</li>
 * <li>Add identity column support</li>
 * </ul>
 *
 * @author Raymond Fan
 */
public class Ingres10Dialect extends Ingres9Dialect {

	@Override
	int getVersion() {
		return 1000;
	}

}
