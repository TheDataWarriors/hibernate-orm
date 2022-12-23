/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.jdbc;

import java.sql.Types;

import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Descriptor for {@link Types#LONGNVARCHAR LONGNVARCHAR} handling.
 *
 * @author Steve Ebersole
 */
public class LongNVarcharJdbcType extends NVarcharJdbcType {
	public static final LongNVarcharJdbcType INSTANCE = new LongNVarcharJdbcType();

	public LongNVarcharJdbcType() {
	}

	@Override
	public String toString() {
		return "LongNVarcharTypeDescriptor";
	}

	@Override
	public int getJdbcTypeCode() {
		return Types.LONGNVARCHAR;
	}

	@Override
	public JdbcType resolveIndicatedType(
			JdbcTypeIndicators indicators,
			JavaType<?> domainJtd) {
		assert domainJtd != null;

		final TypeConfiguration typeConfiguration = indicators.getTypeConfiguration();
		final JdbcTypeRegistry jdbcTypeRegistry = typeConfiguration.getJdbcTypeRegistry();

		final int jdbcTypeCode;
		if ( indicators.isLob() ) {
			jdbcTypeCode = indicators.isNationalized() ? Types.NCLOB : Types.CLOB;
		}
		else if ( shouldUseMaterializedLob( indicators ) ) {
			jdbcTypeCode = indicators.isNationalized() ? SqlTypes.MATERIALIZED_NCLOB : SqlTypes.MATERIALIZED_CLOB;
		}
		else {
			jdbcTypeCode = indicators.isNationalized() ? Types.LONGNVARCHAR : Types.LONGVARCHAR;
		}

		return jdbcTypeRegistry.getDescriptor( indicators.resolveJdbcTypeCode( jdbcTypeCode ) );
	}
}
