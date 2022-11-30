/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping.internal;

import org.hibernate.engine.FetchStyle;
import org.hibernate.engine.FetchTiming;
import org.hibernate.metamodel.mapping.AttributeMetadataAccess;
import org.hibernate.metamodel.mapping.ManagedMappingType;
import org.hibernate.metamodel.mapping.SingularAttributeMapping;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.sql.results.graph.FetchOptions;
import org.hibernate.tuple.Generator;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractSingularAttributeMapping
		extends AbstractStateArrayContributorMapping
		implements SingularAttributeMapping {

	private final PropertyAccess propertyAccess;
	private final Generator valueGeneration;

	public AbstractSingularAttributeMapping(
			String name,
			int stateArrayPosition,
			AttributeMetadataAccess attributeMetadataAccess,
			FetchOptions mappedFetchOptions,
			ManagedMappingType declaringType,
			PropertyAccess propertyAccess,
			Generator valueGeneration) {
		super( name, attributeMetadataAccess, mappedFetchOptions, stateArrayPosition, declaringType );
		this.propertyAccess = propertyAccess;
		this.valueGeneration = valueGeneration != null
				? valueGeneration
				: NoValueGeneration.INSTANCE;
	}

	public AbstractSingularAttributeMapping(
			String name,
			int stateArrayPosition,
			AttributeMetadataAccess attributeMetadataAccess,
			FetchTiming fetchTiming,
			FetchStyle fetchStyle,
			ManagedMappingType declaringType,
			PropertyAccess propertyAccess,
			Generator valueGeneration) {
		super( name, attributeMetadataAccess, fetchTiming, fetchStyle, stateArrayPosition, declaringType );
		this.propertyAccess = propertyAccess;
		this.valueGeneration = valueGeneration != null
				? valueGeneration
				: NoValueGeneration.INSTANCE;
	}

	@Override
	public PropertyAccess getPropertyAccess() {
		return propertyAccess;
	}

	@Override
	public Generator getValueGeneration() {
		return valueGeneration;
	}
}
