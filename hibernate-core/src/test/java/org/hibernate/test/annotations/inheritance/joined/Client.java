/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.test.annotations.inheritance.joined;
import java.io.Serializable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "CLIENT")
public class Client extends Person implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private String street;
	
	private String code;
	
	private String city;
	
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinTable(name = "CLIENT_ACCOUNT", 
			joinColumns = {@JoinColumn(name = "FK_CLIENT", referencedColumnName = "ID")},
			inverseJoinColumns = {@JoinColumn(name = "FK_ACCOUNT", referencedColumnName = "ID")})
	private Account account;
		
	

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	public Client() {
		super();
	}

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}
	
}
