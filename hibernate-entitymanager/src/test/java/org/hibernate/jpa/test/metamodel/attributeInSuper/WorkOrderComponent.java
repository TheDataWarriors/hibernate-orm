/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpa.test.metamodel.attributeInSuper;

import javax.persistence.EmbeddedId;

/**
 * @author Steve Ebersole
 */
@javax.persistence.Entity
public class WorkOrderComponent {
	@EmbeddedId
	private WorkOrderComponentId id;
}
