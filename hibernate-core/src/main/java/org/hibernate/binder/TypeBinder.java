/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.binder;

import org.hibernate.Incubating;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;

import java.lang.annotation.Annotation;

/**
 * Allows a user-written annotation to drive some customized model binding.
 * <p>
 * An implementation of this interface interacts directly with model objects
 * like {@link PersistentClass} and {@link Component} to implement the
 * semantics of some {@linkplain org.hibernate.annotations.TypeBinderType
 * custom mapping annotation}.
 *
 * @see org.hibernate.annotations.TypeBinderType
 *
 * @author Gavin King
 */
@Incubating
public interface TypeBinder<A extends Annotation> {
	/**
	 * Perform some custom configuration of the model relating to the given annotated
	 * {@link PersistentClass entity class}.
	 *
	 * @param annotation an annotation of the entity class that is declared as an
	 *                   {@link org.hibernate.annotations.TypeBinderType}
	 * @param persistentClass the entity class
	 */
	void bind(A annotation, MetadataBuildingContext buildingContext, PersistentClass persistentClass);
	/**
	 * Perform some custom configuration of the model relating to the given annotated
	 * {@link Component embeddable class}.
	 *
	 * @param annotation an annotation of the embeddable class that is declared as an
	 *                   {@link org.hibernate.annotations.TypeBinderType}
	 * @param embeddableClass the embeddable class
	 */
	void bind(A annotation, MetadataBuildingContext buildingContext, Component embeddableClass);
}
