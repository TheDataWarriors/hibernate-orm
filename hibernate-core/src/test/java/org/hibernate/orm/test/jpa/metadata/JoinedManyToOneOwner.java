/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.metadata;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;

/**
 * An entity that defines a @ManyToOne @JoinTable
 * <p>
 * See HHH-4720 for details
 *
 * @author Steve Ebersole
 */
@Entity
public class JoinedManyToOneOwner {
	private Long id;
	private House house;

	@Id
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	@ManyToOne @JoinTable( name = "SOME_OTHER_TABLE" )
	public House getHouse() {
		return house;
	}

	public void setHouse(House house) {
		this.house = house;
	}
}
