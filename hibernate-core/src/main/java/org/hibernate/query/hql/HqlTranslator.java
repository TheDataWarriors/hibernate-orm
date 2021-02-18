/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.hql;

import org.hibernate.Incubating;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.sqm.tree.SqmStatement;

/**
 * Main entry point into building semantic queries.
 *
 * @see SessionFactoryImplementor#getQueryEngine()
 * @see QueryEngine#getHqlTranslator()
 *
 * @author Steve Ebersole
 */
@Incubating
public interface HqlTranslator {
	/**
	 * Performs the interpretation of a HQL/JPQL query string to SQM.
	 *
	 * @param hql The HQL/JPQL query string to interpret
	 *
	 * @return The semantic representation of the incoming query.
	 */
	<R> SqmStatement<R> translate(String hql);

	/**
	 * Give the translator a chance to "shut down" if it needs to
	 */
	default void close() {
		// nothing to do generally speaking
	}
}
