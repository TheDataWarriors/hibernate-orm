/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.internal;

import org.hibernate.bytecode.spi.ReflectionOptimizer;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

/**
 * @author Steve Ebersole
 */
public class OptimizedPojoInstantiatorImpl<J> extends AbstractPojoInstantiator {
	private final ReflectionOptimizer.InstantiationOptimizer instantiationOptimizer;

	public OptimizedPojoInstantiatorImpl(JavaTypeDescriptor javaTypeDescriptor, ReflectionOptimizer.InstantiationOptimizer instantiationOptimizer) {
		super( javaTypeDescriptor.getJavaTypeClass() );
		this.instantiationOptimizer = instantiationOptimizer;
	}

	@Override
	public Object instantiate(SessionFactoryImplementor sessionFactory) {
		return instantiationOptimizer.newInstance();
	}
}
