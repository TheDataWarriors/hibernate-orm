/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.mapping.cid.idclass;

/**
 * @author Steve Ebersole
 */
public class PaymentId {
	private OrderId order;
	private String accountNumber;

	public PaymentId() {
	}

	public PaymentId(OrderId order, String accountNumber) {
		this.order = order;
		this.accountNumber = accountNumber;
	}
}
