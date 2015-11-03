/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.serialization;

import javax.naming.NamingException;

import org.junit.Test;

import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.SessionFactoryRegistry;
import org.hibernate.internal.util.SerializationHelper;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.testing.junit4.BaseUnitTestCase;
import org.hibernate.type.TypeFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * @author Gail Badner
 */
public class TypeFactorySerializationTest extends BaseUnitTestCase {
	private static String NAME = "test name";

	@Test
	public void testWithSameRegisteredSessionFactory() throws Exception {
		Configuration cfg = new Configuration()
				.setProperty( AvailableSettings.SESSION_FACTORY_NAME, NAME )
				.setProperty( AvailableSettings.SESSION_FACTORY_NAME_IS_JNDI, "false" ); // default is true
		SessionFactoryImplementor factory = (SessionFactoryImplementor) cfg.buildSessionFactory();

		// Session factory is registered.
		assertSame( factory, SessionFactoryRegistry.INSTANCE.getNamedSessionFactory( NAME ) );

		TypeFactory typeFactory = factory.getTypeResolver().getTypeFactory();
		byte[] typeFactoryBytes = SerializationHelper.serialize( typeFactory );
		typeFactory = (TypeFactory) SerializationHelper.deserialize( typeFactoryBytes );

		assertSame( factory, typeFactory.resolveSessionFactory() );
		factory.close();
	}

	@Test
	public void testUnregisterSerializeRegisterSameSessionFactory() throws Exception {
		Configuration cfg = new Configuration()
				.setProperty( AvailableSettings.SESSION_FACTORY_NAME, NAME )
				.setProperty( AvailableSettings.SESSION_FACTORY_NAME_IS_JNDI, "false" ); // default is true
		SessionFactoryImplementor factory = (SessionFactoryImplementor) cfg.buildSessionFactory();
		assertSame( factory, SessionFactoryRegistry.INSTANCE.getNamedSessionFactory( NAME ) );

		// Remove the session factory from the registry
		SessionFactoryRegistry.INSTANCE.removeSessionFactory( getSessionFactoryUuid( factory ), NAME, false, null );
		assertNull( findSessionFactory( getSessionFactoryUuid( factory ), NAME ) );

		TypeFactory typeFactory = factory.getTypeResolver().getTypeFactory();
		byte[] typeFactoryBytes = SerializationHelper.serialize( typeFactory );
		typeFactory = (TypeFactory) SerializationHelper.deserialize( typeFactoryBytes );

		try {
			typeFactory.resolveSessionFactory();
			fail( "should have failed with HibernateException because session factory is not registered." );
		}
		catch ( HibernateException ex ) {
			// expected because the session factory is not registered.
		}

		// Re-register the same session factory.
		SessionFactoryRegistry.INSTANCE.addSessionFactory( getSessionFactoryUuid( factory ), NAME, false, factory, null );

		// Session factory resolved from typeFactory should be the new session factory
		// (because it is resolved from SessionFactoryRegistry.INSTANCE)
		assertSame( factory, typeFactory.resolveSessionFactory() );
		factory.close();
	}

	@Test
	public void testUnregisterSerializeRegisterSameSessionFactoryNoName() throws Exception {
		Configuration cfg = new Configuration();
		SessionFactoryImplementor factory = (SessionFactoryImplementor) cfg.buildSessionFactory();
		assertSame( factory, findSessionFactory( getSessionFactoryUuid( factory ), null ) );

		// Remove the session factory from the registry
		SessionFactoryRegistry.INSTANCE.removeSessionFactory( getSessionFactoryUuid( factory ), null, false, null );
		assertNull( findSessionFactory( getSessionFactoryUuid( factory ), null ) );

		TypeFactory typeFactory = factory.getTypeResolver().getTypeFactory();
		byte[] typeFactoryBytes = SerializationHelper.serialize( typeFactory );
		typeFactory = (TypeFactory) SerializationHelper.deserialize( typeFactoryBytes );

		try {
			typeFactory.resolveSessionFactory();
			fail( "should have failed with HibernateException because session factory is not registered." );
		}
		catch ( HibernateException ex ) {
			// expected because the session factory is not registered.
		}

		// Re-register the same session factory.
		SessionFactoryRegistry.INSTANCE.addSessionFactory( getSessionFactoryUuid( factory), null, false, factory, null );

		// Session factory resolved from typeFactory should be the new session factory
		// (because it is resolved from SessionFactoryRegistry.INSTANCE)
		assertSame( factory, typeFactory.resolveSessionFactory() );
		factory.close();
	}

