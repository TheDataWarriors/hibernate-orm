/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.mapping.naturalid.inheritance;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Table;

import org.hibernate.annotations.NaturalId;
import org.hibernate.persister.entity.EntityPersister;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.FailureExpected;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@DomainModel( annotatedClasses = { MappedSuperclassOverrideTest.MyMappedSuperclass.class, MappedSuperclassOverrideTest.MyEntity.class } )
@SessionFactory( exportSchema = false )
public class MappedSuperclassOverrideTest {
	@Test
	public void testModel(SessionFactoryScope scope) {
		final EntityPersister entityPersister = scope.getSessionFactory()
				.getRuntimeMetamodels()
				.getEntityMappingType( MyEntity.class )
				.getEntityPersister();

		// defining a natural-id on a sub-entity is not allowed, only on the root.
		// 		- here, because the root does not declare `#getName` as a natural-id
		//		the hierarchy does not define a natural-id
		assertFalse( entityPersister.hasNaturalIdentifier() );
	}

	@MappedSuperclass
	public abstract static class MyMappedSuperclass {
		private Integer id;
		private String name;

		public MyMappedSuperclass() {
		}

		public MyMappedSuperclass(Integer id, String name) {
			this.id = id;
			this.name = name;
		}

		@Id
		public Integer getId() {
			return id;
		}

		protected void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	@Entity( name = "MyEntity" )
	@Table( name = "the_entity" )
	public static class MyEntity extends MyMappedSuperclass {
		public MyEntity() {
			super();
		}

		public MyEntity(Integer id, String name) {
			super( id, name );
		}

		@Override
		@NaturalId
		public String getName() {
			return super.getName();
		}
	}
}
