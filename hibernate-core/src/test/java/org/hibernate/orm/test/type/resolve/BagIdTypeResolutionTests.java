/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.type.resolve;

import java.util.List;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.CollectionId;
import org.hibernate.annotations.Type;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.IdentifierBag;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.type.BasicType;
import org.hibernate.type.ShortType;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.DomainModelScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Steve Ebersole
 */
@DomainModel( annotatedClasses = BagIdTypeResolutionTests.EntityWithBag.class )
public class BagIdTypeResolutionTests {
	@Test
	public void testBagIdResolution(DomainModelScope scope) {
		final PersistentClass entityDescriptor = scope.getDomainModel().getEntityBinding( EntityWithBag.class.getName() );
		final Property namesDescriptor = entityDescriptor.getProperty( "names" );
		final IdentifierBag namesTypeDescriptor = (IdentifierBag) namesDescriptor.getValue();
		final BasicValue identifier = (BasicValue) namesTypeDescriptor.getIdentifier();
		final BasicValue.Resolution<?> identifierResolution = identifier.resolve();

		final BasicType<?> legacyResolvedBasicType = identifierResolution.getLegacyResolvedBasicType();
		assertThat( legacyResolvedBasicType, instanceOf( ShortType.class ) );
		assertThat( identifier.getIdentifierGeneratorStrategy(), equalTo( "increment" ) );
	}

	@Entity( name = "EntityWithBag" )
	@Table( name = "entity_with_bag" )
	public static class EntityWithBag {
		@Id
		private Integer id;
		private String name;
		@ElementCollection
		@CollectionId(
				columns = @Column( name = "bag_id" ),
				type = @Type( type = "short" ),
				generator = "increment"
		)
		private List<String> names;
	}
}
