/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.jdbc;

import java.sql.CallableStatement;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.hibernate.engine.jdbc.CharacterStream;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * Descriptor for {@link Types#NCLOB NCLOB} handling.
 *
 * @author Steve Ebersole
 * @author Gail Badner
 */
public abstract class NClobJdbcType implements JdbcType {
	@Override
	public int getJdbcTypeCode() {
		return Types.NCLOB;
	}

	@Override
	public String getFriendlyName() {
		return "NCLOB";
	}

	@Override
	public String toString() {
		return "NClobTypeDescriptor";
	}

	@Override
	public <X> ValueExtractor<X> getExtractor(final JavaType<X> javaType) {
		return new BasicExtractor<X>( javaType, this ) {
			@Override
			protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
				return javaType.wrap( rs.getNClob( paramIndex ), options );
			}

			@Override
			protected X doExtract(CallableStatement statement, int index, WrapperOptions options)
					throws SQLException {
				return javaType.wrap( statement.getNClob( index ), options );
			}

			@Override
			protected X doExtract(CallableStatement statement, String name, WrapperOptions options)
					throws SQLException {
				return javaType.wrap( statement.getNClob( name ), options );
			}
		};
	}

	protected abstract <X> BasicBinder<X> getNClobBinder(JavaType<X> javaType);

	@Override
	public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
		return getNClobBinder( javaType );
	}


	public static final NClobJdbcType DEFAULT = new NClobJdbcType() {
		@Override
		public String toString() {
			return "NClobTypeDescriptor(DEFAULT)";
		}

		@Override
		public <X> BasicBinder<X> getNClobBinder(final JavaType<X> javaType) {
			return new BasicBinder<X>( javaType, this ) {
				@Override
				protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options)
						throws SQLException {
					if ( options.useStreamForLobBinding() ) {
						STREAM_BINDING.getNClobBinder( javaType ).doBind( st, value, index, options );
					}
					else {
						NCLOB_BINDING.getNClobBinder( javaType ).doBind( st, value, index, options );
					}
				}

				@Override
				protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
						throws SQLException {
					if ( options.useStreamForLobBinding() ) {
						STREAM_BINDING.getNClobBinder( javaType ).doBind( st, value, name, options );
					}
					else {
						NCLOB_BINDING.getNClobBinder( javaType ).doBind( st, value, name, options );
					}
				}
			};
		}
	};

	public static final NClobJdbcType NCLOB_BINDING = new NClobJdbcType() {
		@Override
		public String toString() {
			return "NClobTypeDescriptor(NCLOB_BINDING)";
		}

		@Override
		public <X> BasicBinder<X> getNClobBinder(final JavaType<X> javaType) {
			return new BasicBinder<X>( javaType, this ) {
				@Override
				protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options)
						throws SQLException {
					st.setNClob( index, javaType.unwrap( value, NClob.class, options ) );
				}

				@Override
				protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
						throws SQLException {
					st.setNClob( name, javaType.unwrap( value, NClob.class, options ) );
				}
			};
		}
	};

	public static final NClobJdbcType STREAM_BINDING = new NClobJdbcType() {
		@Override
		public String toString() {
			return "NClobTypeDescriptor(STREAM_BINDING)";
		}

		@Override
		public <X> BasicBinder<X> getNClobBinder(final JavaType<X> javaType) {
			return new BasicBinder<X>( javaType, this ) {
				@Override
				protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options)
						throws SQLException {
					final CharacterStream characterStream = javaType.unwrap(
							value,
							CharacterStream.class,
							options
					);
					st.setNCharacterStream( index, characterStream.asReader(), characterStream.getLength() );
				}

				@Override
				protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
						throws SQLException {
					final CharacterStream characterStream = javaType.unwrap(
							value,
							CharacterStream.class,
							options
					);
					st.setNCharacterStream( name, characterStream.asReader(), characterStream.getLength() );
				}
			};
		}
	};
}
