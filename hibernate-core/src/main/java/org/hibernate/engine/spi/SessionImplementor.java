/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.engine.spi;

import org.hibernate.HibernateException;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.event.spi.DeleteContext;
import org.hibernate.event.spi.MergeContext;
import org.hibernate.event.spi.PersistContext;
import org.hibernate.event.spi.RefreshContext;
import org.hibernate.graph.spi.RootGraphImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.resource.transaction.spi.TransactionCoordinator;
import org.hibernate.resource.transaction.spi.TransactionCoordinatorBuilder;

/**
 * Defines the "internal contract" for {@link Session} and other parts of Hibernate such
 * as {@link org.hibernate.type.Type}, {@link EntityPersister}
 * and {@link org.hibernate.persister.collection.CollectionPersister} implementations.
 *
 * A Session, through this interface and SharedSessionContractImplementor, implements:
 * <ul>
 *     <li>
 *         {@link org.hibernate.resource.jdbc.spi.JdbcSessionOwner} to drive the behavior
 *         of the {@link org.hibernate.resource.jdbc.spi.JdbcSessionContext} delegate
 *     </li>
 *     <li>
 *         {@link TransactionCoordinatorBuilder.Options}
 *         to drive the creation of the {@link TransactionCoordinator} delegate
 *     </li>
 *     <li>
 *         {@link org.hibernate.engine.jdbc.LobCreationContext} to act as the context for
 *         JDBC LOB instance creation
 *     </li>
 *     <li>
 *         {@link org.hibernate.type.descriptor.WrapperOptions} to fulfill the behavior
 *         needed while binding/extracting values to/from JDBC as part of the {@code Type}
 *         contracts
 *     </li>
 * </ul>
 *
 * See also {@link org.hibernate.event.spi.EventSource} which extends this interface
 * providing s bridge to the event generation features of {@link org.hibernate.event}.
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public interface SessionImplementor extends Session, SharedSessionContractImplementor {

	@Override
	default SessionImplementor getSession() {
		return this;
	}

	@Override
	SessionFactoryImplementor getSessionFactory();

	@Override
	<T> RootGraphImplementor<T> createEntityGraph(Class<T> rootType);

	@Override
	RootGraphImplementor<?> createEntityGraph(String graphName);

	@Override
	RootGraphImplementor<?> getEntityGraph(String graphName);

	ActionQueue getActionQueue();

	Object instantiate(EntityPersister persister, Object id) throws HibernateException;

	void forceFlush(EntityEntry e) throws HibernateException;

	void lock(String entityName, Object child, LockOptions lockOptions);

	/**
	 * @deprecated  OperationalContext should cover this overload I believe
	 */
	@Deprecated
	void merge(String entityName, Object object, MergeContext copiedAlready) throws HibernateException;

	/**
	 * @deprecated  OperationalContext should cover this overload I believe
	 */
	@Deprecated
	void persist(String entityName, Object object, PersistContext createdAlready) throws HibernateException;

	/**
	 * @deprecated  OperationalContext should cover this overload I believe
	 */
	@Deprecated
	void persistOnFlush(String entityName, Object object, PersistContext copiedAlready);

	/**
	 * @deprecated  OperationalContext should cover this overload I believe
	 */
	@Deprecated
	void refresh(String entityName, Object object, RefreshContext refreshedAlready) throws HibernateException;

	/**
	 * @deprecated  OperationalContext should cover this overload I believe
	 */
	@Deprecated
	void delete(String entityName, Object child, boolean isCascadeDeleteEnabled, DeleteContext transientEntities);

	/**
	 * @deprecated  OperationalContext should cover this overload I believe
	 */
	@Deprecated
	void removeOrphanBeforeUpdates(String entityName, Object child);

	@Override
	default SessionImplementor asSessionImplementor() {
		return this;
	}

	@Override
	default boolean isSessionImplementor() {
		return true;
	}

}
