/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.criteria;

import org.hibernate.HibernateException;

/**
 * This enum defines how values passed to JPA Criteria API are handled.
 *
 * The {@code BIND} mode (default) will use bind variables for any value.
 *
 * The {@code INLINE} mode will inline values as literals.
 *
 * @author Christian Beikov
 */
public enum ValueHandlingMode {
	BIND,
	INLINE;

	/**
	 * Interpret the configured valueHandlingMode value.
	 * Valid values are either a {@link ValueHandlingMode} object or its String representation.
	 * For string values, the matching is case insensitive, so you can use either {@code BIND} or {@code bind}.
	 *
	 * @param valueHandlingMode configured {@link ValueHandlingMode} representation
	 * @return associated {@link ValueHandlingMode} object
	 */
	public static ValueHandlingMode interpret(Object valueHandlingMode) {
		if ( valueHandlingMode == null ) {
			return BIND;
		}
		else if ( valueHandlingMode instanceof ValueHandlingMode ) {
			return (ValueHandlingMode) valueHandlingMode;
		}
		else if ( valueHandlingMode instanceof String ) {
			for ( ValueHandlingMode value : values() ) {
				if ( value.name().equalsIgnoreCase( (String) valueHandlingMode ) ) {
					return value;
				}
			}
		}
		throw new HibernateException(
				"Unrecognized value_handling_mode value : " + valueHandlingMode
						+ ".  Supported values include 'inline' and 'bind'."
		);
	}
}
