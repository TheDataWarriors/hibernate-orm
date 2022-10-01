/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.jdbc;

import java.sql.Types;

/**
 * Descriptor for {@link Types#LONGVARBINARY LONGVARBINARY} handling.
 *
 * @author Steve Ebersole
 */
public class LongVarbinaryJdbcType extends VarbinaryJdbcType {
	public static final LongVarbinaryJdbcType INSTANCE = new LongVarbinaryJdbcType();

	private final int jdbcTypeCode;

	public LongVarbinaryJdbcType() {
		this(Types.LONGVARBINARY);
	}

	public LongVarbinaryJdbcType(int jdbcTypeCode) {
		this.jdbcTypeCode = jdbcTypeCode;
	}

	@Override
	public String toString() {
		return "LongVarbinaryTypeDescriptor";
	}

	@Override
	public int getJdbcTypeCode() {
		return jdbcTypeCode;
	}
}
