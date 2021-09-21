/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

//$Id: AccessTest.java 15025 2008-08-11 09:14:39Z hardy.ferentschik $

package org.hibernate.orm.test.bootstrap.binding.annotations.access.jpa;
import java.util.List;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;


/**
 * @author Hardy Ferentschik
 */
@Entity
@Access(AccessType.PROPERTY)
public class Course4 {
	@Id
	@GeneratedValue
	private long id;

	private String title;

	@OneToMany(cascade = CascadeType.ALL)
	private List<Student> students;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public List<Student> getStudents() {
		return students;
	}

	public void setStudents(List<Student> students) {
		this.students = students;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}
}
