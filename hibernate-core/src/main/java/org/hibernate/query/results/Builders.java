/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.results;

import java.util.Locale;
import javax.persistence.AttributeConverter;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;

import org.hibernate.LockMode;
import org.hibernate.NotYetImplementedFor6Exception;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.RuntimeMetamodels;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.SingularAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.internal.ResultSetMappingResolutionContext;
import org.hibernate.query.results.dynamic.DynamicResultBuilderAttribute;
import org.hibernate.query.results.dynamic.DynamicResultBuilderBasic;
import org.hibernate.query.results.dynamic.DynamicResultBuilderBasicConverted;
import org.hibernate.query.results.dynamic.DynamicResultBuilderBasicStandard;
import org.hibernate.query.results.dynamic.DynamicResultBuilderEntityCalculated;
import org.hibernate.query.results.dynamic.DynamicResultBuilderEntityStandard;
import org.hibernate.query.results.dynamic.DynamicResultBuilderInstantiation;
import org.hibernate.query.results.dynamic.DynamicFetchBuilderLegacy;
import org.hibernate.query.results.implicit.ImplicitFetchBuilder;
import org.hibernate.query.results.implicit.ImplicitFetchBuilderBasic;
import org.hibernate.query.results.implicit.ImplicitFetchBuilderEmbeddable;
import org.hibernate.query.results.implicit.ImplicitModelPartResultBuilderEntity;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetchable;
import org.hibernate.sql.results.graph.embeddable.EmbeddableValuedFetchable;
import org.hibernate.sql.results.graph.entity.EntityValuedFetchable;
import org.hibernate.type.BasicType;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

/**
 * @author Steve Ebersole
 */
public class Builders {
	public static DynamicResultBuilderBasic scalar(String columnAlias) {
		return scalar( columnAlias, columnAlias );
	}

	public static DynamicResultBuilderBasic scalar(String columnAlias, String resultAlias) {
		return new DynamicResultBuilderBasicStandard( columnAlias, resultAlias );
	}

	public static DynamicResultBuilderBasic scalar(String columnAlias, BasicType<?> type) {
		return scalar( columnAlias, columnAlias, type );
	}

	public static DynamicResultBuilderBasic scalar(String columnAlias, String resultAlias, BasicType<?> type) {
		return new DynamicResultBuilderBasicStandard( columnAlias, resultAlias, type );
	}

	public static DynamicResultBuilderBasic scalar(
			String columnAlias,
			Class<?> javaType,
			SessionFactoryImplementor factory) {
		return scalar( columnAlias, columnAlias, javaType, factory );
	}

	public static DynamicResultBuilderBasic scalar(
			String columnAlias,
			String resultAlias,
			Class<?> javaType,
			SessionFactoryImplementor factory) {
		final JavaTypeDescriptor<?> javaTypeDescriptor = factory.getTypeConfiguration()
				.getJavaTypeDescriptorRegistry()
				.getDescriptor( javaType );

		return new DynamicResultBuilderBasicStandard( columnAlias, resultAlias, javaTypeDescriptor );
	}

	public static <R> ResultBuilder converted(
			String columnAlias,
			Class<R> jdbcJavaType,
			AttributeConverter<?, R> converter,
			SessionFactoryImplementor sessionFactory) {
		return converted( columnAlias, null, jdbcJavaType, converter, sessionFactory );
	}

	public static <O,R> ResultBuilder converted(
			String columnAlias,
			Class<O> domainJavaType,
			Class<R> jdbcJavaType,
			AttributeConverter<O, R> converter,
			SessionFactoryImplementor sessionFactory) {
		return new DynamicResultBuilderBasicConverted( columnAlias, domainJavaType, jdbcJavaType, converter, sessionFactory );
	}

	public static <R> ResultBuilder converted(
			String columnAlias,
			Class<R> jdbcJavaType,
			Class<? extends AttributeConverter<?, R>> converterJavaType,
			SessionFactoryImplementor sessionFactory) {
		return converted( columnAlias, null, jdbcJavaType, (Class) converterJavaType, sessionFactory );
	}

	public static <O,R> ResultBuilder converted(
			String columnAlias,
			Class<O> domainJavaType,
			Class<R> jdbcJavaType,
			Class<? extends AttributeConverter<O,R>> converterJavaType,
			SessionFactoryImplementor sessionFactory) {
		return new DynamicResultBuilderBasicConverted( columnAlias, domainJavaType, jdbcJavaType, converterJavaType, sessionFactory );
	}

	public static ResultBuilderBasicValued scalar(int position) {
		// will be needed for interpreting legacy HBM <resultset/> mappings
		throw new NotYetImplementedFor6Exception();
	}

	public static ResultBuilderBasicValued scalar(int position, BasicType<?> type) {
		// will be needed for interpreting legacy HBM <resultset/> mappings
		throw new NotYetImplementedFor6Exception();
	}

	public static <J> DynamicResultBuilderInstantiation<J> instantiation(Class<J> targetJavaType, SessionFactoryImplementor factory) {
		final JavaTypeDescriptor<J> targetJtd = factory.getTypeConfiguration()
				.getJavaTypeDescriptorRegistry()
				.getDescriptor( targetJavaType );
		return new DynamicResultBuilderInstantiation<>( targetJtd );
	}

