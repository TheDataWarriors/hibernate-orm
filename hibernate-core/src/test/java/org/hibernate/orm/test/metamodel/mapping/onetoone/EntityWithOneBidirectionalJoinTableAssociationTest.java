/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.metamodel.mapping.onetoone;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.metamodel.mapping.ForeignKeyDescriptor;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.internal.ToOneAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Andrea Boriero
 */
@DomainModel(
		annotatedClasses = {
				EntityWithOneBidirectionalJoinTableAssociationTest.Parent.class,
				EntityWithOneBidirectionalJoinTableAssociationTest.Child.class,
		}
)
@ServiceRegistry
@SessionFactory
public class EntityWithOneBidirectionalJoinTableAssociationTest {
	@Test
	public void basicTest(SessionFactoryScope scope) {
		final EntityPersister parentDescriptor = scope.getSessionFactory()
				.getMetamodel()
				.findEntityDescriptor( Parent.class );

		final ModelPart childAssociation = parentDescriptor.findSubPart( "child" );

		assertThat( childAssociation, instanceOf( ToOneAttributeMapping.class ) );

		final ToOneAttributeMapping childAttributeMapping = (ToOneAttributeMapping) childAssociation;

		ForeignKeyDescriptor foreignKeyDescriptor = childAttributeMapping.getForeignKeyDescriptor();
		foreignKeyDescriptor.visitReferringColumns( (keyTable, keyColumn, isKeyColumnFormula, jdbcMapping) -> {
			assertThat( keyTable, is( "PARENT_CHILD" ) );
			assertThat( keyColumn, is( "child_id" ) );
		} );

		foreignKeyDescriptor.visitTargetColumns( (targetTable, targetColumn, isTargetColumnFormula, jdbcMapping) -> {
			assertThat( targetTable, is( "CHILD" ) );
			assertThat( targetColumn, is( "id" ) );
		} );

		final EntityPersister childDescriptor = scope.getSessionFactory()
				.getMetamodel()
				.findEntityDescriptor( Child.class );

		final ModelPart parentAssociation = childDescriptor.findSubPart( "parent" );

		assertThat( parentAssociation, instanceOf( ToOneAttributeMapping.class ) );

		final ToOneAttributeMapping parentAttributeMapping = (ToOneAttributeMapping) parentAssociation;

		foreignKeyDescriptor = parentAttributeMapping.getForeignKeyDescriptor();
		foreignKeyDescriptor.visitReferringColumns( (keyTable, keyColumn, isKeyColumnFormula, jdbcMapping) -> {
			assertThat( keyTable, is( "PARENT_CHILD" ) );
			assertThat( keyColumn, is( "parent_id" ) );
		} );

		foreignKeyDescriptor.visitTargetColumns( (targetTable, targetColumn, isTargetColumnFormula, jdbcMapping) -> {
			assertThat( targetTable, is( "PARENT" ) );
			assertThat( targetColumn, is( "id" ) );
		} );
	}

	@Entity(name = "Parent")
	@Table(name = "PARENT")
	public static class Parent {
		private Integer id;

		private String description;
		private Child child;

		Parent() {
		}

		public Parent(Integer id, String description) {
			this.id = id;
			this.description = description;
		}

		@Id
		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		@OneToOne
		@JoinTable(name = "PARENT_CHILD", inverseJoinColumns = @JoinColumn(name = "child_id"), joinColumns = @JoinColumn(name = "parent_id"))
		public Child getChild() {
			return child;
		}

		public void setChild(Child other) {
			this.child = other;
		}

	}

	@Entity(name = "Child")
	@Table(name = "CHILD")
	public static class Child {
		private Integer id;

		private String name;
		private Parent parent;

		Child() {
		}

		Child(Integer id, Parent parent) {
			this.id = id;
			this.parent = parent;
			this.parent.setChild( this );
		}

		@Id
		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@OneToOne(mappedBy = "child")
		public Parent getParent() {
			return parent;
		}

		public void setParent(Parent parent) {
			this.parent = parent;
		}
	}

}
