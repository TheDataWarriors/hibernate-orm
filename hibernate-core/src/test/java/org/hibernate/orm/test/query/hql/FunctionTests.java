/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.hql;

import org.hibernate.QueryException;
import org.hibernate.dialect.CockroachDialect;
import org.hibernate.dialect.DB2Dialect;
import org.hibernate.dialect.DerbyDialect;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.dialect.SybaseDialect;
import org.hibernate.dialect.TiDBDialect;
import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.domain.StandardDomainModel;
import org.hibernate.testing.orm.domain.gambit.EntityOfBasics;
import org.hibernate.testing.orm.domain.gambit.EntityOfLists;
import org.hibernate.testing.orm.domain.gambit.EntityOfMaps;
import org.hibernate.testing.orm.domain.gambit.SimpleEntity;
import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SkipForDialect;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZoneOffset;
import java.util.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isOneOf;

import static org.hibernate.testing.orm.domain.gambit.EntityOfBasics.Gender.FEMALE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Gavin King
 */
@DomainModel( standardModels = StandardDomainModel.GAMBIT )
@SessionFactory
public class FunctionTests {

	public static final double ERROR = 0.00001d;

	@BeforeAll @SuppressWarnings("deprecation")
	public void prepareData(SessionFactoryScope scope) {
		scope.inTransaction(
				em -> {
					EntityOfBasics entity = new EntityOfBasics();
					entity.setTheInt(5);
					entity.setTheDouble(1.0);
					entity.setId(123);
					entity.setTheDate( new Date( 74, 2, 25 ) );
					entity.setTheTime( new Time( 20, 10, 8 ) );
					entity.setTheDuration( Duration.of(3, ChronoUnit.SECONDS).plus( Duration.of(23,ChronoUnit.MILLIS) ) );
					entity.setTheTimestamp( new Timestamp( 121, 4, 27, 13, 22, 50, 123456789 ) );
					em.persist(entity);

					EntityOfLists eol = new EntityOfLists(1,"");
					eol.addBasic("hello");
					eol.addNumber(1.0);
					eol.addNumber(2.0);
					SimpleEntity hello = new SimpleEntity(3, "hello", 5L, 7);
					SimpleEntity goodbye = new SimpleEntity(6, "goodbye", 10L, 9);
					em.persist(hello);
					em.persist(goodbye);
					eol.addOneToMany(hello);
					eol.addManyToMany(goodbye);
					em.persist(eol);

					EntityOfMaps eom = new EntityOfMaps(2,"");
					eom.addBasicByBasic("hello", "world");
					eom.addNumberByNumber(1,1.0);
					em.persist(eom);
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-15711")
	public void testLowerUpperFunctionsWithEnums(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select lower(e.gender) from EntityOfBasics e", String.class)
							.list();
					session.createQuery("select upper(e.gender) from EntityOfBasics e", String.class)
							.list();
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-15711")
	public void testLowerUpperFunctionsWithConvertedEnums(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select lower(e.convertedGender) from EntityOfBasics e", String.class)
							.list();
					session.createQuery("select upper(e.convertedGender) from EntityOfBasics e", String.class)
							.list();
				}
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-15711")
	public void testLowerFunctionsOrdinalEnumsShouldFail(SessionFactoryScope scope) {
		scope.inTransaction(
				session ->
						assertThrows( IllegalArgumentException.class, () ->
								session.createQuery( "select lower(e.ordinalGender) from EntityOfBasics e", String.class )
										.list()
						)
		);
	}

	@Test
	public void testIdVersionFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select id(w) from VersionedEntity w", Integer.class)
							.list();
					session.createQuery("select version(w) from VersionedEntity w", Integer.class)
							.list();
					session.createQuery("select naturalid(w) from VersionedEntity w", String.class)
							.list();
				}
		);
	}

	@Test
	public void testImplicitCollectionJoinInSelect(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select index(eol.listOfNumbers) from EntityOfLists eol", Integer.class)
							.getResultList(), hasItems(0,1) );
					assertThat( session.createQuery("select element(eol.listOfNumbers) from EntityOfLists eol", Double.class)
							.getResultList(), hasItems(1.0, 2.0) );

					assertThat( session.createQuery("select key(eom.numberByNumber) from EntityOfMaps eom", Integer.class)
							.getResultList(), hasItems(1) );
					assertThat( session.createQuery("select value(eom.numberByNumber) from EntityOfMaps eom", Double.class)
							.getResultList(), hasItems(1.0) );

					assertThat( session.createQuery("select key(eom.basicByBasic) from EntityOfMaps eom", String.class)
							.getResultList(), hasItems("hello") );
					assertThat( session.createQuery("select value(eom.basicByBasic) from EntityOfMaps eom", String.class)
							.getResultList(), hasItems("world") );

					assertThat( session.createQuery("select element(eol.listOfOneToMany) from EntityOfLists eol", SimpleEntity.class).getSingleResult().getId(), is(3) ) ;
					assertThat( session.createQuery("select element(eol.listOfManyToMany) from EntityOfLists eol", SimpleEntity.class).getSingleResult().getId(), is(6) );

					assertThat( session.createQuery("select element(se).someLong from EntityOfLists eol join eol.listOfOneToMany se", Long.class)
							.getSingleResult(), is(5L) );
					assertThat( session.createQuery("select element(eol.listOfOneToMany).someLong from EntityOfLists eol", Long.class)
							.getSingleResult(), is(5L) );

					assertThat( session.createQuery("select element(se).someLong from EntityOfLists eol join eol.listOfManyToMany se", Long.class)
							.getSingleResult(), is(10L) );
					assertThat( session.createQuery("select element(eol.listOfManyToMany).someLong from EntityOfLists eol", Long.class)
							.getSingleResult(), is(10L) );
				}
		);
	}

	@Test
	public void testImplicitCollectionJoinInWhere(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("from EntityOfLists eol where index(eol.listOfNumbers)=0", EntityOfLists.class)
							.getResultList();
					session.createQuery("from EntityOfLists eol where element(eol.listOfNumbers)=1.0", EntityOfLists.class)
							.getResultList();

					session.createQuery("from EntityOfMaps eom where key(eom.numberByNumber)=1", EntityOfMaps.class)
							.getResultList();
					session.createQuery("from EntityOfMaps eom where value(eom.numberByNumber)=1.0", EntityOfMaps.class)
							.getResultList();

					session.createQuery("from EntityOfMaps eom where key(eom.basicByBasic)='hello'", EntityOfMaps.class)
							.getResultList();
					session.createQuery("from EntityOfMaps eom where value(eom.basicByBasic)='world'", EntityOfMaps.class)
							.getResultList();

					session.createQuery("from EntityOfLists eol join eol.listOfOneToMany se where element(se).someLong=5", EntityOfLists.class)
							.getSingleResult();
					session.createQuery("from EntityOfLists eol where element(eol.listOfOneToMany).someLong=5", EntityOfLists.class)
							.getSingleResult();
					session.createQuery("from EntityOfLists eol join eol.listOfManyToMany se where element(se).someLong=10", EntityOfLists.class)
							.getSingleResult();
					session.createQuery("from EntityOfLists eol where element(eol.listOfManyToMany).someLong=10", EntityOfLists.class)
							.getSingleResult();
				}
		);
	}

	@Test
	public void testImplicitCollectionJoinInSelectAggregate(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select max(index(eol.listOfNumbers)) from EntityOfLists eol group by eol", Integer.class)
							.getSingleResult(), is(1) );
					assertThat( session.createQuery("select max(element(eol.listOfNumbers)) from EntityOfLists eol group by eol", Double.class)
							.getSingleResult(), is(2.0) );

					assertThat( session.createQuery("select sum(index(eol.listOfNumbers)) from EntityOfLists eol group by eol", Long.class)
							.getSingleResult(), is(1L) );
					assertThat( session.createQuery("select sum(element(eol.listOfNumbers)) from EntityOfLists eol group by eol", Double.class)
							.getSingleResult(), is(3.0) );

					assertThat( session.createQuery("select avg(index(eol.listOfNumbers)) from EntityOfLists eol group by eol", Double.class)
							.getSingleResult(), is(0.5) );
					assertThat( session.createQuery("select avg(element(eol.listOfNumbers)) from EntityOfLists eol group by eol", Double.class)
							.getSingleResult(), is(1.5) );

					assertThat( session.createQuery("select max(key(eom.numberByNumber)) from EntityOfMaps eom group by eom", Integer.class)
							.getSingleResult(), is(1) );
					assertThat( session.createQuery("select max(value(eom.numberByNumber)) from EntityOfMaps eom group by eom", Double.class)
							.getSingleResult(), is(1.0) );

					assertThat( session.createQuery("select sum(key(eom.numberByNumber)) from EntityOfMaps eom group by eom", Long.class)
							.getSingleResult(), is(1L) );
					assertThat( session.createQuery("select sum(value(eom.numberByNumber)) from EntityOfMaps eom group by eom", Double.class)
							.getSingleResult(), is(1.0) );

					assertThat( session.createQuery("select avg(key(eom.numberByNumber)) from EntityOfMaps eom group by eom", Double.class)
							.getSingleResult(), is(1.0) );
					assertThat( session.createQuery("select avg(value(eom.numberByNumber)) from EntityOfMaps eom group by eom", Double.class)
							.getSingleResult(), is(1.0) );
				}
		);
	}

	@Test
	public void testAggregateIndicesElementsWithPath(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select max(indices(eol.listOfNumbers)) from EntityOfLists eol", Integer.class)
							.getSingleResult(), is(1) );
					assertThat( session.createQuery("select max(elements(eol.listOfNumbers)) from EntityOfLists eol", Double.class)
							.getSingleResult(), is(2.0) );

					assertThat( session.createQuery("select sum(indices(eol.listOfNumbers)) from EntityOfLists eol", Long.class)
							.getSingleResult(), is(1L) );
					assertThat( session.createQuery("select sum(elements(eol.listOfNumbers)) from EntityOfLists eol", Double.class)
							.getSingleResult(), is(3.0) );

					assertThat( session.createQuery("select avg(indices(eol.listOfNumbers)) from EntityOfLists eol", Double.class)
							.getSingleResult(), is(0.5) );
					assertThat( session.createQuery("select avg(elements(eol.listOfNumbers)) from EntityOfLists eol", Double.class)
							.getSingleResult(), is(1.5) );

					assertThat( session.createQuery("select max(indices(eom.numberByNumber)) from EntityOfMaps eom", Integer.class)
							.getSingleResult(), is(1) );
					assertThat( session.createQuery("select max(elements(eom.numberByNumber)) from EntityOfMaps eom", Double.class)
							.getSingleResult(), is(1.0) );

					assertThat( session.createQuery("select sum(indices(eom.numberByNumber)) from EntityOfMaps eom", Long.class)
							.getSingleResult(), is(1L) );
					assertThat( session.createQuery("select sum(elements(eom.numberByNumber)) from EntityOfMaps eom", Double.class)
							.getSingleResult(), is(1.0) );

					assertThat( session.createQuery("select avg(indices(eom.numberByNumber)) from EntityOfMaps eom", Double.class)
							.getSingleResult(), is(1.0) );
					assertThat( session.createQuery("select avg(elements(eom.numberByNumber)) from EntityOfMaps eom", Double.class)
							.getSingleResult(), is(1.0) );
				}
		);
	}

	@Test
	public void testAggregateIndexElementKeyValueWithAlias(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select max(index(l)) from EntityOfLists eol join eol.listOfNumbers l group by eol", Integer.class)
							.getSingleResult(), is(1) );
					assertThat( session.createQuery("select max(element(l)) from EntityOfLists eol join eol.listOfNumbers l group by eol", Double.class)
							.getSingleResult(), is(2.0) );

					assertThat( session.createQuery("select sum(index(l)) from EntityOfLists eol join eol.listOfNumbers l group by eol", Long.class)
							.getSingleResult(), is(1L) );
					assertThat( session.createQuery("select sum(element(l)) from EntityOfLists eol join eol.listOfNumbers l group by eol", Double.class)
							.getSingleResult(), is(3.0) );

					assertThat( session.createQuery("select avg(index(l)) from EntityOfLists eol join eol.listOfNumbers l group by eol", Double.class)
							.getSingleResult(), is(0.5) );
					assertThat( session.createQuery("select avg(element(l)) from EntityOfLists eol join eol.listOfNumbers l group by eol", Double.class)
							.getSingleResult(), is(1.5) );

					assertThat( session.createQuery("select max(key(m)) from EntityOfMaps eom join eom.numberByNumber m group by eom", Integer.class)
							.getSingleResult(), is(1) );
					assertThat( session.createQuery("select max(value(m)) from EntityOfMaps eom join eom.numberByNumber m group by eom", Double.class)
							.getSingleResult(), is(1.0) );

					assertThat( session.createQuery("select sum(key(m)) from EntityOfMaps eom join eom.numberByNumber m group by eom", Long.class)
							.getSingleResult(), is(1L) );
					assertThat( session.createQuery("select sum(value(m)) from EntityOfMaps eom join eom.numberByNumber m group by eom", Double.class)
							.getSingleResult(), is(1.0) );

					assertThat( session.createQuery("select avg(key(m)) from EntityOfMaps eom join eom.numberByNumber m group by eom", Double.class)
							.getSingleResult(), is(1.0) );
					assertThat( session.createQuery("select avg(value(m)) from EntityOfMaps eom join eom.numberByNumber m group by eom", Double.class)
							.getSingleResult(), is(1.0) );
				}
		);
	}

	@Test
	public void testMaxindexMaxelement(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select maxindex(eol.listOfBasics) from EntityOfLists eol", Integer.class)
							.getSingleResult(), is(0) );
					assertThat( session.createQuery("select maxelement(eol.listOfBasics) from EntityOfLists eol", String.class)
							.getSingleResult(), is("hello") );

					assertThat( session.createQuery("select maxindex(eom.basicByBasic) from EntityOfMaps eom", String.class)
							.getSingleResult(), is("hello") );
					assertThat( session.createQuery("select maxelement(eom.basicByBasic) from EntityOfMaps eom", String.class)
							.getSingleResult(), is("world") );
				}
		);
	}

	@Test
	public void testKeyIndexValueEntry(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select index(l) from EntityOfLists eol join eol.listOfBasics l", Integer.class)
							.getSingleResult(), is(0) );
					assertThat( session.createQuery("select value(l) from EntityOfLists eol join eol.listOfBasics l", String.class)
							.getSingleResult(), is("hello") );

					assertThat( session.createQuery("select key(m) from EntityOfMaps eom join eom.basicByBasic m", String.class)
							.getSingleResult(), is("hello") );
					assertThat( session.createQuery("select index(m) from EntityOfMaps eom join eom.basicByBasic m", String.class)
							.getSingleResult(), is("hello") );
					assertThat( session.createQuery("select value(m) from EntityOfMaps eom join eom.basicByBasic m", String.class)
							.getSingleResult(), is("world") );

					assertThat( session.createQuery("select entry(m) from EntityOfMaps eom join eom.basicByBasic m", Map.Entry.class)
							.getSingleResult(), is( Map.entry("hello", "world") ) );
				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsCharCodeConversion.class)
	public void testAsciiChrFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select chr(65)", Character.class).getSingleResult(), is( 'A' ) );
					assertThat( session.createQuery("select ascii('A')", Integer.class).getSingleResult(), anyOf( is( 65 ), is( (short) 65 ) ) );
				}
		);
	}

	@Test
	public void testConcatFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select concat('foo', e.theString, 'bar') from EntityOfBasics e", String.class)
							.list();
					session.createQuery("select 'foo' || e.theString || 'bar' from EntityOfBasics e", String.class)
							.list();
					assertThat( session.createQuery("select concat('hello',' ','world')", String.class).getSingleResult(), is("hello world") );
					assertThat( session.createQuery("select 'hello'||' '||'world'", String.class).getSingleResult(), is("hello world") );
				}
		);
	}

	@Test
	public void testConcatFunctionParameters(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select cast(:hello as String)||cast(:world as String)", String.class).setParameter("hello","hello").setParameter("world","world").getSingleResult(), is("helloworld") );
					assertThat( session.createQuery("select cast(?1 as String)||cast(?2 as String)", String.class).setParameter(1,"hello").setParameter(2,"world").getSingleResult(), is("helloworld") );
					assertThat( session.createQuery("select cast(?1 as String)||cast(?1 as String)", String.class).setParameter(1,"hello").getSingleResult(), is("hellohello") );
				}
		);
	}

	@Test
	public void testCoalesceFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					//Derby does not like literal nulls :-/
