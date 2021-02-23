/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.java;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.hibernate.dialect.Dialect;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.spi.Primitive;

/**
 * Descriptor for {@link Long} handling.
 *
 * @author Steve Ebersole
 */
public class LongTypeDescriptor extends AbstractClassTypeDescriptor<Long> implements Primitive<Long> {
	public static final LongTypeDescriptor INSTANCE = new LongTypeDescriptor();

	public LongTypeDescriptor() {
		super( Long.class );
	}
	@Override
	public String toString(Long value) {
		return value == null ? null : value.toString();
	}
	@Override
	public Long fromString(String string) {
		return Long.valueOf( string );
	}

	@SuppressWarnings({ "unchecked" })
	@Override
	public <X> X unwrap(Long value, Class<X> type, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}
		if ( Long.class.isAssignableFrom( type ) ) {
			return (X) value;
		}
		if ( Byte.class.isAssignableFrom( type ) ) {
			return (X) Byte.valueOf( value.byteValue() );
		}
		if ( Short.class.isAssignableFrom( type ) ) {
			return (X) Short.valueOf( value.shortValue() );
		}
		if ( Integer.class.isAssignableFrom( type ) ) {
			return (X) Integer.valueOf( value.intValue() );
		}
		if ( Double.class.isAssignableFrom( type ) ) {
			return (X) Double.valueOf( value.doubleValue() );
		}
		if ( Float.class.isAssignableFrom( type ) ) {
			return (X) Float.valueOf( value.floatValue() );
		}
		if ( BigInteger.class.isAssignableFrom( type ) ) {
			return (X) BigInteger.valueOf( value );
		}
		if ( BigDecimal.class.isAssignableFrom( type ) ) {
			return (X) BigDecimal.valueOf( value );
		}
		if ( String.class.isAssignableFrom( type ) ) {
			return (X) value.toString();
		}
		throw unknownUnwrap( type );
	}

	@Override
	public <X> Long wrap(X value, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}
		if ( Long.class.isInstance( value ) ) {
			return (Long) value;
		}
		if ( Number.class.isInstance( value ) ) {
			return ( (Number) value ).longValue();
		}
		else if ( String.class.isInstance( value ) ) {
			return Long.valueOf( ( (String) value ) );
		}
		throw unknownWrap( value.getClass() );
	}

	@Override
	public Class getPrimitiveClass() {
		return long.class;
	}

	@Override
	public Long getDefaultValue() {
		return 0L;
	}

	@Override
	public long getDefaultSqlLength(Dialect dialect) {
		return getDefaultSqlPrecision(dialect)+1;
	}

	@Override
	public int getDefaultSqlPrecision(Dialect dialect) {
		return 19;
	}

	@Override
	public int getDefaultSqlScale() {
		return 0;
	}
}
