/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.envers.entities.customtype;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Properties;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.descriptor.java.StringJavaType;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;
import org.hibernate.usertype.ParameterizedType;
import org.hibernate.usertype.UserType;

/**
 * @author Adam Warski (adam at warski dot org)
 */
public class ParametrizedTestUserType implements UserType<String>, ParameterizedType {

	private String param1;
	private String param2;

	public void setParameterValues(Properties parameters) {
		param1 = parameters.getProperty( "param1" );
		param2 = parameters.getProperty( "param2" );
	}

	public Class<String> returnedClass() {
		return String.class;
	}

	@Override
	public String nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
		final String string = rs.getString( position );
		return rs.wasNull() ? null : string;
	}

	public void nullSafeSet(PreparedStatement st, String value, int index, SharedSessionContractImplementor session)
			throws HibernateException, SQLException {
		if ( value != null ) {
			if ( !value.startsWith( param1 ) ) {
				value = param1 + value;
			}
			if ( !value.endsWith( param2 ) ) {
				value = value + param2;
			}
		}
		VarcharJdbcType.INSTANCE.getBinder( StringJavaType.INSTANCE )
				.bind( st, value, index, session );
	}

	@Override
	public int getSqlType() {
		return Types.VARCHAR;
	}

	public String assemble(Object cached, Object owner) throws HibernateException {
		return (String) cached;
	}

	public String deepCopy(String value) throws HibernateException {
		return value;
	}

	public Object disassemble(String value) throws HibernateException {
		return value;
	}

	public boolean equals(String x, String y) throws HibernateException {
		//noinspection ObjectEquality
		if ( x == y ) {
			return true;
		}

		if ( x == null || y == null ) {
			return false;
		}

		return x.equals( y );
	}

	public int hashCode(String x) throws HibernateException {
		return x.hashCode();
	}

	public boolean isMutable() {
		return false;
	}

	public String replace(String original, String target, Object owner) throws HibernateException {
		return original;
	}
}
