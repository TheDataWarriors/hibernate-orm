/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.mapping.basic;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import org.hibernate.annotations.JavaType;
import org.hibernate.annotations.Nationalized;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.NationalizationSupport;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.internal.BasicAttributeMapping;
import org.hibernate.metamodel.spi.MappingMetamodelImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.java.CharacterArrayJavaType;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;

/**
 * @see CharacterArrayMappingTests
 *
 * @author Steve Ebersole
 */
@DomainModel(annotatedClasses = CharacterArrayNationalizedMappingTests.EntityWithCharArrays.class)
@SessionFactory
@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsNationalizedData.class)
@ServiceRegistry(
		settings = @Setting(name = AvailableSettings.WRAPPER_ARRAY_HANDLING, value = "ALLOW")
)
public class CharacterArrayNationalizedMappingTests {
	@Test
	public void verifyMappings(SessionFactoryScope scope) {
		final MappingMetamodelImplementor mappingMetamodel = scope.getSessionFactory()
				.getRuntimeMetamodels()
				.getMappingMetamodel();
		final EntityPersister entityDescriptor = mappingMetamodel.getEntityDescriptor(EntityWithCharArrays.class);
		final JdbcTypeRegistry jdbcTypeRegistry = mappingMetamodel.getTypeConfiguration().getJdbcTypeRegistry();

		final Dialect dialect = scope.getSessionFactory().getJdbcServices().getDialect();
		final NationalizationSupport nationalizationSupport = dialect.getNationalizationSupport();

		{
			final BasicAttributeMapping attributeMapping = (BasicAttributeMapping) entityDescriptor.findAttributeMapping("primitiveNVarchar");
			final JdbcMapping jdbcMapping = attributeMapping.getJdbcMapping();
			assertThat( jdbcMapping.getJdbcType(), is( jdbcTypeRegistry.getDescriptor( nationalizationSupport.getVarcharVariantCode())));
		}

		{
			final BasicAttributeMapping attributeMapping = (BasicAttributeMapping) entityDescriptor.findAttributeMapping("wrapperNVarchar");
			final JdbcMapping jdbcMapping = attributeMapping.getJdbcMapping();
			if ( dialect.supportsStandardArrays() ) {
				assertThat( jdbcMapping.getJdbcType(), instanceOf( ArrayJdbcType.class ) );
				assertThat(
						( (ArrayJdbcType) jdbcMapping.getJdbcType() ).getElementJdbcType(),
						is( jdbcTypeRegistry.getDescriptor( nationalizationSupport.getCharVariantCode() ) )
				);
			}
			else {
				assertThat(
						jdbcMapping.getJdbcType().getJdbcTypeCode(),
						isOneOf( SqlTypes.ARRAY, SqlTypes.SQLXML, SqlTypes.VARBINARY )
				);
			}
		}

		{
			final BasicAttributeMapping attributeMapping = (BasicAttributeMapping) entityDescriptor.findAttributeMapping("wrapperNVarcharOld");
			final JdbcMapping jdbcMapping = attributeMapping.getJdbcMapping();
			assertThat( jdbcMapping.getJdbcType(), is( jdbcTypeRegistry.getDescriptor( nationalizationSupport.getVarcharVariantCode())));
		}


		{
			final BasicAttributeMapping attributeMapping = (BasicAttributeMapping) entityDescriptor.findAttributeMapping("primitiveNClob");
			final JdbcMapping jdbcMapping = attributeMapping.getJdbcMapping();
			assertThat( jdbcMapping.getJdbcType(), is( jdbcTypeRegistry.getDescriptor( nationalizationSupport.getClobVariantCode())));
		}

		{
			final BasicAttributeMapping attributeMapping = (BasicAttributeMapping) entityDescriptor.findAttributeMapping("wrapperNClob");
			final JdbcMapping jdbcMapping = attributeMapping.getJdbcMapping();
			assertThat( jdbcMapping.getJdbcType(), is( jdbcTypeRegistry.getDescriptor( nationalizationSupport.getClobVariantCode())));
		}
	}

	@Entity(name = "EntityWithCharArrays")
	@Table(name = "EntityWithCharArrays")
	public static class EntityWithCharArrays {
		@Id
		public Integer id;

		//tag::basic-nchararray-example[]
		// mapped as NVARCHAR
		@Nationalized
		char[] primitiveNVarchar;
		@Nationalized
		Character[] wrapperNVarchar;
		@Nationalized
		@JavaType( CharacterArrayJavaType.class )
		Character[] wrapperNVarcharOld;

		// mapped as NCLOB
		@Lob
		@Nationalized
		char[] primitiveNClob;
		@Lob
		@Nationalized
		Character[] wrapperNClob;
		//end::basic-nchararray-example[]
	}
}
