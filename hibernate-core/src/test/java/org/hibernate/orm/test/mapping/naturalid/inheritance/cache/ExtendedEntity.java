/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.mapping.naturalid.inheritance.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class ExtendedEntity extends MyEntity {
	public ExtendedEntity() {
	}

	public ExtendedEntity(final String uid, final String extendedValue) {
		super( uid );
		this.extendedValue = extendedValue;
	}

	private String extendedValue;

	@Column
	public String getExtendedValue() {
		return extendedValue;
	}

	public void setExtendedValue(final String extendedValue) {
		this.extendedValue = extendedValue;
	}
}
