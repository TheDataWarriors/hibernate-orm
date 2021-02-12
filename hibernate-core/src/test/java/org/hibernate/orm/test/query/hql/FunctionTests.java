/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.hql;

import org.hibernate.dialect.DerbyDialect;

import org.hibernate.testing.orm.domain.StandardDomainModel;
import org.hibernate.testing.orm.domain.gambit.EntityOfBasics;
import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SkipForDialect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Gavin King
 */
@ServiceRegistry
@DomainModel( standardModels = StandardDomainModel.GAMBIT )
@SessionFactory
public class FunctionTests {

	@BeforeAll
	public void prepareData(SessionFactoryScope scope) {
		scope.inTransaction(
				em -> {
					EntityOfBasics entity = new EntityOfBasics();
					entity.setId(123);
					entity.setTheDate( new Date( 74, 2, 25 ) );
					entity.setTheTime( new Time( 23, 10, 8 ) );
					entity.setTheTimestamp( new Timestamp( System.currentTimeMillis() ) );
					em.persist(entity);
				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsCharCodeConversion.class)
	public void testAsciiChrFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select function('ascii', 'x'), function('chr', 120) from EntityOfBasics w")
							.list();
					session.createQuery("from EntityOfBasics e where function('ascii', 'x') > 0")
							.list();
					session.createQuery("from EntityOfBasics e where function('chr', 120) = 'z'")
							.list();

					session.createQuery("select ascii('x'), chr(120) from EntityOfBasics w")
							.list();
					session.createQuery("from EntityOfBasics e where ascii('x') > 0")
							.list();
					session.createQuery("from EntityOfBasics e where chr(120) = 'z'")
							.list();
				}
		);
	}

	@Test
	public void testConcatFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select concat('foo', e.theString, 'bar') from EntityOfBasics e")
							.list();
					session.createQuery("select 'foo' || e.theString || 'bar' from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select concat('hello',' ','world')").getSingleResult(), is("hello world") );
					assertThat( session.createQuery("select 'hello'||' '||'world'").getSingleResult(), is("hello world") );
				}
		);
	}

	@Test
	public void testConcatFunctionParameters(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select cast(:hello as String)||cast(:world as String)").setParameter("hello","hello").setParameter("world","world").getSingleResult(), is("helloworld") );
					assertThat( session.createQuery("select cast(?1 as String)||cast(?2 as String)").setParameter(1,"hello").setParameter(2,"world").getSingleResult(), is("helloworld") );
					assertThat( session.createQuery("select cast(?1 as String)||cast(?1 as String)").setParameter(1,"hello").getSingleResult(), is("hellohello") );
				}
		);
	}

	@Test
	public void testCoalesceFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select coalesce(nullif('',''), e.gender, e.convertedGender) from EntityOfBasics e")
							.list();
					session.createQuery("select ifnull(e.gender, e.convertedGender) from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select coalesce(nullif('',''), nullif('bye','bye'), 'hello', 'oops')").getSingleResult(), is("hello") );
					assertThat( session.createQuery("select ifnull(nullif('bye','bye'), 'hello')").getSingleResult(), is("hello") );
				}
		);
	}

	@Test
	public void testNullifFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select nullif(e.theString, '') from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select nullif('foo', 'foo')").getSingleResult(), nullValue() );
					assertThat( session.createQuery("select nullif('foo', 'bar')").getSingleResult(), is("foo") );
				}
		);
	}

	@Test
	public void testTrigFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select sin(e.theDouble), cos(e.theDouble), tan(e.theDouble), asin(e.theDouble), acos(e.theDouble), atan(e.theDouble) from EntityOfBasics e")
							.list();
					session.createQuery("select atan2(sin(e.theDouble), cos(e.theDouble)) from EntityOfBasics e")
							.list();
				}
		);
	}

	@Test
	public void testMathFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select +e.theInt, -e.theInt from EntityOfBasics e")
							.list();
					session.createQuery("select abs(e.theInt), sign(e.theInt), mod(e.theInt, 2) from EntityOfBasics e")
							.list();
					session.createQuery("select e.theInt % 2 from EntityOfBasics e")
							.list();
					session.createQuery("select abs(e.theDouble), sign(e.theDouble), sqrt(e.theDouble) from EntityOfBasics e")
							.list();
					session.createQuery("select exp(e.theDouble), ln(e.theDouble + 1) from EntityOfBasics e")
							.list();
					session.createQuery("select power(e.theDouble + 1, 2.5) from EntityOfBasics e")
							.list();
					session.createQuery("select ceiling(e.theDouble), floor(e.theDouble) from EntityOfBasics e")
							.list();
					session.createQuery("select round(cast(e.theDouble as BigDecimal), 3) from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select abs(-2)").getSingleResult(), is(2) );
					assertThat( session.createQuery("select sign(-2)").getSingleResult(), is(-1) );
					assertThat( session.createQuery("select power(3.0,2.0)").getSingleResult(), is(9.0f) );
					assertThat( session.createQuery("select round(32.12345,2)").getSingleResult(), is(32.12f) );
					assertThat( session.createQuery("select mod(3,2)").getSingleResult(), is(1) );
					assertThat( session.createQuery("select 3%2").getSingleResult(), is(1) );
					assertThat( session.createQuery("select sqrt(9.0)").getSingleResult(), is(3.0f) );
				}
		);
	}

	@Test
	public void testLowerUpperFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select lower(e.theString), upper(e.theString) from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select lower('HELLO')").getSingleResult(), is("hello") );
					assertThat( session.createQuery("select upper('hello')").getSingleResult(), is("HELLO") );
				}
		);
	}

	@Test
	public void testLengthFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select length(e.theString) from EntityOfBasics e where length(e.theString) > 1")
							.list();
					assertThat( session.createQuery("select length('hello')").getSingleResult(), is(5) );
				}
		);
	}

	@Test
	public void testSubstringFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select substring(e.theString, e.theInt) from EntityOfBasics e")
							.list();
					session.createQuery("select substring(e.theString, 0, e.theInt) from EntityOfBasics e")
							.list();
					session.createQuery("select substring(e.theString from e.theInt) from EntityOfBasics e")
							.list();
					session.createQuery("select substring(e.theString from 0 for e.theInt) from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select substring('hello world',4, 5)").getSingleResult(), is("lo wo") );
				}
		);
	}

	@Test
	public void testLeftRightFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select left(e.theString, e.theInt), right(e.theString, e.theInt) from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select left('hello world', 5)").getSingleResult(), is("hello") );
					assertThat( session.createQuery("select right('hello world', 5)").getSingleResult(), is("world") );
				}
		);
	}

	@Test
	public void testPositionFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select position('hello' in e.theString) from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select position('world' in 'hello world')").getSingleResult(), is(7) );
				}
		);
	}

	@Test
	public void testLocateFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select locate('hello', e.theString) from EntityOfBasics e")
							.list();
					session.createQuery("select locate('hello', e.theString, e.theInteger) from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select locate('world', 'hello world')").getSingleResult(), is(7) );
				}
		);
	}

	@Test
	public void testOverlayFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat( session.createQuery("select overlay('hello world' placing 'goodbye' from 1 for 5) from EntityOfBasics")
							.list().get(0), is("goodbye world") );
					assertThat( session.createQuery("select overlay('hello world' placing 'goodbye' from 7 for 5) from EntityOfBasics")
							.list().get(0), is("hello goodbye") );
					assertThat( session.createQuery("select overlay('xxxxxx' placing 'yy' from 3) from EntityOfBasics")
							.list().get(0), is("xxyyxx") );
					assertThat( session.createQuery("select overlay('xxxxxx' placing ' yy ' from 3 for 2) from EntityOfBasics")
							.list().get(0), is("xx yy xx") );
				}
		);
	}

	@Test
	@SkipForDialect(dialectClass = DerbyDialect.class, reason = "Derby doesn't support a parameter in the 'length' function or the 'char' function which we render as emulation")
	public void testOverlayFunctionParameters(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select overlay(?1 placing 'yy' from 3)")
							.setParameter(1, "xxxxxx")
							.list();
					session.createQuery("select overlay('xxxxxx' placing ?1 from 3)")
							.setParameter(1, "yy")
							.list();
					session.createQuery("select overlay('xxxxxx' placing 'yy' from ?1)")
							.setParameter(1, 3)
							.list();
					session.createQuery("select overlay(?2 placing ?1 from 3)")
							.setParameter(1, "yy")
							.setParameter(2, "xxxxxx")
							.list();
					session.createQuery("select overlay(:text placing :rep from 3)")
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
					session.createQuery("select replace(e.theString, 'hello', 'goodbye') from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select replace('hello world', 'hello', 'goodbye')").getSingleResult(), is("goodbye world") );
					assertThat( session.createQuery("select replace('hello world', 'o', 'ooo')").getSingleResult(), is("hellooo wooorld") );
				}
		);
	}

	@Test
	public void testTrimFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select trim(leading ' ' from e.theString) from EntityOfBasics e")
							.list();
					session.createQuery("select trim(trailing ' ' from e.theString) from EntityOfBasics e")
							.list();
					session.createQuery("select trim(both ' ' from e.theString) from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select trim(leading from '   hello')").getSingleResult(), is("hello") );
					assertThat( session.createQuery("select trim(trailing from 'hello   ')").getSingleResult(), is("hello") );
					assertThat( session.createQuery("select trim(both from '   hello   ')").getSingleResult(), is("hello") );
					assertThat( session.createQuery("select trim(both '-' from '---hello---')").getSingleResult(), is("hello") );
				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsPadWithChar.class)
	public void testPadFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat(session.createQuery("select pad('hello' with 10 leading)").getSingleResult(),
							is("     hello"));
					assertThat(session.createQuery("select pad('hello' with 10 trailing)").getSingleResult(),
							is("hello     "));
					assertThat(session.createQuery("select pad('hello' with 10 leading '.')").getSingleResult(),
							is(".....hello"));
					assertThat(session.createQuery("select pad('hello' with 10 trailing '.')").getSingleResult(),
							is("hello....."));
				}
		);
	}

	@Test
	@SkipForDialect(dialectClass = DerbyDialect.class, reason = "Derby doesn't support a parameter in the 'length' function or the 'char' function which we render as emulation")
	public void testPadFunctionParameters(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select pad(?1 with ?2 leading)")
							.setParameter(1, "hello")
							.setParameter(2, 10)
							.getSingleResult();
					session.createQuery("select pad(:string with :length leading)")
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
					assertThat( ((String) session.createQuery("select cast(e.theBoolean as String) from EntityOfBasics e").getSingleResult()).toLowerCase(), is("false") );
					assertThat( ((String) session.createQuery("select cast(e.theNumericBoolean as String) from EntityOfBasics e").getSingleResult()).toLowerCase(), is("false") );
					assertThat( ((String) session.createQuery("select cast(e.theStringBoolean as String) from EntityOfBasics e").getSingleResult()).toLowerCase(), is("false") );

					session.createQuery("select cast(e.theDate as String), cast(e.theTime as String), cast(e.theTimestamp as String) from EntityOfBasics e")
							.list();
					session.createQuery("select cast(e.id as String), cast(e.theInt as String), cast(e.theDouble as String) from EntityOfBasics e")
							.list();
					session.createQuery("select cast(e.id as Float), cast(e.theInt as Double), cast(e.theDouble as Long) from EntityOfBasics e")
							.list();
					session.createQuery("select cast(e.id as BigInteger(10)), cast(e.theDouble as BigDecimal(10,5)) from EntityOfBasics e")
							.list();
					session.createQuery("select cast(e.theString as String(15)), cast(e.theDouble as String(8)) from EntityOfBasics e")
							.list();

					session.createQuery("select cast('1002342345234523.452435245245243' as BigDecimal) from EntityOfBasics")
							.list();
					session.createQuery("select cast('1002342345234523.452435245245243' as BigDecimal(30, 10)) from EntityOfBasics")
							.list();
					session.createQuery("select cast('1234234523452345243524524524' as BigInteger) from EntityOfBasics")
							.list();
					session.createQuery("select cast('1234234523452345243524524524' as BigInteger(30)) from EntityOfBasics")
							.list();
					session.createQuery("select cast('3811234234.12312' as Double) from EntityOfBasics")
							.list();
					session.createQuery("select cast('1234234' as Integer) from EntityOfBasics")
							.list();
					session.createQuery("select cast(1 as Boolean), cast(0 as Boolean) from EntityOfBasics")
							.list();
					session.createQuery("select cast('ABCDEF' as Character) from EntityOfBasics")
							.list();

					session.createQuery("select cast('12:13:14' as Time) from EntityOfBasics")
							.list();
					session.createQuery("select cast('1911-10-09' as Date) from EntityOfBasics")
							.list();
					session.createQuery("select cast('1911-10-09 12:13:14.123' as Timestamp) from EntityOfBasics")
							.list();

					session.createQuery("select cast('12:13:14' as LocalTime) from EntityOfBasics")
							.list();
					session.createQuery("select cast('1911-10-09' as LocalDate) from EntityOfBasics")
							.list();
					session.createQuery("select cast('1911-10-09 12:13:14.123' as LocalDateTime) from EntityOfBasics")
							.list();

					assertThat( session.createQuery("select cast(1 as Boolean)").getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast(0 as Boolean)").getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast('123' as Integer)").getSingleResult(), is(123) );
					assertThat( session.createQuery("select cast('123' as Long)").getSingleResult(), is(123l) );
					assertThat( session.createQuery("select cast('123.12' as Float)").getSingleResult(), is(123.12f) );
