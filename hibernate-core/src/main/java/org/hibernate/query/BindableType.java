/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query;

import org.hibernate.Incubating;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.sqm.SqmExpressible;
import org.hibernate.query.sqm.spi.SqmCreationContext;

/**
 * Types that can be used to handle binding {@link Query} parameters
 *
 * @see org.hibernate.type.BasicTypeReference
 * @see org.hibernate.type.StandardBasicTypes
 *
 * @author Steve Ebersole
 */
@Incubating
public interface BindableType<J> {
	/**
	 * The expected Java type
	 */
	Class<J> getBindableJavaType();

	default boolean isInstance(J value) {
		return getBindableJavaType().isInstance( value );
	}

	/**
	 * Resolve this parameter type to the corresponding SqmExpressible
	 */
	default SqmExpressible<J> resolveExpressible(SessionFactoryImplementor sessionFactory) {
		return resolveExpressible( (SqmCreationContext) sessionFactory );
	}

	SqmExpressible<J> resolveExpressible(SqmCreationContext creationContext);
}
