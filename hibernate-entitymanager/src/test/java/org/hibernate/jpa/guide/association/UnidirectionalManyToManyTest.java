/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpa.guide.association;

import org.hibernate.jpa.test.BaseEntityManagerFunctionalTestCase;
import org.jboss.logging.Logger;
import org.junit.Test;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

import static org.hibernate.jpa.test.util.TransactionUtil.*;

/**
 * @author Vlad Mihalcea
 */
public class UnidirectionalManyToManyTest extends BaseEntityManagerFunctionalTestCase {

    private static final Logger log = Logger.getLogger( UnidirectionalManyToManyTest.class );

    @Override
    protected Class<?>[] getAnnotatedClasses() {
        return new Class<?>[] {
            Person.class,
            Address.class,
        };
    }

    @Test
    public void testLifecycle() {
        doInJPA(this::entityManagerFactory, entityManager -> {
            Person person1 = new Person();
            Person person2 = new Person();

            Address address1 = new Address("12th Avenue", "12A");
            Address address2 = new Address("18th Avenue", "18B");

            person1.getAddresses().add(address1);
            person1.getAddresses().add(address2);

            person2.getAddresses().add(address1);

            entityManager.persist(person1);
            entityManager.persist(person2);

            entityManager.flush();

            person1.getAddresses().remove(address1);
        });
    }

    @Test
    public void testRemove() {
        final Long personId = doInJPA(this::entityManagerFactory, entityManager -> {
            Person person1 = new Person();
            Person person2 = new Person();

            Address address1 = new Address("12th Avenue", "12A");
            Address address2 = new Address("18th Avenue", "18B");

            person1.getAddresses().add(address1);
            person1.getAddresses().add(address2);

            person2.getAddresses().add(address1);

            entityManager.persist(person1);
            entityManager.persist(person2);

            return person1.id;
        });
        doInJPA(this::entityManagerFactory, entityManager -> {
            log.info("Remove");
            Person person1 = entityManager.find(Person.class, personId);
            entityManager.remove(person1);
        });
    }

    @Entity(name = "Person")
    public static class Person  {

        @Id
        @GeneratedValue
        private Long id;

        public Person() {}

        @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE} )
        private List<Address> addresses = new ArrayList<>();

        public List<Address> getAddresses() {
            return addresses;
        }
    }

    @Entity(name = "Address")
    public static class Address  {

        @Id
        @GeneratedValue
        private Long id;

        private String street;

        private String number;

        public Address() {}

        public Address(String street, String number) {
            this.street = street;
            this.number = number;
        }

        public Long getId() {
            return id;
        }

        public String getStreet() {
            return street;
        }

        public String getNumber() {
            return number;
        }
    }
}
