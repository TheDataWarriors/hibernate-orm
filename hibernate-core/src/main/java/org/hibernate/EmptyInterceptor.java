/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate;

import java.io.Serializable;

import org.hibernate.metamodel.RepresentationMode;
import org.hibernate.type.Type;

/**
 * An interceptor that does nothing.  May be used as a base class for application-defined custom interceptors.
 * 
 * @author Gavin King
 *
 * @deprecated implement {@link Interceptor} directly
 */
@Deprecated(since = "6.0")
public class EmptyInterceptor implements Interceptor, Serializable {

	protected EmptyInterceptor() {}

}
