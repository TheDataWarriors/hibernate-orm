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

import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.BasicJavaType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.internal.JdbcLiteralFormatterNumericData;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Descriptor for {@link Types#FLOAT FLOAT} handling.
 *
 * @author Steve Ebersole
 */
public class FloatJdbcType implements JdbcType {
	public static final FloatJdbcType INSTANCE = new FloatJdbcType();

	public FloatJdbcType() {
	}

	@Override
	public int getJdbcTypeCode() {
		return Types.FLOAT;
	}

	@Override
	public String getFriendlyName() {
		return "FLOAT";
	}

	@Override
	public String toString() {
		return "FloatTypeDescriptor";
	}

	@Override
	public <T> BasicJavaType<T> getJdbcRecommendedJavaTypeMapping(
			Integer length,
			Integer scale,
			TypeConfiguration typeConfiguration) {
		if ( length != null && length <= typeConfiguration.getServiceRegistry().getService( JdbcServices.class ).getDialect().getFloatPrecision() ) {
			return (BasicJavaType<T>) typeConfiguration.getJavaTypeRegistry().getDescriptor( Float.class );
		}
		return (BasicJavaType<T>) typeConfiguration.getJavaTypeRegistry().getDescriptor( Double.class );
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> JdbcLiteralFormatter<T> getJdbcLiteralFormatter(JavaType<T> javaType) {
		return new JdbcLiteralFormatterNumericData( javaType, Float.class );
	}

	@Override
	public <X> ValueBinder<X> getBinder(final JavaType<X> javaType) {
		return new BasicBinder<X>( javaType, this ) {
			@Override
			protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
				st.setFloat( index, javaType.unwrap( value, Float.class, options ) );
			}

			@Override
			protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
					throws SQLException {
				st.setFloat( name, javaType.unwrap( value, Float.class, options ) );
			}
		};
	}

	@Override
	public <X> ValueExtractor<X> getExtractor(final JavaType<X> javaType) {
		return new BasicExtractor<X>( javaType, this ) {
			@Override
			protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
				return javaType.wrap( rs.getFloat( paramIndex ), options );
			}

			@Override
			protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
				return javaType.wrap( statement.getFloat( index ), options );
			}

			@Override
			protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
				return javaType.wrap( statement.getFloat( name ), options );
			}
		};
	}
}
