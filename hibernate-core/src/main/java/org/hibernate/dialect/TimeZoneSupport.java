/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import org.hibernate.Incubating;

/**
 * Describes the extent to which a given database supports the SQL
 * {@code with time zone} types.
 * <p>
 * Really we only care about {@code timestamp with time zone} here,
 * since the type {@code time with time zone} is deeply conceptually
 * questionable, and so Hibernate eschews its use.
 *
 * @author Christian Beikov
 */
@Incubating
public enum TimeZoneSupport {
	/**
	 * The {@code with time zone} types retain the time zone information.
	 * That is, a round trip writing and reading a zoned datetime results
	 * in the exact same zoned datetime with the same timezone.
	 */
	NATIVE,
	/**
	 * The {@code with time zone} types normalize to UTC. That is, a round
	 * trip writing and reading a zoned datetime results in a datetime
	 * representing the same instant, but in the timezone UTC.
	 */
	NORMALIZE,
	/**
	 * No support for {@code with time zone} types.
	 */
	NONE
}
