/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hibernate.tuple.GenerationTiming;

/**
 * Specifies that the database {@code current_timestamp} function is used to
 * generate values of the annotated attribute, based on {@link #timing()}.
 *
 * @see CurrentTimestampGeneration
 *
 * @author Steve Ebersole
 */
@ValueGenerationType(generatedBy = CurrentTimestampGeneration.class)
@Retention(RetentionPolicy.RUNTIME)
@Target( { ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE } )
@Inherited
public @interface CurrentTimestamp {
	GenerationTiming timing();
}
