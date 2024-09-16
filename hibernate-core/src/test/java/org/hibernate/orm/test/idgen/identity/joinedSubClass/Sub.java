/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.idgen.identity.joinedSubClass;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;

/**
 * @author Andrey Vlasov
 * @author Steve Ebersole
 */
@Entity
@PrimaryKeyJoinColumn(name = "super_id")
public class Sub extends Super {
}
