/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies that a column has a {@code DEFAULT} value specified in DDL,
 * and whether Hibernate should fetch the defaulted value from the database.
 * <p>
 * {@code @ColumnDefault} may be used in combination with:
 * <ul>
 *     <li>{@code DynamicInsert}, to let the database fill in the value of
 *     a null entity attribute, or
 *     <li>{@code @Generated(INSERT)}, to populate an entity attribute with
 *     the defaulted value of a database column.
 * </ul>
 *
 * @author Steve Ebersole
 *
 * @see GeneratedColumn
 */
@Target( {FIELD, METHOD} )
@Retention( RUNTIME )
public @interface ColumnDefault {
	/**
	 * The {@code DEFAULT} value to use in generated DDL.
	 *
	 * @return a SQL expression that evaluates to the default column value
	 */
	String value();

	/**
	 * The name of the generated column. Optional for a field or property
	 * mapped to a single column.
	 * <ul>
	 * <li>If the column name is explicitly specified using the
	 * {@link jakarta.persistence.Column @Column} annotation, the name given
	 * here must match the name specified by
	 * {@link jakarta.persistence.Column#name}.
	 * <li>Or, if the column name is inferred implicitly by Hibernate, the
	 * name given here must match the inferred name.
	 * </ul>
	 */
	String name() default "";
}
