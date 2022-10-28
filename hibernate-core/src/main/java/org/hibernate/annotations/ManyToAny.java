/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.annotations;

import java.lang.annotation.Retention;
import jakarta.persistence.FetchType;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares a many-valued association targeting one of several entity types,
 * depending on a local discriminator column. This is the collection-valued
 * form of {@link Any}.
 *
 * @see Any
 *
 * @author Emmanuel Bernard
 * @author Steve Ebersole
 */
@java.lang.annotation.Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface ManyToAny {
	/**
	 * Specifies whether the value of the field or property should be fetched
	 * lazily or eagerly:
	 * <ul>
	 * <li>{@link FetchType#EAGER}, the default, requires that the association
	 *     be fetched immediately, but
	 * <li>{@link FetchType#LAZY} is a hint which has no effect unless bytecode
	 *     enhancement is enabled.
	 * </ul>
	 */
	FetchType fetch() default FetchType.EAGER;
}
