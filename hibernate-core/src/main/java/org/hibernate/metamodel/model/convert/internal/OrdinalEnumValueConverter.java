/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.model.convert.internal;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import org.hibernate.dialect.Dialect;
import org.hibernate.metamodel.model.convert.spi.EnumValueConverter;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.java.EnumJavaType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;

/**
 * BasicValueConverter handling the conversion of an enum based on
 * JPA {@link jakarta.persistence.EnumType#ORDINAL} strategy (storing the ordinal)
 *
 * @author Steve Ebersole
 */
public class OrdinalEnumValueConverter<E extends Enum<E>, N extends Number> implements EnumValueConverter<E, N>, Serializable {

	private final EnumJavaType<E> enumJavaType;
	private final JdbcType jdbcType;
	private final JavaType<N> relationalJavaType;

	public OrdinalEnumValueConverter(
			EnumJavaType<E> enumJavaType,
			JdbcType jdbcType,
			JavaType<N> relationalJavaType) {
		this.enumJavaType = enumJavaType;
		this.jdbcType = jdbcType;
		this.relationalJavaType = relationalJavaType;
	}

	@Override
	public E toDomainValue(Number relationalForm) {
		return enumJavaType.fromOrdinal( relationalForm == null ? null : relationalForm.intValue() );
	}

	@Override @SuppressWarnings("unchecked")
	public N toRelationalValue(E domainForm) {
		return (N) enumJavaType.toOrdinal( domainForm );
	}

	@Override
	public int getJdbcTypeCode() {
		return jdbcType.getDefaultSqlTypeCode();
	}

	@Override
	public EnumJavaType<E> getDomainJavaType() {
		return enumJavaType;
	}

	@Override
	public JavaType<N> getRelationalJavaType() {
		return relationalJavaType;
	}

	@Override
	public String toSqlLiteral(Object value) {
		//noinspection rawtypes
		return Integer.toString( ( (Enum) value ).ordinal() );
	}

	@Override
	public String getCheckCondition(String columnName, JdbcType jdbcType, Dialect dialect) {
		int max = getDomainJavaType().getJavaTypeClass().getEnumConstants().length - 1;
		return dialect.getCheckCondition( columnName, 0, max );
	}
}
