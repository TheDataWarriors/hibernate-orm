/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jdbc.internal;

import java.sql.Statement;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.engine.jdbc.batch.internal.BasicBatchKey;
import org.hibernate.engine.jdbc.batch.internal.BatchBuilderImpl;
import org.hibernate.engine.jdbc.batch.spi.Batch;
import org.hibernate.engine.jdbc.batch.spi.BatchKey;
import org.hibernate.engine.jdbc.mutation.ParameterUsage;
import org.hibernate.engine.jdbc.mutation.internal.JdbcValueBindingsImpl;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.jdbc.Expectation;
import org.hibernate.jdbc.Expectations;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.orm.test.common.JournalingBatchObserver;
import org.hibernate.resource.jdbc.spi.LogicalConnectionImplementor;
import org.hibernate.sql.model.MutationType;
import org.hibernate.sql.model.TableMapping;
import org.hibernate.sql.model.jdbc.JdbcValueDescriptor;
import org.hibernate.type.StandardBasicTypes;

import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.jdbc.Expectations.NONE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Steve Ebersole
 * @author Brett Meyer
 */
public class BatchingTest extends BaseCoreFunctionalTestCase implements BatchKey {
	private final String SANDBOX_TBL = "SANDBOX_JDBC_TST";
	private final TableMapping SANDBOX_TBL_MAPPING = new TableMapping() {
		@Override
		public String getTableName() {
			return SANDBOX_TBL;
		}

		@Override
		public int getRelativePosition() {
			return 0;
		}

		@Override
		public boolean isOptional() {
			return false;
		}

		@Override
		public boolean isInverse() {
			return false;
		}

		@Override
		public boolean isIdentifierTable() {
			return true;
		}

		@Override
		public MutationDetails getInsertDetails() {
			return new MutationDetails( MutationType.INSERT, NONE, null, false );
		}

		@Override
		public MutationDetails getUpdateDetails() {
			return new MutationDetails( MutationType.UPDATE, NONE, null, false );
		}

		@Override
		public boolean isCascadeDeleteEnabled() {
			return false;
		}

		@Override
		public MutationDetails getDeleteDetails() {
			return new MutationDetails( MutationType.DELETE, NONE, null, false );
		}
	};

	@Override
	public int getBatchedStatementCount() {
		return 1;
	}

	@Override
	public Expectation getExpectation() {
		return Expectations.BASIC;
	}

