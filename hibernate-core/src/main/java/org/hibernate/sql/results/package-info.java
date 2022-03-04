/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */

/**
 * Package for processing JDBC ResultSets into hydrated domain model graphs based on a "load plan"
 * defined by a "domain result graph" - one or more {@link org.hibernate.sql.results.graph.DomainResult} nodes
 * with zero-or-more {@link org.hibernate.sql.results.graph.Fetch} nodes
 */
@Incubating
package org.hibernate.sql.results;

import org.hibernate.Incubating;
