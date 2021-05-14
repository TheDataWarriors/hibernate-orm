/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.jdbc;

import java.io.Serializable;
import java.sql.Types;

import org.hibernate.query.CastType;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.BasicJavaDescriptor;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Descriptor for the <tt>SQL</tt>/<tt>JDBC</tt> side of a value mapping.
 * <p/>
 * NOTE : Implementations should be registered with the {@link JdbcTypeDescriptor}.  The built-in Hibernate
 * implementations register themselves on construction.
 *
 * @author Steve Ebersole
 */
public interface JdbcTypeDescriptor extends Serializable {
	/**
	 * A "friendly" name for use in logging
	 */
	default String getFriendlyName() {
		return Integer.toString( getJdbcType() );
	}

	/**
	 * Return the {@linkplain java.sql.Types JDBC type-code} for the column mapped by this type.
	 *
	 * @apiNote Prefer {@link #getJdbcTypeCode}
	 *
	 * @return typeCode The JDBC type-code
	 */
	int getJdbcType();

	/**
	 * Get the JDBC type code associated with this SQL type descriptor
	 *
	 * @see #getJdbcType
	 */
	default int getJdbcTypeCode() {
		return getJdbcType();
	}

	/**
	 * Is this descriptor available for remapping?
	 *
	 * @return {@code true} indicates this descriptor can be remapped; otherwise, {@code false}
	 *
	 * @see org.hibernate.type.descriptor.WrapperOptions#remapSqlTypeDescriptor
	 * @see org.hibernate.dialect.Dialect#remapSqlTypeDescriptor
	 */
	boolean canBeRemapped();

	@SuppressWarnings("unchecked")
	default <T> BasicJavaDescriptor<T> getJdbcRecommendedJavaTypeMapping(TypeConfiguration typeConfiguration) {
		// match legacy behavior
		return (BasicJavaDescriptor<T>) typeConfiguration.getJavaTypeDescriptorRegistry().getDescriptor(
				JdbcTypeJavaClassMappings.INSTANCE.determineJavaClassForJdbcTypeCode( getJdbcType() )
		);
	}

	/**
	 * todo (6.0) : move to {@link org.hibernate.metamodel.mapping.JdbcMapping}?
	 */
	default <T> JdbcLiteralFormatter<T> getJdbcLiteralFormatter(JavaTypeDescriptor<T> javaTypeDescriptor) {
		return (value, dialect, wrapperOptions) -> value.toString();
	}

	/**
	 * Get the binder (setting JDBC in-going parameter values) capable of handling values of the type described by the
	 * passed descriptor.
	 *
	 * @param javaTypeDescriptor The descriptor describing the types of Java values to be bound
	 *
	 * @return The appropriate binder.
	 */
	<X> ValueBinder<X> getBinder(JavaTypeDescriptor<X> javaTypeDescriptor);

	/**
	 * Get the extractor (pulling out-going values from JDBC objects) capable of handling values of the type described
	 * by the passed descriptor.
	 *
	 * @param javaTypeDescriptor The descriptor describing the types of Java values to be extracted
	 *
	 * @return The appropriate extractor
	 */
	<X> ValueExtractor<X> getExtractor(JavaTypeDescriptor<X> javaTypeDescriptor);

	default boolean isInteger() {
		switch ( getJdbcType() ) {
			case Types.BIT:
			case Types.TINYINT:
			case Types.SMALLINT:
			case Types.INTEGER:
			case Types.BIGINT:
				return true;
		}
		return false;
	}

	default boolean isNumber() {
		switch ( getJdbcType() ) {
			case Types.BIT:
			case Types.TINYINT:
			case Types.SMALLINT:
			case Types.INTEGER:
			case Types.BIGINT:
			case Types.FLOAT:
			case Types.REAL:
			case Types.DOUBLE:
			case Types.DECIMAL:
			case Types.NUMERIC:
				return true;
		}
		return false;
	}

	default boolean isBinary() {
		switch ( getJdbcType() ) {
			case Types.BINARY:
			case Types.VARBINARY:
			case Types.LONGVARBINARY:
				return true;
		}
		return false;
	}

	default boolean isString() {
		switch ( getJdbcType() ) {
			case Types.CHAR:
			case Types.NCHAR:
			case Types.VARCHAR:
			case Types.NVARCHAR:
			case Types.LONGVARCHAR:
			case Types.LONGNVARCHAR:
				return true;
		}
		return false;
	}

	default boolean isTemporal() {
		switch ( getJdbcType() ) {
			case Types.DATE:
			case Types.TIME:
			case Types.TIMESTAMP:
			case Types.TIMESTAMP_WITH_TIMEZONE:
				return true;
		}
		return false;
	}

	default CastType getCastType() {
		switch ( getJdbcType() ) {
			case Types.INTEGER:
			case Types.TINYINT:
			case Types.SMALLINT:
				return CastType.INTEGER;
			case Types.BIGINT:
				return CastType.LONG;
			case Types.FLOAT:
			case Types.REAL:
				return CastType.FLOAT;
			case Types.DOUBLE:
				return CastType.DOUBLE;
			case Types.CHAR:
			case Types.NCHAR:
			case Types.VARCHAR:
			case Types.NVARCHAR:
			case Types.LONGVARCHAR:
			case Types.LONGNVARCHAR:
				return CastType.STRING;
			case Types.BOOLEAN:
				return CastType.BOOLEAN;
			case Types.DECIMAL:
			case Types.NUMERIC:
				return CastType.FIXED;
			case Types.DATE:
				return CastType.DATE;
			case Types.TIME:
				return CastType.TIME;
			case Types.TIMESTAMP:
				return CastType.TIMESTAMP;
			case Types.TIMESTAMP_WITH_TIMEZONE:
				return CastType.OFFSET_TIMESTAMP;
			case Types.NULL:
				return CastType.NULL;
			default:
				return CastType.OTHER;
		}
	}
}
