/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.collection.internal;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.model.domain.spi.PersistentCollectionDescriptor;
import org.hibernate.NotYetImplementedFor6Exception;

/**
 * An unordered, unkeyed collection that can contain the same element
 * multiple times. The Java collections API, curiously, has no <tt>Bag</tt>.
 * Most developers seem to use <tt>List</tt>s to represent bag semantics,
 * so Hibernate follows this practice.
 *
 * @author Gavin King
 */
public class PersistentBag extends AbstractPersistentCollection implements List {

	protected List bag;

	/**
	 * Constructs a PersistentBag.  Needed for SOAP libraries, etc
	 */
	@SuppressWarnings("UnusedDeclaration")
	public PersistentBag() {
	}

	/**
	 * Constructs a PersistentBag
	 *
	 * @param session The session
	 */
	public PersistentBag(SharedSessionContractImplementor session) {
		super( session );
	}

	/**
	 * Constructs a PersistentBag
	 *
	 * @param session The session
	 * @param coll The base elements.
	 */
	@SuppressWarnings("unchecked")
	public PersistentBag(SharedSessionContractImplementor session, Collection coll) {
		super( session );
		if ( coll instanceof List ) {
			bag = (List) coll;
		}
		else {
			bag = new ArrayList();
			for ( Object element : coll ) {
				bag.add( element );
			}
		}
		setInitialized();
		setDirectlyAccessible( true );
	}

	@Override
	public boolean isWrapper(Object collection) {
		return bag==collection;
	}

	@Override
	public boolean empty() {
		return bag.isEmpty();
	}

