/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.annotations;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.hibernate.query.NativeQuery;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Extends {@link jakarta.persistence.NamedNativeQuery} with Hibernate features.
 *
 * @author Emmanuel Bernard
 *
 * @see NativeQuery
 */
@Target({TYPE, PACKAGE})
@Retention(RUNTIME)
@Repeatable(NamedNativeQueries.class)
public @interface NamedNativeQuery {
	/**
	 * The registration name.
	 *
	 * @see org.hibernate.SessionFactory#addNamedQuery
	 * @see org.hibernate.Session#createNamedQuery
	 */
	String name();

	/**
	 * The SQL query string.
	 */
	String query();

	/**
	 * The result Class.  Should not be used in conjunction with {@link #resultSetMapping()}
	 */
	Class<?> resultClass() default void.class;

	/**
	 * The name of a SQLResultSetMapping to use.  Should not be used in conjunction with {@link #resultClass()}.
	 */
	String resultSetMapping() default "";

	/**
	 * The flush mode for the query.
	 */
	FlushModeType flushMode() default FlushModeType.PERSISTENCE_CONTEXT;

	/**
	 * Whether the query (results) is cacheable or not.  Default is {@code false}, that is not cacheable.
	 */
	boolean cacheable() default false;

	/**
	 * If the query results are cacheable, name the query cache region to use.
	 */
	String cacheRegion() default "";

	/**
	 * The number of rows fetched by the JDBC Driver per trip.
	 */
	int fetchSize() default -1;

	/**
	 * The query timeout (in seconds).  Default is no timeout.
	 */
	int timeout() default -1;

	/**
	 * A comment added to the SQL query.  Useful when engaging with DBA.
	 */
	String comment() default "";

	/**
	 * The cache mode used for this query.  This refers to entities/collections returned from the query.
	 */
	CacheModeType cacheMode() default CacheModeType.NORMAL;

	/**
	 * Whether the results should be read-only.  Default is {@code false}.
	 */
	boolean readOnly() default false;

	/**
	 * The query spaces to apply for the query.
	 *
	 * @see org.hibernate.query.SynchronizeableQuery
	 */
	String[] querySpaces() default {};

	/**
	 * Does the SQL ({@link #query()}) represent a call to a procedure/function?
	 *
	 * @deprecated Calling database procedures and functions through {@link NativeQuery} is
	 * no longer supported; use {@link jakarta.persistence.NamedStoredProcedureQuery} instead
	 */
	@Deprecated( since = "6.0" )
	boolean callable() default false;
}
