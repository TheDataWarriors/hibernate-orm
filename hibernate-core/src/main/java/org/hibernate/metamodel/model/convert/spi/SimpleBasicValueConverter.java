/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.model.convert.spi;

import java.util.function.Function;

import org.hibernate.type.descriptor.java.JavaType;

/**
 * @author Steve Ebersole
 */
public class SimpleBasicValueConverter<D,R> implements BasicValueConverter<D,R> {
	private final JavaType<D> domainJtd;
	private final JavaType<R> relationalJtd;

	private final Function<R,D> toDomainHandler;
	private final Function<D,R> toRelationalHandler;

	public SimpleBasicValueConverter(
			JavaType<D> domainJtd,
			JavaType<R> relationalJtd,
			Function<R,D> toDomainHandler,
			Function<D,R> toRelationalHandler) {
		this.domainJtd = domainJtd;
		this.relationalJtd = relationalJtd;
		this.toDomainHandler = toDomainHandler;
		this.toRelationalHandler = toRelationalHandler;
	}

	@Override
	public D toDomainValue(R relationalForm) {
		return toDomainHandler.apply( relationalForm );
	}

	@Override
	public R toRelationalValue(D domainForm) {
		return toRelationalHandler.apply( domainForm );
	}

	@Override
	public JavaType<D> getDomainJavaType() {
		return domainJtd;
	}

	@Override
	public JavaType<R> getRelationalJavaType() {
		return relationalJtd;
	}
}
