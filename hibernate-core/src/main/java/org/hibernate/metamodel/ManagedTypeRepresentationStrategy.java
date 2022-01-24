/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel;

import org.hibernate.Incubating;
import org.hibernate.bytecode.spi.ReflectionOptimizer;
import org.hibernate.mapping.Property;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * Defines a singular extension point for capabilities pertaining to
 * a representation mode.  Acts as a factory for delegates encapsulating
 * these capabilities.
 *
 * @see org.hibernate.metamodel.spi.ManagedTypeRepresentationResolver
 */
@Incubating
public interface ManagedTypeRepresentationStrategy {
	/**
	 * The mode represented
	 */
	RepresentationMode getMode();

	/**
	 * The reflection optimizer to use for this embeddable.
	 */
	ReflectionOptimizer getReflectionOptimizer();

	/**
	 * The Java type descriptor for the concrete type.  For dynamic-map models
	 * this will return the JTD for java.util.Map
	 */
	JavaType<?> getMappedJavaType();

	/**
	 * Create the property accessor object for the specified attribute
	 */
	PropertyAccess resolvePropertyAccess(Property bootAttributeDescriptor);
}