	@Test
	public void testBatchingUsage() throws Exception {
		final Session session = openSession();
		final SessionImplementor sessionImpl = (SessionImplementor) session;
		final JdbcCoordinator jdbcCoordinator = sessionImpl.getJdbcCoordinator();

		exportSandboxSchema( sessionImpl );

		// ok, now we can get down to it...
		Transaction txn = session.getTransaction();  // same as Session#getTransaction
		txn.begin();

		final String insertSql = "insert into SANDBOX_JDBC_TST( ID, NAME ) values ( ?, ? )";

		final BatchBuilderImpl batchBuilder = new BatchBuilderImpl( 2 );
		final BatchKey batchKey = new BasicBatchKey( "this" );
		final Batch insertBatch = batchBuilder.buildBatch( batchKey, null, SANDBOX_TBL, sessionImpl, insertSql );
		assertThat( insertBatch ).isNotNull();

		final JournalingBatchObserver batchObserver = new JournalingBatchObserver();
		insertBatch.addObserver( batchObserver );

		final JdbcValueBindingsImpl jdbcValueBindings = sandboxInsertValueBindings( sessionImpl );

		// bind values for #1 - should do nothing at the JDBC level
		jdbcValueBindings.bindValue( 1, SANDBOX_TBL, "ID", ParameterUsage.SET, sessionImpl );
		jdbcValueBindings.bindValue( "name", SANDBOX_TBL, "NAME", ParameterUsage.SET, sessionImpl );
		assertThat( batchObserver.getExplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( batchObserver.getImplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() ).isFalse();

		// add #1 to the batch - will acquire prepared statement to bind values
		insertBatch.addToBatch( jdbcValueBindings, null );
		assertThat( batchObserver.getExplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( batchObserver.getImplicitExecutionCount() ).isEqualTo( 0 );
        assertThat( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() ).isTrue();

		// bind values for #2 - again, nothing at JDBC level (we have statement from earlier)
		jdbcValueBindings.bindValue( 2, SANDBOX_TBL, "ID", ParameterUsage.SET, sessionImpl );
		jdbcValueBindings.bindValue( "another name", SANDBOX_TBL, "NAME", ParameterUsage.SET, sessionImpl );
		assertThat( batchObserver.getExplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( batchObserver.getImplicitExecutionCount() ).isEqualTo( 0 );

		// add #2 to the batch -
		// 		- uses the previous prepared statement to bind values
		//		- batch size has been exceeded, trigger an implicit execution
		insertBatch.addToBatch( jdbcValueBindings, null );
		assertThat( batchObserver.getExplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( batchObserver.getImplicitExecutionCount() ).isEqualTo( 1 );
		assertThat( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() ).isTrue();

		// execute the batch - effectively only increments the explicit-execution counter
		insertBatch.execute();
		assertThat( batchObserver.getExplicitExecutionCount() ).isEqualTo( 1 );
		assertThat( batchObserver.getImplicitExecutionCount() ).isEqualTo( 1 );
		assertThat( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() ).isFalse();

		insertBatch.release();

		txn.commit();
		session.close();
	}

	private JdbcValueBindingsImpl sandboxInsertValueBindings(SessionImplementor session) {
		return new JdbcValueBindingsImpl(
				MutationType.INSERT,
				null,
				(tableName, columnName, usage) -> {
					assert tableName.equals( SANDBOX_TBL );

					if ( columnName.equals( "ID" ) ) {
						return new JdbcValueDescriptor() {
							@Override
							public String getColumnName() {
								return "ID";
							}

							@Override
							public ParameterUsage getUsage() {
								return ParameterUsage.SET;
							}

							@Override
							public int getJdbcPosition() {
								return 1;
							}

							@Override
							public JdbcMapping getJdbcMapping() {
								return session.getTypeConfiguration()
										.getBasicTypeRegistry()
										.resolve( StandardBasicTypes.INTEGER );
							}
						};
					}

					if ( columnName.equals( "NAME" ) ) {
						return new JdbcValueDescriptor() {
							@Override
							public String getColumnName() {
								return "NAME";
							}

							@Override
							public ParameterUsage getUsage() {
								return ParameterUsage.SET;
							}

							@Override
							public int getJdbcPosition() {
								return 2;
							}

							@Override
							public JdbcMapping getJdbcMapping() {
								return session.getTypeConfiguration()
										.getBasicTypeRegistry()
										.resolve( StandardBasicTypes.STRING );
							}
						};
					}

					throw new IllegalArgumentException( "Unknown column : " + columnName );
				}
		);
	}

	@Test
	public void testSessionBatchingUsage() throws Exception {
		Session session = openSession();
		session.setJdbcBatchSize( 3 );
		SessionImplementor sessionImpl = (SessionImplementor) session;
		final JdbcCoordinator jdbcCoordinator = sessionImpl.getJdbcCoordinator();

		exportSandboxSchema( sessionImpl );

		// ok, now we can get down to it...
		Transaction txn = session.getTransaction();  // same as Session#getTransaction
		txn.begin();


		final String insertSql = "insert into SANDBOX_JDBC_TST( ID, NAME ) values ( ?, ? )";

		final BatchBuilderImpl batchBuilder = new BatchBuilderImpl( 2 );
		final BatchKey batchKey = new BasicBatchKey( "this" );
		final Batch insertBatch = batchBuilder.buildBatch( batchKey, 3, SANDBOX_TBL, sessionImpl, insertSql );
		assertThat( insertBatch ).isNotNull();

		final JournalingBatchObserver batchObserver = new JournalingBatchObserver();
		insertBatch.addObserver( batchObserver );

		final JdbcValueBindingsImpl jdbcValueBindings = sandboxInsertValueBindings( sessionImpl );

		// bind values for #1 - this does nothing at the JDBC level
		jdbcValueBindings.bindValue( 1, SANDBOX_TBL, "ID", ParameterUsage.SET, sessionImpl );
		jdbcValueBindings.bindValue( "name", SANDBOX_TBL, "NAME", ParameterUsage.SET, sessionImpl );
		assertThat( batchObserver.getExplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( batchObserver.getImplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() ).isFalse();

		// add the values to the batch - this creates the prepared statement and binds the values
		insertBatch.addToBatch( jdbcValueBindings, null );
		assertThat( batchObserver.getExplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( batchObserver.getImplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() ).isTrue();

		// bind values for #2 - this does nothing at the JDBC level : we do still have the statement defining the batch
		jdbcValueBindings.bindValue( 2, SANDBOX_TBL, "ID", ParameterUsage.SET, sessionImpl );
		jdbcValueBindings.bindValue( "another name", SANDBOX_TBL, "NAME", ParameterUsage.SET, sessionImpl );
		assertThat( batchObserver.getExplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( batchObserver.getImplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() ).isTrue();

		// add #2 to batch - we have not exceeded batch size, so we should not get an implicit execution
		insertBatch.addToBatch( jdbcValueBindings, null );
		assertThat( batchObserver.getExplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( batchObserver.getImplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() ).isTrue();

		// bind values for #3 - this does nothing at the JDBC level : we do still have the statement defining the batch
		jdbcValueBindings.bindValue( 3, SANDBOX_TBL, "ID", ParameterUsage.SET, sessionImpl );
		jdbcValueBindings.bindValue( "yet another name", SANDBOX_TBL, "NAME", ParameterUsage.SET, sessionImpl );
		assertThat( batchObserver.getExplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( batchObserver.getImplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() ).isTrue();

		insertBatch.addToBatch( jdbcValueBindings, null );
		assertThat( batchObserver.getExplicitExecutionCount() ).isEqualTo( 0 );
		assertThat( batchObserver.getImplicitExecutionCount() ).isEqualTo( 1 );
		assertThat( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() ).isTrue();

		insertBatch.execute();
		assertThat( batchObserver.getExplicitExecutionCount() ).isEqualTo( 1 );
		assertThat( batchObserver.getImplicitExecutionCount() ).isEqualTo( 1 );
		assertThat( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() ).isFalse();

		insertBatch.release();

		txn.commit();
		session.close();
	}

	private void exportSandboxSchema(SessionImplementor sessionImpl) {
		Transaction txn = sessionImpl.beginTransaction();
		final JdbcCoordinator jdbcCoordinator = sessionImpl.getJdbcCoordinator();
		LogicalConnectionImplementor logicalConnection = jdbcCoordinator.getLogicalConnection();

		// set up some tables to use
		Statement statement = jdbcCoordinator.getStatementPreparer().createStatement();
		String dropSql = sessionFactory().getJdbcServices().getDialect().getDropTableString( "SANDBOX_JDBC_TST" );
		try {
			jdbcCoordinator.getResultSetReturn().execute( statement, dropSql );
		}
		catch ( Exception e ) {
			// ignore if the DB doesn't support "if exists" and the table doesn't exist
		}
		jdbcCoordinator.getResultSetReturn().execute( statement, "create table SANDBOX_JDBC_TST ( ID integer, NAME varchar(100) )" );
		assertTrue( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() );
		assertTrue( logicalConnection.isPhysicallyConnected() );
		jdbcCoordinator.getLogicalConnection().getResourceRegistry().release( statement );
		assertFalse( jdbcCoordinator.getLogicalConnection().getResourceRegistry().hasRegisteredResources() );
		assertTrue( logicalConnection.isPhysicallyConnected() ); // after_transaction specified
		txn.commit();
	}

	@Override
	protected void cleanupTest() throws Exception {
		try (Session session = openSession()) {
			session.doWork( connection -> {
				final Statement stmnt = connection.createStatement();

				stmnt.execute( sessionFactory().getJdbcServices().getDialect().getDropTableString( "SANDBOX_JDBC_TST" ) );
			} );
		}
	}

}
