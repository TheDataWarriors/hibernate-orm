/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate;

/**
 * The kind of CTE search clause.
 *
 * @author Christian Beikov
 */
public enum CteSearchClauseKind {
	/**
	 * Use depth first for a recursive CTE.
	 */
	DEPTH_FIRST,
	/**
	 * Use breadth first for a recursive CTE.
	 */
	BREADTH_FIRST;
}
