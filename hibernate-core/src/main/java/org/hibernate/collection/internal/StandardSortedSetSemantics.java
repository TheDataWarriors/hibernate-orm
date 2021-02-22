/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.collection.internal;

import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.CollectionClassification;
import org.hibernate.persister.collection.CollectionPersister;

/**
 * @author Steve Ebersole
 */
public class StandardSortedSetSemantics<E> extends AbstractSetSemantics<SortedSet<E>,E> {
	/**
	 * Singleton access
	 */
	public static final StandardSortedSetSemantics<?> INSTANCE = new StandardSortedSetSemantics<>();

	private StandardSortedSetSemantics() {
	}

	@Override
	public CollectionClassification getCollectionClassification() {
		return CollectionClassification.SORTED_SET;
	}

	@Override
	public Class<SortedSet> getCollectionJavaType() {
		return SortedSet.class;
	}

	@Override
	public SortedSet<E> instantiateRaw(
			int anticipatedSize,
			CollectionPersister collectionDescriptor) {
		return new TreeSet<E>( (Comparator) collectionDescriptor.getSortingComparator() );
	}

	@Override
	public PersistentCollection<E> instantiateWrapper(
			Object key,
			CollectionPersister collectionDescriptor,
			SharedSessionContractImplementor session) {
		return new PersistentSortedSet<>( session );
	}

	@Override
	public PersistentCollection<E> wrap(
			SortedSet<E> rawCollection,
			CollectionPersister collectionDescriptor,
			SharedSessionContractImplementor session) {
		return new PersistentSortedSet<>( session, rawCollection );
	}

	@Override
	public Iterator<E> getElementIterator(SortedSet<E> rawCollection) {
		return rawCollection.iterator();
	}
}