	@Test
	public void testUnregisterSerializeRegisterDiffSessionFactory() throws Exception {
		Configuration cfg = new Configuration()
				.setProperty( AvailableSettings.SESSION_FACTORY_NAME, NAME )
				.setProperty( AvailableSettings.SESSION_FACTORY_NAME_IS_JNDI, "false" ); // default is true
		SessionFactoryImplementor factory = (SessionFactoryImplementor) cfg.buildSessionFactory();
		assertSame( factory, SessionFactoryRegistry.INSTANCE.getNamedSessionFactory( NAME ) );

		// Remove the session factory from the registry
		SessionFactoryRegistry.INSTANCE.removeSessionFactory( getSessionFactoryUuid( factory ), NAME, false, null );
		assertNull( findSessionFactory( getSessionFactoryUuid( factory ), NAME ) );

		TypeFactory typeFactory = factory.getTypeResolver().getTypeFactory();
		byte[] typeFactoryBytes = SerializationHelper.serialize( typeFactory );
		typeFactory = (TypeFactory) SerializationHelper.deserialize( typeFactoryBytes );

		try {
			typeFactory.resolveSessionFactory();
			fail( "should have failed with HibernateException because session factory is not registered." );
		}
		catch ( HibernateException ex ) {
			// expected because the session factory is not registered.
		}

		// Now create a new session factory with the same name; it will have a different UUID.
		SessionFactoryImplementor factoryWithSameName = (SessionFactoryImplementor) cfg.buildSessionFactory();
		assertSame( factoryWithSameName, SessionFactoryRegistry.INSTANCE.getNamedSessionFactory( NAME ) );
		assertFalse( getSessionFactoryUuid( factory ).equals( getSessionFactoryUuid( factoryWithSameName ) ) );

		// Session factory resolved from typeFactory should be the new session factory
		// (because it is resolved from SessionFactoryRegistry.INSTANCE)
		assertSame( factoryWithSameName, typeFactory.resolveSessionFactory() );

		factory.close();
		factoryWithSameName.close();
	}

	@Test
	public void testUnregisterSerializeRegisterDiffSessionFactoryNoName() throws Exception {
		Configuration cfg = new Configuration();
		SessionFactoryImplementor factory = (SessionFactoryImplementor) cfg.buildSessionFactory();
		assertSame( factory, SessionFactoryRegistry.INSTANCE.getSessionFactory( getSessionFactoryUuid( factory ) ) );

		// Remove the session factory from the registry
		SessionFactoryRegistry.INSTANCE.removeSessionFactory( getSessionFactoryUuid( factory ), null, false, null );
		assertNull( SessionFactoryRegistry.INSTANCE.getSessionFactory( getSessionFactoryUuid( factory ) ) );

		TypeFactory typeFactory = factory.getTypeResolver().getTypeFactory();
		byte[] typeFactoryBytes = SerializationHelper.serialize( typeFactory );
		typeFactory = (TypeFactory) SerializationHelper.deserialize( typeFactoryBytes );

		try {
			typeFactory.resolveSessionFactory();
			fail( "should have failed with HibernateException because session factory is not registered." );
		}
		catch ( HibernateException ex ) {
			// expected because the session factory is not registered.
		}

		// Now create a new session factory with the same name; it will have a different UUID.
		SessionFactoryImplementor factoryWithDiffUuid = (SessionFactoryImplementor) cfg.buildSessionFactory();
		assertSame( factoryWithDiffUuid, SessionFactoryRegistry.INSTANCE.getSessionFactory( getSessionFactoryUuid( factoryWithDiffUuid ) ) );
		assertFalse( getSessionFactoryUuid( factory ).equals( getSessionFactoryUuid( factoryWithDiffUuid ) ) );

		// It should not be possible to resolve the session factory with no name configured.
		try {
			typeFactory.resolveSessionFactory();
			fail( "should have failed with HibernateException because session factories were not registered with the same non-null name." );
		}
		catch ( HibernateException ex ) {
			// expected
		}

		factory.close();
		factoryWithDiffUuid.close();
	}

	private static String getSessionFactoryUuid(SessionFactory sessionFactory) throws NamingException {
		try {
			return (String) sessionFactory.getReference().get( "uuid" ).getContent() ;
		}
		catch (NamingException ex) {
			throw new HibernateException(
					"Could not determine UUID.",
					ex
			);
		}
	}

	private static SessionFactory findSessionFactory(String uuid, String name ) {
		SessionFactory sessionFactory = SessionFactoryRegistry.INSTANCE.getSessionFactory( uuid );
		if ( sessionFactory == null && StringHelper.isNotEmpty( name ) ) {
			sessionFactory = SessionFactoryRegistry.INSTANCE.getNamedSessionFactory( name );
		}
		return sessionFactory;

	}
}
