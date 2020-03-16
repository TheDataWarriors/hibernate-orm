/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.graph.collection.internal;

import org.hibernate.collection.internal.PersistentArrayHolder;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.sql.results.graph.DomainResultAssembler;
import org.hibernate.sql.results.graph.collection.CollectionInitializer;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesSourceProcessingOptions;
import org.hibernate.sql.results.jdbc.spi.RowProcessingState;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

/**
 * @author Andrea Boriero
 */
public abstract class AbstractCollectionAssembler implements DomainResultAssembler {
	private final PluralAttributeMapping fetchedMapping;

	protected final CollectionInitializer initializer;

	public AbstractCollectionAssembler(
			PluralAttributeMapping fetchedMapping,
			CollectionInitializer initializer) {
		this.fetchedMapping = fetchedMapping;
		this.initializer = initializer;
	}

	@Override
	public Object assemble(RowProcessingState rowProcessingState, JdbcValuesSourceProcessingOptions options) {
		PersistentCollection collectionInstance = initializer.getCollectionInstance();
		if ( collectionInstance instanceof PersistentArrayHolder ) {
			return collectionInstance.getValue();
		}
		return collectionInstance;
	}

	@Override
	public JavaTypeDescriptor getAssembledJavaTypeDescriptor() {
		return fetchedMapping.getJavaTypeDescriptor();
	}
}
