/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.results;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.named.NamedResultSetMappingMemento;

/**
 * Context (aka, a parameter object) used in resolving a {@link NamedResultSetMappingMemento}
 *
 * @author Steve Ebersole
 */
public interface ResultSetMappingResolutionContext {
	SessionFactoryImplementor getSessionFactory();
}
