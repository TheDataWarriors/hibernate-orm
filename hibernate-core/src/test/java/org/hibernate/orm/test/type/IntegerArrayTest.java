/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.type;

import org.hibernate.dialect.HSQLDialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.dialect.SybaseASEDialect;

import org.hibernate.testing.jdbc.SharedDriverManagerTypeCacheClearingIntegrator;
import org.hibernate.testing.orm.junit.BootstrapServiceRegistry;
import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SkipForDialect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedNativeQueries;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Query;
import jakarta.persistence.Table;
import jakarta.persistence.TypedQuery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author Jordan Gigov
 * @author Christian Beikov
 */
@BootstrapServiceRegistry(
		// Clear the type cache, otherwise we might run into ORA-21700: object does not exist or is marked for delete
		integrators = SharedDriverManagerTypeCacheClearingIntegrator.class
)
@DomainModel(annotatedClasses = IntegerArrayTest.TableWithIntegerArrays.class)
@SessionFactory
@SkipForDialect(dialectClass = SybaseASEDialect.class, reason = "Sybase or the driver are trimming trailing zeros in byte arrays")
public class IntegerArrayTest {

	@BeforeAll
	public void startUp(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			em.persist( new TableWithIntegerArrays( 1L, new Integer[]{} ) );
			em.persist( new TableWithIntegerArrays( 2L, new Integer[]{ 512, 112, null, 0 } ) );
			em.persist( new TableWithIntegerArrays( 3L, null ) );

			Query q;
			q = em.createNamedQuery( "TableWithIntegerArrays.Native.insert" );
			q.setParameter( "id", 4L );
			q.setParameter( "data", new Integer[]{ null, null, 0 } );
			q.executeUpdate();

			q = em.createNativeQuery( "INSERT INTO table_with_integer_arrays(id, the_array) VALUES ( :id , :data )" );
			q.setParameter( "id", 5L );
			q.setParameter( "data", new Integer[]{ null, null, 0 } );
			q.executeUpdate();
		} );
	}

	@Test
	public void testById(SessionFactoryScope scope) {
		scope.inSession( em -> {
			TableWithIntegerArrays tableRecord;
			tableRecord = em.find( TableWithIntegerArrays.class, 1L );
			assertThat( tableRecord.getTheArray(), is( new Integer[]{} ) );

			tableRecord = em.find( TableWithIntegerArrays.class, 2L );
			assertThat( tableRecord.getTheArray(), is( new Integer[]{ 512, 112, null, 0 } ) );

			tableRecord = em.find( TableWithIntegerArrays.class, 3L );
			assertThat( tableRecord.getTheArray(), is( (Object) null ) );
		} );
	}

	@Test
	public void testQueryById(SessionFactoryScope scope) {
		scope.inSession( em -> {
			TypedQuery<TableWithIntegerArrays> tq = em.createNamedQuery( "TableWithIntegerArrays.JPQL.getById", TableWithIntegerArrays.class );
			tq.setParameter( "id", 2L );
			TableWithIntegerArrays tableRecord = tq.getSingleResult();
			assertThat( tableRecord.getTheArray(), is( new Integer[]{ 512, 112, null, 0 } ) );
		} );
	}

	@Test
	public void testQuery(SessionFactoryScope scope) {
		scope.inSession( em -> {
			TypedQuery<TableWithIntegerArrays> tq = em.createNamedQuery( "TableWithIntegerArrays.JPQL.getByData", TableWithIntegerArrays.class );
			tq.setParameter( "data", new Integer[]{} );
			TableWithIntegerArrays tableRecord = tq.getSingleResult();
			assertThat( tableRecord.getId(), is( 1L ) );
		} );
	}

	@Test
	public void testNativeQueryById(SessionFactoryScope scope) {
		scope.inSession( em -> {
			TypedQuery<TableWithIntegerArrays> tq = em.createNamedQuery( "TableWithIntegerArrays.Native.getById", TableWithIntegerArrays.class );
			tq.setParameter( "id", 2L );
			TableWithIntegerArrays tableRecord = tq.getSingleResult();
			assertThat( tableRecord.getTheArray(), is( new Integer[]{ 512, 112, null, 0 } ) );
		} );
	}

	@Test
	@SkipForDialect(dialectClass = HSQLDialect.class, reason = "HSQL does not like plain parameters in the distinct from predicate")
	@SkipForDialect(dialectClass = OracleDialect.class, reason = "Oracle requires a special function to compare XML")
	public void testNativeQuery(SessionFactoryScope scope) {
		scope.inSession( em -> {
			final String op = em.getJdbcServices().getDialect().supportsDistinctFromPredicate() ? "IS NOT DISTINCT FROM" : "=";
			TypedQuery<TableWithIntegerArrays> tq = em.createNativeQuery(
					"SELECT * FROM table_with_integer_arrays t WHERE the_array " + op + " :data",
					TableWithIntegerArrays.class
			);
			tq.setParameter( "data", new Integer[]{ 512, 112, null, 0 } );
			TableWithIntegerArrays tableRecord = tq.getSingleResult();
			assertThat( tableRecord.getId(), is( 2L ) );
		} );
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsStructuralArrays.class)
	public void testNativeQueryUntyped(SessionFactoryScope scope) {
		scope.inSession( em -> {
			Query q = em.createNamedQuery( "TableWithIntegerArrays.Native.getByIdUntyped" );
			q.setParameter( "id", 2L );
			Object[] tuple = (Object[]) q.getSingleResult();
			assertThat( tuple[1], is( new Integer[] { 512, 112, null, 0 } ) );
		} );
	}

	@Entity( name = "TableWithIntegerArrays" )
	@Table( name = "table_with_integer_arrays" )
	@NamedQueries( {
		@NamedQuery( name = "TableWithIntegerArrays.JPQL.getById",
				query = "SELECT t FROM TableWithIntegerArrays t WHERE id = :id" ),
		@NamedQuery( name = "TableWithIntegerArrays.JPQL.getByData",
				query = "SELECT t FROM TableWithIntegerArrays t WHERE theArray IS NOT DISTINCT FROM :data" ), } )
	@NamedNativeQueries( {
		@NamedNativeQuery( name = "TableWithIntegerArrays.Native.getById",
				query = "SELECT * FROM table_with_integer_arrays t WHERE id = :id",
				resultClass = TableWithIntegerArrays.class ),
		@NamedNativeQuery( name = "TableWithIntegerArrays.Native.getByIdUntyped",
				query = "SELECT * FROM table_with_integer_arrays t WHERE id = :id" ),
		@NamedNativeQuery( name = "TableWithIntegerArrays.Native.insert",
				query = "INSERT INTO table_with_integer_arrays(id, the_array) VALUES ( :id , :data )" )
	} )
	public static class TableWithIntegerArrays {

		@Id
		private Long id;

		@Column( name = "the_array" )
		private Integer[] theArray;

		public TableWithIntegerArrays() {
		}

		public TableWithIntegerArrays(Long id, Integer[] theArray) {
			this.id = id;
			this.theArray = theArray;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public Integer[] getTheArray() {
			return theArray;
		}

		public void setTheArray(Integer[] theArray) {
			this.theArray = theArray;
		}
	}

}
