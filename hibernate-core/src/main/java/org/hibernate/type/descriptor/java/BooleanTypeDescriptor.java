/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.java;

import java.sql.Types;

import org.hibernate.dialect.Dialect;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.spi.Primitive;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

/**
 * Descriptor for {@link Boolean} handling.
 *
 * @author Steve Ebersole
 */
public class BooleanTypeDescriptor extends AbstractClassTypeDescriptor<Boolean> implements Primitive<Boolean> {
	public static final BooleanTypeDescriptor INSTANCE = new BooleanTypeDescriptor();

	private final char characterValueTrue;
	private final char characterValueFalse;

	private final char characterValueTrueLC;

	private final String stringValueTrue;
	private final String stringValueFalse;

	public BooleanTypeDescriptor() {
		this( 'Y', 'N' );
	}

	public BooleanTypeDescriptor(char characterValueTrue, char characterValueFalse) {
		super( Boolean.class );
		this.characterValueTrue = Character.toUpperCase( characterValueTrue );
		this.characterValueFalse = Character.toUpperCase( characterValueFalse );

		characterValueTrueLC = Character.toLowerCase( characterValueTrue );

		stringValueTrue = String.valueOf( characterValueTrue );
		stringValueFalse = String.valueOf( characterValueFalse );
	}
	@Override
	public String toString(Boolean value) {
		return value == null ? null : value.toString();
	}
	@Override
	public Boolean fromString(String string) {
		return Boolean.valueOf( string );
	}

	@SuppressWarnings({ "unchecked" })
	@Override
	public <X> X unwrap(Boolean value, Class<X> type, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}
		if ( Boolean.class.isAssignableFrom( type ) ) {
			return (X) value;
		}
		if ( Byte.class.isAssignableFrom( type ) ) {
			return (X) toByte( value );
		}
		if ( Short.class.isAssignableFrom( type ) ) {
			return (X) toShort( value );
		}
		if ( Integer.class.isAssignableFrom( type ) ) {
			return (X) toInteger( value );
		}
		if ( Long.class.isAssignableFrom( type ) ) {
			return (X) toLong( value );
		}
		if ( Character.class.isAssignableFrom( type ) ) {
			return (X) Character.valueOf( value ? characterValueTrue : characterValueFalse );
		}
		if ( String.class.isAssignableFrom( type ) ) {
			return (X) (value ? stringValueTrue : stringValueFalse);
		}
		throw unknownUnwrap( type );
	}
	@Override
	public <X> Boolean wrap(X value, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}
		if ( Boolean.class.isInstance( value ) ) {
			return (Boolean) value;
		}
		if ( Number.class.isInstance( value ) ) {
			final int intValue = ( (Number) value ).intValue();
			return intValue != 0;
		}
		if ( Character.class.isInstance( value ) ) {
			return isTrue( (Character) value );
		}
		if ( String.class.isInstance( value ) ) {
			return isTrue((String) value);
		}
		throw unknownWrap( value.getClass() );
	}

	private boolean isTrue(String strValue) {
		if (strValue != null && !strValue.isEmpty()) {
			return isTrue(strValue.charAt(0));
		}
		return false;
	}

	private boolean isTrue(char charValue) {
		return charValue == characterValueTrue || charValue == characterValueTrueLC;
	}

	public int toInt(Boolean value) {
		return value ? 1 : 0;
	}

	public Byte toByte(Boolean value) {
		return (byte) toInt( value );
	}

	public Short toShort(Boolean value) {
		return (short) toInt( value );
	}

	public Integer toInteger(Boolean value) {
		return toInt( value );
	}

	public Long toLong(Boolean value) {
		return (long) toInt( value );
	}

	@Override
	public Class getPrimitiveClass() {
		return boolean.class;
	}

	@Override
	public Boolean getDefaultValue() {
		return false;
	}

	@Override
	public long getDefaultSqlLength(Dialect dialect) {
		return 1;
	}

	@Override
	public int getDefaultSqlPrecision(Dialect dialect) {
		return 1;
	}

	@Override
	public int getDefaultSqlScale() {
		return 0;
	}

	@Override
	public String getCheckCondition(String columnName, SqlTypeDescriptor sqlTypeDescriptor, Dialect dialect) {
		switch ( sqlTypeDescriptor.getSqlType() ) {
			case Types.CHAR:
			case Types.VARCHAR:
			case Types.LONGVARCHAR:
			case Types.NCHAR:
			case Types.NVARCHAR:
			case Types.LONGNVARCHAR:
				return columnName + " in ('" + characterValueFalse + "','" + characterValueTrue + "')";
			case Types.BIT:
			case Types.SMALLINT:
			case Types.TINYINT:
			case Types.INTEGER:
			case Types.BIGINT:
			case Types.DOUBLE:
			case Types.REAL:
			case Types.FLOAT:
			case Types.NUMERIC:
			case Types.DECIMAL:
				if ( !dialect.supportsBitType() ) {
					return columnName + " in (0,1)";
				}
		}
		return null;
	}
}
