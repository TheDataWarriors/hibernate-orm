/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.bytecode.enhancement.lazy.proxy.inlinedirtychecking;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.stat.Statistics;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.bytecode.enhancement.BytecodeEnhancerRunner;
import org.hibernate.testing.bytecode.enhancement.CustomEnhancementContext;
import org.hibernate.testing.junit4.BaseNonConfigCoreFunctionalTestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Andrea Boriero
 */
@RunWith(BytecodeEnhancerRunner.class)
@CustomEnhancementContext({ DirtyCheckEnhancementContext.class, NoDirtyCheckEnhancementContext.class })
@TestForIssue(jiraKey = "HHH-13705")
public class ManyToOneWithEmbeddedAndNotOptionalFieldTest extends BaseNonConfigCoreFunctionalTestCase {

	@Override
	protected void configureStandardServiceRegistryBuilder(StandardServiceRegistryBuilder ssrb) {
		super.configureStandardServiceRegistryBuilder( ssrb );
		ssrb.applySetting( AvailableSettings.FORMAT_SQL, "false" );
		ssrb.applySetting( AvailableSettings.GENERATE_STATISTICS, "true" );
	}

	@Override
	protected void configureSessionFactoryBuilder(SessionFactoryBuilder sfb) {
		super.configureSessionFactoryBuilder( sfb );
		sfb.applyStatisticsSupport( true );
		sfb.applySecondLevelCacheSupport( false );
		sfb.applyQueryCacheSupport( false );
	}

	@Override
	protected void applyMetadataSources(MetadataSources sources) {
		super.applyMetadataSources( sources );
		sources.addAnnotatedClass( Client.class );
		sources.addAnnotatedClass( User.class );
	}

	private Long userId;

	@Before
	public void setUp() {
		User user = new User();
		inTransaction(
				session -> {

					Log log = new Log();
					log.setCreationDate( OffsetDateTime.now() );

					Client client = new Client();
					client.setName( "And" );
					client.setLog( log );


					session.save( client );

					user.setName( "Fab" );

					user.setClient( client );

					session.save( user );
				}
		);
		userId = user.getId();
	}


	@Test
	public void load() {
		final Statistics stats = sessionFactory().getStatistics();
		stats.clear();
		inTransaction(
				session -> {
					session.find( User.class, userId );
				}
		);
		assertThat( stats.getPrepareStatementCount(), is( 1L ) );
	}

	@Entity(name = "Client")
	public static class Client {

		@Id
		@GeneratedValue
		private Long id;

		private String name;

		@Embedded
		private Log log = new Log();

		public Long getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public Log getLog() {
			return log;
		}

		public void setLog(Log log) {
			this.log = log;
		}
	}

	@Entity(name = "User")
	@Table(name = "`User`")
	public static class User {
		@Id
		@GeneratedValue
		private Long id;

		@Column(length = 120, nullable = false)
		private String name;

		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "`idClient`")
		private Client client;

		public Long getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Client getClient() {
			return client;
		}

		public void setClient(Client client) {
			this.client = client;
		}
	}

	@Embeddable
	public static class Log {
		@Column(name = "`creationDate`", nullable = false)
		private OffsetDateTime creationDate;

		public OffsetDateTime getCreationDate() {
			return creationDate;
		}

		public void setCreationDate(OffsetDateTime creationDate) {
			this.creationDate = creationDate;
		}
	}
}
