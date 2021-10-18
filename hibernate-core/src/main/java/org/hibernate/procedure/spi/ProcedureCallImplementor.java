/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.procedure.spi;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Parameter;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.TemporalType;

import org.hibernate.procedure.ProcedureCall;
import org.hibernate.query.spi.QueryImplementor;
import org.hibernate.type.BasicTypeReference;

/**
 * @author Steve Ebersole
 */
public interface ProcedureCallImplementor<R> extends ProcedureCall, QueryImplementor<R> {
	@Override
	default List<R> getResultList() {
		return list();
	}

	ParameterStrategy getParameterStrategy();

	@Override
	default R getSingleResult() {
		return uniqueResult();
	}

	@Override
	ProcedureCallImplementor<R> registerStoredProcedureParameter(int position, BasicTypeReference<?> type, ParameterMode mode);

	@Override
	ProcedureCallImplementor<R> registerStoredProcedureParameter(String parameterName, BasicTypeReference<?> type, ParameterMode mode);

	@Override
	ProcedureCallImplementor<R> setHint(String hintName, Object value);

	@Override
	<T> ProcedureCallImplementor<R> setParameter(Parameter<T> param, T value);

	@Override
	ProcedureCallImplementor<R> setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType);

	@Override
	ProcedureCallImplementor<R> setParameter(Parameter<Date> param, Date value, TemporalType temporalType);

	@Override
	ProcedureCallImplementor<R> setParameter(String name, Object value);

	@Override
	ProcedureCallImplementor<R> setParameter(String name, Calendar value, TemporalType temporalType);

	@Override
	ProcedureCallImplementor<R> setParameter(String name, Date value, TemporalType temporalType);

	@Override
	ProcedureCallImplementor<R> setParameter(int position, Object value);

	@Override
	ProcedureCallImplementor<R> setParameter(int position, Calendar value, TemporalType temporalType);

	@Override
	ProcedureCallImplementor<R> setParameter(int position, Date value, TemporalType temporalType);

	@Override
	ProcedureCallImplementor<R> setFlushMode(FlushModeType flushMode);

	@Override
	ProcedureCallImplementor<R> registerStoredProcedureParameter(int position, Class type, ParameterMode mode);

	@Override
	ProcedureCallImplementor<R> registerStoredProcedureParameter(String parameterName, Class type, ParameterMode mode);

}
