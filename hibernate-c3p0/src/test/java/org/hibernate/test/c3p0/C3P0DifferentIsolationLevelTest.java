package org.hibernate.test.c3p0;

import java.sql.Connection;
import java.sql.SQLException;
import javax.persistence.Entity;
import javax.persistence.Id;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.H2Dialect;

import org.hibernate.testing.jdbc.SQLStatementInterceptor;
import org.hibernate.testing.junit5.DatabaseAgnostic;
import org.hibernate.testing.junit5.JiraKey;
import org.hibernate.testing.junit5.RequiresDialect;
import org.hibernate.testing.junit5.SessionFactoryBasedFunctionalTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hibernate.testing.transaction.TransactionUtil.doInHibernate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Vlad Mihalcea
 */
@JiraKey( "HHH-12749" )
@DatabaseAgnostic
@RequiresDialect( H2Dialect.class )
public class C3P0DifferentIsolationLevelTest extends SessionFactoryBasedFunctionalTest {

	private final SQLStatementInterceptor sqlStatementInterceptor = new SQLStatementInterceptor();

	private C3P0ProxyConnectionProvider connectionProvider;

	@Override
	protected void applySettings(StandardServiceRegistryBuilder builder) {
		super.applySettings( builder );

		connectionProvider = new C3P0ProxyConnectionProvider();

		builder.applySetting( AvailableSettings.STATEMENT_INSPECTOR, sqlStatementInterceptor );
		builder.applySetting( AvailableSettings.CONNECTION_PROVIDER, connectionProvider );
		builder.applySetting( AvailableSettings.ISOLATION, "REPEATABLE_READ" );
	}

	@Override
	protected void configure(SessionFactoryBuilder builder) {
		super.configure( builder );

		builder.applyStatementInspector( sqlStatementInterceptor );
	}

	@Override
	protected void applyMetadataSources(MetadataSources metadataSources) {
		super.applyMetadataSources( metadataSources );

		metadataSources.addAnnotatedClass( Person.class );
	}

	@Override
	protected boolean exportSchema() {
		return true;
	}

	@Test
	public void testStoredProcedureOutParameter() throws SQLException {
		sessionFactoryScope().inTransaction(
				session -> {
					Person person = new Person();
					person.id = 1L;
					person.name = "Vlad Mihalcea";
					session.persist( person );
				}
		);

		assertEquals( 1, sqlStatementInterceptor.getSqlQueries().size() );
		assertTrue( sqlStatementInterceptor.getSqlQueries().get( 0 ).toLowerCase().startsWith( "insert into" ) );
		Connection connectionSpy = connectionProvider.getConnectionSpyMap().keySet().iterator().next();
		verify( connectionSpy, times(1) ).setTransactionIsolation( Connection.TRANSACTION_REPEATABLE_READ );

		clearSpies();

		doInHibernate( this::sessionFactory, session -> {
			Person person = session.find( Person.class, 1L );

			assertEquals( "Vlad Mihalcea", person.name );
		} );

		assertEquals( 1, sqlStatementInterceptor.getSqlQueries().size() );
		assertTrue( sqlStatementInterceptor.getSqlQueries().get( 0 ).toLowerCase().startsWith( "select" ) );
		connectionSpy = connectionProvider.getConnectionSpyMap().keySet().iterator().next();
		verify( connectionSpy, times(1) ).setTransactionIsolation( Connection.TRANSACTION_REPEATABLE_READ );
	}

	@BeforeEach
	private void clearSpies() {
		sqlStatementInterceptor.getSqlQueries().clear();
		connectionProvider.clear();
	}

	@Entity(name = "Person")
	public static class Person {

		@Id
		private Long id;

		private String name;
	}

}
