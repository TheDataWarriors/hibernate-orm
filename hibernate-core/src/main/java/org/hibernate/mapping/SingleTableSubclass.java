/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.mapping;

import java.util.Iterator;
import java.util.List;

import org.hibernate.MappingException;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.internal.util.collections.JoinedIterator;
import org.hibernate.internal.util.collections.JoinedList;

/**
 * @author Gavin King
 */
public class SingleTableSubclass extends Subclass {

	public SingleTableSubclass(PersistentClass superclass, MetadataBuildingContext buildingContext) {
		super( superclass, buildingContext );
	}

	@Deprecated @SuppressWarnings("deprecation")
	protected Iterator<Property> getNonDuplicatedPropertyIterator() {
		return new JoinedIterator<>(
				getSuperclass().getUnjoinedPropertyIterator(),
				getUnjoinedPropertyIterator()
		);
	}

	protected List<Property> getNonDuplicatedProperties() {
		return new JoinedList<>( getSuperclass().getUnjoinedProperties(), getUnjoinedProperties() );
	}

	@Deprecated @SuppressWarnings("deprecation")
	protected Iterator<Selectable> getDiscriminatorColumnIterator() {
		return isDiscriminatorInsertable() && !getDiscriminator().hasFormula()
				? getDiscriminator().getColumnIterator()
				: super.getDiscriminatorColumnIterator();
	}

	public Object accept(PersistentClassVisitor mv) {
		return mv.accept( this );
	}

	public void validate(Metadata mapping) throws MappingException {
		if ( getDiscriminator() == null ) {
			throw new MappingException(
					"No discriminator found for " + getEntityName()
							+ ". Discriminator is needed when 'single-table-per-hierarchy' "
							+ "is used and a class has subclasses"
			);
		}
		super.validate( mapping );
	}
}
