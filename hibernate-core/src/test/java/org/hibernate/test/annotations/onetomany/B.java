/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.annotations.onetomany;

import java.util.ArrayList;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Cascade;

/**
 * @author Peter Kotula
 */
@Entity
public class B {
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Id
	Long id;
	
	@NotNull
	String name;	
	
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @Cascade(org.hibernate.annotations.CascadeType.ALL)
	@OrderBy("name")
	java.util.List<C> cs = new ArrayList<C>();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	

	public java.util.List<C> getCs() {
		return cs;
	}

	@Override
	public String toString() {
		return "B [id=" + id + ", name=" + name + ", cs="+cs+"]";
	}

}
