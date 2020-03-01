/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type;

import java.time.LocalDate;
import javax.persistence.TemporalType;

import org.hibernate.QueryException;
import org.hibernate.metamodel.model.domain.AllowableTemporalParameterType;
import org.hibernate.type.descriptor.java.LocalDateJavaDescriptor;
import org.hibernate.type.descriptor.sql.DateTypeDescriptor;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * @author Steve Ebersole
 */
public class LocalDateType
		extends AbstractSingleColumnStandardBasicType<LocalDate>
		implements LiteralType<LocalDate>, AllowableTemporalParameterType<LocalDate> {

	/**
	 * Singleton access
	 */
	public static final LocalDateType INSTANCE = new LocalDateType();

	public LocalDateType() {
		super( DateTypeDescriptor.INSTANCE, LocalDateJavaDescriptor.INSTANCE );
	}

	@Override
	public String getName() {
		return LocalDate.class.getSimpleName();
	}

	@Override
	protected boolean registerUnderJavaType() {
		return true;
	}

	@Override
	public AllowableTemporalParameterType resolveTemporalPrecision(
			TemporalType temporalPrecision,
			TypeConfiguration typeConfiguration) {
		switch ( temporalPrecision ) {
			case DATE: {
				return this;
			}
			case TIMESTAMP: {
				return LocalDateTimeType.INSTANCE;
			}
			default: {
				throw new QueryException( "LocalDate type cannot be treated using `" + temporalPrecision.name() + "` precision" );
			}
		}
	}
}
