/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.id.uuid;

import java.util.UUID;

import org.hibernate.id.uuid.CustomVersionOneStrategy;

import org.hibernate.testing.junit5.BaseUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;


/**
 * @author Steve Ebersole
 */
public class CustomVersionOneStrategyTest extends BaseUnitTest {

	@Test
	public void testUniqueCounter() {
		CustomVersionOneStrategy strategy = new CustomVersionOneStrategy();
		long now = System.currentTimeMillis();
		UUID uuid1 = new UUID(
				strategy.getMostSignificantBits(),
				CustomVersionOneStrategy.generateLeastSignificantBits( now )
		);
		assertEquals( 2, uuid1.variant() );
		assertEquals( 1, uuid1.version() );

		for ( int i = 0; i < 100; i++ ) {
			UUID uuidX = new UUID(
					strategy.getMostSignificantBits(),
					CustomVersionOneStrategy.generateLeastSignificantBits( now )
			);
			assertEquals( 2, uuidX.variant() );
			assertEquals( 1, uuidX.version() );
			assertFalse( uuid1.equals( uuidX ) );
			assertEquals( uuid1.getMostSignificantBits(), uuidX.getMostSignificantBits() );
		}
	}

	@Test
	public void testRangeOfValues() {
		CustomVersionOneStrategy strategy = new CustomVersionOneStrategy();

		UUID uuid = new UUID(
				strategy.getMostSignificantBits(),
				CustomVersionOneStrategy.generateLeastSignificantBits( 0 )
		);
		assertEquals( 2, uuid.variant() );
		assertEquals( 1, uuid.version() );

		uuid = new UUID(
				strategy.getMostSignificantBits(),
				CustomVersionOneStrategy.generateLeastSignificantBits( Long.MAX_VALUE )
		);
		assertEquals( 2, uuid.variant() );
		assertEquals( 1, uuid.version() );
	}
}
