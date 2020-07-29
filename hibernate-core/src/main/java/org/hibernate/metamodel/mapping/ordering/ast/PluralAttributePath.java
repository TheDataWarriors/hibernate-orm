/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping.ordering.ast;

import org.hibernate.metamodel.mapping.CollectionPart;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.mapping.ordering.TranslationContext;
import org.hibernate.query.NavigablePath;

/**
 * Represents the collection as a DomainPath
 *
 * @see RootSequencePart
 *
 * @author Steve Ebersole
 */
public class PluralAttributePath implements DomainPath {
	private final NavigablePath navigablePath;
	private final PluralAttributeMapping pluralAttributeMapping;

	PluralAttributePath(PluralAttributeMapping pluralAttributeMapping) {
		this.navigablePath = new NavigablePath( pluralAttributeMapping.getRootPathName() );
		this.pluralAttributeMapping = pluralAttributeMapping;
	}

	@Override
	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	@Override
	public DomainPath getLhs() {
		return null;
	}

	@Override
	public PluralAttributeMapping getReferenceModelPart() {
		return pluralAttributeMapping;
	}

	@Override
	public DomainPath resolvePathPart(
			String name,
			boolean isTerminal,
			TranslationContext translationContext) {
		final ModelPart subPart = pluralAttributeMapping.findSubPart( name, null );

		if ( subPart != null ) {
			if ( subPart instanceof CollectionPart ) {
				return new CollectionPartPath( this, (CollectionPart) subPart );
			}
			else if ( !( subPart instanceof EmbeddableValuedModelPart ) ) {
				final CollectionPartPath elementPath = new CollectionPartPath(
						this,
						pluralAttributeMapping.getElementDescriptor()
				);

				return new DomainPathContinuation(
						elementPath.getNavigablePath().append( name ),
						this,
						pluralAttributeMapping.getElementDescriptor()
				);
			}
		}

		// the above checks for explicit element or index descriptor references
		// 		try also as an implicit element or index sub-part reference...

		if ( pluralAttributeMapping.getElementDescriptor() instanceof EmbeddableValuedModelPart ) {
			final EmbeddableValuedModelPart elementDescriptor = (EmbeddableValuedModelPart) pluralAttributeMapping.getElementDescriptor();
			final ModelPart elementSubPart = elementDescriptor.findSubPart( name, null );
			if ( elementSubPart != null ) {
				// create the CollectionSubPath to use as the `lhs` for the element sub-path
				final CollectionPartPath elementPath = new CollectionPartPath(
						this,
						(CollectionPart) elementDescriptor
				);

				return new DomainPathContinuation(
						elementPath.getNavigablePath().append( name ),
						this,
						elementSubPart
				);
			}
		}

		if ( pluralAttributeMapping.getIndexDescriptor() instanceof EmbeddableValuedModelPart ) {
			final EmbeddableValuedModelPart indexDescriptor = (EmbeddableValuedModelPart) pluralAttributeMapping.getIndexDescriptor();
			final ModelPart indexSubPart = indexDescriptor.findSubPart( name, null );
			if ( indexSubPart != null ) {
				// create the CollectionSubPath to use as the `lhs` for the element sub-path
				final CollectionPartPath indexPath = new CollectionPartPath(
						this,
						(CollectionPart) indexDescriptor
				);
				return new DomainPathContinuation(
						indexPath.getNavigablePath().append( name ),
						this,
						indexSubPart
				);
			}
		}

		return null;
	}
}