//					assertThat( session.createQuery("select cast('123.12' as Double)").getSingleResult(), is(123.12d) );

					assertThat( session.createQuery("select cast('hello' as String)").getSingleResult(), is("hello") );
					assertThat( ((String) session.createQuery("select cast(true as String)").getSingleResult()).toLowerCase(), is("true") );
					assertThat( ((String) session.createQuery("select cast(false as String)").getSingleResult()).toLowerCase(), is("false") );
					assertThat( session.createQuery("select cast(123 as String)").getSingleResult(), is("123") );
//					assertThat( session.createQuery("select cast(123.12 as String)").getSingleResult(), is("123.12") );

					assertThat( session.createQuery("select cast('1911-10-09' as LocalDate)").getSingleResult(), is(LocalDate.of(1911,10,9)) );
					assertThat( session.createQuery("select cast('12:13:14' as LocalTime)").getSingleResult(), is(LocalTime.of(12,13,14)) );
					assertThat( session.createQuery("select cast('1911-10-09 12:13:14' as LocalDateTime)").getSingleResult(), is(LocalDateTime.of(1911,10,9,12,13,14)) );

					assertThat( session.createQuery("select cast(date 1911-10-09 as String)").getSingleResult(), is("1911-10-09") );
					assertThat( session.createQuery("select cast(time 12:13:14 as String)").getSingleResult(), is("12:13:14") );
					assertThat( (String) session.createQuery("select cast(datetime 1911-10-09 12:13:14 as String)").getSingleResult(), startsWith("1911-10-09 12:13:14") );

					assertThat( session.createQuery("select cast(1 as NumericBoolean)").getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast(0 as NumericBoolean)").getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast(true as YesNo)").getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast(false as YesNo)").getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast(1 as YesNo)").getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast(0 as YesNo)").getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast(true as TrueFalse)").getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast(false as TrueFalse)").getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast(1 as TrueFalse)").getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast(0 as TrueFalse)").getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast('Y' as YesNo)").getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast('N' as YesNo)").getSingleResult(), is(false) );
					assertThat( session.createQuery("select cast('T' as TrueFalse)").getSingleResult(), is(true) );
					assertThat( session.createQuery("select cast('F' as TrueFalse)").getSingleResult(), is(false) );
				}
		);
	}

	@Test
	@SkipForDialect(dialectClass = DerbyDialect.class, reason = "Derby doesn't support casting to the binary types")
	public void testCastFunctionBinary(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select cast(e.theString as Binary) from EntityOfBasics e")
							.list();
					session.createQuery("select cast(e.theString as Binary(10)) from EntityOfBasics e")
							.list();
				}
		);
	}

	@Test
	public void testStrFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select str(e.theDate), str(e.theTime), str(e.theTimestamp) from EntityOfBasics e")
							.list();
					session.createQuery("select str(e.id), str(e.theInt), str(e.theDouble) from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select str(69)").getSingleResult(), is("69") );
					assertThat( session.createQuery("select str(date 1911-10-09)").getSingleResult(), is("1911-10-09") );
					assertThat( session.createQuery("select str(time 12:13:14)").getSingleResult(), is("12:13:14") );
				}
		);
	}

	@Test
	public void testExtractFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select year(e.theDate) from EntityOfBasics e")
							.list();
					session.createQuery("select month(e.theDate) from EntityOfBasics e")
							.list();
					session.createQuery("select day(e.theDate) from EntityOfBasics e")
							.list();

					session.createQuery("select hour(e.theTime) from EntityOfBasics e")
							.list();
					session.createQuery("select minute(e.theTime) from EntityOfBasics e")
							.list();
					session.createQuery("select second(e.theTime) from EntityOfBasics e")
							.list();

					session.createQuery("select year(e.theTimestamp) from EntityOfBasics e")
							.list();
					session.createQuery("select month(e.theTimestamp) from EntityOfBasics e")
							.list();
					session.createQuery("select day(e.theTimestamp) from EntityOfBasics e")
							.list();
					session.createQuery("select hour(e.theTimestamp) from EntityOfBasics e")
							.list();
					session.createQuery("select minute(e.theTimestamp) from EntityOfBasics e")
							.list();
					session.createQuery("select second(e.theTimestamp) from EntityOfBasics e")
							.list();
				}
		);
	}


	@Test
	public void testLeastGreatestFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select least(1, 2, e.theInt, -1), greatest(1, e.theInt, 2, -1) from EntityOfBasics e")
							.list();
					session.createQuery("select least(0.0, e.theDouble), greatest(0.0, e.theDouble, 2.0) from EntityOfBasics e")
							.list();
					assertThat( session.createQuery("select least(1,2,-1,3,4)").getSingleResult(), is(-1) );
					assertThat( session.createQuery("select greatest(1,2,-1,30,4)").getSingleResult(), is(30) );
				}
		);
	}

	@Test
	public void testCountFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select count(*) from EntityOfBasics e")
							.list();
					session.createQuery("select count(e) from EntityOfBasics e")
							.list();
					session.createQuery("select count(distinct e) from EntityOfBasics e")
							.list();
				}
		);
	}

	@Test
	public void testAggregateFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select avg(e.theDouble), avg(abs(e.theDouble)), min(e.theDouble), max(e.theDouble), sum(e.theDouble), sum(e.theInt) from EntityOfBasics e")
							.list();
					session.createQuery("select avg(distinct e.theInt) from EntityOfBasics e")
							.list();
					session.createQuery("select sum(distinct e.theInt) from EntityOfBasics e")
							.list();
					session.createQuery("select any(e.theInt > 0), every(e.theInt > 0) from EntityOfBasics e")
							.list();
					//not supported by grammar:
