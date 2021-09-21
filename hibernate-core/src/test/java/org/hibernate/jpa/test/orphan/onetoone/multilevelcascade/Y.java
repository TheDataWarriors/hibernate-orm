/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpa.test.orphan.onetoone.multilevelcascade;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;

/**
 * @author Gail Badner
 */
@Entity
public class Y {

	@Id
	@GeneratedValue
	private Long id;

	@OneToOne(optional = true, fetch = FetchType.LAZY)
	private Tranche tranche;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Tranche getTranche() {
		return tranche;
	}

	public void setTranche(Tranche tranche) {
		this.tranche = tranche;
	}
}
