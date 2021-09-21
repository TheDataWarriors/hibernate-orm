/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.annotations.embeddables.nested;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * @author Thomas Vanstals
 * @author Steve Ebersole
 */
@Embeddable
public class MonetaryAmount {
	public static enum CurrencyCode {
		USD,
		EUR
	}

	private BigDecimal amount;
	private CurrencyCode currency;

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	@Column(length = 3)
	@Enumerated(EnumType.STRING)
	public CurrencyCode getCurrency() {
		return currency;
	}

	public void setCurrency(CurrencyCode currency) {
		this.currency = currency;
	}
}
