/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.collection.internal;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Consumer;

import org.hibernate.LockMode;
import org.hibernate.collection.spi.CollectionInitializerProducer;
import org.hibernate.collection.spi.CollectionSemantics;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.FetchTiming;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.CollectionClassification;
import org.hibernate.metamodel.mapping.CollectionPart;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.query.NavigablePath;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.collection.internal.ArrayInitializerProducer;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.FetchParent;

/**
 * CollectionSemantics implementation for arrays
 *
 * @author Steve Ebersole
 */
public class StandardArraySemantics<E> implements CollectionSemantics<E[], E> {
	/**
	 * Singleton access
	 */
	public static final StandardArraySemantics<?> INSTANCE = new StandardArraySemantics<>();

	private StandardArraySemantics() {
	}

	@Override
	public CollectionClassification getCollectionClassification() {
		return CollectionClassification.ARRAY;
	}

	@Override
	public Class<Object[]> getCollectionJavaType() {
		return Object[].class;
	}

	@Override
	public E[] instantiateRaw(
			int anticipatedSize,
			CollectionPersister collectionDescriptor) {
//		return (Object[]) Array.newInstance(
//				collectionDescriptor.getJavaTypeDescriptor().getJavaType().getComponentType(),
//				anticipatedSize
//		);
		throw new UnsupportedOperationException();
	}


	@Override
	public PersistentCollection<E> instantiateWrapper(
			Object key,
			CollectionPersister collectionDescriptor,
			SharedSessionContractImplementor session) {
		return new PersistentArrayHolder<>( session, collectionDescriptor );
	}

	@Override
	public PersistentCollection<E> wrap(
			E[] rawCollection,
			CollectionPersister collectionDescriptor,
			SharedSessionContractImplementor session) {
		return new PersistentArrayHolder<>( session, rawCollection );
	}

	@Override
	public Iterator<E> getElementIterator(E[] rawCollection) {
		return Arrays.stream( rawCollection ).iterator();
	}

	@Override
	public void visitElements(E[] array, Consumer<? super E> action) {
		if ( array == null ) {
			return;
		}

		for ( E element : array ) {
			action.accept( element );
		}
	}

	@Override
	public CollectionInitializerProducer createInitializerProducer(
			NavigablePath navigablePath,
			PluralAttributeMapping attributeMapping,
			FetchParent fetchParent,
			boolean selected,
			String resultVariable,
			LockMode lockMode,
			DomainResultCreationState creationState) {
		return new ArrayInitializerProducer(
				attributeMapping,
				attributeMapping.getIndexDescriptor().generateFetch(
						fetchParent,
						navigablePath.append( CollectionPart.Nature.INDEX.getName() ),
						FetchTiming.IMMEDIATE,
						selected,
						lockMode,
						null,
						creationState
				),
				attributeMapping.getElementDescriptor().generateFetch(
						fetchParent,
						navigablePath.append( CollectionPart.Nature.ELEMENT.getName() ),
						FetchTiming.IMMEDIATE,
						selected,
						lockMode,
						null,
						creationState
				)
		);
	}

	@Override
	public CollectionInitializerProducer createInitializerProducer(
			NavigablePath navigablePath,
			PluralAttributeMapping attributeMapping,
			FetchParent fetchParent,
			boolean selected,
			String resultVariable,
			LockMode lockMode,
			Fetch indexFetch,
			Fetch elementFetch,
			DomainResultCreationState creationState){
		if ( indexFetch == null ) {
			indexFetch = attributeMapping.getIndexDescriptor().generateFetch(
					fetchParent,
					navigablePath.append( CollectionPart.Nature.INDEX.getName() ),
					FetchTiming.IMMEDIATE,
					selected,
					lockMode,
					null,
					creationState
			);
		}
		if ( elementFetch == null ) {
			elementFetch = attributeMapping.getElementDescriptor().generateFetch(
					fetchParent,
					navigablePath.append( CollectionPart.Nature.ELEMENT.getName() ),
					FetchTiming.IMMEDIATE,
					selected,
					lockMode,
					null,
					creationState
			);
		}
		return new ArrayInitializerProducer(
				attributeMapping,
				indexFetch,
				elementFetch
		);
	}

}