//					session.createQuery("select any(e.theBoolean), every(e.theBoolean) from EntityOfBasics e")
//							.list();
					session.createQuery("select some(e.theInt > 0), all(e.theInt > 0) from EntityOfBasics e")
							.list();
				}
		);
	}

	@Test
	public void testCurrentDateTimeFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select current time, current date, current timestamp from EntityOfBasics")
							.list();
					session.createQuery("select local time, local date, local datetime from EntityOfBasics")
							.list();
					session.createQuery("from EntityOfBasics e where e.theDate > current_date and e.theTime > current_time and e.theTimestamp > current_timestamp")
							.list();
					session.createQuery("from EntityOfBasics e where e.theDate > local date and e.theTime > local time and e.theTimestamp > local datetime")
							.list();
					session.createQuery("select instant from EntityOfBasics")
							.list();
					session.createQuery("select offset datetime from EntityOfBasics")
							.list();
				}
		);
	}

	@Test
	public void testTimestampAddDiffFunctions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select function('timestampadd',month,2,current date) from EntityOfBasics e")
							.list();
					session.createQuery("select function('timestampdiff',hour,e.theTimestamp,current timestamp) from EntityOfBasics e")
							.list();

					session.createQuery("select timestampadd(month,2,current date) from EntityOfBasics e")
							.list();
					session.createQuery("select timestampdiff(hour,e.theTimestamp,current timestamp) from EntityOfBasics e")
							.list();
				}
		);
	}

	@Test
	public void testIntervalAddExpressions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select e.theDate + 1 year from EntityOfBasics e")
							.list();
					session.createQuery("select e.theDate + 2 month from EntityOfBasics e")
							.list();
					session.createQuery("select e.theDate + 7 day from EntityOfBasics e")
							.list();

					session.createQuery("select e.theTime + 1 hour from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTime + 59 minute from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTime + 30 second from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTime + 300000000 nanosecond from EntityOfBasics e")
							.list();

					session.createQuery("select e.theTimestamp + 1 year from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp + 2 month from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp + 7 day from EntityOfBasics e")
							.list();

					session.createQuery("select e.theTimestamp + 1 hour from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp + 59 minute from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp + 30 second from EntityOfBasics e")
							.list();

				}
		);
	}

	@Test
	public void testIntervalSubExpressions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select e.theDate - 1 year from EntityOfBasics e")
							.list();
					session.createQuery("select e.theDate - 2 month from EntityOfBasics e")
							.list();
					session.createQuery("select e.theDate - 7 day from EntityOfBasics e")
							.list();

					session.createQuery("select e.theTime - 1 hour from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTime - 59 minute from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTime - 30 second from EntityOfBasics e")
							.list();

					session.createQuery("select e.theTimestamp - 1 year from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp - 2 month from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp - 7 day from EntityOfBasics e")
							.list();

					session.createQuery("select e.theTimestamp - 1 hour from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp - 59 minute from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp - 30 second from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp + 3.333e-3 second from EntityOfBasics e")
							.list();

				}
		);
	}

	@Test
	public void testIntervalAddSubExpressions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select e.theTimestamp + 4 day - 1 week from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp - 4 day + 2 hour from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp + (4 day - 1 week) from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp - (4 day + 2 hour) from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp + 2 * e.theDuration from EntityOfBasics e")
							.list();
				}
		);
	}

	@Test
	public void testIntervalScaleExpressions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select e.theTimestamp + 3 * 1 week from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp + 3 * (4 day - 1 week) from EntityOfBasics e")
							.list();
					session.createQuery("select e.theTimestamp + 3.5 * (4 day - 1 week) from EntityOfBasics e")
							.list();

					session.createQuery("select 4 day by second from EntityOfBasics e")
							.list();
					session.createQuery("select (4 day + 2 hour) by second from EntityOfBasics e")
							.list();
					session.createQuery("select (2 * 4 day) by second from EntityOfBasics e")
							.list();
