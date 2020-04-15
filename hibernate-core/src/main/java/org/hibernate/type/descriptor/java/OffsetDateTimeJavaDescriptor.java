/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.java;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.persistence.TemporalType;

import org.hibernate.dialect.Dialect;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.sql.DateTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptorIndicators;
import org.hibernate.type.descriptor.sql.TimeTypeDescriptor;
import org.hibernate.type.descriptor.sql.TimestampTypeDescriptor;
import org.hibernate.type.descriptor.sql.TimestampWithTimeZoneDescriptor;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Java type descriptor for the {@link OffsetDateTime} type.
 *
 * @author Steve Ebersole
 */
public class OffsetDateTimeJavaDescriptor extends AbstractTemporalTypeDescriptor<OffsetDateTime> {
	/**
	 * Singleton access
	 */
	public static final OffsetDateTimeJavaDescriptor INSTANCE = new OffsetDateTimeJavaDescriptor();

	@SuppressWarnings("unchecked")
	public OffsetDateTimeJavaDescriptor() {
		super( OffsetDateTime.class, ImmutableMutabilityPlan.INSTANCE );
	}

	@Override
	public TemporalType getPrecision() {
		return TemporalType.TIMESTAMP;
	}

	@Override
	public SqlTypeDescriptor getJdbcRecommendedSqlType(SqlTypeDescriptorIndicators stdIndicators) {
		final TemporalType temporalPrecision = stdIndicators.getTemporalPrecision();

		if ( temporalPrecision == null || temporalPrecision == TemporalType.TIMESTAMP ) {
			return TimestampWithTimeZoneDescriptor.INSTANCE;
		}

		switch ( temporalPrecision ) {
			case TIME: {
				return TimeTypeDescriptor.INSTANCE;
			}
			case DATE: {
				return DateTypeDescriptor.INSTANCE;
			}
			default: {
				throw new IllegalArgumentException( "Unexpected javax.persistence.TemporalType : " + temporalPrecision );
			}
		}
	}

	@Override
	protected <X> TemporalJavaTypeDescriptor<X> forTimestampPrecision(TypeConfiguration typeConfiguration) {
		//noinspection unchecked
		return (TemporalJavaTypeDescriptor<X>) this;
	}

	@Override
	public String toString(OffsetDateTime value) {
		return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format( value );
	}

	@Override
	public OffsetDateTime fromString(String string) {
		return OffsetDateTime.from( DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse( string ) );
	}

	@Override
	@SuppressWarnings("unchecked")
	public <X> X unwrap(OffsetDateTime offsetDateTime, Class<X> type, WrapperOptions options) {
		if ( offsetDateTime == null ) {
			return null;
		}

		if ( OffsetDateTime.class.isAssignableFrom( type ) ) {
			return (X) offsetDateTime;
		}

		if ( ZonedDateTime.class.isAssignableFrom( type ) ) {
			return (X) offsetDateTime.toZonedDateTime();
		}

		if ( Calendar.class.isAssignableFrom( type ) ) {
			return (X) GregorianCalendar.from( offsetDateTime.toZonedDateTime() );
		}

		if ( Timestamp.class.isAssignableFrom( type ) ) {
			/*
			 * This works around two bugs:
			 * - HHH-13266 (JDK-8061577): around and before 1900,
			 * the number of milliseconds since the epoch does not mean the same thing
			 * for java.util and java.time, so conversion must be done using the year, month, day, hour, etc.
			 * - HHH-13379 (JDK-4312621): after 1908 (approximately),
			 * Daylight Saving Time introduces ambiguity in the year/month/day/hour/etc representation once a year
			 * (on DST end), so conversion must be done using the number of milliseconds since the epoch.
			 * - around 1905, both methods are equally valid, so we don't really care which one is used.
			 */
			if ( offsetDateTime.getYear() < 1905 ) {
				return (X) Timestamp.valueOf(
						offsetDateTime.atZoneSameInstant( ZoneId.systemDefault() ).toLocalDateTime()
				);
			}
			else {
				return (X) Timestamp.from( offsetDateTime.toInstant() );
			}
		}

		if ( java.sql.Date.class.isAssignableFrom( type ) ) {
			return (X) java.sql.Date.from( offsetDateTime.toInstant() );
		}

		if ( java.sql.Time.class.isAssignableFrom( type ) ) {
			return (X) java.sql.Time.from( offsetDateTime.toInstant() );
		}

		if ( Date.class.isAssignableFrom( type ) ) {
			return (X) Date.from( offsetDateTime.toInstant() );
		}

		if ( Long.class.isAssignableFrom( type ) ) {
			return (X) Long.valueOf( offsetDateTime.toInstant().toEpochMilli() );
		}

		throw unknownUnwrap( type );
	}

	@Override
	public <X> OffsetDateTime wrap(X value, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}

		if ( OffsetDateTime.class.isInstance( value ) ) {
			return (OffsetDateTime) value;
		}

		if ( ZonedDateTime.class.isInstance( value ) ) {
			ZonedDateTime zonedDateTime = (ZonedDateTime) value;
			return OffsetDateTime.of( zonedDateTime.toLocalDateTime(), zonedDateTime.getOffset() );
		}

		if ( Timestamp.class.isInstance( value ) ) {
			final Timestamp ts = (Timestamp) value;
			/*
			 * This works around two bugs:
			 * - HHH-13266 (JDK-8061577): around and before 1900,
			 * the number of milliseconds since the epoch does not mean the same thing
			 * for java.util and java.time, so conversion must be done using the year, month, day, hour, etc.
			 * - HHH-13379 (JDK-4312621): after 1908 (approximately),
			 * Daylight Saving Time introduces ambiguity in the year/month/day/hour/etc representation once a year
			 * (on DST end), so conversion must be done using the number of milliseconds since the epoch.
			 * - around 1905, both methods are equally valid, so we don't really care which one is used.
			 */
			if ( ts.getYear() < 5 ) { // Timestamp year 0 is 1900
				return ts.toLocalDateTime().atZone( ZoneId.systemDefault() ).toOffsetDateTime();
			}
			else {
				return OffsetDateTime.ofInstant( ts.toInstant(), ZoneId.systemDefault() );
			}
		}

		if ( Date.class.isInstance( value ) ) {
			final Date date = (Date) value;
			return OffsetDateTime.ofInstant( date.toInstant(), ZoneId.systemDefault() );
		}

		if ( Long.class.isInstance( value ) ) {
			return OffsetDateTime.ofInstant( Instant.ofEpochMilli( (Long) value ), ZoneId.systemDefault() );
		}

		if ( Calendar.class.isInstance( value ) ) {
			final Calendar calendar = (Calendar) value;
			return OffsetDateTime.ofInstant( calendar.toInstant(), calendar.getTimeZone().toZoneId() );
		}

		throw unknownWrap( value.getClass() );
	}

	@Override
	public int getDefaultSqlPrecision(Dialect dialect) {
		return dialect.getDefaultTimestampPrecision();
	}
}
