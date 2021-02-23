/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.type.descriptor.java.spi;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptorIndicators;

/**
 * AbstractBasicTypeDescriptor adapter for cases where we do not know a proper JavaTypeDescriptor
 * for a given Java type.
 *
 * @author Steve Ebersole
 */
public class JavaTypeDescriptorBasicAdaptor<T> extends AbstractClassTypeDescriptor<T> {
	public JavaTypeDescriptorBasicAdaptor(Class<T> type) {
		super( type );
	}

	@Override
	public SqlTypeDescriptor getJdbcRecommendedSqlType(SqlTypeDescriptorIndicators context) {
		throw new UnsupportedOperationException(
				"Recommended SqlTypeDescriptor not known for this Java type : " + getJavaType().getTypeName()
		);
	}

	@Override
	public String toString(T value) {
		return value.toString();
	}

	@Override
	public T fromString(String string) {
		throw new UnsupportedOperationException(
				"Conversion from String strategy not known for this Java type : " + getJavaType().getTypeName()
		);
	}

	@Override
	public <X> X unwrap(T value, Class<X> type, WrapperOptions options) {
		throw new UnsupportedOperationException(
				"Unwrap strategy not known for this Java type : " + getJavaType().getTypeName()
		);
	}

	@Override
	public <X> T wrap(X value, WrapperOptions options) {
		throw new UnsupportedOperationException(
				"Wrap strategy not known for this Java type : " + getJavaType().getTypeName()
		);
	}

	@Override
	public String toString() {
		return "JavaTypeDescriptorBasicAdaptor(" + getJavaType().getTypeName() + ")";
	}
}
