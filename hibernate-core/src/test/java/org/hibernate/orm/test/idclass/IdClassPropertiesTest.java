/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.idclass;

import java.io.Serializable;

import org.hibernate.AnnotationException;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import org.hibernate.testing.orm.junit.Jira;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Marco Belladelli
 */
@Jira( "https://hibernate.atlassian.net/browse/HHH-16761" )
public class IdClassPropertiesTest {
	@Test
	public void testRight() {
		try (StandardServiceRegistry ssr = new StandardServiceRegistryBuilder().build()) {
			final MetadataSources metadataSources = new MetadataSources( ssr )
					.addAnnotatedClass( RightEntity.class );
			assertDoesNotThrow( () -> metadataSources.buildMetadata() );
		}
	}

	@Test
	public void testWrongLess() {
		try (StandardServiceRegistry ssr = new StandardServiceRegistryBuilder().build()) {
			final MetadataSources metadataSources = new MetadataSources( ssr )
					.addAnnotatedClass( WrongEntityLess.class );
			final AnnotationException thrown = assertThrows( AnnotationException.class, metadataSources::buildMetadata );
			assertTrue( thrown.getMessage().contains( "childId' belongs to an '@IdClass' but has no matching property in entity class" ) );
		}
	}

	@Test
	public void testWrongMore() {
		try (StandardServiceRegistry ssr = new StandardServiceRegistryBuilder().build()) {
			final MetadataSources metadataSources = new MetadataSources( ssr )
					.addAnnotatedClass( WrongEntityMore.class );
			final AnnotationException thrown = assertThrows( AnnotationException.class, metadataSources::buildMetadata );
			assertTrue( thrown.getMessage().contains( "'anotherId' which do not match properties of the specified '@IdClass'" ) );
		}
	}

	public static class ParentPK implements Serializable {
		private Long parentId;
	}

	public static class ChildPK extends ParentPK {
		private String childId;
	}

	@Entity( name = "RightEntity" )
	@IdClass( ChildPK.class )
	public static class RightEntity {
		@Id
		private Long parentId;
		@Id
		private String childId;
		private String nonIdProp;
	}

	@Entity( name = "WrongEntityLess" )
	@IdClass( ChildPK.class )
	public static class WrongEntityLess {
		@Id
		private Long parentId;
	}

	@Entity( name = "WrongEntityMore" )
	@IdClass( ChildPK.class )
	public static class WrongEntityMore {
		@Id
		private Long parentId;
		@Id
		private String childId;
		@Id
		private Integer anotherId;
	}
}
