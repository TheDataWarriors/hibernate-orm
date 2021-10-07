/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.bootstrap.binding.annotations.basics.sql;

import java.sql.Types;
import java.util.List;
import java.util.function.Consumer;

import org.hibernate.annotations.ListIndexJdbcType;
import org.hibernate.annotations.ListIndexJdbcTypeCode;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.type.descriptor.jdbc.JdbcTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.TinyIntTypeDescriptor;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.DomainModelScope;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.junit.jupiter.api.Test;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Steve Ebersole
 */
@DomainModel( annotatedClasses = ListIndexJdbcTypeTests.TheEntity.class )
@SessionFactory
public class ListIndexJdbcTypeTests {

	@Test
	public void verifyResolutions(DomainModelScope scope) {
		final PersistentClass entityBinding = scope.getDomainModel().getEntityBinding( TheEntity.class.getName() );

		verifyJdbcTypeCodes( entityBinding.getProperty( "listOfStrings" ), Types.TINYINT );

		verifyJdbcTypeCodes( entityBinding.getProperty( "anotherListOfStrings" ), Types.TINYINT );
	}
	private void verifyJdbcTypeCodes(Property property, int expectedCode) {
		verifyJdbcTypeResolution(
				property,
				(jdbcType) -> assertThat(
						"List index for `" + property.getName() + "`",
						jdbcType.getJdbcTypeCode(),
						equalTo( expectedCode )
				)
		);
	}
	private void verifyJdbcTypeResolution(
			Property property,
			Consumer<JdbcTypeDescriptor> typeVerifier) {
		assertThat( property.getValue(), instanceOf( org.hibernate.mapping.List.class ) );
		final org.hibernate.mapping.List listValue = (org.hibernate.mapping.List) property.getValue();

		assertThat( listValue.getIndex(), instanceOf( BasicValue.class ) );
		final BasicValue indexValue = (BasicValue) listValue.getIndex();
		final BasicValue.Resolution<?> indexResolution = indexValue.resolve();
		typeVerifier.accept( indexResolution.getJdbcTypeDescriptor() );
	}

	@Entity( name = "TheEntity" )
	@Table( name = "t_entity" )
	public static class TheEntity {
		@Id
		private Integer id;
		private String name;
		@ElementCollection
		@OrderColumn
		@ListIndexJdbcType( TinyIntTypeDescriptor.class )
		private List<String> listOfStrings;
		@ElementCollection
		@OrderColumn
		@ListIndexJdbcTypeCode( Types.TINYINT )
		private List<String> anotherListOfStrings;
	}
}
