/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.produce.path.internal;

import org.hibernate.query.sqm.produce.internal.hql.SemanticQueryBuilder;
import org.hibernate.query.sqm.produce.path.spi.AbstractStandardNavigableJoinBuilder;

/**
 * @author Steve Ebersole
 */
public class NavigableJoinBuilderRoot extends AbstractStandardNavigableJoinBuilder {
	public NavigableJoinBuilderRoot(SemanticQueryBuilder queryBuilder) {
		super( queryBuilder );
	}
}
