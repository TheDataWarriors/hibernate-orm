/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.mapping.converted.converter.custom;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import org.hibernate.internal.util.StringHelper;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.JdbcTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.VarcharTypeDescriptor;

/**
 * A custom SqlTypeDescriptor.  For example, this might be used to provide support
 * for a "non-standard" SQL type or to provide some special handling of values (e.g.
 * Oracle's dodgy handling of `""` as `null` but only in certain uses).
 *
 * This descriptor shows an example of replacing how VARCHAR values are handled.
 *
 * @author Steve Ebersole
 */
public class MyCustomJdbcTypeDescriptor implements JdbcTypeDescriptor {
	/**
	 * Singleton access
	 */
	public static final MyCustomJdbcTypeDescriptor INSTANCE = new MyCustomJdbcTypeDescriptor();

	private MyCustomJdbcTypeDescriptor() {
	}

	@Override
	public int getJdbcTypeCode() {
		// given the Oracle example above we might want to replace the
		// handling of VARCHAR
		return Types.VARCHAR;
	}

	@Override
	public boolean canBeRemapped() {
		return false;
	}

	@Override
	public <X> ValueBinder<X> getBinder(JavaTypeDescriptor<X> javaTypeDescriptor) {
		return new BasicBinder<X>( javaTypeDescriptor, this ) {
			@Override
			protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
				final String valueStr = javaTypeDescriptor.unwrap( value, String.class, options );
				if ( StringHelper.isBlank( valueStr ) ) {
					st.setNull( index, getJdbcTypeCode() );
				}
				else {
					st.setString( index, valueStr );
				}
			}

			@Override
			protected void doBind(CallableStatement st, X value, String name, WrapperOptions options) throws SQLException {
				final String valueStr = javaTypeDescriptor.unwrap( value, String.class, options );
				if ( StringHelper.isBlank( valueStr ) ) {
					st.setNull( name, getJdbcTypeCode() );
				}
				else {
					st.setString( name, valueStr );
				}
			}
		};
	}

	@Override
	public <X> ValueExtractor<X> getExtractor(JavaTypeDescriptor<X> javaTypeDescriptor) {
		return VarcharTypeDescriptor.INSTANCE.getExtractor( javaTypeDescriptor );
	}
}
