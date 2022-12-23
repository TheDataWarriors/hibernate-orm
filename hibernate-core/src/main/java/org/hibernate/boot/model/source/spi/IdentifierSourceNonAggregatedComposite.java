/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.model.source.spi;

import java.util.List;

/**
 * Additional contract describing the source of an identifier mapping whose {@linkplain #getNature() nature} is
 * {@link org.hibernate.id.EntityIdentifierNature#NON_AGGREGATED_COMPOSITE}.
 * <p>
 * Think {@link jakarta.persistence.IdClass}
 *
 * @author Steve Ebersole
 */
public interface IdentifierSourceNonAggregatedComposite extends CompositeIdentifierSource {
	/**
	 * Obtain the source descriptor for the identifier attribute.
	 *
	 * @return The identifier attribute source.
	 */
	List<SingularAttributeSource> getAttributeSourcesMakingUpIdentifier();

	/**
	 * Retrieve the source information for the {@link jakarta.persistence.IdClass} definition
	 *
	 * @return The IdClass source information, or {@code null} if none.
	 */
	EmbeddableSource getIdClassSource();
}
