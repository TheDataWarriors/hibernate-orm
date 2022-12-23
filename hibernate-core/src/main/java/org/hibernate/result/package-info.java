/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

/**
 * Defines support for dealing with database results, accounting for mixed
 * result sets and update counts hiding the complexity of how this is exposed
 * via the JDBC API.
 * <ul>
 * <li>{@link org.hibernate.result.Outputs} represents the overall group of results.
 * <li>{@link org.hibernate.result.Output} represents the mixed individual outcomes,
 *     which might be either a {@link org.hibernate.result.ResultSetOutput} or
 *     a {@link org.hibernate.result.UpdateCountOutput}.
 * </ul>
 *
 * <pre>{@code
 *     Outputs outputs = ...;
 *     while ( outputs.goToNext() ) {
 *         final Output output = outputs.getCurrent();
 *         if ( rtn.isResultSet() ) {
 *             handleResultSetOutput( (ResultSetOutput) output );
 *         }
 *         else {
 *             handleUpdateCountOutput( (UpdateCountOutput) output );
 *         }
 *     }
 * }</pre>
 */
package org.hibernate.result;