//					session.createQuery("select coalesce(null, e.gender, org.hibernate.testing.orm.domain.gambit.EntityOfBasics$Gender.MALE) from EntityOfBasics e", EntityOfBasics.Gender.class)
//							.list();
					session.createQuery("select coalesce(nullif(e.gender,org.hibernate.testing.orm.domain.gambit.EntityOfBasics$Gender.FEMALE), e.gender) from EntityOfBasics e", EntityOfBasics.Gender.class)
							.list();
					session.createQuery("select coalesce(nullif(e.gender,?1), e.gender) from EntityOfBasics e", EntityOfBasics.Gender.class)
							.setParameter(1, FEMALE)
							.list();
					session.createQuery("select ifnull(e.gender, org.hibernate.testing.orm.domain.gambit.EntityOfBasics$Gender.FEMALE) from EntityOfBasics e", EntityOfBasics.Gender.class)
							.list();
					assertThat( session.createQuery("select coalesce(nullif('',''), nullif('bye','bye'), 'hello', 'oops')", String.class).getSingleResult(), is("hello") );
					assertThat( session.createQuery("select ifnull(nullif('bye','bye'), 'hello')", String.class).getSingleResult(), is("hello") );
				}
		);
	}

	@Test
	public void testNullifFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select nullif(e.theString, '') from EntityOfBasics e", String.class)
							.list();
					assertThat( session.createQuery("select nullif('foo', 'foo')", String.class).getSingleResult(), nullValue() );
					assertThat( session.createQuery("select nullif('foo', 'bar')", String.class).getSingleResult(), is("foo") );
				}
		);
	}

	@Test
	public void testTrigFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select sin(e.theDouble), cos(e.theDouble), tan(e.theDouble), asin(e.theDouble), acos(e.theDouble), atan(e.theDouble) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select atan2(sin(e.theDouble), cos(e.theDouble)) from EntityOfBasics e", Object[].class)
							.list();
				}
		);
	}

	@Test
	public void testMathFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select +e.theInt, -e.theInt from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select abs(e.theInt), sign(e.theInt), mod(e.theInt, 2) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select e.theInt % 2 from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select abs(e.theDouble), sign(e.theDouble), sqrt(e.theDouble) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select exp(e.theDouble), ln(e.theDouble + 1), log10(e.theDouble + 2) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select power(e.theDouble + 1, 2.5) from EntityOfBasics e", Double.class)
							.list();
					session.createQuery("select ceiling(e.theDouble), floor(e.theDouble) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select round(e.theDouble, 2) from EntityOfBasics e", Double.class)
							.list();
					session.createQuery("select round(cast(e.theDouble as BigDecimal), 3) from EntityOfBasics e", BigDecimal.class)
							.list();
					assertThat( session.createQuery("select abs(-2)", Integer.class).getSingleResult(), is(2) );
					assertThat( session.createQuery("select sign(-2)", Integer.class).getSingleResult(), is(-1) );
					assertThat(
							session.createQuery("select power(3.0,2.0)", Double.class).getSingleResult(),
							// The LN/EXP emulation can cause some precision loss
							// i.e. on Derby the 16th decimal for LN(3.0) is off by 1 when compared to e.g. PostgreSQL
							// Fetching the result as float would "hide" the error as that would do some rounding
							Matchers.closeTo( 9.0d, ERROR )
					);
					assertThat( session.createQuery("select round(32.12345,2)", Float.class).getSingleResult(), is(32.12f) );
					assertThat( session.createQuery("select mod(3,2)", Integer.class).getSingleResult(), is(1) );
					assertThat( session.createQuery("select 3%2", Integer.class).getSingleResult(), is(1) );
					assertThat( session.createQuery("select sqrt(9.0)", Double.class).getSingleResult(), is(3.0d) );
				}
		);
	}

	@Test
	public void testRoundTruncFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select trunc(32.92345f)", Float.class).getSingleResult(), is(32f) );
					assertThat( session.createQuery("select trunc(32.92345f,3)", Float.class).getSingleResult(), is(32.923f) );
					assertThat( session.createQuery("select trunc(-32.92345f)", Float.class).getSingleResult(), is(-32f) );
					assertThat( session.createQuery("select trunc(-32.92345f,3)", Float.class).getSingleResult(), is(-32.923f) );
					assertThat( session.createQuery("select truncate(32.92345f)", Float.class).getSingleResult(), is(32f) );
					assertThat( session.createQuery("select truncate(32.92345f,3)", Float.class).getSingleResult(), is(32.923f) );
					assertThat( session.createQuery("select round(32.92345f)", Float.class).getSingleResult(), is(33f) );
					assertThat( session.createQuery("select round(32.92345f,1)", Float.class).getSingleResult(), is(32.9f) );
					assertThat( session.createQuery("select round(32.92345f,3)", Float.class).getSingleResult(), is(32.923f) );
					assertThat( session.createQuery("select round(32.923451f,4)", Float.class).getSingleResult(), is(32.9235f) );

					assertThat( session.createQuery("select trunc(32.92345d)", Double.class).getSingleResult(), is(32d) );
					assertThat( session.createQuery("select trunc(32.92345d,3)", Double.class).getSingleResult(), is(32.923d) );
					assertThat( session.createQuery("select trunc(-32.92345d)", Double.class).getSingleResult(), is(-32d) );
					assertThat( session.createQuery("select trunc(-32.92345d,3)", Double.class).getSingleResult(), is(-32.923d) );
					assertThat( session.createQuery("select truncate(32.92345d)", Double.class).getSingleResult(), is(32d) );
					assertThat( session.createQuery("select truncate(32.92345d,3)", Double.class).getSingleResult(), is(32.923d) );
					assertThat( session.createQuery("select round(32.92345d)", Double.class).getSingleResult(), is(33d) );
					assertThat( session.createQuery("select round(32.92345d,1)", Double.class).getSingleResult(), is(32.9d) );
					assertThat( session.createQuery("select round(32.92345d,3)", Double.class).getSingleResult(), is(32.923d) );
					assertThat( session.createQuery("select round(32.923451d,4)", Double.class).getSingleResult(), is(32.9235d) );
				}
		);
	}

	@Test
	@SkipForDialect(dialectClass = DerbyDialect.class, reason = "Derby doesn't support any form of date truncation")
	public void testDateTruncFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery( "select trunc(current_timestamp,year)", Timestamp.class ).getSingleResult();
					session.createQuery( "select trunc(current_timestamp,month)", Timestamp.class ).getSingleResult();
					session.createQuery( "select trunc(current_timestamp,day)", Timestamp.class ).getSingleResult();
					session.createQuery( "select truncate(current_timestamp,hour)", Timestamp.class ).getSingleResult();
					session.createQuery( "select truncate(current_timestamp,minute)", Timestamp.class ).getSingleResult();
					session.createQuery( "select truncate(current_timestamp,second)", Timestamp.class ).getSingleResult();

					assertThat( session.createQuery( "select truncate(datetime 1974-10-03 12:30, day)", LocalDateTime.class ).getSingleResult(),
							is( LocalDateTime.of(1974,10,3,0,0,0) ) );
					assertThat( session.createQuery( "select truncate(datetime 1974-10-03 12:30, month)", LocalDateTime.class ).getSingleResult(),
							is( LocalDateTime.of(1974,10,1,0,0,0) ) );
					assertThat( session.createQuery( "select truncate(datetime 1974-10-03 12:30, year)", LocalDateTime.class ).getSingleResult(),
							is( LocalDateTime.of(1974,1,1,0,0,0) ) );
					assertThat( session.createQuery( "select truncate(datetime 1974-10-03 12:30:45, hour)", LocalDateTime.class ).getSingleResult(),
							is( LocalDateTime.of(1974,10,3,12,0,0) ) );
					assertThat( session.createQuery( "select truncate(datetime 1974-10-03 12:30:45, minute)", LocalDateTime.class ).getSingleResult(),
							is( LocalDateTime.of(1974,10,3,12,30,0) ) );
					assertThat( session.createQuery( "select truncate(datetime 1974-10-03 12:30:45.123, second)", LocalDateTime.class ).getSingleResult(),
							is( LocalDateTime.of(1974,10,3,12,30,45,0) ) );
				}
		);
	}

	@Test
	@SkipForDialect(dialectClass = DerbyDialect.class, reason = "Derby doesn't support any form of date truncation")
	@SkipForDialect(dialectClass = OracleDialect.class, reason = "See HHH-16442, Oracle trunc() throws away the timezone")
	public void testDateTruncWithOffsetFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery( "select truncate(offset datetime 1974-10-03 12:30-12:00, day)", OffsetDateTime.class ).getSingleResult(),
							isOneOf( OffsetDateTime.of( 1974,10,3,0,0,0, 0, ZoneOffset.ofHours(-12) ),
									OffsetDateTime.of( 1974,10,4,0,0,0, 0, ZoneOffset.UTC ) ) );
					assertThat( session.createQuery( "select truncate(offset datetime 1974-10-03 12:30-12:00, minute)", OffsetDateTime.class ).getSingleResult(),
							isOneOf( OffsetDateTime.of( 1974,10,3,12,30,0, 0, ZoneOffset.ofHours(-12) ),
									OffsetDateTime.of( 1974,10,4,0,30,0, 0, ZoneOffset.UTC ) ) );
				}
		);
	}

	@Test
	public void testLowerUpperFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select lower(e.theString), upper(e.theString) from EntityOfBasics e", Object[].class)
							.list();
					assertThat( session.createQuery("select lower('HELLO')", String.class).getSingleResult(), is("hello") );
					assertThat( session.createQuery("select upper('hello')", String.class).getSingleResult(), is("HELLO") );
				}
		);

		try {
			scope.inTransaction(
					session -> session.createQuery( "select upper(3)", String.class).list()
			);
			fail();
		}
		catch (IllegalArgumentException e) {
			assertThat( e.getCause(), is(instanceOf(QueryException.class)) );
		}
	}

	@Test
	public void testLengthFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select length(e.theString) from EntityOfBasics e where length(e.theString) > 1", Integer.class)
							.list();
					assertThat( session.createQuery("select length('hello')", Integer.class).getSingleResult(), is(5) );
				}
		);

		try {
			scope.inTransaction(
					session -> session.createQuery( "select length(3)", Integer.class).list()
			);
			fail();
		}
		catch (IllegalArgumentException e) {
			assertThat( e.getCause(), is(instanceOf(QueryException.class)) );
		}
	}

	@Test
	public void testSubstringFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select substring(e.theString, e.theInt) from EntityOfBasics e", String.class)
							.list();
					session.createQuery("select substring(e.theString, 0, e.theInt) from EntityOfBasics e", String.class)
							.list();
					session.createQuery("select substring(e.theString from e.theInt) from EntityOfBasics e", String.class)
							.list();
					session.createQuery("select substring(e.theString from 0 for e.theInt) from EntityOfBasics e", String.class)
							.list();
					assertThat( session.createQuery("select substring('hello world',4, 5)", String.class).getSingleResult(), is("lo wo") );
				}
		);

		try {
			scope.inTransaction(
					session -> session.createQuery( "select substring('hello world', 'world', 5)", String.class).list()
			);
			fail();
		}
		catch (IllegalArgumentException e) {
			assertThat( e.getCause(), is(instanceOf(QueryException.class)) );
		}
	}

	@Test
	public void testLeftRightFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select left(e.theString, e.theInt), right(e.theString, e.theInt) from EntityOfBasics e", Object[].class)
							.list();
					assertThat( session.createQuery("select left('hello world', 5)", String.class).getSingleResult(), is("hello") );
					assertThat( session.createQuery("select right('hello world', 5)", String.class).getSingleResult(), is("world") );
				}
		);
	}

	@Test
	public void testPositionFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select position('hello' in e.theString) from EntityOfBasics e", Integer.class)
							.list();
					assertThat( session.createQuery("select position('world' in 'hello world')", Integer.class).getSingleResult(), is(7) );
				}
		);
	}

	@Test
	public void testLocateFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select locate('hello', e.theString) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select locate('hello', e.theString, e.theInteger) from EntityOfBasics e", Integer.class)
							.list();
					assertThat( session.createQuery("select locate('world', 'hello world')", Integer.class).getSingleResult(), is(7) );
				}
		);
	}

	@Test
	public void testOverlayFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select overlay('hello world' placing 'goodbye' from 1 for 5) from EntityOfBasics", String.class)
							.list().get(0), is("goodbye world") );
					assertThat( session.createQuery("select overlay('hello world' placing 'goodbye' from 7 for 5) from EntityOfBasics", String.class)
							.list().get(0), is("hello goodbye") );
					assertThat( session.createQuery("select overlay('xxxxxx' placing 'yy' from 3) from EntityOfBasics", String.class)
							.list().get(0), is("xxyyxx") );
					assertThat( session.createQuery("select overlay('xxxxxx' placing ' yy ' from 3 for 2) from EntityOfBasics", String.class)
							.list().get(0), is("xx yy xx") );
				}
		);
	}

	@Test
	public void testOverlayFunctionParameters(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select overlay(?1 placing 'yy' from 3)", String.class)
							.setParameter(1, "xxxxxx")
							.list();
					session.createQuery("select overlay('xxxxxx' placing ?1 from 3)", String.class)
							.setParameter(1, "yy")
							.list();
					session.createQuery("select overlay('xxxxxx' placing 'yy' from ?1)", String.class)
							.setParameter(1, 3)
							.list();
					session.createQuery("select overlay(?2 placing ?1 from 3)", String.class)
							.setParameter(1, "yy")
							.setParameter(2, "xxxxxx")
							.list();
					session.createQuery("select overlay(:text placing :rep from 3)", String.class)
							.setParameter("rep", "yy")
							.setParameter("text", "xxxxxx")
							.list();
				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsReplace.class)
	public void testReplaceFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select replace(e.theString, 'hello', 'goodbye') from EntityOfBasics e", String.class)
							.list();
					assertThat( session.createQuery("select replace('hello world', 'hello', 'goodbye')", String.class).getSingleResult(), is("goodbye world") );
					assertThat( session.createQuery("select replace('hello world', 'o', 'ooo')", String.class).getSingleResult(), is("hellooo wooorld") );
				}
		);

		try {
			scope.inTransaction(
					session -> session.createQuery( "select replace(e.theString, 1, 'goodbye') from EntityOfBasics e", String.class).list()
			);
			fail();
		}
		catch (IllegalArgumentException e) {
			assertThat( e.getCause(), is(instanceOf(QueryException.class)) );
		}
	}

	@Test
	public void testTrimFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select trim(leading ' ' from e.theString) from EntityOfBasics e", String.class)
							.list();
					session.createQuery("select trim(trailing ' ' from e.theString) from EntityOfBasics e", String.class)
							.list();
					session.createQuery("select trim(both ' ' from e.theString) from EntityOfBasics e", String.class)
							.list();
					assertThat( session.createQuery("select trim(leading from '   hello')", String.class).getSingleResult(), is("hello") );
					assertThat( session.createQuery("select trim(trailing from 'hello   ')", String.class).getSingleResult(), is("hello") );
					assertThat( session.createQuery("select trim(both from '   hello   ')", String.class).getSingleResult(), is("hello") );
					assertThat( session.createQuery("select trim(both '-' from '---hello---')", String.class).getSingleResult(), is("hello") );
				}
		);

		try {
			scope.inTransaction(
					session -> session.createQuery( "select trim(leading ' ' from 3)", String.class).list()
			);
			fail();
		}
		catch (IllegalArgumentException e) {
			assertThat( e.getCause(), is(instanceOf(QueryException.class)) );
		}
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsPadWithChar.class)
	public void testPadFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat(session.createQuery("select pad('hello' with 10 leading)", String.class).getSingleResult(),
							is("     hello"));
					assertThat(session.createQuery("select pad('hello' with 10 trailing)", String.class).getSingleResult(),
							is("hello     "));
					assertThat(session.createQuery("select pad('hello' with 10 leading '.')", String.class).getSingleResult(),
							is(".....hello"));
					assertThat(session.createQuery("select pad('hello' with 10 trailing '.')", String.class).getSingleResult(),
							is("hello....."));
				}
		);

		try {
			scope.inTransaction(
					session -> session.createQuery( "select pad('hello' with ' ' leading)", String.class).list()
			);
			fail();
		}
		catch (IllegalArgumentException e) {
			assertThat( e.getCause(), is(instanceOf(QueryException.class)) );
		}
		try {
			scope.inTransaction(
					session -> session.createQuery( "select pad(3 with 4 leading)", String.class).list()
			);
			fail();
		}
		catch (IllegalArgumentException e) {
			assertThat( e.getCause(), is(instanceOf(QueryException.class)) );
		}
	}

	@Test
	public void testPadFunctionParameters(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select pad(?1 with ?2 leading)", String.class)
							.setParameter(1, "hello")
							.setParameter(2, 10)
							.getSingleResult();
					session.createQuery("select pad(:string with :length leading)", String.class)
							.setParameter("string", "hello")
							.setParameter("length", 10)
							.getSingleResult();
				}
		);
	}

	@Test
	public void testCastFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( ( session.createQuery("select cast(e.theBoolean as String) from EntityOfBasics e", String.class).getSingleResult()).toLowerCase(), is("false") );
					assertThat( ( session.createQuery("select cast(e.theNumericBoolean as String) from EntityOfBasics e", String.class).getSingleResult()).toLowerCase(), is("false") );
					assertThat( ( session.createQuery("select cast(e.theStringBoolean as String) from EntityOfBasics e", String.class).getSingleResult()).toLowerCase(), is("false") );

					session.createQuery("select cast(e.theDate as String), cast(e.theTime as String), cast(e.theTimestamp as String) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select cast(e.id as String), cast(e.theInt as String), cast(e.theDouble as String) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select cast(e.id as Float), cast(e.theInt as Double), cast(e.theDouble as Long) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select cast(e.id as BigInteger(10)), cast(e.theDouble as BigDecimal(10,5)) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select cast(e.theString as String(15)), cast(e.theDouble as String(17)) from EntityOfBasics e", Object[].class)
							.list();

					session.createQuery( "select cast('1002342345234523.452435245245243' as BigDecimal) from EntityOfBasics", BigDecimal.class)
							.list();
					session.createQuery("select cast('1002342345234523.452435245245243' as BigDecimal(30, 10)) from EntityOfBasics", BigDecimal.class)
							.list();
					session.createQuery("select cast('1234234523452345243524524524' as BigInteger) from EntityOfBasics", BigInteger.class)
							.list();
					session.createQuery("select cast('1234234523452345243524524524' as BigInteger(30)) from EntityOfBasics", BigInteger.class)
							.list();
					session.createQuery("select cast('3811234234.12312' as Double) from EntityOfBasics", Double.class)
							.list();
					session.createQuery("select cast('1234234' as Integer) from EntityOfBasics", Integer.class)
							.list();
					session.createQuery("select cast(1 as Boolean), cast(0 as Boolean) from EntityOfBasics", Object[].class)
							.list();

					session.createQuery("select cast('12:13:14' as Time) from EntityOfBasics", Time.class)
							.list();
					session.createQuery("select cast('1911-10-09' as Date) from EntityOfBasics", Date.class)
							.list();
					session.createQuery("select cast('1911-10-09 12:13:14.123' as Timestamp) from EntityOfBasics", Timestamp.class)
							.list();

					session.createQuery("select cast('12:13:14' as LocalTime) from EntityOfBasics", LocalTime.class)
							.list();
					session.createQuery("select cast('1911-10-09' as LocalDate) from EntityOfBasics", LocalDate.class)
							.list();
					session.createQuery("select cast('1911-10-09 12:13:14.123' as LocalDateTime) from EntityOfBasics", LocalDateTime.class)
							.list();

					assertThat( session.createQuery("select cast(1 as Boolean)", Boolean.class).getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast(0 as Boolean)", Boolean.class).getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast('1234' as Integer)", Integer.class).getSingleResult(), is(1234) );
					assertThat( session.createQuery("select cast('1234' as Short)", Short.class).getSingleResult(), is((short) 1234) );
					assertThat( session.createQuery("select cast('123' as Byte)", Byte.class).getSingleResult(), is((byte) 123) );
					assertThat( session.createQuery("select cast('123' as Long)", Long.class).getSingleResult(), is(123L) );
					assertThat( session.createQuery("select cast('123.12' as Float)", Float.class).getSingleResult(), is(123.12f) );

					assertThat( session.createQuery("select cast('hello' as String)", String.class).getSingleResult(), is("hello") );
					assertThat( ( session.createQuery("select cast(true as String)", String.class).getSingleResult()).toLowerCase(), is("true") );
					assertThat( ( session.createQuery("select cast(false as String)", String.class).getSingleResult()).toLowerCase(), is("false") );
					assertThat( session.createQuery("select cast(123 as String)", String.class).getSingleResult(), is("123") );

					assertThat( session.createQuery("select cast('1911-10-09' as LocalDate)", LocalDate.class).getSingleResult(), is(LocalDate.of(1911,10,9)) );
					assertThat( session.createQuery("select cast('12:13:14' as LocalTime)", LocalTime.class).getSingleResult(), is(LocalTime.of(12,13,14)) );
					assertThat( session.createQuery("select cast('1911-10-09 12:13:14' as LocalDateTime)", LocalDateTime.class).getSingleResult(), is(LocalDateTime.of(1911,10,9,12,13,14)) );

					assertThat( session.createQuery("select cast(local datetime as LocalTime)", LocalTime.class).getSingleResult(), instanceOf(LocalTime.class) );
					assertThat( session.createQuery("select cast(local datetime as LocalDate)", LocalDate.class).getSingleResult(), instanceOf(LocalDate.class) );
					assertThat( session.createQuery("select cast('1911-10-09 12:13:14.123' as LocalDateTime)", LocalDateTime.class).getSingleResult(), instanceOf(LocalDateTime.class) );

					assertThat( session.createQuery("select cast('12:13:14' as Time)", Time.class).getSingleResult(), instanceOf(Time.class) );
					assertThat( session.createQuery("select cast('1911-10-09' as Date)", Date.class).getSingleResult(), instanceOf(Date.class) );
					assertThat( session.createQuery("select cast('1911-10-09 12:13:14.123' as Timestamp)", Timestamp.class).getSingleResult(), instanceOf(Timestamp.class) );

					assertThat( session.createQuery("select cast(date 1911-10-09 as String)", String.class).getSingleResult(), is("1911-10-09") );
					assertThat( session.createQuery("select cast(time 12:13:14 as String)", String.class).getSingleResult(), anyOf( is("12:13:14"), is("12:13:14.0000"), is("12.13.14") ) );
					assertThat( session.createQuery("select cast(datetime 1911-10-09 12:13:14 as String)", String.class).getSingleResult(), anyOf( startsWith("1911-10-09 12:13:14"), startsWith("1911-10-09-12.13.14") ) );

					assertThat( session.createQuery("select cast(local datetime as Instant)", Instant.class).getSingleResult(), instanceOf(Instant.class) );
					assertThat( session.createQuery("select cast(offset datetime as Instant)", Instant.class).getSingleResult(), instanceOf(Instant.class) );

					assertThat( session.createQuery("select cast(1 as NumericBoolean)", Boolean.class).getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast(0 as NumericBoolean)", Boolean.class).getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast(true as YesNo)", Boolean.class).getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast(false as YesNo)", Boolean.class).getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast(1 as YesNo)", Boolean.class).getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast(0 as YesNo)", Boolean.class).getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast(true as TrueFalse)", Boolean.class).getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast(false as TrueFalse)", Boolean.class).getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast(1 as TrueFalse)", Boolean.class).getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast(0 as TrueFalse)", Boolean.class).getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast('Y' as YesNo)", Boolean.class).getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast('N' as YesNo)", Boolean.class).getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast('T' as TrueFalse)", Boolean.class).getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast('F' as TrueFalse)", Boolean.class).getSingleResult(), is(false) );
				}
		);
	}

	@Test
	@SkipForDialect(dialectClass = DB2Dialect.class, matchSubTypes = true)
	@SkipForDialect(dialectClass = DerbyDialect.class)
	@SkipForDialect(dialectClass = SybaseDialect.class, matchSubTypes = true)
	public void testCastToOffsetDatetime(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			session.createQuery("select cast(datetime 1911-10-09 12:13:14-02:00 as String)", String.class).getSingleResult();
			session.createQuery("select cast('1911-10-09 12:13:14.123-02:00' as OffsetDateTime)", OffsetDateTime.class)
					.getSingleResult();

		});
	}

	@Test
	public void testCastDoubleToString(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select str(123.12)", String.class).getSingleResult(), is("123.12") );
					assertThat( session.createQuery("select cast(123.12 as String)", String.class).getSingleResult(), is("123.12") );
					assertThat( session.createQuery("select cast(123.12d as String)", String.class).getSingleResult(), is("123.12") );
					assertThat( session.createQuery("select cast(123.12f as String)", String.class).getSingleResult(), is("123.12") );
					assertThat( session.createQuery("select cast('123.12' as Double)", Double.class).getSingleResult(), is(123.12d) );
					assertThat( session.createQuery("select cast('123.12' as Float)", Float.class).getSingleResult(), is(123.12f) );
				}
		);
	}

	@Test
	@RequiresDialectFeature( feature = DialectFeatureChecks.SupportsTruncateThroughCast.class)
	public void testCastFunction_withTruncation(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select cast(e.theString as String(15)), cast(e.theDouble as String(8)) from EntityOfBasics e", Object[].class)
							.list();

					session.createQuery("select cast('ABCDEF' as Character) from EntityOfBasics", Character.class)
							.list();
				}
		);
	}

	@Test
	@SkipForDialect(dialectClass = DerbyDialect.class, reason = "Derby doesn't support casting to the binary types")
	public void testCastFunctionBinary(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select cast(e.theString as Binary) from EntityOfBasics e", byte[].class)
							.list();
					session.createQuery("select cast(e.theString as Binary(10)) from EntityOfBasics e", byte[].class)
							.list();
				}
		);
	}

	@Test
	public void testStrFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select str(e.theDate), str(e.theTime), str(e.theTimestamp) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select str(e.id), str(e.theInt), str(e.theDouble) from EntityOfBasics e", Object[].class)
							.list();
					assertThat( session.createQuery("select str(69)", String.class).getSingleResult(), is("69") );
					assertThat( session.createQuery("select str(date 1911-10-09)", String.class).getSingleResult(), is("1911-10-09") );
					assertThat( session.createQuery("select str(time 12:13:14)", String.class).getSingleResult(), anyOf( is( "12:13:14"), is( "12:13:14.0000"), is( "12.13.14") ) );
				}
		);
	}

	@Test
	public void testExtractFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select year(e.theDate) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select month(e.theDate) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select day(e.theDate) from EntityOfBasics e", Integer.class)
							.list();

					session.createQuery("select hour(e.theTime) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select minute(e.theTime) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select second(e.theTime) from EntityOfBasics e", Float.class)
							.list();

					session.createQuery("select year(e.theTimestamp) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select month(e.theTimestamp) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select day(e.theTimestamp) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select hour(e.theTimestamp) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select minute(e.theTimestamp) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select second(e.theTimestamp) from EntityOfBasics e", Float.class)
							.list();
				}
		);
	}


	@Test
	public void testLeastGreatestFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select least(1, 2, e.theInt, -1), greatest(1, e.theInt, 2, -1) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select least(0.0, e.theDouble), greatest(0.0, e.theDouble, 2.0) from EntityOfBasics e", Object[].class)
							.list();
					assertThat( session.createQuery("select least(1,2,-1,3,4)", Integer.class).getSingleResult(), is(-1) );
					assertThat( session.createQuery("select greatest(1,2,-1,30,4)", Integer.class).getSingleResult(), is(30) );
				}
		);
	}

	@Test
	public void testCountFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select count(*) from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select count(e) from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select count(distinct e) from EntityOfBasics e", Long.class)
							.list();
				}
		);
	}

	@Test
	public void testAggregateFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select avg(e.theDouble), avg(abs(e.theDouble)), min(e.theDouble), max(e.theDouble), sum(e.theDouble), sum(e.theInt) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select avg(distinct e.theInt) from EntityOfBasics e", Double.class)
							.list();
					session.createQuery("select sum(distinct e.theInt) from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select any(e.theInt > 0), every(e.theInt > 0) from EntityOfBasics e", Object[].class)
							.list();
					//not supported by grammar:
