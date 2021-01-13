/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.type.internal;

import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.type.BasicType;
import org.hibernate.type.SqlTypeDescriptorIndicatorCapable;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptorIndicators;

/**
 * @author Steve Ebersole
 */
@SuppressWarnings("rawtypes")
public class StandardBasicTypeImpl<J>
		extends AbstractSingleColumnStandardBasicType
		implements SqlTypeDescriptorIndicatorCapable {
	public static final String[] NO_REG_KEYS = ArrayHelper.EMPTY_STRING_ARRAY;

	public StandardBasicTypeImpl(JavaTypeDescriptor<J> jtd, SqlTypeDescriptor std) {
		//noinspection unchecked
		super( std, jtd );
	}

	@Override
	public String[] getRegistrationKeys() {
		// irrelevant - these are created on-the-fly
		return NO_REG_KEYS;
	}

	@Override
	public String getName() {
		// again, irrelevant
		return null;
	}

	@Override
	public BasicType resolveIndicatedType(SqlTypeDescriptorIndicators indicators) {
		final SqlTypeDescriptor recommendedSqlType = getJavaTypeDescriptor().getJdbcRecommendedSqlType( indicators );
		if ( recommendedSqlType == getSqlTypeDescriptor() ) {
			return this;
		}

		return indicators.getTypeConfiguration()
				.getBasicTypeRegistry()
				.resolve( getJavaTypeDescriptor(), recommendedSqlType );
	}
}
