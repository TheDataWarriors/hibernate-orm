/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.cascade.multilevel;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "BOTTOM")
public class Bottom {
	@Id
	@GeneratedValue
	private Long id;
	@OneToOne(mappedBy = "bottom")
	private Middle middle;

	Long getId() {
		return id;
	}

	void setId(Long id) {
		this.id = id;
	}

	Middle getMiddle() {
		return middle;
	}

	void setMiddle(Middle middle) {
		this.middle = middle;
	}
}
