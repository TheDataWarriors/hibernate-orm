/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

//$Id$
package org.hibernate.orm.test.bootstrap.binding.annotations.embedded;
import java.io.Serializable;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Embeddable;

import javax.persistence.Access;

/**
 * Non realistic embedded dependent object
 *
 * @author Emmanuel Bernard
 */
@Embeddable
@Access(AccessType.PROPERTY)
public class Country implements Serializable {
	private String iso2;
	private String name;

	public String getIso2() {
		return iso2;
	}

	public void setIso2(String iso2) {
		this.iso2 = iso2;
	}

	@Column(name = "countryName")
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
