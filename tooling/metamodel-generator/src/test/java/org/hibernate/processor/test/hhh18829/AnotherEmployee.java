/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.processor.test.hhh18829;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AnotherEmployee extends Address {
	String empName;

	Integer empId;

	@Id
	public String getEmpName() {
		return empName;
	}

	public void setEmpName(String empName) {
		this.empName = empName;
	}

	@Id
	public Integer getEmpId() {
		return empId;
	}

	public void setEmpId(Integer empId) {
		this.empId = empId;
	}
}
