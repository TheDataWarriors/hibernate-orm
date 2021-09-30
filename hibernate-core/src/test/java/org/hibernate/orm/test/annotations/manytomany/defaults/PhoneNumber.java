/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.annotations.manytomany.defaults;
import java.util.Collection;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;

@Entity
public class PhoneNumber {
	int phNumber;
	Collection<Employee> employees;

	@Id
	public int getPhNumber() {
		return phNumber;
	}

	public void setPhNumber(int phNumber) {
		this.phNumber = phNumber;
	}

	@ManyToMany(mappedBy="contactInfo.phoneNumbers", cascade= CascadeType.ALL)
	public Collection<Employee> getEmployees() {
		return employees;
	}

	public void setEmployees(Collection<Employee> employees) {
		this.employees = employees;
	}
}
