/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpa.guide.collection;

import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.SortComparator;
import org.hibernate.jpa.test.BaseEntityManagerFunctionalTestCase;
import org.jboss.logging.Logger;
import org.junit.Test;

import javax.persistence.*;
import java.util.*;

import static org.hibernate.jpa.test.util.TransactionUtil.doInJPA;
import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class BidirectionalComparatorSortedSetTest extends BaseEntityManagerFunctionalTestCase {

    private static final Logger log = Logger.getLogger( BidirectionalComparatorSortedSetTest.class );

    @Override
    protected Class<?>[] getAnnotatedClasses() {
        return new Class<?>[] {
            Person.class,
                Phone.class,
        };
    }

    @Test
    public void testLifecycle() {
        doInJPA(this::entityManagerFactory, entityManager -> {
            Person person = new Person(1L);
            entityManager.persist(person);
            person.addPhone(new Phone(1L, "landline", "028-234-9876"));
            person.addPhone(new Phone(2L, "mobile", "072-122-9876"));
        });
        doInJPA(this::entityManagerFactory, entityManager -> {
            Person person = entityManager.find(Person.class, 1L);
            Set<Phone> phones = person.getPhones();
            assertEquals(2, phones.size());
            phones.stream().forEach(phone -> log.infov("Phone number %s", phone.getNumber()));
            person.removePhone(phones.iterator().next());
            assertEquals(1, phones.size());
        });
        doInJPA(this::entityManagerFactory, entityManager -> {
            Person person = entityManager.find(Person.class, 1L);
            Set<Phone> phones = person.getPhones();
            assertEquals(1, phones.size());
        });
    }

    @Entity(name = "Person")
    public static class Person  {

        @Id
        private Long id;

        public Person() {}

        public Person(Long id) {
            this.id = id;
        }

        @OneToMany(mappedBy = "person", cascade = CascadeType.ALL)
        @SortComparator(ReverseComparator.class)
        private SortedSet<Phone> phones = new TreeSet<>();

        public Set<Phone> getPhones() {
            return phones;
        }

        public void addPhone(Phone phone) {
            phones.add(phone);
            phone.setPerson(this);
        }

        public void removePhone(Phone phone) {
            phones.remove(phone);
            phone.setPerson(null);
        }
    }

    public static class ReverseComparator implements Comparator<Phone> {
        @Override
        public int compare(Phone o1, Phone o2) {
            return o2.compareTo(o1);
        }
    }

    @Entity(name = "Phone")
    public static class Phone implements Comparable<Phone> {

        @Id
        private Long id;

        private String type;

        @Column(unique = true)
        @NaturalId
        private String number;

        @ManyToOne
        private Person person;

        public Phone() {
        }

        public Phone(Long id, String type, String number) {
            this.id = id;
            this.type = type;
            this.number = number;
        }

        public Long getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getNumber() {
            return number;
        }

        public Person getPerson() {
            return person;
        }

        public void setPerson(Person person) {
            this.person = person;
        }

        @Override
        public int compareTo(Phone o) {
            return number.compareTo(o.getNumber());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Phone phone = (Phone) o;
            return Objects.equals(number, phone.number);
        }

        @Override
        public int hashCode() {
            return Objects.hash(number);
        }
    }
}
