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
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.BasicJavaType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.internal.JdbcLiteralFormatterTemporal;
import org.hibernate.type.spi.TypeConfiguration;

import jakarta.persistence.TemporalType;

/**
 * Descriptor for {@link SqlTypes#TIMESTAMP_UTC TIMESTAMP_UTC} handling.
 *
 * @author Christian Beikov
 */
public class InstantJdbcType implements JdbcType {
	public static final InstantJdbcType INSTANCE = new InstantJdbcType();

	public InstantJdbcType() {
	}

	@Override
	public int getJdbcTypeCode() {
		return Types.TIMESTAMP_WITH_TIMEZONE;
	}

	@Override
	public int getDefaultSqlTypeCode() {
		return SqlTypes.TIMESTAMP_UTC;
	}

	@Override
	public String getFriendlyName() {
		return "TIMESTAMP_UTC";
	}

	@Override
	public String toString() {
		return "TimestampUtcDescriptor";
	}

	@Override
	public <T> JavaType<T> getJdbcRecommendedJavaTypeMapping(
			Integer length,
			Integer scale,
			TypeConfiguration typeConfiguration) {
		return typeConfiguration.getJavaTypeRegistry().getDescriptor( Instant.class );
	}

	@Override
	public Class<?> getPreferredJavaTypeClass(WrapperOptions options) {
		return Instant.class;
	}

	@Override
	public <T> JdbcLiteralFormatter<T> getJdbcLiteralFormatter(JavaType<T> javaType) {
		return new JdbcLiteralFormatterTemporal<>( javaType, TemporalType.TIMESTAMP );
	}

	@Override
	public <X> ValueBinder<X> getBinder(final JavaType<X> javaType) {
		return new BasicBinder<>( javaType, this ) {
			@Override
			protected void doBind(
					PreparedStatement st,
					X value,
					int index,
					WrapperOptions wrapperOptions) throws SQLException {
				try {
					final Instant dateTime = javaType.unwrap( value, Instant.class, wrapperOptions );
					// supposed to be supported in JDBC 4.2
					st.setObject( index, dateTime, Types.TIMESTAMP_WITH_TIMEZONE );
				}
				catch (SQLException|AbstractMethodError e) {
					// fall back to treating it as a JDBC Timestamp
					final Timestamp timestamp = javaType.unwrap( value, Timestamp.class, wrapperOptions );
					st.setTimestamp( index, timestamp );
				}
			}

			@Override
			protected void doBind(
					CallableStatement st,
					X value,
					String name,
					WrapperOptions wrapperOptions)
					throws SQLException {
				try {
					final Instant dateTime = javaType.unwrap( value, Instant.class, wrapperOptions );
					// supposed to be supported in JDBC 4.2
					st.setObject( name, dateTime, Types.TIMESTAMP_WITH_TIMEZONE );
				}
				catch (SQLException|AbstractMethodError e) {
					// fall back to treating it as a JDBC Timestamp
					final Timestamp timestamp = javaType.unwrap( value, Timestamp.class, wrapperOptions );
					st.setTimestamp( name, timestamp );
				}
			}
		};
	}

	@Override
	public <X> ValueExtractor<X> getExtractor(final JavaType<X> javaType) {
		return new BasicExtractor<>( javaType, this ) {
			@Override
			protected X doExtract(ResultSet rs, int position, WrapperOptions wrapperOptions) throws SQLException {
				try {
					// supposed to be supported in JDBC 4.2
					return javaType.wrap( rs.getObject( position, Instant.class ), wrapperOptions );
				}
				catch (SQLException|AbstractMethodError e) {
					// fall back to treating it as a JDBC Timestamp
					return javaType.wrap( rs.getTimestamp( position ), wrapperOptions );
				}
			}

			@Override
			protected X doExtract(CallableStatement statement, int position, WrapperOptions wrapperOptions) throws SQLException {
				try {
					// supposed to be supported in JDBC 4.2
					return javaType.wrap( statement.getObject( position, Instant.class ), wrapperOptions );
				}
				catch (SQLException|AbstractMethodError e) {
					// fall back to treating it as a JDBC Timestamp
					return javaType.wrap( statement.getTimestamp( position ), wrapperOptions );
				}
			}

			@Override
			protected X doExtract(CallableStatement statement, String name, WrapperOptions wrapperOptions) throws SQLException {
				try {
					// supposed to be supported in JDBC 4.2
					return javaType.wrap( statement.getObject( name, Instant.class ), wrapperOptions );
				}
				catch (SQLException|AbstractMethodError e) {
					// fall back to treating it as a JDBC Timestamp
					return javaType.wrap( statement.getTimestamp( name ), wrapperOptions );
				}
			}
		};
	}
}
