/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks an arbitrary class as available for use in HQL queries by its unqualified name.
 * <p>
 * By default, non-entity class names must be fully-qualified in the query language.
 *
 * @author Gavin King
 *
 * @since 6.2
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface Imported {
	/**
	 * Provide an alias for the class, to avoid collisions.
	 */
	String rename() default "";
}
