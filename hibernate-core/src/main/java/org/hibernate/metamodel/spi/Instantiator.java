/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.spi;

import org.hibernate.Incubating;
import org.hibernate.engine.spi.SessionFactoryImplementor;

/**
 * Strategy for instantiating representation structure instances.
 *
 * @author Steve Ebersole
 */
@Incubating
public interface Instantiator<J> {
	/**
	 * Create an instance of the managed embedded value structure.
	 */
	J instantiate(SessionFactoryImplementor sessionFactory);

	/**
	 * Performs and "instance of" check to see if the given object is an
	 * instance of managed structure
	 */
	boolean isInstance(Object object, SessionFactoryImplementor sessionFactory);

	boolean isSameClass(Object object, SessionFactoryImplementor sessionFactory);

}
