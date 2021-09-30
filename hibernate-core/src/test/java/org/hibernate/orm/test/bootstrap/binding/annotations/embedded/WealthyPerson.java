/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.bootstrap.binding.annotations.embedded;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;

@Entity
public class WealthyPerson extends Person {

	@ElementCollection
	protected Set<Address> vacationHomes = new HashSet<Address>();

	@ElementCollection
	@CollectionTable(name = "WelPers_LegacyVacHomes")
	protected Set<Address> legacyVacationHomes = new HashSet<Address>();

	@ElementCollection
	@CollectionTable(name = "WelPers_VacHomes", indexes = @Index( columnList = "countryName, type_id"))
	protected Set<Address> explicitVacationHomes = new HashSet<Address>();
}
