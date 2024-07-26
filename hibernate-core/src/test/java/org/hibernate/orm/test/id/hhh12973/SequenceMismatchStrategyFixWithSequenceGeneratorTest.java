/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.id.hhh12973;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;

import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.EntityManagerFactoryBasedFunctionalTest;
import org.hibernate.testing.logger.LoggerInspectionRule;
import org.hibernate.testing.logger.Triggerable;
import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.util.ServiceRegistryUtil;
import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import org.jboss.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Vlad Mihalcea
 */
@JiraKey(value = "HHH-12973")
@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsSequences.class)
public class SequenceMismatchStrategyFixWithSequenceGeneratorTest extends EntityManagerFactoryBasedFunctionalTest {

	@Rule
	public LoggerInspectionRule logInspection = new LoggerInspectionRule(
			Logger.getMessageLogger(
					CoreMessageLogger.class,
					SequenceStyleGenerator.class.getName()
			)
	);

	private Triggerable triggerable = logInspection.watchForLogMessages( "HHH000497:" );

	protected ServiceRegistry serviceRegistry;
	protected MetadataImplementor metadata;

	@Override
	public EntityManagerFactory produceEntityManagerFactory() {
		serviceRegistry = ServiceRegistryUtil.serviceRegistry();
		metadata = (MetadataImplementor) new MetadataSources( serviceRegistry )
				.addAnnotatedClass( ApplicationConfigurationHBM2DDL.class )
				.buildMetadata();

		new SchemaExport().create( EnumSet.of( TargetType.DATABASE ), metadata );
		return super.produceEntityManagerFactory();
	}

	@AfterAll
	public void releaseResources() {
		if ( metadata != null ) {
			new SchemaExport().drop( EnumSet.of( TargetType.DATABASE ), metadata );
		}
		if ( serviceRegistry != null ) {
			StandardServiceRegistryBuilder.destroy( serviceRegistry );
		}
	}

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] {
				ApplicationConfiguration.class,
		};
	}

	@Override
	protected void addConfigOptions(Map options) {
		options.put( AvailableSettings.HBM2DDL_AUTO, "none" );
		options.put( AvailableSettings.SEQUENCE_INCREMENT_SIZE_MISMATCH_STRATEGY, "fix" );
		triggerable.reset();
	}

	@Override
	protected void entityManagerFactoryBuilt(EntityManagerFactory factory) {
		assertTrue( triggerable.wasTriggered() );
	}

	@Override
	protected boolean exportSchema() {
		return false;
	}

	@Test
	public void test() {

		final AtomicLong id = new AtomicLong();

		final int ITERATIONS = 51;

		inTransaction( entityManager -> {
			for ( int i = 1; i <= ITERATIONS; i++ ) {
				ApplicationConfiguration model = new ApplicationConfiguration();

				entityManager.persist( model );

				id.set( model.getId() );
			}
		} );

		assertEquals( ITERATIONS, id.get() );
	}

	@Entity
	@Table(name = "application_configurations")
	public static class ApplicationConfigurationHBM2DDL {

		@Id
		@jakarta.persistence.SequenceGenerator(
				name = "app_config_sequence",
				sequenceName = "app_config_sequence",
				allocationSize = 1
		)
		@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_config_sequence")
		private Long id;

		public Long getId() {
			return id;
		}

		public void setId(final Long id) {
			this.id = id;
		}
	}

	@Entity
	@Table(name = "application_configurations")
	public static class ApplicationConfiguration {

		@Id
		@jakarta.persistence.SequenceGenerator(
				name = "app_config_sequence",
				sequenceName = "app_config_sequence"
		)
		@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_config_sequence")
		private Long id;

		public Long getId() {
			return id;
		}

		public void setId(final Long id) {
			this.id = id;
		}
	}
}