	public static ResultBuilder attributeResult(
			String columnAlias,
			String entityName,
			String attributePath,
			SessionFactoryImplementor sessionFactory) {
		if ( attributePath.contains( "." ) ) {
			throw new NotYetImplementedFor6Exception(
					"Support for defining a NativeQuery attribute result based on a composite path is not yet implemented"
			);
		}

		final RuntimeMetamodels runtimeMetamodels = sessionFactory.getRuntimeMetamodels();
		final String fullEntityName = runtimeMetamodels.getMappingMetamodel().getImportedName( entityName );
		final EntityPersister entityMapping = runtimeMetamodels.getMappingMetamodel().findEntityDescriptor( fullEntityName );
		if ( entityMapping == null ) {
			throw new IllegalArgumentException( "Could not locate entity mapping : " + fullEntityName );
		}

		final AttributeMapping attributeMapping = entityMapping.findAttributeMapping( attributePath );
		if ( attributeMapping == null ) {
			throw new IllegalArgumentException( "Could not locate attribute mapping : " + fullEntityName + "." + attributePath );
		}

		if ( attributeMapping instanceof SingularAttributeMapping ) {
			final SingularAttributeMapping singularAttributeMapping = (SingularAttributeMapping) attributeMapping;
			return new DynamicResultBuilderAttribute( singularAttributeMapping, columnAlias, fullEntityName, attributePath );
		}

		throw new IllegalArgumentException(
				String.format(
						Locale.ROOT,
						"Specified attribute mapping [%s.%s] not a basic attribute: %s",
						fullEntityName,
						attributePath,
						attributeMapping
				)
		);
	}

	public static ResultBuilder attributeResult(String columnAlias, SingularAttribute<?, ?> attribute) {
		if ( ! ( attribute.getDeclaringType() instanceof EntityType ) ) {
			throw new NotYetImplementedFor6Exception(
					"Support for defining a NativeQuery attribute result based on a composite path is not yet implemented"
			);
		}

		throw new NotYetImplementedFor6Exception();
	}

	/**
	 * Creates a EntityResultBuilder allowing for further configuring of the mapping.
	 *
	 * @param tableAlias
	 * @param entityName
	 * @return
	 */
	public static DynamicResultBuilderEntityStandard entity(
			String tableAlias,
			String entityName,
			SessionFactoryImplementor sessionFactory) {
		final RuntimeMetamodels runtimeMetamodels = sessionFactory.getRuntimeMetamodels();
		final EntityMappingType entityMapping = runtimeMetamodels.getEntityMappingType( entityName );

		return new DynamicResultBuilderEntityStandard( entityMapping, tableAlias );
	}

	/**
	 * Creates a EntityResultBuilder that does not allow any further configuring of the mapping.
	 *
	 * @see org.hibernate.query.NativeQuery#addEntity(Class)
	 * @see org.hibernate.query.NativeQuery#addEntity(String)
	 * @see org.hibernate.query.NativeQuery#addEntity(String, Class)
	 * @see org.hibernate.query.NativeQuery#addEntity(String, String)
	 */
	public static DynamicResultBuilderEntityCalculated entityCalculated(
			String tableAlias,
			String entityName,
			SessionFactoryImplementor sessionFactory) {
		return entityCalculated( tableAlias, entityName, null,sessionFactory );
	}

	/**
	 * Creates a EntityResultBuilder that does not allow any further configuring of the mapping.
	 *
	 * @see #entityCalculated(String, String, SessionFactoryImplementor)
	 * @see org.hibernate.query.NativeQuery#addEntity(String, Class, LockMode)
	 * @see org.hibernate.query.NativeQuery#addEntity(String, String, LockMode)
	 */
	public static DynamicResultBuilderEntityCalculated entityCalculated(
			String tableAlias,
			String entityName,
			LockMode explicitLockMode,
			SessionFactoryImplementor sessionFactory) {
		final RuntimeMetamodels runtimeMetamodels = sessionFactory.getRuntimeMetamodels();
		final EntityMappingType entityMapping = runtimeMetamodels.getEntityMappingType( entityName );

		return new DynamicResultBuilderEntityCalculated( entityMapping, tableAlias, explicitLockMode, sessionFactory );
	}

	public static DynamicFetchBuilderLegacy fetch(String tableAlias, String ownerTableAlias, String joinPropertyName) {
		throw new NotYetImplementedFor6Exception( );
	}

	public static ResultBuilder implicitEntityResultBuilder(
			Class<?> resultMappingClass,
			ResultSetMappingResolutionContext resolutionContext) {
		final EntityMappingType entityMappingType = resolutionContext
				.getSessionFactory()
				.getRuntimeMetamodels()
				.getEntityMappingType( resultMappingClass );
		return new ImplicitModelPartResultBuilderEntity( entityMappingType );
	}

	public static ImplicitFetchBuilder implicitFetchBuilder(
			NavigablePath fetchPath,
			Fetchable fetchable,
			DomainResultCreationState creationState) {
		if ( fetchable instanceof BasicValuedModelPart ) {
			final BasicValuedModelPart basicValuedFetchable = (BasicValuedModelPart) fetchable;
			return new ImplicitFetchBuilderBasic( fetchPath, basicValuedFetchable );
		}

		if ( fetchable instanceof EmbeddableValuedFetchable ) {
			final EmbeddableValuedFetchable embeddableValuedFetchable = (EmbeddableValuedFetchable) fetchable;
			return new ImplicitFetchBuilderEmbeddable( fetchPath, embeddableValuedFetchable, creationState );
		}

		if ( fetchable instanceof EntityValuedFetchable ) {
			final EntityValuedFetchable entityValuedFetchable = (EntityValuedFetchable) fetchable;
			throw new NotYetImplementedFor6Exception( "Support for implicit entity-valued fetches is not yet implemented" );
		}

		throw new UnsupportedOperationException();
	}
}
