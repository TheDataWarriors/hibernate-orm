/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.annotations.xml.ejb3;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.UniqueConstraint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Ejb3XmlManyToOneTest extends Ejb3XmlTestCase {
	@Test
	public void testNoJoins() throws Exception {
		reader = getReader( Entity1.class, "field1", "many-to-one.orm1.xml" );
		assertAnnotationPresent( ManyToOne.class );
		assertAnnotationNotPresent( JoinColumn.class );
		assertAnnotationNotPresent( JoinColumns.class );
		assertAnnotationNotPresent( JoinTable.class );
		assertAnnotationNotPresent( Id.class );
		assertAnnotationNotPresent( MapsId.class );
		assertAnnotationNotPresent( Access.class );
		ManyToOne relAnno = reader.getAnnotation( ManyToOne.class );
		assertEquals( 0, relAnno.cascade().length );
		assertEquals( FetchType.EAGER, relAnno.fetch() );
		assertTrue( relAnno.optional() );
		assertEquals( void.class, relAnno.targetEntity() );
	}

	/**
	 * When there's a single join column, we still wrap it with a JoinColumns
	 * annotation.
	 */
	@Test
	public void testSingleJoinColumn() throws Exception {
		reader = getReader( Entity1.class, "field1", "many-to-one.orm2.xml" );
		assertAnnotationPresent( ManyToOne.class );
		assertAnnotationNotPresent( JoinColumn.class );
		assertAnnotationPresent( JoinColumns.class );
		assertAnnotationNotPresent( JoinTable.class );
		JoinColumns joinColumnsAnno = reader.getAnnotation( JoinColumns.class );
		JoinColumn[] joinColumns = joinColumnsAnno.value();
		assertEquals( 1, joinColumns.length );
		assertEquals( "col1", joinColumns[0].name() );
		assertEquals( "col2", joinColumns[0].referencedColumnName() );
		assertEquals( "table1", joinColumns[0].table() );
	}

	@Test
	public void testMultipleJoinColumns() throws Exception {
		reader = getReader( Entity1.class, "field1", "many-to-one.orm3.xml" );
		assertAnnotationPresent( ManyToOne.class );
		assertAnnotationNotPresent( JoinColumn.class );
		assertAnnotationPresent( JoinColumns.class );
		assertAnnotationNotPresent( JoinTable.class );
		JoinColumns joinColumnsAnno = reader.getAnnotation( JoinColumns.class );
		JoinColumn[] joinColumns = joinColumnsAnno.value();
		assertEquals( 2, joinColumns.length );
		assertEquals( "", joinColumns[0].name() );
		assertEquals( "", joinColumns[0].referencedColumnName() );
		assertEquals( "", joinColumns[0].table() );
		assertEquals( "", joinColumns[0].columnDefinition() );
		assertTrue( joinColumns[0].insertable() );
		assertTrue( joinColumns[0].updatable() );
		assertTrue( joinColumns[0].nullable() );
		assertFalse( joinColumns[0].unique() );
		assertEquals( "col1", joinColumns[1].name() );
		assertEquals( "col2", joinColumns[1].referencedColumnName() );
		assertEquals( "table1", joinColumns[1].table() );
		assertEquals( "int", joinColumns[1].columnDefinition() );
		assertFalse( joinColumns[1].insertable() );
		assertFalse( joinColumns[1].updatable() );
		assertFalse( joinColumns[1].nullable() );
		assertTrue( joinColumns[1].unique() );
	}

	@Test
	public void testJoinTableNoChildren() throws Exception {
		reader = getReader( Entity1.class, "field1", "many-to-one.orm4.xml" );
		assertAnnotationPresent( ManyToOne.class );
		assertAnnotationNotPresent( JoinColumn.class );
		assertAnnotationNotPresent( JoinColumns.class );
		assertAnnotationPresent( JoinTable.class );
		JoinTable joinTableAnno = reader.getAnnotation( JoinTable.class );
		assertEquals( "", joinTableAnno.catalog() );
		assertEquals( "", joinTableAnno.name() );
		assertEquals( "", joinTableAnno.schema() );
		assertEquals( 0, joinTableAnno.joinColumns().length );
		assertEquals( 0, joinTableAnno.inverseJoinColumns().length );
		assertEquals( 0, joinTableAnno.uniqueConstraints().length );
	}

	@Test
	public void testJoinTableAllChildren() throws Exception {
		reader = getReader( Entity1.class, "field1", "many-to-one.orm5.xml" );
		assertAnnotationPresent( ManyToOne.class );
		assertAnnotationNotPresent( JoinColumn.class );
		assertAnnotationNotPresent( JoinColumns.class );
		assertAnnotationPresent( JoinTable.class );
		JoinTable joinTableAnno = reader.getAnnotation( JoinTable.class );
		assertEquals( "cat1", joinTableAnno.catalog() );
		assertEquals( "table1", joinTableAnno.name() );
		assertEquals( "schema1", joinTableAnno.schema() );

		// JoinColumns
		JoinColumn[] joinColumns = joinTableAnno.joinColumns();
		assertEquals( 2, joinColumns.length );
		assertEquals( "", joinColumns[0].name() );
		assertEquals( "", joinColumns[0].referencedColumnName() );
		assertEquals( "", joinColumns[0].table() );
		assertEquals( "", joinColumns[0].columnDefinition() );
		assertTrue( joinColumns[0].insertable() );
		assertTrue( joinColumns[0].updatable() );
		assertTrue( joinColumns[0].nullable() );
		assertFalse( joinColumns[0].unique() );
		assertEquals( "col1", joinColumns[1].name() );
		assertEquals( "col2", joinColumns[1].referencedColumnName() );
		assertEquals( "table2", joinColumns[1].table() );
		assertEquals( "int", joinColumns[1].columnDefinition() );
		assertFalse( joinColumns[1].insertable() );
		assertFalse( joinColumns[1].updatable() );
		assertFalse( joinColumns[1].nullable() );
		assertTrue( joinColumns[1].unique() );

		// InverseJoinColumns
		JoinColumn[] inverseJoinColumns = joinTableAnno.inverseJoinColumns();
		assertEquals( 2, inverseJoinColumns.length );
		assertEquals( "", inverseJoinColumns[0].name() );
		assertEquals( "", inverseJoinColumns[0].referencedColumnName() );
		assertEquals( "", inverseJoinColumns[0].table() );
		assertEquals( "", inverseJoinColumns[0].columnDefinition() );
		assertTrue( inverseJoinColumns[0].insertable() );
		assertTrue( inverseJoinColumns[0].updatable() );
		assertTrue( inverseJoinColumns[0].nullable() );
		assertFalse( inverseJoinColumns[0].unique() );
		assertEquals( "col3", inverseJoinColumns[1].name() );
		assertEquals( "col4", inverseJoinColumns[1].referencedColumnName() );
		assertEquals( "table3", inverseJoinColumns[1].table() );
		assertEquals( "int", inverseJoinColumns[1].columnDefinition() );
		assertFalse( inverseJoinColumns[1].insertable() );
		assertFalse( inverseJoinColumns[1].updatable() );
		assertFalse( inverseJoinColumns[1].nullable() );
		assertTrue( inverseJoinColumns[1].unique() );

		// UniqueConstraints
		UniqueConstraint[] uniqueConstraints = joinTableAnno
				.uniqueConstraints();
		assertEquals( 2, uniqueConstraints.length );
		assertEquals( "", uniqueConstraints[0].name() );
		assertEquals( 1, uniqueConstraints[0].columnNames().length );
		assertEquals( "col5", uniqueConstraints[0].columnNames()[0] );
		assertEquals( "uq1", uniqueConstraints[1].name() );
		assertEquals( 2, uniqueConstraints[1].columnNames().length );
		assertEquals( "col6", uniqueConstraints[1].columnNames()[0] );
		assertEquals( "col7", uniqueConstraints[1].columnNames()[1] );
	}

	@Test
	public void testAllAttributes() throws Exception {
		reader = getReader( Entity1.class, "field1", "many-to-one.orm6.xml" );
		assertAnnotationPresent( ManyToOne.class );
		assertAnnotationNotPresent( JoinColumn.class );
		assertAnnotationNotPresent( JoinColumns.class );
		assertAnnotationNotPresent( JoinTable.class );
		assertAnnotationPresent( Id.class );
		assertAnnotationPresent( MapsId.class );
		assertAnnotationPresent( Access.class );
		ManyToOne relAnno = reader.getAnnotation( ManyToOne.class );
		assertEquals( 0, relAnno.cascade().length );
		assertEquals( FetchType.LAZY, relAnno.fetch() );
		assertFalse( relAnno.optional() );
		assertEquals( Entity3.class, relAnno.targetEntity() );
		assertEquals( "col1", reader.getAnnotation( MapsId.class ).value() );
		assertEquals(
				AccessType.PROPERTY, reader.getAnnotation( Access.class )
				.value()
		);
	}

	@Test
	public void testCascadeAll() throws Exception {
		reader = getReader( Entity1.class, "field1", "many-to-one.orm7.xml" );
		assertAnnotationPresent( ManyToOne.class );
		ManyToOne relAnno = reader.getAnnotation( ManyToOne.class );
		assertEquals( 1, relAnno.cascade().length );
		assertEquals( CascadeType.ALL, relAnno.cascade()[0] );
	}

	@Test
	public void testCascadeSomeWithDefaultPersist() throws Exception {
		reader = getReader( Entity1.class, "field1", "many-to-one.orm8.xml" );
		assertAnnotationPresent( ManyToOne.class );
		ManyToOne relAnno = reader.getAnnotation( ManyToOne.class );
		assertEquals( 4, relAnno.cascade().length );
		assertEquals( CascadeType.REMOVE, relAnno.cascade()[0] );
		assertEquals( CascadeType.REFRESH, relAnno.cascade()[1] );
		assertEquals( CascadeType.DETACH, relAnno.cascade()[2] );
		assertEquals( CascadeType.PERSIST, relAnno.cascade()[3] );
	}

	/**
	 * Make sure that it doesn't break the handler when {@link CascadeType#ALL}
	 * is specified in addition to a default cascade-persist or individual
	 * cascade settings.
	 */
	@Test
	public void testCascadeAllPlusMore() throws Exception {
		reader = getReader( Entity1.class, "field1", "many-to-one.orm9.xml" );
		assertAnnotationPresent( ManyToOne.class );
		ManyToOne relAnno = reader.getAnnotation( ManyToOne.class );
		assertEquals( 6, relAnno.cascade().length );
		assertEquals( CascadeType.ALL, relAnno.cascade()[0] );
		assertEquals( CascadeType.PERSIST, relAnno.cascade()[1] );
		assertEquals( CascadeType.MERGE, relAnno.cascade()[2] );
		assertEquals( CascadeType.REMOVE, relAnno.cascade()[3] );
		assertEquals( CascadeType.REFRESH, relAnno.cascade()[4] );
		assertEquals( CascadeType.DETACH, relAnno.cascade()[5] );
	}

}
