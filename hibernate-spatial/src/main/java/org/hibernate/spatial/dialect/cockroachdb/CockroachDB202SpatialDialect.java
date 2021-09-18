/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.spatial.dialect.cockroachdb;

import org.hibernate.dialect.CockroachDialect;
import org.hibernate.spatial.SpatialDialect;

/**
 * An @{code SpatialDialect} for CockroachDB 20.2 and later. CockroachDB's spatial features where introduced in
 * that version.
 */
@Deprecated
public class CockroachDB202SpatialDialect extends CockroachDialect implements SpatialDialect {
}
