/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.tool.schema.extract.spi.ColumnTypeInformation;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.BasicPluralJavaType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.JdbcLiteralFormatter;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.ObjectJdbcType;
import org.hibernate.type.spi.TypeConfiguration;

import org.jboss.logging.Logger;

/**
 * Descriptor for {@link Types#ARRAY ARRAY} handling.
 *
 * @author Christian Beikov
 * @author Jordan Gigov
 */
public class OracleArrayJdbcType extends ArrayJdbcType {

	public static final OracleArrayJdbcType INSTANCE = new OracleArrayJdbcType( null, ObjectJdbcType.INSTANCE );
	private static final CoreMessageLogger LOG = Logger.getMessageLogger( CoreMessageLogger.class, OracleArrayJdbcType.class.getName() );
	private static final ClassValue<Method> NAME_BINDER = new ClassValue<Method>() {
		@Override
		protected Method computeValue(Class<?> type) {
			try {
				return type.getMethod( "setArray", String.class, java.sql.Array.class );
			}
			catch ( Exception ex ) {
				// add logging? Did we get NoSuchMethodException or SecurityException?
				// Doesn't matter which. We can't use it.
			}
			return null;
		}
	};
	private static final Class<?> ORACLE_CONNECTION_CLASS;
	private static final Method CREATE_ARRAY_METHOD;

	static {
		Class<?> oracleConnectionClass = null;
		Method createArrayMethod = null;
		try {
			oracleConnectionClass = Class.forName( "oracle.jdbc.OracleConnection" );
			createArrayMethod = oracleConnectionClass.getMethod( "createOracleArray", String.class, Object.class );
		}
		catch (Exception e) {
			// Ignore since #resolveType should be called anyway and the OracleArrayJdbcType shouldn't be used
			// if driver classes are unavailable
			LOG.warn( "Oracle JDBC driver classes are inaccessible and thus, certain DDL types like ARRAY can not be used!", e );
		}
		ORACLE_CONNECTION_CLASS = oracleConnectionClass;
		CREATE_ARRAY_METHOD = createArrayMethod;
	}

	private final String typeName;

	public OracleArrayJdbcType(String typeName, JdbcType elementJdbcType) {
		super( elementJdbcType );
		this.typeName = typeName;
	}

	@Override
	public <T> JdbcLiteralFormatter<T> getJdbcLiteralFormatter(JavaType<T> javaTypeDescriptor) {
		// No array literal support
		return null;
	}

	@Override
	public JdbcType resolveType(
			TypeConfiguration typeConfiguration,
			Dialect dialect,
			JdbcType elementType,
			ColumnTypeInformation columnTypeInformation) {
		String typeName = columnTypeInformation.getTypeName();
		if ( typeName == null || typeName.isBlank() ) {
			typeName = dialect.getArrayTypeName(
					typeConfiguration.getDdlTypeRegistry().getTypeName(
							elementType.getDefaultSqlTypeCode(),
							dialect
					)
			);
		}
		if ( typeName == null || CREATE_ARRAY_METHOD == null ) {
			// Fallback to XML type for the representation of arrays as the native JSON type was only introduced in 21
			// Also, use the XML type if the Oracle JDBC driver classes are not visible
			return typeConfiguration.getJdbcTypeRegistry().getDescriptor( SqlTypes.SQLXML );
		}
		return new OracleArrayJdbcType( typeName, elementType );
	}

	@Override
	public <X> ValueBinder<X> getBinder(final JavaType<X> javaTypeDescriptor) {
		if ( CREATE_ARRAY_METHOD == null ) {
			throw new RuntimeException( "OracleArrayJdbcType shouldn't be used since JDBC driver classes are not visible." );
		}
		//noinspection unchecked
		final BasicPluralJavaType<X> containerJavaType = (BasicPluralJavaType<X>) javaTypeDescriptor;
		return new BasicBinder<X>( javaTypeDescriptor, this ) {
			@Override
			protected void doBindNull(PreparedStatement st, int index, WrapperOptions options) throws SQLException {
				st.setNull( index, Types.ARRAY, typeName );
			}

			@Override
			protected void doBindNull(CallableStatement st, String name, WrapperOptions options) throws SQLException {
				st.setNull( name, Types.ARRAY, typeName );
			}

			@Override
			protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
				final java.sql.Array arr = getArray( value, containerJavaType, options );
				st.setArray( index, arr );
			}

			@Override
			protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
					throws SQLException {
				final java.sql.Array arr = getArray( value, containerJavaType, options );
				final Method nameBinder = NAME_BINDER.get( st.getClass() );
				if ( nameBinder == null ) {
					try {
						st.setObject( name, arr, Types.ARRAY );
						return;
					}
					catch (SQLException ex) {
						throw new HibernateException( "JDBC driver does not support named parameters for setArray. Use positional.", ex );
					}
				}
				// Not that it's supposed to have setArray(String,Array) by standard.
				// There are numerous missing methods that only have versions for positional parameter,
				// but not named ones.

				try {
					nameBinder.invoke( st, name, arr );
				}
				catch ( Throwable t ) {
					throw new HibernateException( t );
				}
			}

			private java.sql.Array getArray(
					X value,
					BasicPluralJavaType<X> containerJavaType,
					WrapperOptions options) throws SQLException {
				//noinspection unchecked
				final Class<Object[]> arrayClass = (Class<Object[]>) Array.newInstance(
						getElementJdbcType().getPreferredJavaTypeClass( options ),
						0
				).getClass();
				final Object[] objects = javaTypeDescriptor.unwrap( value, arrayClass, options );

				final SharedSessionContractImplementor session = options.getSession();
				final Object oracleConnection = session.getJdbcCoordinator().getLogicalConnection().getPhysicalConnection()
						.unwrap( ORACLE_CONNECTION_CLASS );
				try {
					return (java.sql.Array) CREATE_ARRAY_METHOD.invoke( oracleConnection, typeName, objects );
				}
				catch (Exception e) {
					throw new HibernateException( "Couldn't create a java.sql.Array", e );
				}
			}
		};
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}

		OracleArrayJdbcType that = (OracleArrayJdbcType) o;

		return typeName.equals( that.typeName );
	}

	@Override
	public int hashCode() {
		return typeName.hashCode();
	}
}
