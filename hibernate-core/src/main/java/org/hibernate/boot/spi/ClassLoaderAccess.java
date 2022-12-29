/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.spi;

import java.net.URL;

/**
 * During the process of building the metamodel, access to the {@link ClassLoader}
 * is highly discouraged. However, sometimes it is needed. This contract helps mitigate
 * access to the {@link ClassLoader} in these cases.
 *
 * @author Steve Ebersole
 *
 * @since 5.0
 */
public interface ClassLoaderAccess {
	/**
	 * Obtain a Class reference by name
	 *
	 * @param name The name of the Class to get a reference to.
	 *
	 * @return The Class.
	 */
	<T> Class<T> classForName(String name);

	/**
	 * Locate a resource by name
	 *
	 * @param resourceName The name of the resource to resolve.
	 *
	 * @return The located resource; may return {@code null} to indicate the resource was not found
	 */
	URL locateResource(String resourceName);
}
