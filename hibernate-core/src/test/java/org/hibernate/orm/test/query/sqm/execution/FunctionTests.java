/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.sqm.execution;

import org.hibernate.boot.MetadataSources;
import org.hibernate.testing.orm.domain.StandardDomainModel;

import org.hibernate.testing.junit5.SessionFactoryBasedFunctionalTest;
import org.junit.jupiter.api.Test;

/**
 * @author Gavin King
 */
public class FunctionTests extends SessionFactoryBasedFunctionalTest {

    @Override
    protected void applyMetadataSources(MetadataSources metadataSources) {
        StandardDomainModel.GAMBIT.getDescriptor().applyDomainModel(metadataSources);
    }

    @Test
    public void testDateTimeFunctions() {
        inTransaction(
                session -> {
                    session.createQuery("select current_time, current_date, current_timestamp from EntityOfBasics e")
                            .list();
                    session.createQuery("from EntityOfBasics e where e.theDate > current_date and e.theTime > current_time and e.theTimestamp > current_timestamp")
                            .list();
                    session.createQuery("select current_instant from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testConcatFunction() {
        inTransaction(
                session -> {
                    session.createQuery("select concat('foo', e.theString, 'bar') from EntityOfBasics e")
                            .list();
                    session.createQuery("select 'foo' || e.theString || 'bar' from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testCoalesceFunction() {
        inTransaction(
                session -> {
                    session.createQuery("select coalesce(null, e.gender, e.convertedGender, e.ordinalGender) from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testNullifFunction() {
        inTransaction(
                session -> {
                    session.createQuery("select nullif(e.theString, '') from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testTrigFunctions() {
        inTransaction(
                session -> {
                    session.createQuery("select sin(e.theDouble), cos(e.theDouble), tan(e.theDouble), asin(e.theDouble), acos(e.theDouble), atan(e.theDouble) from EntityOfBasics e")
                            .list();
                    session.createQuery("select atan2(sin(e.theDouble), cos(e.theDouble)) from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testMathFunctions() {
        inTransaction(
                session -> {
                    session.createQuery("select abs(e.theInt), sign(e.theInt), mod(e.theInt, 2) from EntityOfBasics e")
                            .list();
                    session.createQuery("select abs(e.theDouble), sign(e.theDouble), sqrt(e.theDouble) from EntityOfBasics e")
                            .list();
                    session.createQuery("select exp(e.theDouble), ln(e.theDouble) from EntityOfBasics e")
                            .list();
                    session.createQuery("select power(e.theDouble, 2.5) from EntityOfBasics e")
                            .list();
                    session.createQuery("select ceiling(e.theDouble), floor(e.theDouble) from EntityOfBasics e")
                            .list();
                    session.createQuery("select round(e.theDouble, 3) from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testAsciiChrFunctions() {
        inTransaction(
                session -> {
                    session.createQuery("select ascii('x') from EntityOfBasics e")
                            .list();
                    session.createQuery("select chr(120) from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testLowerUpperFunctions() {
        inTransaction(
                session -> {
                    session.createQuery("select lower(e.theString), upper(e.theString) from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testLengthFunctions() {
        inTransaction(
                session -> {
                    session.createQuery("select length(e.theString) from EntityOfBasics e where length(e.theString) > 1")
                            .list();
                    session.createQuery("select bit_length(e.theString), character_length(e.theString) from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testSubstringFunction() {
        inTransaction(
                session -> {
                    session.createQuery("select substring(e.theString, e.theInt) from EntityOfBasics e")
                            .list();
                    session.createQuery("select substring(e.theString, 0, e.theInt) from EntityOfBasics e")
                            .list();
                    session.createQuery("select substring(e.theString from e.theInt) from EntityOfBasics e")
                            .list();
                    session.createQuery("select substring(e.theString from 0 for e.theInt) from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testPositionFunction() {
        inTransaction(
                session -> {
                    session.createQuery("select position('hello' in e.theString) from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testLocateFunction() {
        inTransaction(
                session -> {
                    session.createQuery("select locate('hello', e.theString) from EntityOfBasics e")
                            .list();
                    session.createQuery("select locate('hello', e.theString, e.theInteger) from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testReplaceFunction() {
        inTransaction(
                session -> {
                    session.createQuery("select replace(e.theString, 'hello', 'goodbye') from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testTrimFunction() {
        inTransaction(
                session -> {
                    session.createQuery("select trim(leading ' ' from e.theString) from EntityOfBasics e")
                            .list();
                    session.createQuery("select trim(trailing ' ' from e.theString) from EntityOfBasics e")
                            .list();
                    session.createQuery("select trim(both ' ' from e.theString) from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testCastFunction() {
        inTransaction(
                session -> {
                    session.createQuery("select cast(e.theDate as string) from EntityOfBasics e")
                            .list();
                    session.createQuery("select cast(e.id as string) from EntityOfBasics e")
                            .list();
                }
        );
    }

    @Test
    public void testExtractFunction() {
        inTransaction(
                session -> {
                    session.createQuery("select extract(year from e.theDate) from EntityOfBasics e")
                            .list();
                    session.createQuery("select extract(month from e.theDate) from EntityOfBasics e")
                            .list();
                    session.createQuery("select extract(day from e.theDate) from EntityOfBasics e")
                            .list();

                    session.createQuery("select extract(hour from e.theTime) from EntityOfBasics e")
                            .list();
                    session.createQuery("select extract(minute from e.theTime) from EntityOfBasics e")
                            .list();
                    session.createQuery("select extract(second from e.theTime) from EntityOfBasics e")
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
                }
        );
    }

    @Test
    public void testCountFunction() {
        inTransaction(
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
    public void testAggregateFunctions() {
        inTransaction(
                session -> {
                    session.createQuery("select avg(e.theDouble), avg(abs(e.theDouble)), min(e.theDouble), max(e.theDouble), sum(e.theDouble), sum(e.theInt) from EntityOfBasics e")
                            .list();
                    session.createQuery("select avg(distinct e.theInt), sum(distinct e.theInt) from EntityOfBasics e")
                            .list();
                }
        );
    }

}