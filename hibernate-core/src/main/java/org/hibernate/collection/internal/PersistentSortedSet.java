/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.collection.internal;

import java.io.Serializable;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeMap;

import org.hibernate.EntityMode;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.collection.BasicCollectionPersister;

/**
 * A persistent wrapper for a <tt>java.util.SortedSet</tt>. Underlying
 * collection is a <tt>TreeSet</tt>.
 *
 * @see java.util.TreeSet
 * @author <a href="mailto:doug.currie@alum.mit.edu">e</a>
 */
public class PersistentSortedSet<E> extends PersistentSet<E> implements SortedSet<E> {
	protected Comparator<? super E> comparator;

	/**
	 * Constructs a PersistentSortedSet.  This form needed for SOAP libraries, etc
	 */
	@SuppressWarnings("UnusedDeclaration")
	public PersistentSortedSet() {
	}

	/**
	 * Constructs a PersistentSortedSet
	 *
	 * @param session The session
	 */
	public PersistentSortedSet(SharedSessionContractImplementor session) {
		super( session );
	}

	/**
	 * Constructs a PersistentSortedSet
	 *
	 * @param session The session
	 * @deprecated {@link #PersistentSortedSet(SharedSessionContractImplementor)} should be used instead.
	 */
	@Deprecated
	public PersistentSortedSet(SessionImplementor session) {
		this( (SharedSessionContractImplementor) session );
	}

	/**
	 * Constructs a PersistentSortedSet
	 *
	 * @param session The session
	 * @param set The underlying set data
	 */
	public PersistentSortedSet(SharedSessionContractImplementor session, SortedSet<E> set) {
		super( session, set );
		comparator = set.comparator();
	}

	/**
	 * Constructs a PersistentSortedSet
	 *
	 * @param session The session
	 * @param set The underlying set data
	 * @deprecated {@link #PersistentSortedSet(SharedSessionContractImplementor, SortedSet)} should be used instead.
	 */
	@Deprecated
	public PersistentSortedSet(SessionImplementor session, SortedSet<E> set) {
		this( (SharedSessionContractImplementor) session, set );
	}

	@SuppressWarnings("UnusedParameters")
	protected Serializable snapshot(BasicCollectionPersister persister, EntityMode entityMode)
			throws HibernateException {
		final TreeMap<E,E> clonedSet = new TreeMap<>( comparator );
		for ( E setElement : set ) {
			final E copy = (E) persister.getElementType().deepCopy( setElement, persister.getFactory() );
			clonedSet.put( copy, copy );
		}
		return clonedSet;
	}

	public void setComparator(Comparator<? super E> comparator) {
		this.comparator = comparator;
	}

	@Override
	public Comparator<? super E> comparator() {
		return comparator;
	}

	@Override
	public SortedSet<E> subSet(E fromElement, E toElement) {
		read();
		final SortedSet<E> subSet = ( (SortedSet<E>) set ).subSet( fromElement, toElement );
		return new SubSetProxy( subSet );
	}

	@Override
	public SortedSet<E> headSet(E toElement) {
		read();
		final SortedSet<E> headSet = ( (SortedSet<E>) set ).headSet( toElement );
		return new SubSetProxy( headSet );
	}

	@Override
	public SortedSet<E> tailSet(E fromElement) {
		read();
		final SortedSet<E> tailSet = ( (SortedSet<E>) set ).tailSet( fromElement );
		return new SubSetProxy( tailSet );
	}

	@Override
	public E first() {
		read();
		return ( (SortedSet<E>) set ).first();
	}

	@Override
	public E last() {
		read();
		return ( (SortedSet<E>) set ).last();
	}

	/**
	 * wrapper for subSets to propagate write to its backing set
	 */
	class SubSetProxy extends SetProxy<E> implements SortedSet<E> {
		SubSetProxy(SortedSet<E> s) {
			super( s );
		}

		@Override
		public Comparator<? super E> comparator() {
			return ( (SortedSet<E>) this.set ).comparator();
		}

		@Override
		public E first() {
			return ( (SortedSet<E>) this.set ).first();
		}

		@Override
		public SortedSet<E> headSet(E toValue) {
			return new SubSetProxy( ( (SortedSet<E>) this.set ).headSet( toValue ) );
		}

		@Override
		public E last() {
			return ( (SortedSet<E>) this.set ).last();
		}

		@Override
		public SortedSet<E> subSet(E fromValue, E toValue) {
			return new SubSetProxy( ( (SortedSet<E>) this.set ).subSet( fromValue, toValue ) );
		}

		@Override
		public SortedSet<E> tailSet(E fromValue) {
			return new SubSetProxy( ( (SortedSet<E>) this.set ).tailSet( fromValue ) );
		}
	}
}
