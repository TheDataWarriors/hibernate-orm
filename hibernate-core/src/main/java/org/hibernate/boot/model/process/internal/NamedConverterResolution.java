/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.boot.model.process.internal;

import java.util.function.Function;
import javax.persistence.AttributeConverter;

import org.hibernate.boot.model.convert.internal.ClassBasedConverterDescriptor;
import org.hibernate.boot.model.convert.spi.ConverterDescriptor;
import org.hibernate.boot.model.convert.spi.JpaAttributeConverterCreationContext;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.model.convert.spi.JpaAttributeConverter;
import org.hibernate.type.BasicType;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.converter.AttributeConverterMutabilityPlanImpl;
import org.hibernate.type.descriptor.converter.AttributeConverterTypeAdapter;
import org.hibernate.type.descriptor.java.BasicJavaDescriptor;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.java.MutabilityPlan;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptorIndicators;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * @author Steve Ebersole
 */
@SuppressWarnings("rawtypes")
public class NamedConverterResolution<J> implements BasicValue.Resolution<J> {

	public static NamedConverterResolution from(
			ConverterDescriptor converterDescriptor,
			Function<TypeConfiguration, BasicJavaDescriptor> explicitJtdAccess,
			Function<TypeConfiguration, SqlTypeDescriptor> explicitStdAccess,
			Function<TypeConfiguration, MutabilityPlan> explicitMutabilityPlanAccess,
			SqlTypeDescriptorIndicators sqlTypeIndicators,
			JpaAttributeConverterCreationContext converterCreationContext,
			MetadataBuildingContext context) {
		return fromInternal(
				explicitJtdAccess,
				explicitStdAccess,
				explicitMutabilityPlanAccess,
				converterDescriptor.createJpaAttributeConverter( converterCreationContext ),
				sqlTypeIndicators,
				context
		);
	}

	public static NamedConverterResolution from(
			String name,
			Function<TypeConfiguration, BasicJavaDescriptor> explicitJtdAccess,
			Function<TypeConfiguration, SqlTypeDescriptor> explicitStdAccess,
			Function<TypeConfiguration, MutabilityPlan> explicitMutabilityPlanAccess,
			SqlTypeDescriptorIndicators sqlTypeIndicators,
			JpaAttributeConverterCreationContext converterCreationContext,
			MetadataBuildingContext context) {
		assert name.startsWith( ConverterDescriptor.TYPE_NAME_PREFIX );
		final String converterClassName = name.substring( ConverterDescriptor.TYPE_NAME_PREFIX.length() );

		final StandardServiceRegistry serviceRegistry = context.getBootstrapContext().getServiceRegistry();
		final ClassLoaderService classLoaderService = serviceRegistry.getService( ClassLoaderService.class );

		final Class<? extends AttributeConverter> converterClass = classLoaderService.classForName( converterClassName );
		final ClassBasedConverterDescriptor converterDescriptor = new ClassBasedConverterDescriptor(
				converterClass,
				context.getBootstrapContext().getClassmateContext()
		);

		return fromInternal(
				explicitJtdAccess,
				explicitStdAccess,
				explicitMutabilityPlanAccess,
				converterDescriptor.createJpaAttributeConverter( converterCreationContext ),
				sqlTypeIndicators,
				context
		);
	}

	private static NamedConverterResolution fromInternal(
			Function<TypeConfiguration, BasicJavaDescriptor> explicitJtdAccess,
			Function<TypeConfiguration, SqlTypeDescriptor> explicitStdAccess,
			Function<TypeConfiguration, MutabilityPlan> explicitMutabilityPlanAccess,
			JpaAttributeConverter converter, SqlTypeDescriptorIndicators sqlTypeIndicators,
			MetadataBuildingContext context) {
		final TypeConfiguration typeConfiguration = context.getBootstrapContext().getTypeConfiguration();

		final JavaTypeDescriptor explicitJtd = explicitJtdAccess != null
				? explicitJtdAccess.apply( typeConfiguration )
				: null;

		final JavaTypeDescriptor domainJtd = explicitJtd != null
				? explicitJtd
				: converter.getDomainJavaDescriptor();

		final SqlTypeDescriptor explicitStd = explicitStdAccess != null
				? explicitStdAccess.apply( typeConfiguration )
				: null;

		final JavaTypeDescriptor relationalJtd = converter.getRelationalJavaDescriptor();

		final SqlTypeDescriptor relationalStd = explicitStd != null
				? explicitStd
				: relationalJtd.getJdbcRecommendedSqlType( sqlTypeIndicators );

		final MutabilityPlan explicitMutabilityPlan = explicitMutabilityPlanAccess != null
				? explicitMutabilityPlanAccess.apply( typeConfiguration )
				: null;

		final MutabilityPlan mutabilityPlan = explicitMutabilityPlan != null
				? explicitMutabilityPlan
				: new AttributeConverterMutabilityPlanImpl( converter, true );
		return new NamedConverterResolution(
				domainJtd,
				relationalJtd,
				relationalStd,
				converter,
				mutabilityPlan
		);
	}


