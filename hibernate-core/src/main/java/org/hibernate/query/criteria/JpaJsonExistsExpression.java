/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.query.criteria;

import org.hibernate.Incubating;

import jakarta.persistence.criteria.Expression;

/**
 * A special expression for the {@code json_exists} function.
 * @since 7.0
 */
@Incubating
public interface JpaJsonExistsExpression extends JpaExpression<Boolean> {
	/**
	 * Get the {@link ErrorBehavior} of this json value expression.
	 *
	 * @return the error behavior
	 */
	ErrorBehavior getErrorBehavior();

	/**
	 * Sets the {@link ErrorBehavior#UNSPECIFIED} for this json exists expression.
	 *
	 * @return {@code this} for method chaining
	 */
	JpaJsonExistsExpression unspecifiedOnError();
	/**
	 * Sets the {@link ErrorBehavior#ERROR} for this json exists expression.
	 *
	 * @return {@code this} for method chaining
	 */
	JpaJsonExistsExpression errorOnError();
	/**
	 * Sets the {@link ErrorBehavior#TRUE} for this json exists expression.
	 *
	 * @return {@code this} for method chaining
	 */
	JpaJsonExistsExpression trueOnError();
	/**
	 * Sets the {@link ErrorBehavior#FALSE} for this json exists expression.
	 *
	 * @return {@code this} for method chaining
	 */
	JpaJsonExistsExpression falseOnError();

	/**
	 * Passes the given {@link Expression} as value for the parameter with the given name in the JSON path.
	 *
	 * @return {@code this} for method chaining
	 */
	JpaJsonExistsExpression passing(String parameterName, Expression<?> expression);

	/**
	 * The behavior of the json value expression when a JSON processing error occurs.
	 */
	enum ErrorBehavior {
		/**
		 * SQL/JDBC error should be raised.
		 */
		ERROR,
		/**
		 * {@code true} should be returned on error.
		 */
		TRUE,
		/**
		 * {@code false} should be returned on error.
		 */
		FALSE,
		/**
		 * Unspecified behavior i.e. the default database behavior.
		 */
		UNSPECIFIED
	}
}
