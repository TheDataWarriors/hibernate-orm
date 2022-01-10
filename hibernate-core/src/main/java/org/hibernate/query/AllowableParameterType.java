/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query;

import org.hibernate.Incubating;
import org.hibernate.NotYetImplementedFor6Exception;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.sqm.SqmExpressable;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.metamodel.ManagedType;

/**
 * Types that can be used to handle binding {@link org.hibernate.query.Query} parameters
 *
 * @see org.hibernate.type.BasicTypeReference
 * @see org.hibernate.type.StandardBasicTypes
 *
 * @author Steve Ebersole
 */
@Incubating
public interface AllowableParameterType<J> {
	/**
	 * The expected Java type
	 */
	Class<J> getBindableJavaType();

	static <T> AllowableParameterType<? extends T> parameterType(Class<T> type) {
		throw new NotYetImplementedFor6Exception( "AllowableParameterType#parameterType" );
	}

	static <T> AllowableParameterType<? extends T> parameterType(Class<?> javaType, AttributeConverter<T,?> converter) {
		throw new NotYetImplementedFor6Exception( "AllowableParameterType#parameterType" );
	}

	static <T> AllowableParameterType<? extends T> parameterType(Class<?> javaType, Class<? extends AttributeConverter<T,?>> converter) {
		throw new NotYetImplementedFor6Exception( "AllowableParameterType#parameterType" );
	}

	static <T> AllowableParameterType<? extends T> parameterType(ManagedType<T> managedType) {
		throw new NotYetImplementedFor6Exception( "AllowableParameterType#parameterType" );
	}

	static <T> AllowableParameterType<? extends T> parameterType(jakarta.persistence.metamodel.Bindable<T> jpaBindable) {
		throw new NotYetImplementedFor6Exception( "AllowableParameterType#parameterType" );
	}

	static <T> AllowableParameterType<? extends T> parameterType(org.hibernate.metamodel.mapping.Bindable bindable) {
		throw new NotYetImplementedFor6Exception( "AllowableParameterType#parameterType" );
	}

	/**
	 * Resolve this parameter type to the corresponding SqmExpressable
	 *
	 * @todo (6.0) - use SessionFactory (API) here instead - we'll just cast "below"
	 */
	SqmExpressable<J> resolveExpressable(SessionFactoryImplementor sessionFactory);
}