//					session.createQuery("select any(e.theBoolean), every(e.theBoolean) from EntityOfBasics e")
//							.list();
					session.createQuery("select some(e.theInt > 0), all(e.theInt > 0) from EntityOfBasics e", Object[].class)
							.list();
				}
		);
	}

	@Test
	public void testStatisticalFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> session.createQuery( "select var_samp(e.theDouble), var_pop(abs(e.theDouble)), stddev_samp(e.theDouble), stddev_pop(e.theDouble) from EntityOfBasics e", Object[].class)
						.list()
		);
	}

	@Test
	public void testCurrentDateTimeFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select current time, current date, current timestamp from EntityOfBasics", Object[].class)
							.list();
					session.createQuery("select local time, local date, local datetime from EntityOfBasics", Object[].class)
							.list();
					session.createQuery("from EntityOfBasics e where e.theDate > current_date and e.theTime > current_time and e.theTimestamp > current_timestamp", EntityOfBasics.class)
							.list();
					session.createQuery("from EntityOfBasics e where e.theDate > local date and e.theTime > local time and e.theTimestamp > local datetime", EntityOfBasics.class)
							.list();
					session.createQuery("select instant from EntityOfBasics", Instant.class)
							.list();
					session.createQuery("select offset datetime from EntityOfBasics", OffsetDateTime.class)
							.list();
				}
		);
	}

	@Test
	public void testTimestampAddDiffFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select function('timestampadd',month,2,current date) from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select function('timestampdiff',hour,e.theTimestamp,current timestamp) from EntityOfBasics e", Long.class)
							.list();

					session.createQuery("select timestampadd(month,2,current date) from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select timestampdiff(hour,e.theTimestamp,current timestamp) from EntityOfBasics e", Long.class)
							.list();

					assertThat(
							session.createQuery("select timestampadd(day, 5, local datetime)", LocalDateTime.class).getSingleResult(),
							is( instanceOf(LocalDateTime.class) )
					);
					assertThat(
							session.createQuery("select timestampdiff(day, local datetime, local datetime)", Long.class).getSingleResult(),
							is( instanceOf(Long.class) )
					);
				}
		);
	}

	@Test
	@SkipForDialect(dialectClass = MySQLDialect.class)
	@SkipForDialect(dialectClass = MariaDBDialect.class)
	@SkipForDialect(dialectClass = TiDBDialect.class)
	@SkipForDialect(dialectClass = CockroachDialect.class, reason = "unsupported binary operator: <timestamptz> - <date>")
	public void testDateAddDiffFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					//we treat dateadd + datediff as synonyms for timestampadd + timestampdiff on most dbs
					assertThat(
							session.createQuery("select dateadd(day, 5, local datetime)", LocalDateTime.class).getSingleResult(),
							is( instanceOf(LocalDateTime.class) )
					);
					assertThat(
							session.createQuery("select datediff(day, local date, local datetime)", Long.class).getSingleResult(),
							is( instanceOf(Long.class) )
					);
				}
		);
	}

	@Test @RequiresDialect(MySQLDialect.class)
	public void testDatediffFunctionMySQL(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					//on MySQL we make room for the native datediff function
					assertThat(
							session.createQuery("select datediff(datetime 1999-07-19 00:00, datetime 1999-07-23 23:59)", Integer.class).getSingleResult(),
							is( instanceOf(Integer.class) )
					);
				}
		);
	}

	@Test
	public void testIntervalAddExpressions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select e.theDate + 1 year from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theDate + 2 month from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theDate + 7 day from EntityOfBasics e", Date.class)
							.list();

					session.createQuery("select e.theTime + 1 hour from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTime + 59 minute from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTime + 30 second from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTime + 300000000 nanosecond from EntityOfBasics e", Date.class)
							.list();

					session.createQuery("select e.theTimestamp + 1 year from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp + 2 month from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp + 7 day from EntityOfBasics e", Date.class)
							.list();

					session.createQuery("select e.theTimestamp + 1 hour from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp + 59 minute from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp + 30 second from EntityOfBasics e", Date.class)
							.list();

				}
		);
	}

	@Test
	public void testIntervalSubExpressions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select e.theDate - 1 year from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theDate - 2 month from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theDate - 7 day from EntityOfBasics e", Date.class)
							.list();

					session.createQuery("select e.theTime - 1 hour from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTime - 59 minute from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTime - 30 second from EntityOfBasics e", Date.class)
							.list();

					session.createQuery("select e.theTimestamp - 1 year from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp - 2 month from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp - 7 day from EntityOfBasics e", Date.class)
							.list();

					session.createQuery("select e.theTimestamp - 1 hour from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp - 59 minute from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp - 30 second from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp + 3.333e-3 second from EntityOfBasics e", Date.class)
							.list();

				}
		);
	}

	@Test
	public void testIntervalAddSubExpressions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select e.theTimestamp + 4 day - 1 week from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp - 4 day + 2 hour from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp + (4 day - 1 week) from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp - (4 day + 2 hour) from EntityOfBasics e", Date.class)
							.list();
				}
		);
	}

	@Test
	public void testIntervalScaleExpressions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select e.theTimestamp + 3 * 1 week from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp + 3 * (4 day - 1 week) from EntityOfBasics e", Date.class)
							.list();
					session.createQuery("select e.theTimestamp + 3.5 * (4 day - 1 week) from EntityOfBasics e", Date.class)
							.list();

					session.createQuery("select 4 day by second from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (4 day + 2 hour) by second from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (2 * 4 day) by second from EntityOfBasics e", Long.class)
							.list();
