package org.hibernate.orm.test.annotations.collectionelement;

import java.util.HashSet;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;

import org.hibernate.query.spi.QueryImplementor;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

/**
 * @author Andrea Boriero
 */
@DomainModel(annotatedClasses = {
		EmbeddableElementCollectionMemberOfTest.Person.class,
		EmbeddableElementCollectionMemberOfTest.City.class
})
@SessionFactory
public class EmbeddableElementCollectionMemberOfTest {

	@Test
	public void testMemberOfQuery(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					Address a = new Address();
					a.setStreet( "Lollard Street" );
					QueryImplementor query = session.createQuery( "from Person p where :address member of p.addresses" );
					query.setParameter( "address", a );
					query.list();
				}
		);
	}

	@Entity(name = "Person")
	public static class Person {
		private Long id;
		private String name;
		private Set<Address> addresses = new HashSet<>();
		private Address address;

		@Id
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

		@ElementCollection
		@JoinTable(
				name = "addresses",
				joinColumns = @JoinColumn(name = "PERSON_ID"))
		public Set<Address> getAddresses() {
			return addresses;
		}

		public void setAddresses(Set<Address> addresses) {
			this.addresses = addresses;
		}

		public Address getAddress() {
			return address;
		}

		public void setAddress(Address address) {
			this.address = address;
		}
	}

	@Embeddable
	public static class Address {
		public String street;
		public int number;

		public City city;

		public String getStreet() {
			return street;
		}

		public void setStreet(String street) {
			this.street = street;
		}

		@Column(name = "house_number")
		public int getNumber() {
			return number;
		}

		public void setNumber(int number) {
			this.number = number;
		}

		@ManyToOne
		public City getCity() {
			return city;
		}

		public void setCity(City city) {
			this.city = city;
		}
	}

	@Entity(name = "City")
	public static class City {

		private Long id;

		private String name;

		@Id
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
	}
}
