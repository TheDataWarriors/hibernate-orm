/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpa.spi;

import org.hibernate.Remove;

import java.util.Map;

/**
 * Provides a set of {@link org.hibernate.id.IdentifierGenerator} strategies,
 * overriding the default strategies.
 *
 * @author Emmanuel Bernard
 *
 * @deprecated supply a {@link org.hibernate.id.factory.spi.GenerationTypeStrategyRegistration} instead
 */
@Deprecated(since = "6.0", forRemoval = true)
public interface IdentifierGeneratorStrategyProvider {
	/**
	 * set of strategy / generator class pairs to register as accepted strategies
	 */
	Map<String,Class<?>> getStrategies();
}
