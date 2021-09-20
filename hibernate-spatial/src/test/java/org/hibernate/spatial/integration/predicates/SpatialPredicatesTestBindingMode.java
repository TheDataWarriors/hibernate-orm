/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.spatial.integration.predicates;

import java.util.stream.Stream;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.spatial.testing.IsSupportedBySpatial;
import org.hibernate.spatial.testing.dialects.PredicateRegexes;

import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.Setting;

@RequiresDialectFeature(feature = IsSupportedBySpatial.class)
@ServiceRegistry(settings = {
		@Setting(name = AvailableSettings.CRITERIA_VALUE_HANDLING_MODE, value = "BIND")
})
@SessionFactory
public class SpatialPredicatesTestBindingMode extends SpatialPredicatesTest {
	@Override
	public Stream<PredicateRegexes.PredicateRegex> getTestRegexes() {
		return  super.predicateRegexes.bindingModeRegexes();
	}
}