//					session.createQuery("select (1 year - 1 month) by day from EntityOfBasics e")
//							.list();

					session.createQuery("select (2 * (e.theTimestamp - e.theTimestamp) + 3 * (4 day + 2 hour)) by second from EntityOfBasics e", Long.class)
							.list();

					session.createQuery("select e.theDuration by second from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (2 * e.theDuration + 3 day) by hour from EntityOfBasics e", Long.class)
							.list();
				}
		);
	}

	@Test
	public void testInstantCast(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					Instant instant = Instant.ofEpochSecond(123456789);
					assertEquals( instant,
							session.createQuery("select cast(?1 as Instant)", Instant.class)
									.setParameter( 1, instant.atOffset(ZoneOffset.UTC) )
									.getSingleResult() );
					assertEquals( instant,
							session.createQuery("select cast(?1 as Instant)", Instant.class)
									.setParameter( 1, instant.atOffset(ZoneOffset.UTC).toLocalDateTime() )
									.getSingleResult() );
					assertEquals( instant.atOffset(ZoneOffset.UTC),
							session.createQuery("select cast(?1 as OffsetDateTime)", OffsetDateTime.class)
									.setParameter( 1, instant )
									.getSingleResult() );
					assertEquals( instant.atOffset(ZoneOffset.UTC).toLocalDateTime(),
							session.createQuery("select cast(?1 as LocalDateTime)", LocalDateTime.class)
									.setParameter( 1, instant )
									.getSingleResult() );
				}
		);
	}

	@Test
	public void testDurationCast(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertEquals( 3*1_000_000_000L + 23*1_000_000L,
							session.createQuery("select cast(e.theDuration as Long) from EntityOfBasics e", Long.class)
									.getSingleResult() );
					assertEquals( 5*60*1_000_000_000L,
							session.createQuery("select cast(5 minute as Long)", Long.class)
									.getSingleResult() );
					assertEquals( Duration.of(5, ChronoUnit.MINUTES),
							session.createQuery("select cast(?1 as Duration)", Duration.class)
									.setParameter(1, 5*60*1000000000L)
									.getSingleResult() );
					assertEquals( Duration.of(1, ChronoUnit.DAYS),
							session.createQuery("select cast(?1 as Duration)", Duration.class)
									.setParameter(1, 24*60*60*1000000000L)
									.getSingleResult() );
				}
		);
	}

	@Test
	public void testDurationBy(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertEquals( Duration.of(3, ChronoUnit.SECONDS).plus( Duration.of(23,ChronoUnit.MILLIS) ),
							session.createQuery("select e.theDuration from EntityOfBasics e", Duration.class)
									.getSingleResult() );
					assertEquals( 3L,
							session.createQuery("select e.theDuration by second from EntityOfBasics e", Long.class)
									.getSingleResult() );
					assertEquals( 0L,
							session.createQuery("select e.theDuration by day from EntityOfBasics e", Long.class)
									.getSingleResult() );
					assertEquals( 3_023_000_000L,
							session.createQuery("select e.theDuration by nanosecond from EntityOfBasics e", Long.class)
									.getSingleResult() );
				}
		);
	}

	@Test
	public void testDurationLiterals(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertEquals( Duration.of(3, ChronoUnit.SECONDS).plus( Duration.of(23,ChronoUnit.MILLIS) ),
							session.createQuery("select e.theDuration from EntityOfBasics e", Duration.class)
									.getSingleResult() );
					assertEquals( Duration.of(3, ChronoUnit.SECONDS).plus( Duration.of(23,ChronoUnit.MILLIS).plus( Duration.of(21, ChronoUnit.SECONDS) ) ),
							session.createQuery("select e.theDuration + 21 second from EntityOfBasics e", Duration.class)
									.getSingleResult() );
					assertEquals( Duration.of(3, ChronoUnit.SECONDS).plus( Duration.of(23,ChronoUnit.MILLIS).plus( Duration.of(2, ChronoUnit.DAYS) ) ),
							session.createQuery("select e.theDuration + 2 day from EntityOfBasics e", Duration.class)
									.getSingleResult() );
					assertEquals( Duration.of(2, ChronoUnit.DAYS),
							session.createQuery("select 2 day", Duration.class)
									.getSingleResult() );
					assertEquals( Duration.of(5, ChronoUnit.SECONDS),
							session.createQuery("select 5 second", Duration.class)
									.getSingleResult() );
					assertEquals( Duration.of(5, ChronoUnit.SECONDS).plus(Duration.of(2, ChronoUnit.DAYS)),
							session.createQuery("select 5 second + 2 day", Duration.class)
									.getSingleResult() );
					assertEquals( Duration.of(30, ChronoUnit.SECONDS),
							session.createQuery("select 3*(10 second)", Duration.class)
									.getSingleResult() );
					assertEquals( Duration.of(14, ChronoUnit.DAYS),
							session.createQuery("select 2*(7 day)", Duration.class)
									.getSingleResult() );
					assertEquals( Duration.of(15, ChronoUnit.SECONDS).plus(Duration.of(6, ChronoUnit.DAYS)),
							session.createQuery("select 3*(5 second + 2 day)", Duration.class)
									.getSingleResult() );
					assertEquals( Duration.of(6, ChronoUnit.SECONDS).plus( Duration.of(46,ChronoUnit.MILLIS) ),
							session.createQuery("select 2 * e.theDuration from EntityOfBasics e", Duration.class)
									.getSingleResult() );
				}
		);
	}

	@Test
	@SkipForDialect( dialectClass = TiDBDialect.class, reason = "Bug in the TiDB timestampadd function (https://github.com/pingcap/tidb/issues/41052)")
	public void testDurationArithmetic(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertEquals( LocalDate.now().minus(2, ChronoUnit.DAYS),
							session.createQuery("select local date - 2 day", LocalDate.class)
									.getSingleResult() );
					assertEquals( LocalDate.now().plus(1, ChronoUnit.MONTHS),
								  session.createQuery("select local date + 1 month", LocalDate.class)
										  .getSingleResult() );
					assertEquals( LocalDate.now().plus(1, ChronoUnit.YEARS),
								  session.createQuery("select local date + 1 year", LocalDate.class)
										  .getSingleResult() );
					assertEquals( LocalDate.now().plus(3, ChronoUnit.MONTHS),
								  session.createQuery("select local date + 1 quarter", LocalDate.class)
										  .getSingleResult() );
					// Some explicit 'special' cases:
					assertEquals( LocalDate.of(2024, 02, 29),
								  session.createQuery("select {2024-01-31} + 1 month", LocalDate.class)
										  .getSingleResult() );
					assertEquals( LocalDate.of(2025, 02, 28),
								  session.createQuery("select {2024-02-29} + 1 year", LocalDate.class)
										  .getSingleResult() );
					assertEquals( LocalDate.of(2028, 02, 29),
								  session.createQuery("select {2024-02-29} + 4 year", LocalDate.class)
										  .getSingleResult() );
					assertEquals( LocalDate.of(2025, 03, 29),
								  session.createQuery("select {2024-02-29} + 13 month", LocalDate.class)
										  .getSingleResult() );
					assertEquals( LocalDate.of(2024, 02, 29),
								  session.createQuery("select {2023-11-30} + 1 quarter", LocalDate.class)
										  .getSingleResult() );

					session.createQuery("select e.theTimestamp - 21 second from EntityOfBasics e", java.util.Date.class)
							.getSingleResult();
					session.createQuery("select e.theTimestamp + 2 day from EntityOfBasics e", java.util.Date.class)
							.getSingleResult();
					session.createQuery("select e.theTimestamp - 21 second + 2 day from EntityOfBasics e", java.util.Date.class)
							.getSingleResult();
					//TODO: FIX!!
//					session.createQuery("select e.theTimestamp + 2 * e.theDuration from EntityOfBasics e")
//							.list();
				}
		);
	}

	@Test
	public void testDurationArithmeticWithLiterals(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertEquals( LocalDate.of(1974,3,25),
							session.createQuery("select date 1974-03-23 + 2 day", LocalDate.class)
									.getSingleResult() );
					assertEquals( LocalDateTime.of(1974,3,25,5,30,25),
							session.createQuery("select datetime 1974-03-23 5:30:25 + 2 day", LocalDateTime.class)
									.getSingleResult() );
					assertEquals( LocalDateTime.of(1974,3,25,5,30,25),
							session.createQuery("select datetime 1974-03-25 5:30:46 - 21 second", LocalDateTime.class)
									.getSingleResult() );
					assertEquals( LocalDateTime.of(1974,3,25,5,30,25),
							session.createQuery("select datetime 1974-03-23 5:30:46 - 21 second + 2 day", LocalDateTime.class)
									.getSingleResult() );
					assertEquals( Duration.of(5, ChronoUnit.DAYS),
							session.createQuery("select date 1974-03-25 - date 1974-03-20", Duration.class)
									.getSingleResult() );
					assertEquals( 5L,
							session.createQuery("select (date 1974-03-25 - date 1974-03-20) by day", Long.class)
									.getSingleResult() );
					assertEquals( 5*24*60L,
							session.createQuery("select (date 1974-03-25 - date 1974-03-20) by minute", Long.class)
									.getSingleResult() );
					assertEquals( Duration.of(25, ChronoUnit.SECONDS),
							session.createQuery("select (datetime 1974-03-23 5:30:25 - datetime 1974-03-23 5:30:00)", Duration.class)
									.getSingleResult() );
					assertEquals( 25L,
							session.createQuery("select (datetime 1974-03-23 5:30:25 - datetime 1974-03-23 5:30:00) by second", Long.class)
									.getSingleResult() );
					assertEquals( 25L*1000000000,
							session.createQuery("select (datetime 1974-03-23 5:30:25 - datetime 1974-03-23 5:30:00) by nanosecond", Long.class)
									.getSingleResult() );
					assertEquals( Duration.of(10, ChronoUnit.MINUTES),
							session.createQuery("select (datetime 1974-03-23 5:30:25 - datetime 1974-03-23 5:20:25)", Duration.class)
									.getSingleResult() );
					assertEquals( 10L,
							session.createQuery("select (datetime 1974-03-23 5:30:25 - datetime 1974-03-23 5:20:25) by minute", Long.class)
									.getSingleResult() );
					assertEquals( 10L*60,
							session.createQuery("select (datetime 1974-03-23 5:30:25 - datetime 1974-03-23 5:20:25) by second", Long.class)
									.getSingleResult() );
					// timestampadd() might not work for time on at least some dbs:
//					assertEquals( LocalTime.of(5,30,25),
//							session.createQuery("select time 5:30:46 - 21 second")
//									.getSingleResult() );
				}
		);
	}

	@Test
	public void testIntervalDiffExpressions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select (e.theDate - e.theDate) by year from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theDate - e.theDate) by month from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theDate - e.theDate) by day from EntityOfBasics e", Long.class)
							.list();

					session.createQuery("select (e.theTimestamp - e.theTimestamp) by hour from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theTimestamp - e.theTimestamp) by minute from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theTimestamp - e.theTimestamp) by second from EntityOfBasics e", Long.class)
							.list();

					session.createQuery("select (e.theTimestamp - e.theTimestamp + 4 day) by second from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theTimestamp - (e.theTimestamp + 4 day)) by second from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theTimestamp + 4 day - e.theTimestamp) by second from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theTimestamp + 4 day - 2 hour - e.theTimestamp) by second from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theTimestamp - e.theTimestamp + 4 day + 2 hour) by second from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theTimestamp - (e.theTimestamp + 4 day + 2 hour)) by second from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theTimestamp + (4 day - 1 week) - e.theTimestamp) by second from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theTimestamp - e.theTimestamp + (4 day + 2 hour)) by second from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theTimestamp - (e.theTimestamp + (4 day + 2 hour))) by second from EntityOfBasics e", Long.class)
							.list();


					//these cause numerical overflow on Sybase
