/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.userguide.mapping.basic;

import java.sql.Types;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.JdbcTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.VarbinaryTypeDescriptor;

/**
 * JdbcTypeDescriptor for documentation
 *
 * @author Steve Ebersole
 */
public class CustomBinaryJdbcType implements JdbcTypeDescriptor {
	@Override
	public int getJdbcType() {
		return Types.VARBINARY;
	}

	@Override
	public boolean canBeRemapped() {
		return false;
	}

	@Override
	public <X> ValueBinder<X> getBinder(JavaTypeDescriptor<X> javaTypeDescriptor) {
		return VarbinaryTypeDescriptor.INSTANCE.getBinder( javaTypeDescriptor );
	}

	@Override
	public <X> ValueExtractor<X> getExtractor(JavaTypeDescriptor<X> javaTypeDescriptor) {
		return VarbinaryTypeDescriptor.INSTANCE.getExtractor( javaTypeDescriptor );
	}
}