	private final JavaTypeDescriptor domainJtd;
	private final JavaTypeDescriptor relationalJtd;
	private final SqlTypeDescriptor relationalStd;

	private final JpaAttributeConverter valueConverter;
	private final MutabilityPlan mutabilityPlan;

	private final JdbcMapping jdbcMapping;

	private final BasicType legacyResolvedType;

	@SuppressWarnings("unchecked")
	public NamedConverterResolution(
			JavaTypeDescriptor domainJtd,
			JavaTypeDescriptor relationalJtd,
			SqlTypeDescriptor relationalStd,
			JpaAttributeConverter valueConverter,
			MutabilityPlan mutabilityPlan) {
		assert domainJtd != null;
		this.domainJtd = domainJtd;

		assert relationalJtd != null;
		this.relationalJtd = relationalJtd;

		assert relationalStd != null;
		this.relationalStd = relationalStd;

		assert valueConverter != null;
		this.valueConverter = valueConverter;

		assert mutabilityPlan != null;
		this.mutabilityPlan = mutabilityPlan;

		this.jdbcMapping = new JdbcMapping() {
			private final ValueExtractor extractor = relationalStd.getExtractor( relationalJtd );
			private final ValueBinder binder = relationalStd.getBinder( relationalJtd );

			@Override
			public JavaTypeDescriptor getJavaTypeDescriptor() {
				return relationalJtd;
			}

			@Override
			public SqlTypeDescriptor getSqlTypeDescriptor() {
				return relationalStd;
			}

			@Override
			public ValueExtractor getJdbcValueExtractor() {
				return extractor;
			}

			@Override
			public ValueBinder getJdbcValueBinder() {
				return binder;
			}
		};
//		this.jdbcMapping = new StandardBasicTypeImpl( relationalJtd, relationalStd );

		this.legacyResolvedType = new AttributeConverterTypeAdapter(
				ConverterDescriptor.TYPE_NAME_PREFIX + valueConverter.getConverterJavaTypeDescriptor().getJavaType().getTypeName(),
				String.format(
						"BasicType adapter for AttributeConverter<%s,%s>",
						domainJtd.getJavaType().getTypeName(),
						relationalJtd.getJavaType().getTypeName()
				),
				valueConverter,
				relationalStd,
				relationalJtd,
				domainJtd,
				mutabilityPlan
		);
	}

	@Override
	public BasicType<J> getLegacyResolvedBasicType() {
		//noinspection unchecked
		return legacyResolvedType;
	}

	@Override
	public JavaTypeDescriptor<J> getDomainJavaDescriptor() {
		//noinspection unchecked
		return domainJtd;
	}

	@Override
	public JavaTypeDescriptor<?> getRelationalJavaDescriptor() {
		return relationalJtd;
	}

	@Override
	public SqlTypeDescriptor getRelationalSqlTypeDescriptor() {
		return relationalStd;
	}

	@Override
	public JdbcMapping getJdbcMapping() {
		return jdbcMapping;
	}

	@Override
	public JpaAttributeConverter getValueConverter() {
		return valueConverter;
	}

	@Override
	public MutabilityPlan<J> getMutabilityPlan() {
		//noinspection unchecked
		return mutabilityPlan;
	}

	@Override
	public String toString() {
		return "NamedConverterResolution(" + valueConverter.getConverterBean().getBeanClass().getName() + ')';
	}
}
