/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.spi;

import org.jboss.jandex.IndexView;

/**
 * Contract for contributing to {@link org.hibernate.boot.Metadata} ({@link InFlightMetadataCollector}).
 * <p>
 * This hook occurs just after all processing of all {@link org.hibernate.boot.MetadataSources},
 * and just before {@link AdditionalJaxbMappingProducer}.
 *
 * @author Steve Ebersole
 *
 * @since 5.0
 *
 * @deprecated Use {@link AdditionalMappingContributor} or {@link org.hibernate.boot.model.TypeContributor}
 * instead depending on need
 */
@Deprecated(forRemoval = true)
public interface MetadataContributor {
	/**
	 * Perform the contributions.
	 *
	 * @param metadataCollector The metadata collector, representing the in-flight metadata being built
	 * @param jandexIndex The Jandex index
	 */
	void contribute(InFlightMetadataCollector metadataCollector, IndexView jandexIndex);
}
