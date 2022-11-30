/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.tuple;

import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.PostInsertIdentifierGenerator;
import org.hibernate.type.Type;

/**
 * Represents a defined entity identifier property within the Hibernate
 * runtime-metamodel.
 *
 * @author Steve Ebersole
 */
public class IdentifierProperty extends AbstractAttribute implements IdentifierAttribute {

	private final boolean virtual;
	private final boolean embedded;
	private final InMemoryGenerator identifierGenerator;
	private final boolean identifierAssignedByInsert;
	private final boolean hasIdentifierMapper;

	/**
	 * Construct a non-virtual identifier property.
	 *
	 * @param name The name of the property representing the identifier within
	 * its owning entity.
	 * @param type The Hibernate Type for the identifier property.
	 * @param embedded Is this an embedded identifier.
	 * property, represents new (i.e., un-saved) instances of the owning entity.
	 * @param identifierGenerator The generator to use for id value generation.
	 */
	public IdentifierProperty(
			String name,
			Type type,
			boolean embedded,
			InMemoryGenerator identifierGenerator) {
		super( name, type );
		this.virtual = false;
		this.embedded = embedded;
		this.hasIdentifierMapper = false;
		this.identifierGenerator = identifierGenerator;
		this.identifierAssignedByInsert = identifierGenerator instanceof PostInsertIdentifierGenerator;
	}

	/**
	 * Construct a virtual IdentifierProperty.
	 *
	 * @param type The Hibernate Type for the identifier property.
	 * @param embedded Is this an embedded identifier.
	 * property, represents new (i.e., un-saved) instances of the owning entity.
	 * @param identifierGenerator The generator to use for id value generation.
	 */
	public IdentifierProperty(
			Type type,
			boolean embedded,
			boolean hasIdentifierMapper,
			InMemoryGenerator identifierGenerator) {
		super( null, type );
		this.virtual = true;
		this.embedded = embedded;
		this.hasIdentifierMapper = hasIdentifierMapper;
		this.identifierGenerator = identifierGenerator;
		this.identifierAssignedByInsert = identifierGenerator instanceof PostInsertIdentifierGenerator;
	}

	@Override
	public boolean isVirtual() {
		return virtual;
	}

	@Override
	public boolean isEmbedded() {
		return embedded;
	}

	@Override
	public IdentifierGenerator getIdentifierGenerator() {
		return (IdentifierGenerator) identifierGenerator;
	}

	@Override
	public InMemoryGenerator getGenerator() {
		return identifierGenerator;
	}

	@Override
	public boolean isIdentifierAssignedByInsert() {
		return identifierAssignedByInsert;
	}

	@Override
	public boolean hasIdentifierMapper() {
		return hasIdentifierMapper;
	}

	@Override
	public String toString() {
		return "IdentifierAttribute(" + getName() + ")";
	}
}
