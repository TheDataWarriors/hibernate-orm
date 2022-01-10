/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.model.domain.internal;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Member;

import jakarta.persistence.metamodel.Attribute;

import org.hibernate.metamodel.AttributeClassification;
import org.hibernate.metamodel.internal.MetadataContext;
import org.hibernate.metamodel.model.domain.DomainType;
import org.hibernate.metamodel.model.domain.ManagedDomainType;
import org.hibernate.metamodel.model.domain.PersistentAttribute;
import org.hibernate.metamodel.model.domain.SimpleDomainType;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * Models the commonality of the JPA {@link Attribute} hierarchy.
 *
 * @param <D> The type of the class (D)eclaring this attribute
 * @param <J> The (J)ava type of this attribute
 *
 * @author Steve Ebersole
 */
public abstract class AbstractAttribute<D,J,B> implements PersistentAttribute<D,J>, Serializable {
	private final ManagedDomainType<D> declaringType;
	private final String name;
	private final JavaType<J> attributeJtd;

	private final AttributeClassification attributeClassification;

	private final DomainType<B> valueType;
	private transient Member member;

	@SuppressWarnings("WeakerAccess")
	protected AbstractAttribute(
			ManagedDomainType<D> declaringType,
			String name,
			JavaType<J> attributeJtd,
			AttributeClassification attributeClassification,
			DomainType<B> valueType,
			Member member,
			MetadataContext metadataContext) {
		this.declaringType = declaringType;
		this.name = name;
		this.attributeJtd = attributeJtd;
		this.attributeClassification = attributeClassification;
		this.valueType = valueType;
		this.member = member;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Class<J> getJavaType() {
		return attributeJtd.getJavaTypeClass();
	}

	public DomainType<B> getSqmPathType() {
		return valueType;
	}

	@Override
	public JavaType<J> getAttributeJavaTypeDescriptor() {
		return attributeJtd;
	}

	@Override
	public ManagedDomainType<D> getDeclaringType() {
		return declaringType;
	}

	@Override
	public Member getJavaMember() {
		return member;
	}

	@Override
	public AttributeClassification getAttributeClassification() {
		return attributeClassification;
	}

	@Override
	public PersistentAttributeType getPersistentAttributeType() {
		return getAttributeClassification().getJpaClassification();
	}

	@Override
	public DomainType<?> getValueGraphType() {
		return valueType;
	}

	@Override
	public String toString() {
		return declaringType.getTypeName() + '#' + name + '(' + attributeClassification + ')';
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Serialization

	protected Object writeReplace() throws ObjectStreamException {
		return new SerialForm( declaringType, name );
	}

	private static class SerialForm implements Serializable {
		private final ManagedDomainType<?> declaringType;
		private final String name;

		public SerialForm(ManagedDomainType<?> declaringType, String name) {
			this.declaringType = declaringType;
			this.name = name;
		}

		private Object readResolve() {
			return declaringType.findAttribute( name );
		}

	}
}
