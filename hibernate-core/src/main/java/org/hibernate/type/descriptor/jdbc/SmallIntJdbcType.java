/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.jdbc;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.BasicJavaType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.internal.JdbcLiteralFormatterNumericData;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Descriptor for {@link Types#SMALLINT SMALLINT} handling.
 *
 * @author Steve Ebersole
 */
public class SmallIntJdbcType implements JdbcType {
	public static final SmallIntJdbcType INSTANCE = new SmallIntJdbcType();

	public SmallIntJdbcType() {
	}

	@Override
	public int getJdbcTypeCode() {
		return Types.SMALLINT;
	}

	@Override
	public String getFriendlyName() {
		return "SMALLINT";
	}

	@Override
	public String toString() {
		return "SmallIntTypeDescriptor";
	}

	@Override
	public <T> BasicJavaType<T> getJdbcRecommendedJavaTypeMapping(
			Integer length,
			Integer scale,
			TypeConfiguration typeConfiguration) {
		return (BasicJavaType<T>) typeConfiguration.getJavaTypeRegistry().getDescriptor( Short.class );
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> JdbcLiteralFormatter<T> getJdbcLiteralFormatter(JavaType<T> javaType) {
		return new JdbcLiteralFormatterNumericData( javaType, Short.class );
	}

	@Override
	public <X> ValueBinder<X> getBinder(final JavaType<X> javaType) {
		return new BasicBinder<X>( javaType, this ) {
			@Override
			protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
				st.setShort( index, javaType.unwrap( value, Short.class, options ) );
			}

			@Override
			protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
					throws SQLException {
				st.setShort( name, javaType.unwrap( value, Short.class, options ) );
			}
		};
	}

	@Override
	public <X> ValueExtractor<X> getExtractor(final JavaType<X> javaType) {
		return new BasicExtractor<X>( javaType, this ) {
			@Override
			protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
				return javaType.wrap( rs.getShort( paramIndex ), options );
			}

			@Override
			protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
				return javaType.wrap( statement.getShort( index ), options );
			}

			@Override
			protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
				return javaType.wrap( statement.getShort( name ), options );
			}
		};
	}
}