	@Override
	public Iterator entries(PersistentCollectionDescriptor persister) {
		return bag.iterator();
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object readFrom(ResultSet rs, PersistentCollectionDescriptor persister, Object owner) throws SQLException {
		throw new NotYetImplementedFor6Exception(  );
		// todo (6.0) : this is done so much differently in this redesign - I think these metods are not going to be needed
//		// note that if we load this collection from a cartesian product
//		// the multiplicity would be broken ... so use an idbag instead
//		final Object element = persister.readElement( rs, owner, descriptor.getSuffixedElementAliases(), getSession() ) ;
//		if ( element != null ) {
//			bag.add( element );
//		}
//		return element;
	}

	@Override
	public void beforeInitialize(PersistentCollectionDescriptor persister, int anticipatedSize) {
		this.bag = (List) persister.instantiateRaw( anticipatedSize );
	}

	@Override
	public boolean equalsSnapshot(PersistentCollectionDescriptor persister) throws HibernateException {
		final List sn = (List) getSnapshot();
		if ( sn.size() != bag.size() ) {
			return false;
		}
		for ( Object elt : bag ) {
			final boolean unequal = countOccurrences( elt, bag ) != countOccurrences( elt, sn );
			if ( unequal ) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isSnapshotEmpty(Serializable snapshot) {
		return ( (Collection) snapshot ).isEmpty();
	}

	@SuppressWarnings("unchecked")
	private int countOccurrences(Object element, List list) {
		final Iterator iter = list.iterator();
		int result = 0;
		while ( iter.hasNext() ) {
			if ( getCollectionMetadata().getElementDescriptor().getJavaTypeDescriptor().areEqual( element, iter.next() ) ) {
				result++;
			}
		}
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Serializable getSnapshot(PersistentCollectionDescriptor persister)
			throws HibernateException {
		final ArrayList clonedList = new ArrayList( bag.size() );
		for ( Object item : bag ) {
			clonedList.add( getCollectionMetadata().getElementDescriptor().getJavaTypeDescriptor().getMutabilityPlan().deepCopy( item ) );
		}
		return clonedList;
	}

	@Override
	public Collection getOrphans(Serializable snapshot, String entityName) throws HibernateException {
		final List sn = (List) snapshot;
		return getOrphans( sn, bag, entityName, getSession() );
	}

	@Override
	public Serializable disassemble(PersistentCollectionDescriptor persister)
			throws HibernateException {
		final int length = bag.size();
		final Serializable[] result = new Serializable[length];
		for ( int i=0; i<length; i++ ) {
			result[i] = getCollectionMetadata().getElementDescriptor().getJavaTypeDescriptor().getMutabilityPlan().disassemble( bag.get( i ) );
		}
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void initializeFromCache(PersistentCollectionDescriptor persister, Serializable disassembled, Object owner)
			throws HibernateException {
		final Serializable[] array = (Serializable[]) disassembled;
		final int size = array.length;
		beforeInitialize( persister, size );
		for ( Serializable item : array ) {
			final Object element = getCollectionMetadata().getElementDescriptor().getJavaTypeDescriptor().getMutabilityPlan().assemble( item );
			if ( element != null ) {
				bag.add( element );
			}
		}
	}

	@Override
	public boolean needsRecreate(PersistentCollectionDescriptor persister) {
		return !persister.isOneToMany();
	}


	// For a one-to-many, a <bag> is not really a bag;
	// it is *really* a set, since it can't contain the
	// same element twice. It could be considered a bug
	// in the mapping dtd that <bag> allows <one-to-many>.

	// Anyway, here we implement <set> semantics for a
	// <one-to-many> <bag>!

	@Override
	@SuppressWarnings("unchecked")
	public Iterator getDeletes(PersistentCollectionDescriptor persister, boolean indexIsFormula) throws HibernateException {
		final ArrayList deletes = new ArrayList();
		final List sn = (List) getSnapshot();
		final Iterator olditer = sn.iterator();
		int i=0;
		while ( olditer.hasNext() ) {
			final Object old = olditer.next();
			final Iterator newiter = bag.iterator();
			boolean found = false;
			if ( bag.size()>i && getCollectionMetadata().getElementDescriptor().getJavaTypeDescriptor().areEqual( old, bag.get( i++ ) ) ) {
			//a shortcut if its location didn't change!
				found = true;
			}
			else {
				//search for it
				//note that this code is incorrect for other than one-to-many
				while ( newiter.hasNext() ) {
					if ( getCollectionMetadata().getElementDescriptor().getJavaTypeDescriptor().areEqual( old, newiter.next() ) ) {
						found = true;
						break;
					}
				}
			}
			if ( !found ) {
				deletes.add( old );
			}
		}
		return deletes.iterator();
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean needsInserting(Object entry, int i) throws HibernateException {
		final List sn = (List) getSnapshot();
		if ( sn.size() > i && getCollectionMetadata().getElementDescriptor().getJavaTypeDescriptor().areEqual( sn.get( i ), entry ) ) {
			//a shortcut if its location didn't change!
			return false;
		}
		else {
			//search for it
			//note that this code is incorrect for other than one-to-many
			for ( Object old : sn ) {
				if ( getCollectionMetadata().getElementDescriptor().getJavaTypeDescriptor().areEqual( old, entry ) ) {
					return false;
				}
			}
			return true;
		}
	}

	@Override
	public boolean isRowUpdatePossible() {
		return false;
	}

	@Override
	public boolean needsUpdating(Object entry, int i) {
		return false;
	}

	@Override
	public int size() {
		return readSize() ? getCachedSize() : bag.size();
	}

	@Override
	public boolean isEmpty() {
		return readSize() ? getCachedSize()==0 : bag.isEmpty();
	}

	@Override
	public boolean contains(Object object) {
		final Boolean exists = readElementExistence( object );
		return exists == null ? bag.contains( object ) : exists;
	}

	@Override
	public Iterator iterator() {
		read();
		return new IteratorProxy( bag.iterator() );
	}

	@Override
	public Object[] toArray() {
		read();
		return bag.toArray();
	}

	@Override
	public Object[] toArray(Object[] a) {
		read();
		return bag.toArray( a );
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean add(Object object) {
		if ( !isOperationQueueEnabled() ) {
			write();
			return bag.add( object );
		}
		else {
			queueOperation( new SimpleAdd( object ) );
			return true;
		}
	}

	@Override
	public boolean remove(Object o) {
		initialize( true );
		if ( bag.remove( o ) ) {
			dirty();
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean containsAll(Collection c) {
		read();
		return bag.containsAll( c );
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean addAll(Collection values) {
		if ( values.size()==0 ) {
			return false;
		}
		if ( !isOperationQueueEnabled() ) {
			write();
			return bag.addAll( values );
		}
		else {
			for ( Object value : values ) {
				queueOperation( new SimpleAdd( value ) );
			}
			return values.size()>0;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean removeAll(Collection c) {
		if ( c.size()>0 ) {
			initialize( true );
			if ( bag.removeAll( c ) ) {
				dirty();
				return true;
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean retainAll(Collection c) {
		initialize( true );
		if ( bag.retainAll( c ) ) {
			dirty();
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void clear() {
		if ( isClearQueueEnabled() ) {
			queueOperation( new Clear() );
		}
		else {
			initialize( true );
			if ( ! bag.isEmpty() ) {
				bag.clear();
				dirty();
			}
		}
	}

	@Override
	public Object getIndex(Object entry, int i, PersistentCollectionDescriptor persister) {
		throw new UnsupportedOperationException("Bags don't have indexes");
	}

	@Override
	public Object getElement(Object entry) {
		return entry;
	}

	@Override
	public Object getSnapshotElement(Object entry, int i) {
		final List sn = (List) getSnapshot();
		return sn.get( i );
	}

	/**
	 * Count how many times the given object occurs in the elements
	 *
	 * @param o The object to check
	 *
	 * @return The number of occurences.
	 */
	@SuppressWarnings("UnusedDeclaration")
	public int occurrences(Object o) {
		read();
		final Iterator itr = bag.iterator();
		int result = 0;
		while ( itr.hasNext() ) {
			if ( o.equals( itr.next() ) ) {
				result++;
			}
		}
		return result;
	}

	// List OPERATIONS:

	@Override
	@SuppressWarnings("unchecked")
	public void add(int i, Object o) {
		write();
		bag.add( i, o );
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean addAll(int i, Collection c) {
		if ( c.size() > 0 ) {
			write();
			return bag.addAll( i, c );
		}
		else {
			return false;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object get(int i) {
		read();
		return bag.get( i );
	}

	@Override
	@SuppressWarnings("unchecked")
	public int indexOf(Object o) {
		read();
		return bag.indexOf( o );
	}

	@Override
	@SuppressWarnings("unchecked")
	public int lastIndexOf(Object o) {
		read();
		return bag.lastIndexOf( o );
	}

	@Override
	@SuppressWarnings("unchecked")
	public ListIterator listIterator() {
		read();
		return new ListIteratorProxy( bag.listIterator() );
	}

	@Override
	@SuppressWarnings("unchecked")
	public ListIterator listIterator(int i) {
		read();
		return new ListIteratorProxy( bag.listIterator( i ) );
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object remove(int i) {
		write();
		return bag.remove( i );
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object set(int i, Object o) {
		write();
		return bag.set( i, o );
	}

	@Override
	@SuppressWarnings("unchecked")
	public List subList(int start, int end) {
		read();
		return new ListProxy( bag.subList( start, end ) );
	}

	@Override
	public boolean entryExists(Object entry, int i) {
		return entry!=null;
	}

	@Override
	public String toString() {
		read();
		return bag.toString();
	}

	/**
	 * Bag does not respect the collection API and do an
	 * JVM instance comparison to do the equals.
	 * The semantic is broken not to have to initialize a
	 * collection for a simple equals() operation.
	 * @see java.lang.Object#equals(java.lang.Object)
	 *
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj) {
		return super.equals( obj );
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

	final class Clear implements DelayedOperation {
		@Override
		public void operate() {
			bag.clear();
		}

		@Override
		public Object getAddedInstance() {
			return null;
		}

		@Override
		public Object getOrphan() {
			throw new UnsupportedOperationException("queued clear cannot be used with orphan delete");
		}
	}

	final class SimpleAdd extends AbstractValueDelayedOperation {

		public SimpleAdd(Object addedValue) {
			super( addedValue, null );
		}

		@Override
		@SuppressWarnings("unchecked")
		public void operate() {
			bag.add( getAddedInstance() );
		}
	}

}
