/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hibernate.tuple.AnnotationValueGeneration;
import org.hibernate.tuple.ValueGeneration;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Meta-annotation used to mark another annotation as providing configuration
 * for a custom {@linkplain ValueGeneration value generation strategy}. This
 * is the best way to work with customized value generation in Hibernate.
 * <p>
 * For example, if we have a custom value generator:
 * <pre>{@code
 * public class SKUGeneration implements AnnotationValueGeneration<SKU>, ValueGenerator<String> {
 *     ...
 * }
 * }</pre>
 * Then we may also define an annotation which associates this generator with
 * a field or property of an entity and supplies configuration parameters:
 * <pre>{@code
 * @ValueGenerationType(generatedBy = SKUGeneration.class)
 * @Retention(RUNTIME) @Target({METHOD,FIELD})
 * public @interface SKU {}
 * }</pre>
 * and we may use it as follows:
 * <pre>{@code @SKU String sku;}</pre>
 * No more than one generator annotation may be placed on a given property.
 * <p>
 * Adding a generator annotation to an entity property causes the value of the
 * property to be generated when any SQL statement to {@code insert} or
 * {@code update} the entity is executed.
 * <p>
 * Each generator annotation type has an {@link AnnotationValueGeneration}
 * implementation which is responsible for generating values. The generator
 * annotation may have members, which are used to configure the generator,
 * when {@link AnnotationValueGeneration#initialize} is called.
 * <p>
 * There are several excellent examples of the use of this machinery right
 * here in this package. {@link TenantId} and its corresponding generator
 * {@link org.hibernate.tuple.TenantIdGeneration} are a good place to start.
 * <p>
 * A {@code @ValueGenerationType} annotation must have retention policy
 * {@link RetentionPolicy#RUNTIME}.
 *
 * @author Gunnar Morling
 */
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
public @interface ValueGenerationType {
	/**
	 * The type of value generation associated with the annotated value generator annotation type. The referenced
	 * generation type must be parameterized with the type of the given generator annotation.
	 *
	 * @return the value generation type
	 */
	Class<? extends AnnotationValueGeneration<?>> generatedBy();
}
