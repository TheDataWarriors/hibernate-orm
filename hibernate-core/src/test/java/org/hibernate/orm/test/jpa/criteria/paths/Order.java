/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.criteria.paths;

import java.util.Collection;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ORDER_TABLE")
public class Order {

	private String id;
	private double totalPrice;
	private LineItem sampleLineItem;
	private Collection<LineItem> lineItems = new java.util.ArrayList<LineItem>();

	public Order() {
	}

	public Order(String id, double totalPrice) {
		this.id = id;
		this.totalPrice = totalPrice;
	}

	public Order(String id) {
		this.id = id;
	}

	// ====================================================================
	// getters and setters for State fields

	@Id
	@Column(name = "ID")
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Column(name = "TOTALPRICE")
	public double getTotalPrice() {
		return totalPrice;
	}

	public void setTotalPrice(double price) {
		this.totalPrice = price;
	}

	// ====================================================================
	// getters and setters for Association fields

	// 1x1

	@OneToOne(cascade = CascadeType.REMOVE)
	@JoinColumn(name = "FK0_FOR_LINEITEM_TABLE")
	public LineItem getSampleLineItem() {
		return sampleLineItem;
	}

	public void setSampleLineItem(LineItem l) {
		this.sampleLineItem = l;
	}

	// 1xMANY

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "order")
	public Collection<LineItem> getLineItems() {
		return lineItems;
	}

	public void setLineItems(Collection<LineItem> c) {
		this.lineItems = c;
	}
}
