/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.tuple;

import java.io.Serializable;

import org.hibernate.metamodel.spi.ManagedTypeRepresentationStrategy;

/**
 * Contract for implementors responsible for instantiating entity/component instances.
 *
 * @deprecated This contract is no longer used by Hibernate.  Implement/use
 * {@link org.hibernate.metamodel.spi.Instantiator} instead.  See
 * {@link ManagedTypeRepresentationStrategy}
 */
@Deprecated(since = "6.0")
public interface Instantiator extends Serializable {

	/**
	 * Perform the requested entity instantiation.
	 * <p>
	 * This form is never called for component instantiation, only entity instantiation.
	 *
	 * @param id The id of the entity to be instantiated.
	 * @return An appropriately instantiated entity.
	 */
	Object instantiate(Object id);

	/**
	 * Perform the requested instantiation.
	 *
	 * @return The instantiated data structure.
	 */
	Object instantiate();

	/**
	 * Performs check to see if the given object is an instance of the entity
	 * or component which this Instantiator instantiates.
	 *
	 * @param object The object to be checked.
	 * @return True is the object does represent an instance of the underlying
	 * entity/component.
	 */
	boolean isInstance(Object object);
}
