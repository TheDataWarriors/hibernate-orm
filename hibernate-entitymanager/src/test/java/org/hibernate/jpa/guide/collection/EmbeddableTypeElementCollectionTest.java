/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpa.guide.collection;

import org.hibernate.jpa.test.BaseEntityManagerFunctionalTestCase;
import org.junit.Test;

import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.ArrayList;
import java.util.List;

import static org.hibernate.jpa.test.util.TransactionUtil.doInJPA;

/**
 * @author Vlad Mihalcea
 */
public class EmbeddableTypeElementCollectionTest extends BaseEntityManagerFunctionalTestCase {

    @Override
    protected Class<?>[] getAnnotatedClasses() {
        return new Class<?>[] {
            Person.class
        };
    }

    @Test
    public void testLifecycle() {
        doInJPA(this::entityManagerFactory, entityManager -> {
            Person person = new Person();
            person.id = 1L;
            person.getPhones().add(new Phone("landline", "028-234-9876"));
            person.getPhones().add(new Phone("mobile", "072-122-9876"));
            entityManager.persist(person);
        });
    }

    @Entity(name = "Person")
    public static class Person  {

        @Id
        private Long id;

        @ElementCollection
        private List<Phone> phones = new ArrayList<>();

        public List<Phone> getPhones() {
            return phones;
        }
    }

    @Embeddable
    public static class Phone  {

        private String type;

        private String number;

        public Phone() {
        }

        public Phone(String type, String number) {
            this.type = type;
            this.number = number;
        }

        public String getType() {
            return type;
        }

        public String getNumber() {
            return number;
        }
    }
}