//					session.createQuery("select current_timestamp - e.theTimestamp from EntityOfBasics e")
//							.list();
//					session.createQuery("select current_timestamp - (current_timestamp - e.theTimestamp) from EntityOfBasics e")
//							.list();
				}
		);
	}

	@Test
	@SkipForDialect(dialectClass = CockroachDialect.class, reason = "unsupported binary operator: <date> - <timestamp(6)>")
	public void testIntervalDiffExpressionsDifferentTypes(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select (e.theDate - e.theTimestamp) by year from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theDate - e.theTimestamp) by month from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theDate - e.theTimestamp) by day from EntityOfBasics e", Long.class)
							.list();

					session.createQuery("select (e.theTimestamp - e.theDate) by year from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theTimestamp - e.theDate) by month from EntityOfBasics e", Long.class)
							.list();
					session.createQuery("select (e.theTimestamp - e.theDate) by day from EntityOfBasics e", Long.class)
							.list();
				}
		);
	}

	@Test
	public void testExtractFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select extract(year from e.theDate) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select extract(month from e.theDate) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select extract(day from e.theDate) from EntityOfBasics e", Integer.class)
							.list();

					session.createQuery("select extract(day of year from e.theDate) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select extract(day of month from e.theDate) from EntityOfBasics e", Integer.class)
							.list();

					session.createQuery("select extract(quarter from e.theDate) from EntityOfBasics e", Integer.class)
							.list();

					session.createQuery("select extract(hour from e.theTime) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select extract(minute from e.theTime) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select extract(second from e.theTime) from EntityOfBasics e", Float.class)
							.list();
					session.createQuery("select extract(nanosecond from e.theTime) from EntityOfBasics e", Long.class)
							.list();

					session.createQuery("select extract(year from e.theTimestamp) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select extract(month from e.theTimestamp) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select extract(day from e.theTimestamp) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select extract(hour from e.theTimestamp) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select extract(minute from e.theTimestamp) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select extract(second from e.theTimestamp) from EntityOfBasics e", Float.class)
							.list();

					session.createQuery("select extract(time from e.theTimestamp), extract(date from e.theTimestamp) from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select extract(time from local datetime), extract(date from local datetime) from EntityOfBasics e", Object[].class)
							.list();

					session.createQuery("select extract(week of month from current date) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select extract(week of year from current date) from EntityOfBasics e", Integer.class)
							.list();

					assertThat( session.createQuery("select extract(year from date 1974-03-25)", Integer.class).getSingleResult(), is(1974) );
					assertThat( session.createQuery("select extract(month from date 1974-03-25)", Integer.class).getSingleResult(), is(3) );
					assertThat( session.createQuery("select extract(day from date 1974-03-25)", Integer.class).getSingleResult(), is(25) );

					assertThat( session.createQuery("select extract(hour from time 12:30)", Integer.class).getSingleResult(), is(12) );
					assertThat( session.createQuery("select extract(minute from time 12:30)", Integer.class).getSingleResult(), is(30) );
				}
		);
	}

	@Test
	public void testExtractFunctionEpoch(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select extract(epoch from local datetime)", Long.class).getSingleResult();
					session.createQuery("select extract(epoch from offset datetime)", Long.class).getSingleResult();
					assertThat( session.createQuery("select extract(epoch from datetime 1974-03-23 12:35)", Long.class).getSingleResult(), is(133274100L) );
				}
		);
	}

	@Test
	public void testExtractFunctionWeek(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select extract(day of week from e.theDate) from EntityOfBasics e", Integer.class)
							.list();

					session.createQuery("select extract(week from e.theDate) from EntityOfBasics e", Integer.class)
							.list();

				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsTimezoneTypes.class)
	public void testExtractFunctionTimeZone(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select extract(offset hour from e.theZonedDateTime) from EntityOfBasics e", Integer.class)
							.list();
					session.createQuery("select extract(offset minute from e.theZonedDateTime) from EntityOfBasics e", Integer.class)
							.list();
				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsTimezoneTypes.class)
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsFormat.class, comment = "We extract the offset with a format function")
	public void testExtractFunctionTimeZoneOffset(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> session.createQuery( "select extract(offset from e.theZonedDateTime) from EntityOfBasics e", Integer.class)
						.list()
		);
	}

	@Test
	public void testExtractFunctionWithAssertions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat(
							session.createQuery("select extract(week of year from date 2019-01-01) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(1)
					);
					assertThat(
							session.createQuery("select extract(week of year from date 2019-01-05) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(1)
					);
					assertThat(
							session.createQuery("select extract(week of year from date 2019-01-06) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(2)
					);

					assertThat(
							session.createQuery("select extract(week of month from date 2019-05-01) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(1)
					);
					assertThat(
							session.createQuery("select extract(week of month from date 2019-05-04) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(1)
					);
					assertThat(
							session.createQuery("select extract(week of month from date 2019-05-05) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(2)
					);

					assertThat(
							session.createQuery("select extract(week from date 2019-05-27) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(22)
					);
					assertThat(
							session.createQuery("select extract(week from date 2019-06-02) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(22)
					);
					assertThat(
							session.createQuery("select extract(week from date 2019-06-03) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(23)
					);

					assertThat(
							session.createQuery("select extract(day of year from date 2019-05-30) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(150)
					);
					assertThat(
							session.createQuery("select extract(day of month from date 2019-05-27) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(27)
					);

					assertThat(
							session.createQuery("select extract(day from date 2019-05-31) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(31)
					);
					assertThat(
							session.createQuery("select extract(month from date 2019-05-31) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(5)
					);
					assertThat(
							session.createQuery("select extract(year from date 2019-05-31) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(2019)
					);
					assertThat(
							session.createQuery("select extract(quarter from date 2019-05-31) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(2)
					);

					assertThat(
							session.createQuery("select extract(day of week from date 2019-05-27) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(2)
					);
					assertThat(
							session.createQuery("select extract(day of week from date 2019-05-31) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(6)
					);

					assertThat(
							session.createQuery("select extract(second from time 14:12:10) from EntityOfBasics", Float.class).getResultList().get(0),
							is(10f)
					);
					assertThat(
							session.createQuery("select extract(minute from time 14:12:10) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(12)
					);
					assertThat(
							session.createQuery("select extract(hour from time 14:12:10) from EntityOfBasics", Integer.class).getResultList().get(0),
							is(14)
					);

					assertThat(
							session.createQuery("select extract(date from local datetime) from EntityOfBasics", LocalDate.class).getResultList().get(0),
							instanceOf(LocalDate.class)
					);
					assertThat(
							session.createQuery("select extract(time from local datetime) from EntityOfBasics", LocalTime.class).getResultList().get(0),
							instanceOf(LocalTime.class)
					);
				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsFormat.class)
	public void testFormat(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select format(e.theDate as 'dd/MM/yy'), format(e.theDate as 'EEEE, MMMM dd, yyyy') from EntityOfBasics e", Object[].class)
							.list();
					session.createQuery("select format(e.theTimestamp as 'dd/MM/yyyy ''at'' HH:mm:ss') from EntityOfBasics e", Date.class)
							.list();

					assertThat(
							session.createQuery("select format(theDate as 'EEEE, dd/MM/yyyy') from EntityOfBasics where id=123", String.class).getResultList().get(0),
							is("Monday, 25/03/1974")
					);
				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsFormat.class)
	@SkipForDialect(dialectClass = CockroachDialect.class, reason = "unknown signature: experimental_strftime(time, string)") // could cast the first argument to timestamp to workaround this
	public void testFormatTime(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select format(e.theTime as 'hh:mm:ss a') from EntityOfBasics e", Date.class)
							.list();
					assertThat(
							session.createQuery("select format(theTime as '''Hello'', hh:mm:ss a') from EntityOfBasics where id=123", String.class).getResultList().get(0),
							isOneOf( "Hello, 08:10:08 PM", "Hello, 08:10:08 pm" )
					);
				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsMedian.class)
	public void testMedian(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					List<Object[]> list = session.createQuery("select median(e.theDouble), median(e.theInt) from EntityOfBasics e", Object[].class)
							.list();
					assertEquals( 1, list.size() );
					Double d = (Double) list.get(0)[0];
					Double i = (Double) list.get(0)[1];
					assertEquals(d,1.0, 1e-5);
					assertEquals(i,5.0, 1e-5);
				}
		);
	}

	@Test
	public void testHyperbolic(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertEquals( Math.sinh(1), session.createQuery("select sinh(e.theDouble) from EntityOfBasics e", Double.class)
							.getSingleResult(), 1e-6 );
					assertEquals( Math.cosh(1), session.createQuery("select cosh(e.theDouble) from EntityOfBasics e", Double.class)
							.getSingleResult(), 1e-6 );
					assertEquals( Math.tanh(1), session.createQuery("select tanh(e.theDouble) from EntityOfBasics e", Double.class)
							.getSingleResult(), 1e-6 );
				}
		);
	}


	@Test
	public void testGrouping(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> session.createQuery( "select max(e.theDouble), e.gender, e.theInt from EntityOfBasics e group by e.gender, e.theInt", Object[].class)
						.list()
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsGroupByRollup.class)
	public void testGroupByRollup(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> session.createQuery( "select avg(e.theDouble), e.gender, e.theInt from EntityOfBasics e group by rollup(e.gender, e.theInt)", Object[].class)
						.list()
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsGroupByGroupingSets.class)
	public void testGroupByGroupingSets(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> session.createQuery( "select avg(e.theDouble), e.gender, e.theInt from EntityOfBasics e group by cube(e.gender, e.theInt)", Object[].class)
						.list()
		);
	}

	@Test
	public void testIn(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertEquals( 1,
							session.createQuery("select 1 where 1 in (:list)", Integer.class)
									.setParameterList("list",List.of(1,2))
									.list().size() );
					assertEquals( 0,
							session.createQuery("select 1 where 1 in (:list)", Integer.class)
									.setParameterList("list",List.of(0,3,2))
									.list().size() );
					assertEquals( 0,
							session.createQuery("select 1 where 1 in (:list)", Integer.class)
									.setParameterList("list",List.of())
									.list().size() );
					assertEquals( 1,
							session.createQuery("select 1 where 1 in :list", Integer.class)
									.setParameterList("list",List.of(1,2))
									.list().size() );
					assertEquals( 0,
							session.createQuery("select 1 where 1 in :list", Integer.class)
									.setParameterList("list",List.of(0,3,2))
									.list().size() );
					assertEquals( 0,
							session.createQuery("select 1 where 1 in :list", Integer.class)
									.setParameterList("list",List.of())
									.list().size() );
				}
		);
	}

	@Test
	public void testMaxGreatest(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertEquals(10L, session.createQuery("select greatest((select max(someLong) from SimpleEntity), (select max(someInteger) from SimpleEntity))", Long.class)
							.getSingleResult());
				}
		);
	}

	@Test
	@SkipForDialect(dialectClass = OracleDialect.class, reason = "HHH-16576")
	@SkipForDialect(dialectClass = MariaDBDialect.class, reason = "HHH-16576")
	@SkipForDialect(dialectClass = MySQLDialect.class, majorVersion = 5, reason = "HHH-16576")
	public void testMaxOverUnion(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertEquals(10L, session.createQuery("select max(val) from (select someLong as val from SimpleEntity union select someInteger as val from SimpleEntity)", Long.class)
							.getSingleResult());
				}
		);
	}

	@Test
	@SkipForDialect(dialectClass = DerbyDialect.class)
	public void testBetweenDates(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createSelectionQuery("select theDate from EntityOfBasics where theDate between local date and local date + 7 day").getResultList();
				}
		);
	}

	@Test
	public void testMemberOf(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createSelectionQuery("from EntityOfLists where org.hibernate.testing.orm.domain.gambit.EnumValue.THREE member of listOfEnums").getResultList();
				}
		);
	}

}
