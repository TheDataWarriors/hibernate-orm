/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.bytecode.enhancement.dirty;

import org.hibernate.testing.bytecode.enhancement.BytecodeEnhancerRunner;
import org.hibernate.testing.bytecode.enhancement.EnhancerTestUtils;
import org.hibernate.testing.orm.junit.JiraKey;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@JiraKey( "HHH-16774" )
@JiraKey( "HHH-16952" )
@RunWith( BytecodeEnhancerRunner.class )
public class DirtyTrackingEmbeddableTest {

    @Test
    public void test() {
        SimpleEntity entity = new SimpleEntity();
        Address1 address1 = new Address1();
        entity.address1 = address1;
        Address2 address2 = new Address2();
        entity.address2 = address2;
        EnhancerTestUtils.clearDirtyTracking( entity );

        // testing composite object
        address1.city = "Arendal";
        address2.city = "Arendal";
        // In 6.2, we can't change the SPI to make HHH-16952 possible,
        // but we make sure to at least don't fail with an exception
        EnhancerTestUtils.checkDirtyTracking( entity, "address1" );
        EnhancerTestUtils.clearDirtyTracking( entity );
    }

    // --- //

    @Embeddable
    private static class Address1 {
        String street1;
        String street2;
        String city;
        String state;
        String zip;
        String phone;
    }

    private static class Address2 {
        String street1;
        String street2;
        String city;
        String state;
        String zip;
        String phone;
    }

    @Entity
    private static class SimpleEntity {

        @Id
        Long id;

        String name;

        Address1 address1;
        @Embedded
        Address2 address2;

    }
}
