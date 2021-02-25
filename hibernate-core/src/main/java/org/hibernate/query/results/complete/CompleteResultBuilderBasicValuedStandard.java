/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.results.complete;

import java.util.function.BiFunction;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.BasicValuedMapping;
import org.hibernate.query.results.DomainResultCreationStateImpl;
import org.hibernate.query.results.ResultsHelper;
import org.hibernate.query.results.SqlSelectionImpl;
import org.hibernate.query.results.dynamic.DynamicFetchBuilderLegacy;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.basic.BasicResult;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMetadata;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

import static org.hibernate.query.results.ResultsHelper.impl;

/**
 * ResultBuilder for scalar results defined via:<ul>
 *     <li>JPA {@link javax.persistence.ColumnResult}</li>
 *     <li>`<return-scalar/>` as part of a `<resultset/>` stanza in `hbm.xml`</li>
 * </ul>
 *
 * @author Steve Ebersole
 */
public class CompleteResultBuilderBasicValuedStandard implements CompleteResultBuilderBasicValued {

	private final String explicitColumnName;

	private final BasicValuedMapping explicitType;
	private final JavaTypeDescriptor<?> explicitJavaTypeDescriptor;

	public CompleteResultBuilderBasicValuedStandard(
			String explicitColumnName,
			BasicValuedMapping explicitType,
			JavaTypeDescriptor<?> explicitJavaTypeDescriptor) {
		this.explicitColumnName = explicitColumnName;
		this.explicitType = explicitType;
		this.explicitJavaTypeDescriptor = explicitJavaTypeDescriptor;
	}

	@Override
	public BasicResult<?> buildResult(
			JdbcValuesMetadata jdbcResultsMetadata,
			int resultPosition,
			BiFunction<String, String, DynamicFetchBuilderLegacy> legacyFetchResolver,
			DomainResultCreationState domainResultCreationState) {
		final DomainResultCreationStateImpl creationStateImpl = impl( domainResultCreationState );
		final SessionFactoryImplementor sessionFactory = creationStateImpl.getSessionFactory();

		final int jdbcPosition;
		final String columnName;

		if ( explicitColumnName != null ) {
			jdbcPosition = jdbcResultsMetadata.resolveColumnPosition( explicitColumnName );
			columnName = explicitColumnName;
		}
		else {
			jdbcPosition = resultPosition + 1;
			columnName = jdbcResultsMetadata.resolveColumnName( jdbcPosition );
		}

		final int valuesArrayPosition = ResultsHelper.jdbcPositionToValuesArrayPosition( jdbcPosition );

		final BasicValuedMapping basicType;

		if ( explicitType != null ) {
			basicType = explicitType;
		}
		else {
			basicType = jdbcResultsMetadata.resolveType( jdbcPosition, explicitJavaTypeDescriptor );
		}

		creationStateImpl.resolveSqlSelection(
				creationStateImpl.resolveSqlExpression(
						columnName,
						processingState -> new SqlSelectionImpl( valuesArrayPosition, basicType )
				),
				explicitJavaTypeDescriptor,
				sessionFactory.getTypeConfiguration()
		);

		return new BasicResult<>( valuesArrayPosition, columnName, basicType.getMappedType().getMappedJavaTypeDescriptor() );
	}

}
