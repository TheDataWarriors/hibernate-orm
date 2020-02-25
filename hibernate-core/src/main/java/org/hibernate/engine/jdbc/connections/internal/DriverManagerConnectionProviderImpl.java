/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.engine.jdbc.connections.internal;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.hibernate.HibernateException;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Database;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.spi.Configurable;
import org.hibernate.service.spi.ServiceException;
import org.hibernate.service.spi.ServiceRegistryAwareService;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.service.spi.Stoppable;

import static org.hibernate.internal.log.ConnectionPoolingLogger.CONNECTIONS_LOGGER;

/**
 * A connection provider that uses the {@link java.sql.DriverManager} directly to open connections and provides
 * a very rudimentary connection pool.
 * <p/>
 * IMPL NOTE : not intended for production use!
 * <p/>
 * Thanks to Oleg Varaksin and his article on object pooling using the {@link java.util.concurrent} package, from
 * which much of the pooling code here is derived.  See http://ovaraksin.blogspot.com/2013/08/simple-and-lightweight-pool.html
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public class DriverManagerConnectionProviderImpl
		implements ConnectionProvider, Configurable, Stoppable, ServiceRegistryAwareService {

	public static final String MIN_SIZE = "hibernate.connection.min_pool_size";
	public static final String INITIAL_SIZE = "hibernate.connection.initial_pool_size";
	// in TimeUnit.SECONDS
	public static final String VALIDATION_INTERVAL = "hibernate.connection.pool_validation_interval";

	private volatile PoolState state;

	// create the pool ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	private volatile ServiceRegistryImplementor serviceRegistry;

	@Override
	public void injectServices(ServiceRegistryImplementor serviceRegistry) {
		this.serviceRegistry = serviceRegistry;
	}

	@Override
	public void configure(Map configurationValues) {
		CONNECTIONS_LOGGER.usingHibernateBuiltInConnectionPool();
		PooledConnections pool = buildPool( configurationValues, serviceRegistry );
		final long validationInterval = ConfigurationHelper.getLong( VALIDATION_INTERVAL, configurationValues, 30 );
		PoolState newstate = new PoolState( pool, validationInterval );
		this.state = newstate;
	}

	private PooledConnections buildPool(Map configurationValues, ServiceRegistryImplementor serviceRegistry) {
		final boolean autoCommit = ConfigurationHelper.getBoolean(
				AvailableSettings.AUTOCOMMIT,
				configurationValues,
				false
		);
		final int minSize = ConfigurationHelper.getInt( MIN_SIZE, configurationValues, 1 );
		final int maxSize = ConfigurationHelper.getInt( AvailableSettings.POOL_SIZE, configurationValues, 20 );
		final int initialSize = ConfigurationHelper.getInt( INITIAL_SIZE, configurationValues, minSize );

		ConnectionCreator connectionCreator = buildCreator( configurationValues, serviceRegistry );
		PooledConnections.Builder pooledConnectionBuilder = new PooledConnections.Builder(
				connectionCreator,
				autoCommit
		);
		pooledConnectionBuilder.initialSize( initialSize );
		pooledConnectionBuilder.minSize( minSize );
		pooledConnectionBuilder.maxSize( maxSize );

		return pooledConnectionBuilder.build();
	}

	private static ConnectionCreator buildCreator(Map configurationValues, ServiceRegistryImplementor serviceRegistry) {
		final ConnectionCreatorBuilder connectionCreatorBuilder = new ConnectionCreatorBuilder( serviceRegistry );

		final String url = (String) configurationValues.get( AvailableSettings.URL );

		String driverClassName = (String) configurationValues.get( AvailableSettings.DRIVER );
		boolean success = false;
		if ( driverClassName != null ) {
			connectionCreatorBuilder.setDriver( loadDriverIfPossible( driverClassName, serviceRegistry ) );
			success = true;
		}
		else if ( url != null ) {
			//try to guess the driver class from the JDBC URL
			for ( Database database: Database.values() ) {
				if ( database.matchesUrl( url ) ) {
					driverClassName = database.getDriverClassName( url );
					if ( driverClassName != null ) {
						try {
							connectionCreatorBuilder.setDriver( loadDriverIfPossible(driverClassName, serviceRegistry) );
							success = true;
						}
						catch (Exception e) {
							//swallow it, since this was not
							//an explicit setting by the user
						}
						break;
					}
				}
			}
		}

		if ( success ) {
			CONNECTIONS_LOGGER.loadedDriver( driverClassName );
		}
		else {
			//we're hoping that the driver is already loaded
			CONNECTIONS_LOGGER.noDriver( AvailableSettings.DRIVER );
			StringBuilder list = new StringBuilder();
			Enumeration<Driver> drivers = DriverManager.getDrivers();
			while ( drivers.hasMoreElements() ) {
				if ( list.length() != 0) {
					list.append(", ");
				}
				list.append( drivers.nextElement().getClass().getName() );
			}
			CONNECTIONS_LOGGER.loadedDrivers( list.toString() );
		}

		if ( url == null ) {
			final String msg = CONNECTIONS_LOGGER.jdbcUrlNotSpecified( AvailableSettings.URL );
			CONNECTIONS_LOGGER.error( msg );
			throw new HibernateException( msg );
		}
		connectionCreatorBuilder.setUrl( url );

		CONNECTIONS_LOGGER.usingUrl( url );

		final Properties connectionProps = ConnectionProviderInitiator.getConnectionProperties( configurationValues );

		// if debug level is enabled, then log the password, otherwise mask it
		if ( CONNECTIONS_LOGGER.isDebugEnabled() ) {
			CONNECTIONS_LOGGER.connectionProperties( connectionProps );
		}
		else {
			CONNECTIONS_LOGGER.connectionProperties( ConfigurationHelper.maskOut( connectionProps, "password" ) );
		}
		connectionCreatorBuilder.setConnectionProps( connectionProps );

		final boolean autoCommit = ConfigurationHelper.getBoolean( AvailableSettings.AUTOCOMMIT, configurationValues, false );
		CONNECTIONS_LOGGER.autoCommitMode( autoCommit );
		connectionCreatorBuilder.setAutoCommit( autoCommit );

		final Integer isolation = ConnectionProviderInitiator.extractIsolation( configurationValues );
		if ( isolation != null ) {
			CONNECTIONS_LOGGER.jdbcIsolationLevel( ConnectionProviderInitiator.toIsolationNiceName( isolation ) );
		}
		connectionCreatorBuilder.setIsolation( isolation );

		return connectionCreatorBuilder.build();
	}

	private static Driver loadDriverIfPossible(String driverClassName, ServiceRegistryImplementor serviceRegistry) {
		if ( driverClassName == null ) {
			CONNECTIONS_LOGGER.debug( "No driver class specified" );
			return null;
		}

		if ( serviceRegistry != null ) {
			final ClassLoaderService classLoaderService = serviceRegistry.getService( ClassLoaderService.class );
			final Class<Driver> driverClass = classLoaderService.classForName( driverClassName );
			try {
				return driverClass.newInstance();
			}
			catch ( Exception e ) {
				throw new ServiceException( "Specified JDBC Driver " + driverClassName + " could not be loaded", e );
			}
		}

		try {
			return (Driver) Class.forName( driverClassName ).newInstance();
		}
		catch ( Exception e1 ) {
			throw new ServiceException( "Specified JDBC Driver " + driverClassName + " could not be loaded", e1 );
		}
	}


	// use the pool ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public Connection getConnection() throws SQLException {
		return state.getConnection();
	}

	@Override
	public void closeConnection(Connection conn) throws SQLException {
		state.closeConnection( conn );
	}

	@Override
	public boolean supportsAggressiveRelease() {
		return false;
	}

	@Override
	public boolean isUnwrappableAs(Class unwrapType) {
		return ConnectionProvider.class.equals( unwrapType ) ||
				DriverManagerConnectionProviderImpl.class.isAssignableFrom( unwrapType );
	}

	@Override
	@SuppressWarnings( {"unchecked"})
	public <T> T unwrap(Class<T> unwrapType) {
		if ( ConnectionProvider.class.equals( unwrapType ) ||
				DriverManagerConnectionProviderImpl.class.isAssignableFrom( unwrapType ) ) {
			return (T) this;
		}
		else {
			throw new UnknownUnwrapTypeException( unwrapType );
		}
	}


	// destroy the pool ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public void stop() {
		state.stop();
	}

	//CHECKSTYLE:START_ALLOW_FINALIZER
	@Override
	protected void finalize() throws Throwable {
		state.stop();
		super.finalize();
	}
	//CHECKSTYLE:END_ALLOW_FINALIZER

	/**
	 * Exposed to facilitate testing only.
	 */
	public Properties getConnectionProperties() {
		BasicConnectionCreator connectionCreator = (BasicConnectionCreator) this.state.pool.connectionCreator;
		return connectionCreator.getConnectionProperties();
	}


	public static class PooledConnections {

		private final ConcurrentLinkedQueue<Connection> allConnections = new ConcurrentLinkedQueue<>();
		private final ConcurrentLinkedQueue<Connection> availableConnections = new ConcurrentLinkedQueue<>();

		private final ConnectionCreator connectionCreator;
		private final boolean autoCommit;
		private final int minSize;
		private final int maxSize;

		private volatile boolean primed;

		private PooledConnections(
				Builder builder) {
			CONNECTIONS_LOGGER.debugf( "Initializing Connection pool with %s Connections", builder.initialSize );
			connectionCreator = builder.connectionCreator;
			autoCommit = builder.autoCommit;
			maxSize = builder.maxSize;
			minSize = builder.minSize;
			CONNECTIONS_LOGGER.hibernateConnectionPoolSize( maxSize, minSize );
			addConnections( builder.initialSize );
		}

		public void validate() {
			final int size = size();

			if ( !primed && size >= minSize ) {
				// IMPL NOTE : the purpose of primed is to allow the pool to lazily reach its
				// defined min-size.
				CONNECTIONS_LOGGER.debug( "Connection pool now considered primed; min-size will be maintained" );
				primed = true;
			}

			if ( size < minSize && primed ) {
				int numberToBeAdded = minSize - size;
				CONNECTIONS_LOGGER.debugf( "Adding %s Connections to the pool", numberToBeAdded );
				addConnections( numberToBeAdded );
			}
			else if ( size > maxSize ) {
				int numberToBeRemoved = size - maxSize;
				CONNECTIONS_LOGGER.debugf( "Removing %s Connections from the pool", numberToBeRemoved );
				removeConnections( numberToBeRemoved );
			}
		}

		public void add(Connection conn) throws SQLException {
			conn.setAutoCommit( true );
			conn.clearWarnings();
			availableConnections.offer( conn );
		}

		public Connection poll() throws SQLException {
			Connection conn = availableConnections.poll();
			if ( conn == null ) {
				synchronized (allConnections) {
					if(allConnections.size() < maxSize) {
						addConnections( 1 );
						return poll();
					}
				}
				throw new HibernateException( "The internal connection pool has reached its maximum size and no connection is currently available!" );
			}
			conn.setAutoCommit( autoCommit );
			return conn;
		}

		public void close() throws SQLException {
			try {
				int allocationCount = allConnections.size() - availableConnections.size();
				if(allocationCount > 0) {
					CONNECTIONS_LOGGER.error( "Connection leak detected: there are " + allocationCount + " unclosed connections upon shutting down pool " + getUrl());
				}
			}
			finally {
				for ( Connection connection : allConnections ) {
					connection.close();
				}
			}
		}

		public int size() {
			return availableConnections.size();
		}

		protected void removeConnections(int numberToBeRemoved) {
			for ( int i = 0; i < numberToBeRemoved; i++ ) {
				Connection connection = availableConnections.poll();
				try {
					if ( connection != null ) {
						connection.close();
					}
					allConnections.remove( connection );
				}
				catch (SQLException e) {
					CONNECTIONS_LOGGER.unableToCloseConnection( e );
				}
			}
		}

		protected void addConnections(int numberOfConnections) {
			for ( int i = 0; i < numberOfConnections; i++ ) {
				Connection connection = connectionCreator.createConnection();
				allConnections.add( connection );
				availableConnections.add( connection );
			}
		}

		public String getUrl() {
			return connectionCreator.getUrl();
		}

		public static class Builder {
			private final ConnectionCreator connectionCreator;
			private boolean autoCommit;
			private int initialSize = 1;
			private int minSize = 1;
			private int maxSize = 20;

			public Builder(ConnectionCreator connectionCreator, boolean autoCommit) {
				this.connectionCreator = connectionCreator;
				this.autoCommit = autoCommit;
			}

			public Builder initialSize(int initialSize) {
				this.initialSize = initialSize;
				return this;
			}

			public Builder minSize(int minSize) {
				this.minSize = minSize;
				return this;
			}

			public Builder maxSize(int maxSize) {
				this.maxSize = maxSize;
				return this;
			}

			public PooledConnections build() {
				return new PooledConnections( this );
			}
		}
	}

	private static class PoolState {

		//Protecting any lifecycle state change:
		private final ReadWriteLock statelock = new ReentrantReadWriteLock();
		private volatile boolean active = false;
		private ScheduledExecutorService executorService;

		private final PooledConnections pool;
		private final long validationInterval;

		public PoolState(PooledConnections pool, long validationInterval) {
			this.pool = pool;
			this.validationInterval = validationInterval;
		}

		private void startIfNeeded() {
			if ( active ) {
				return;
			}
			statelock.writeLock().lock();
			try {
				if ( active ) {
					return;
				}
				executorService = Executors.newSingleThreadScheduledExecutor( new ValidationThreadFactory() );
				executorService.scheduleWithFixedDelay(
						pool::validate,
						validationInterval,
						validationInterval,
						TimeUnit.SECONDS
				);
				active = true;
			}
			finally {
				statelock.writeLock().unlock();
			}
		}

		public void stop() {
			statelock.writeLock().lock();
			try {
				if ( !active ) {
					return;
				}
				CONNECTIONS_LOGGER.cleaningUpConnectionPool( pool.getUrl() );
				active = false;
				if ( executorService != null ) {
					executorService.shutdown();
				}
				executorService = null;
				try {
					pool.close();
				}
				catch (SQLException e) {
					CONNECTIONS_LOGGER.unableToClosePooledConnection( e );
				}
			}
			finally {
				statelock.writeLock().unlock();
			}
		}

		public Connection getConnection() throws SQLException {
			startIfNeeded();
			statelock.readLock().lock();
			try {
				return pool.poll();
			}
			finally {
				statelock.readLock().unlock();
			}
		}

		public void closeConnection(Connection conn) throws SQLException {
			if (conn == null) {
				return;
			}
			startIfNeeded();
			statelock.readLock().lock();
			try {
				pool.add( conn );
			}
			finally {
				statelock.readLock().unlock();
			}
		}
	}

	private static class ValidationThreadFactory implements ThreadFactory {

		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread( runnable );
			thread.setDaemon( true );
			thread.setName( "Hibernate Connection Pool Validation Thread" );
			return thread;
		}
	}

}
