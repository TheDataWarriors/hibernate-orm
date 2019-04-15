/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.model.domain.spi;

/**
 * @author Steve Ebersole
 */
public interface EntityIdentifierCompositeAggregated<O,J>
		extends EntityIdentifierComposite<O,J>, SingularPersistentAttribute<O,J> {
	@Override
	default void visitNavigable(NavigableVisitationStrategy visitor) {
		visitor.visitAggregateCompositeIdentifier( this );
	}

	@Override
	default int getNumberOfJdbcParametersNeeded() {
		return getColumns().size();
	}

	@Override
	default Class<J> getJavaType() {
		return getJavaTypeDescriptor().getJavaType();
	}
}
