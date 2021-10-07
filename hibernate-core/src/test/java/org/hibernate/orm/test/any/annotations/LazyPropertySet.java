/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.any.annotations;

import org.hibernate.annotations.Any;
import org.hibernate.annotations.AnyDiscriminator;
import org.hibernate.annotations.AnyDiscriminatorValue;
import org.hibernate.annotations.AnyKeyJavaClass;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;

import from.In;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table( name = "lazy_property_set" )
public class LazyPropertySet {
	private Integer id;
	private String name;
	private Property someProperty;

	public LazyPropertySet() {
		super();
	}

	public LazyPropertySet(String name) {
		this.name = name;
	}

	@Id
	@GeneratedValue
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Any( fetch = FetchType.LAZY )
	@Column( name = "property_type" )
	@AnyDiscriminator( DiscriminatorType.STRING )
	@AnyKeyJavaClass( Integer.class )
	@AnyDiscriminatorValue( discriminator = "S", entity = StringProperty.class)
	@AnyDiscriminatorValue( discriminator = "I", entity = IntegerProperty.class)
	@Cascade( value = { CascadeType.ALL } )
	@JoinColumn( name = "property_id" )
	public Property getSomeProperty() {
		return someProperty;
	}

	public void setSomeProperty(Property someProperty) {
		this.someProperty = someProperty;
	}
}
