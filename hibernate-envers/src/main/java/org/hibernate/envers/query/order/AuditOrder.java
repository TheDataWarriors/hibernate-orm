/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.envers.query.order;

import org.hibernate.envers.configuration.Configuration;

/**
 * @author Adam Warski (adam at warski dot org)
 * @author Chris Cranford
 */
public interface AuditOrder {

	/**
	 * Specifies the null order precedence for the order-by column specification.
	 *
	 * @param nullPrecedence the null precedence, may be {@code null}.
	 * @return this {@link AuditOrder} for chaining purposes
	 */
	AuditOrder nulls(NullPrecedence nullPrecedence);

	/**
	 * @param configuration the configuration
	 * @return the order data.
	 */
	OrderData getData(Configuration configuration);

	class OrderData {

		private final String alias;
		private final String propertyName;
		private final boolean ascending;
		private final NullPrecedence nullPrecedence;

		public OrderData(String alias, String propertyName, boolean ascending, NullPrecedence nullPrecedence) {
			this.alias = alias;
			this.propertyName = propertyName;
			this.ascending = ascending;
			this.nullPrecedence = nullPrecedence;
		}

		public String getAlias(String baseAlias) {
			return alias == null ? baseAlias : alias;
		}

		public String getPropertyName() {
			return propertyName;
		}

		public boolean isAscending() {
			return ascending;
		}

		public NullPrecedence getNullPrecedence() {
			return nullPrecedence;
		}
	}

}
