/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.onetoone.jointable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * @author Christian Beikov
 */
@Entity
public class Message extends ABlockableEntity {
	@Column(name = "description")
	private String description;

	public Message() {
	}

	public Message(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
