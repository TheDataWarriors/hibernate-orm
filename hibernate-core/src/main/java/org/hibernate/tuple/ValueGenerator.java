/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.tuple;

import org.hibernate.Session;
import org.hibernate.generator.Generator;

/**
 * Defines a generator for in-VM generation of (non-identifier) attribute values.
 *
 * @deprecated Replaced by {@link Generator}
 *
 * @author Steve Ebersole
 */
@Deprecated(since = "6.2", forRemoval = true)
public interface ValueGenerator<T> {
	/**
	 * Generate the value.
	 *
	 * @param session The Session from which the request originates.
	 * @param owner The instance of the object owning the attribute for which we are generating a value.
	 *
	 * @return The generated value
	 */
	T generateValue(Session session, Object owner);

	/**
	 * Generate the value.
	 *
	 * @param session The Session from which the request originates.
	 * @param owner The instance of the object owning the attribute for which we are generating a value.
	 * @param currentValue The current value assigned to the property
	 *
	 * @return The generated value
	 */
	default T generateValue(Session session, Object owner, Object currentValue) {
		return generateValue( session, owner );
	}
}
