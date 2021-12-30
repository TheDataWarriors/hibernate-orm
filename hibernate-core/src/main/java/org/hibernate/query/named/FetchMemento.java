/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.named;

import java.util.function.Consumer;

import org.hibernate.query.results.ResultSetMappingResolutionContext;
import org.hibernate.query.results.FetchBuilder;

/**
 * @author Steve Ebersole
 */
public interface FetchMemento extends ModelPartReferenceMemento {
	/**
	 * The parent node for the fetch
	 *
	 * @todo (6.0) - how does this differ from {@link org.hibernate.query.results.FetchParentMemento}?
	 */
	interface Parent extends ModelPartReferenceMemento {
	}

	/**
	 * Resolve the fetch-memento into the result-graph-node builder
	 */
	FetchBuilder resolve(Parent parent, Consumer<String> querySpaceConsumer, ResultSetMappingResolutionContext context);
}
