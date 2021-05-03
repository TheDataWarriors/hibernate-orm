/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.transaction;

import javax.persistence.EntityManager;
import javax.transaction.Status;
import javax.transaction.TransactionManager;

import org.hibernate.engine.transaction.internal.jta.JtaStatusHelper;
import org.hibernate.internal.SessionImpl;
import org.hibernate.resource.transaction.backend.jta.internal.JtaTransactionCoordinatorImpl;

import org.hibernate.testing.jta.TestingJtaPlatformImpl;
import org.hibernate.testing.orm.junit.ExtraAssertions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Helper for centralized transaction join checking
 *
 * @author Steve Ebersole
 */
public class TransactionJoinHandlingChecker {
	public static void validateExplicitJoiningHandling(EntityManager entityManager) throws Exception {
		TransactionManager transactionManager = TestingJtaPlatformImpl.INSTANCE.getTransactionManager();

		try (SessionImpl session = entityManager.unwrap( SessionImpl.class )) {

			ExtraAssertions.assertTyping( JtaTransactionCoordinatorImpl.class, session.getTransactionCoordinator() );
			JtaTransactionCoordinatorImpl transactionCoordinator = (JtaTransactionCoordinatorImpl) session.getTransactionCoordinator();

			assertFalse( transactionCoordinator.isSynchronizationRegistered() );
			assertFalse( transactionCoordinator.isJtaTransactionCurrentlyActive() );
			assertFalse( transactionCoordinator.isJoined() );

			session.getFlushMode();
			assertFalse( transactionCoordinator.isSynchronizationRegistered() );
			assertFalse( transactionCoordinator.isJtaTransactionCurrentlyActive() );
			assertFalse( transactionCoordinator.isJoined() );

			transactionManager.begin();
			assertTrue( JtaStatusHelper.isActive( transactionManager ) );
			assertTrue( transactionCoordinator.isJtaTransactionCurrentlyActive() );
			assertFalse( transactionCoordinator.isJoined() );
			assertFalse( transactionCoordinator.isSynchronizationRegistered() );

			session.getFlushMode();
			assertTrue( JtaStatusHelper.isActive( transactionManager ) );
			assertTrue( transactionCoordinator.isJtaTransactionCurrentlyActive() );
			assertFalse( transactionCoordinator.isJoined() );
			assertFalse( transactionCoordinator.isSynchronizationRegistered() );

			entityManager.joinTransaction();
			assertTrue( JtaStatusHelper.isActive( transactionManager ) );
			assertTrue( transactionCoordinator.isJtaTransactionCurrentlyActive() );
			assertTrue( transactionCoordinator.isSynchronizationRegistered() );
			assertTrue( transactionCoordinator.isJoined() );

			assertTrue( entityManager.isOpen() );
			assertTrue( session.isOpen() );
			entityManager.close();
			assertFalse( entityManager.isOpen() );
			assertFalse( session.isOpen() );

			transactionManager.commit();
			assertFalse( entityManager.isOpen() );
			assertFalse( session.isOpen() );
		}
		catch (Throwable t){
			try {
				switch ( transactionManager.getStatus() ) {
					case Status.STATUS_ACTIVE:
					case Status.STATUS_MARKED_ROLLBACK:
						transactionManager.rollback();
				}
			}
			catch (Exception exception) {
				//ignore exception
			}
			throw t;
		}
	}
}