/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.schemavalidation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.tool.hbm2ddl.SchemaValidator;
import org.hibernate.tool.schema.JdbcMetadaAccessStrategy;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.BaseSessionFactoryFunctionalTest;
import org.hibernate.testing.orm.junit.RequiresDialect;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Allows the BaseCoreFunctionalTestCase to create the schema using TestEntity.  The test method validates against an
 * identical entity, but using the synonym name.
 *
 * When SYNONYM are used, the GROUPED Strategy cannot be applied because when the tableNamePattern was not provided
 * java.sql.DatabaseMetaData#getColumns(...) Oracle implementation returns only the columns related with the synonym
 *
 * @author Brett Meyer
 */
@RequiresDialect(value = OracleDialect.class, version = 900)
public class SynonymValidationTest extends BaseSessionFactoryFunctionalTest {

	private StandardServiceRegistry ssr;

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] {TestEntity.class};
	}

	@BeforeAll
	public void setUp() {
		inTransaction(
				session -> session.createNativeQuery( "CREATE SYNONYM test_synonym FOR test_entity" ).executeUpdate()
		);
	}

	@AfterAll
	public void tearDown() {
		inTransaction(
				session -> session.createNativeQuery( "DROP SYNONYM test_synonym FORCE" ).executeUpdate()
		);
	}

	@Test
	public void testSynonymUsingIndividuallySchemaValidator() {
		ssr = new StandardServiceRegistryBuilder()
				.applySetting( AvailableSettings.ENABLE_SYNONYMS, "true" )
				.applySetting(
						AvailableSettings.HBM2DDL_JDBC_METADATA_EXTRACTOR_STRATEGY,
						JdbcMetadaAccessStrategy.INDIVIDUALLY
				)
				.build();
		try {
			final MetadataSources metadataSources = new MetadataSources( ssr );
			metadataSources.addAnnotatedClass( TestEntityWithSynonym.class );
			metadataSources.addAnnotatedClass( TestEntity.class );

			new SchemaValidator().validate( metadataSources.buildMetadata() );
		}
		finally {
			StandardServiceRegistryBuilder.destroy( ssr );
		}
	}

	@Test
	@TestForIssue( jiraKey = "HHH-12406")
	public void testSynonymUsingDefaultStrategySchemaValidator() {
		// Hibernate should use JdbcMetadaAccessStrategy.INDIVIDUALLY when
		// AvailableSettings.ENABLE_SYNONYMS is true.
		ssr = new StandardServiceRegistryBuilder()
				.applySetting( AvailableSettings.ENABLE_SYNONYMS, "true" )
				.build();
		try {
			final MetadataSources metadataSources = new MetadataSources( ssr );
			metadataSources.addAnnotatedClass( TestEntityWithSynonym.class );
			metadataSources.addAnnotatedClass( TestEntity.class );

			new SchemaValidator().validate( metadataSources.buildMetadata() );
		}
		finally {
			StandardServiceRegistryBuilder.destroy( ssr );
		}
	}

	@Test
	@TestForIssue( jiraKey = "HHH-12406")
	public void testSynonymUsingGroupedSchemaValidator() {
		// Hibernate should use JdbcMetadaAccessStrategy.INDIVIDUALLY when
		// AvailableSettings.ENABLE_SYNONYMS is true,
		// even if JdbcMetadaAccessStrategy.GROUPED is specified.
		ssr = new StandardServiceRegistryBuilder()
				.applySetting( AvailableSettings.ENABLE_SYNONYMS, "true" )
				.applySetting(
						AvailableSettings.HBM2DDL_JDBC_METADATA_EXTRACTOR_STRATEGY,
						JdbcMetadaAccessStrategy.GROUPED
				)
				.build();
		try {
			final MetadataSources metadataSources = new MetadataSources( ssr );
			metadataSources.addAnnotatedClass( TestEntityWithSynonym.class );
			metadataSources.addAnnotatedClass( TestEntity.class );

			new SchemaValidator().validate( metadataSources.buildMetadata() );
		}
		finally {
			StandardServiceRegistryBuilder.destroy( ssr );
		}
	}

	@Entity
	@Table(name = "test_entity")
	private static class TestEntity {
		@Id
		@GeneratedValue
		private Long id;

		@Column(nullable = false)
		private String key;

		private String value;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}

	@Entity
	@Table(name = "test_synonym")
	private static class TestEntityWithSynonym {
		@Id
		@GeneratedValue
		private Long id;

		@Column(nullable = false)
		private String key;

		private String value;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	}
}