//					session.createQuery("select (1 year - 1 month) by day from EntityOfBasics e")
//							.list();

					session.createQuery("select (2 * (e.theTimestamp - e.theTimestamp) + 3 * (4 day + 2 hour)) by second from EntityOfBasics e")
							.list();

					session.createQuery("select e.theDuration by second from EntityOfBasics e")
							.list();
					session.createQuery("select (2 * e.theDuration + 3 day) by hour from EntityOfBasics e")
							.list();
				}
		);
	}

	@Test
	public void testIntervalDiffExpressions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select (e.theDate - e.theDate) by year from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theDate - e.theDate) by month from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theDate - e.theDate) by day from EntityOfBasics e")
							.list();

					session.createQuery("select (e.theDate - e.theTimestamp) by year from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theDate - e.theTimestamp) by month from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theDate - e.theTimestamp) by day from EntityOfBasics e")
							.list();

					session.createQuery("select (e.theTimestamp - e.theDate) by year from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theTimestamp - e.theDate) by month from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theTimestamp - e.theDate) by day from EntityOfBasics e")
							.list();

					session.createQuery("select (e.theTimestamp - e.theTimestamp) by hour from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theTimestamp - e.theTimestamp) by minute from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theTimestamp - e.theTimestamp) by second from EntityOfBasics e")
							.list();

					session.createQuery("select (e.theTimestamp - e.theTimestamp + 4 day) by second from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theTimestamp - (e.theTimestamp + 4 day)) by second from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theTimestamp + 4 day - e.theTimestamp) by second from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theTimestamp + 4 day - 2 hour - e.theTimestamp) by second from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theTimestamp - e.theTimestamp + 4 day + 2 hour) by second from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theTimestamp - (e.theTimestamp + 4 day + 2 hour)) by second from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theTimestamp + (4 day - 1 week) - e.theTimestamp) by second from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theTimestamp - e.theTimestamp + (4 day + 2 hour)) by second from EntityOfBasics e")
							.list();
					session.createQuery("select (e.theTimestamp - (e.theTimestamp + (4 day + 2 hour))) by second from EntityOfBasics e")
							.list();


					session.createQuery("select current_timestamp - (current_timestamp - e.theTimestamp) from EntityOfBasics e")
							.list();
				}
		);
	}

	@Test
	public void testExtractFunction(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select extract(year from e.theDate) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(month from e.theDate) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(day from e.theDate) from EntityOfBasics e")
							.list();

					session.createQuery("select extract(day of year from e.theDate) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(day of month from e.theDate) from EntityOfBasics e")
							.list();

					session.createQuery("select extract(quarter from e.theDate) from EntityOfBasics e")
							.list();

					session.createQuery("select extract(hour from e.theTime) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(minute from e.theTime) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(second from e.theTime) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(nanosecond from e.theTime) from EntityOfBasics e")
							.list();

					session.createQuery("select extract(year from e.theTimestamp) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(month from e.theTimestamp) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(day from e.theTimestamp) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(hour from e.theTimestamp) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(minute from e.theTimestamp) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(second from e.theTimestamp) from EntityOfBasics e")
							.list();

					session.createQuery("select extract(time from e.theTimestamp), extract(date from e.theTimestamp) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(time from local datetime), extract(date from local datetime) from EntityOfBasics e")
							.list();

					session.createQuery("select extract(week of month from current date) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(week of year from current date) from EntityOfBasics e")
							.list();

					assertThat( session.createQuery("select extract(year from date 1974-03-25)").getSingleResult(), is(1974) );
					assertThat( session.createQuery("select extract(month from date 1974-03-25)").getSingleResult(), is(3) );
					assertThat( session.createQuery("select extract(day from date 1974-03-25)").getSingleResult(), is(25) );

					assertThat( session.createQuery("select extract(hour from time 12:30)").getSingleResult(), is(12) );
					assertThat( session.createQuery("select extract(minute from time 12:30)").getSingleResult(), is(30) );
				}
		);
	}

	@Test
	public void testExtractFunctionWeek(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select extract(day of week from e.theDate) from EntityOfBasics e")
							.list();

					session.createQuery("select extract(week from e.theDate) from EntityOfBasics e")
							.list();

				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsTimezoneTypes.class)
	public void testExtractFunctionTimeZone(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select extract(offset hour from e.theZonedDateTime) from EntityOfBasics e")
							.list();
					session.createQuery("select extract(offset minute from e.theZonedDateTime) from EntityOfBasics e")
							.list();

					session.createQuery("select extract(offset from e.theZonedDateTime) from EntityOfBasics e")
							.list();
				}
		);
	}

	@Test
	public void testExtractFunctionWithAssertions(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					assertThat(
							session.createQuery("select extract(week of year from date 2019-01-01) from EntityOfBasics").getResultList().get(0),
							is(1)
					);
					assertThat(
							session.createQuery("select extract(week of year from date 2019-01-05) from EntityOfBasics").getResultList().get(0),
							is(1)
					);
					assertThat(
							session.createQuery("select extract(week of year from date 2019-01-06) from EntityOfBasics").getResultList().get(0),
							is(2)
					);

					assertThat(
							session.createQuery("select extract(week of month from date 2019-05-01) from EntityOfBasics").getResultList().get(0),
							is(1)
					);
					assertThat(
							session.createQuery("select extract(week of month from date 2019-05-04) from EntityOfBasics").getResultList().get(0),
							is(1)
					);
					assertThat(
							session.createQuery("select extract(week of month from date 2019-05-05) from EntityOfBasics").getResultList().get(0),
							is(2)
					);

					assertThat(
							session.createQuery("select extract(week from date 2019-05-27) from EntityOfBasics").getResultList().get(0),
							is(22)
					);
					assertThat(
							session.createQuery("select extract(week from date 2019-06-02) from EntityOfBasics").getResultList().get(0),
							is(22)
					);
					assertThat(
							session.createQuery("select extract(week from date 2019-06-03) from EntityOfBasics").getResultList().get(0),
							is(23)
					);

					assertThat(
							session.createQuery("select extract(day of year from date 2019-05-30) from EntityOfBasics").getResultList().get(0),
							is(150)
					);
					assertThat(
							session.createQuery("select extract(day of month from date 2019-05-27) from EntityOfBasics").getResultList().get(0),
							is(27)
					);

					assertThat(
							session.createQuery("select extract(day from date 2019-05-31) from EntityOfBasics").getResultList().get(0),
							is(31)
					);
					assertThat(
							session.createQuery("select extract(month from date 2019-05-31) from EntityOfBasics").getResultList().get(0),
							is(5)
					);
					assertThat(
							session.createQuery("select extract(year from date 2019-05-31) from EntityOfBasics").getResultList().get(0),
							is(2019)
					);
					assertThat(
							session.createQuery("select extract(quarter from date 2019-05-31) from EntityOfBasics").getResultList().get(0),
							is(2)
					);

					assertThat(
							session.createQuery("select extract(day of week from date 2019-05-27) from EntityOfBasics").getResultList().get(0),
							is(2)
					);
					assertThat(
							session.createQuery("select extract(day of week from date 2019-05-31) from EntityOfBasics").getResultList().get(0),
							is(6)
					);

					assertThat(
							session.createQuery("select extract(second from time 14:12:10) from EntityOfBasics").getResultList().get(0),
							is(10f)
					);
					assertThat(
							session.createQuery("select extract(minute from time 14:12:10) from EntityOfBasics").getResultList().get(0),
							is(12)
					);
					assertThat(
							session.createQuery("select extract(hour from time 14:12:10) from EntityOfBasics").getResultList().get(0),
							is(14)
					);

					assertThat(
							session.createQuery("select extract(date from local datetime) from EntityOfBasics").getResultList().get(0),
							instanceOf(LocalDate.class)
					);
					assertThat(
							session.createQuery("select extract(time from local datetime) from EntityOfBasics").getResultList().get(0),
							instanceOf(LocalTime.class)
					);
				}
		);
	}

	@Test
	@SkipForDialect(dialectClass = DerbyDialect.class, reason = "Derby doesn't support formatting temporal types to strings")
	public void testFormat(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select format(e.theTime as 'hh:mm:ss aa') from EntityOfBasics e")
							.list();
					session.createQuery("select format(e.theDate as 'dd/MM/yy'), format(e.theDate as 'EEEE, MMMM dd, yyyy') from EntityOfBasics e")
							.list();
					session.createQuery("select format(e.theTimestamp as 'dd/MM/yyyy ''at'' HH:mm:ss') from EntityOfBasics e")
							.list();

					assertThat(
							session.createQuery("select format(theDate as 'EEEE, dd/MM/yyyy') from EntityOfBasics where id=123").getResultList().get(0),
							is("Monday, 25/03/1974")
					);
					assertThat(
							session.createQuery("select format(theTime as '''Hello'', hh:mm:ss aa') from EntityOfBasics where id=123").getResultList().get(0),
							is("Hello, 11:10:08 PM")
					);
				}
		);
	}

	@Test
	public void testGrouping(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select max(e.theDouble), e.gender, e.theInt from EntityOfBasics e group by e.gender, e.theInt")
							.list();
				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsGroupByRollup.class)
	public void testGroupByRollup(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select avg(e.theDouble), e.gender, e.theInt from EntityOfBasics e group by rollup(e.gender, e.theInt)")
							.list();
				}
		);
	}

	@Test
	@RequiresDialectFeature(feature = DialectFeatureChecks.SupportsGroupByGroupingSets.class)
	public void testGroupByGroupingSets(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery("select avg(e.theDouble), e.gender, e.theInt from EntityOfBasics e group by cube(e.gender, e.theInt)")
							.list();
				}
		);
	}

}