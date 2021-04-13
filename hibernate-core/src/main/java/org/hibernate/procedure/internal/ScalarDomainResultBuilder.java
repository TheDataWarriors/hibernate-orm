/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.procedure.internal;

import java.util.function.BiFunction;

import org.hibernate.query.results.ResultBuilder;
import org.hibernate.query.results.dynamic.DynamicFetchBuilderLegacy;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.basic.BasicResult;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMetadata;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

/**
 * @author Steve Ebersole
 */
public class ScalarDomainResultBuilder<T> implements ResultBuilder {
	private final JavaTypeDescriptor<T> typeDescriptor;

	public ScalarDomainResultBuilder(JavaTypeDescriptor<T> typeDescriptor) {
		this.typeDescriptor = typeDescriptor;
	}

	@Override
	public DomainResult<T> buildResult(
			JdbcValuesMetadata jdbcResultsMetadata,
			int resultPosition,
			BiFunction<String, String, DynamicFetchBuilderLegacy> legacyFetchResolver,
			DomainResultCreationState domainResultCreationState) {
		return new BasicResult<>( resultPosition, null, typeDescriptor );
	}
}
