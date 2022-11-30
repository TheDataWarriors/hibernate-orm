/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping;

import org.hibernate.Internal;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.tuple.GenerationTiming;
import org.hibernate.tuple.InMemoryGenerator;

/**
 * GeneratedValueResolver impl for in-memory generation
 *
 * @author Steve Ebersole
 */
@Internal
public class InMemoryGeneratedValueResolver implements GeneratedValueResolver {
	private final GenerationTiming generationTiming;
	private final InMemoryGenerator valueGenerator;

	public InMemoryGeneratedValueResolver(InMemoryGenerator valueGenerator, GenerationTiming generationTiming) {
		this.valueGenerator = valueGenerator;
		this.generationTiming = generationTiming;
	}

	@Override
	public GenerationTiming getGenerationTiming() {
		return generationTiming;
	}

	@Override
	public Object resolveGeneratedValue(Object[] row, Object entity, SharedSessionContractImplementor session, Object currentValue) {
		return valueGenerator.generate( session, entity, currentValue );
	}
}
