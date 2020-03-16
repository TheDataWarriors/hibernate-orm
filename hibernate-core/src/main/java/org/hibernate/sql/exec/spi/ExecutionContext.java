/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.exec.spi;

import org.hibernate.engine.spi.CollectionKey;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.spi.QueryParameterBindings;
import org.hibernate.resource.jdbc.spi.LogicalConnectionImplementor;
import org.hibernate.sql.results.graph.entity.LoadingEntityEntry;

/**
 * @author Steve Ebersole
 */
public interface ExecutionContext {
	SharedSessionContractImplementor getSession();

	QueryOptions getQueryOptions();

	default LoadQueryInfluencers getLoadQueryInfluencers() {
		return getSession().getLoadQueryInfluencers();
	}

	QueryParameterBindings getQueryParameterBindings();

	Callback getCallback();

	/**
	 * Get the collection key for the collection which is to be loaded immediately.
	 */
	default CollectionKey getCollectionKey() {
		return null;
	}

	/**
	 * Should only be used when initializing a bytecode-proxy
	 */
	default Object getEntityInstance() {
		return null;
	}

	default Object getEntityId() {
		return null;
	}

	default void registerLoadingEntityEntry(EntityKey entityKey, LoadingEntityEntry entry) {
		// by default do nothing
	}

	/**
	 * Hook to allow delaying calls to {@link LogicalConnectionImplementor#afterStatement()}.
	 * Mainly used in the case of batching and multi-table mutations
	 *
	 * todo (6.0) : come back and make sure we are calling this at appropriate times.  despite the name, it should be
	 * 		called after a logical group of statements - e.g., after all of the delete statements against all of the
	 * 		tables for a particular entity
	 */
	default void afterStatement(LogicalConnectionImplementor logicalConnection) {
		logicalConnection.afterStatement();
	}
}
