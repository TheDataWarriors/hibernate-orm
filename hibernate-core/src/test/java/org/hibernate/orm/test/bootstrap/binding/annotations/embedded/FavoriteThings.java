/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.bootstrap.binding.annotations.embedded;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Has collection of embeddable objects inside embeddable objects for testing HHH-4598
 */

@Entity
public class FavoriteThings {
	@Id
	int id;

	@Embedded
	InternetFavorites web;

	public InternetFavorites getWeb() {
		return web;
	}

	public void setWeb(InternetFavorites web) {
		this.web = web;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}
}
