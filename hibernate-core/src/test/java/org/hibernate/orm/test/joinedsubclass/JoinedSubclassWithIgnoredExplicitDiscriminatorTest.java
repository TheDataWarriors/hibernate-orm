/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.joinedsubclass;

import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.JoinedSubclassEntityPersister;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Steve Ebersole
 */
@TestForIssue(jiraKey = "HHH-6911")
@DomainModel(
		annotatedClasses = {
				JoinedSubclassWithIgnoredExplicitDiscriminatorTest.Animal.class,
				JoinedSubclassWithIgnoredExplicitDiscriminatorTest.Cat.class,
				JoinedSubclassWithIgnoredExplicitDiscriminatorTest.Dog.class
		}
)
@SessionFactory
@ServiceRegistry(
		settings =
		@Setting(
				name = AvailableSettings.IGNORE_EXPLICIT_DISCRIMINATOR_COLUMNS_FOR_JOINED_SUBCLASS,
				value = "true"))
public class JoinedSubclassWithIgnoredExplicitDiscriminatorTest {


	@Test
	public void metadataAssertions(SessionFactoryScope scope) {
		EntityPersister p = scope.getSessionFactory().getEntityPersister( Dog.class.getName() );
		assertNotNull( p );
		final JoinedSubclassEntityPersister dogPersister = assertTyping( JoinedSubclassEntityPersister.class, p );
		assertEquals( "integer", dogPersister.getDiscriminatorType().getName() );
		assertEquals( "clazz_", dogPersister.getDiscriminatorColumnName() );
		assertTrue( Integer.class.isInstance( dogPersister.getDiscriminatorValue() ) );

		p = scope.getSessionFactory().getEntityPersister( Cat.class.getName() );
		assertNotNull( p );
		final JoinedSubclassEntityPersister catPersister = assertTyping( JoinedSubclassEntityPersister.class, p );
		assertEquals( "integer", catPersister.getDiscriminatorType().getName() );
		assertEquals( "clazz_", catPersister.getDiscriminatorColumnName() );
		assertTrue( Integer.class.isInstance( catPersister.getDiscriminatorValue() ) );
	}

	@Test
	public void basicUsageTest(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.save( new Cat( 1 ) );
					session.save( new Dog( 2 ) );
				}
		);

		scope.inTransaction(
				session -> {
					session.createQuery( "from Animal" ).list();
					Cat cat = session.get( Cat.class, 1 );
					assertNotNull( cat );
					session.delete( cat );
					Dog dog = session.get( Dog.class, 2 );
					assertNotNull( dog );
					session.delete( dog );
				}
		);
	}

	public <T> T assertTyping(Class<T> expectedType, Object value) {
		if ( !expectedType.isInstance( value ) ) {
			fail(
					String.format(
							"Expecting value of type [%s], but found [%s]",
							expectedType.getName(),
							value == null ? "<null>" : value
					)
			);
		}
		return (T) value;
	}

	@Entity(name = "Animal")
	@Table(name = "animal")
	@Inheritance(strategy = InheritanceType.JOINED)
	@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
	@DiscriminatorValue(value = "???animal???")
	public static abstract class Animal {
		@Id
		public Integer id;

		protected Animal() {
		}

		protected Animal(Integer id) {
			this.id = id;
		}
	}

	@Entity(name = "Cat")
	@DiscriminatorValue(value = "cat")
	public static class Cat extends Animal {
		public Cat() {
			super();
		}

		public Cat(Integer id) {
			super( id );
		}
	}

	@Entity(name = "Dog")
	@DiscriminatorValue(value = "dog")
	public static class Dog extends Animal {
		public Dog() {
			super();
		}

		public Dog(Integer id) {
			super( id );
		}
	}
}
