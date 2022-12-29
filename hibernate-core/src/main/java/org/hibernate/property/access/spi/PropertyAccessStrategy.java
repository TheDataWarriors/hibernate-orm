/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.property.access.spi;

/**
 * Describes a strategy for accessing a persistent attribute,
 * for example: field, JavaBean-style property, or whatever.
 * <p>
 * Acts as a factory for {@link PropertyAccess} instances.
 */
public interface PropertyAccessStrategy {
	/**
	 * Build a {@link PropertyAccess} for the indicated property
	 *
	 * @param containerJavaType The Java type that contains the property; may be {@code null} for non-pojo cases.
	 * @param propertyName The property name
	 * @param setterRequired Whether it is an error if we are unable to find a corresponding setter
	 *
	 * @return The appropriate PropertyAccess
	 */
	PropertyAccess buildPropertyAccess(Class<?> containerJavaType, String propertyName, boolean setterRequired);
}
