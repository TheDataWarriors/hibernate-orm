/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import org.hibernate.dialect.identity.DB2390IdentityColumnSupport;
import org.hibernate.dialect.identity.DB2IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.pagination.FetchLimitHandler;
import org.hibernate.dialect.pagination.LegacyDB2LimitHandler;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.sequence.DB2iSequenceSupport;
import org.hibernate.dialect.sequence.NoSequenceSupport;
import org.hibernate.dialect.sequence.SequenceSupport;
import org.hibernate.dialect.unique.DB2UniqueDelegate;
import org.hibernate.dialect.unique.DefaultUniqueDelegate;
import org.hibernate.dialect.unique.UniqueDelegate;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;

/**
 * An SQL dialect for DB2 for iSeries previously known as DB2/400.
 *
 * @author Peter DeGregorio (pdegregorio)
 * @author Christian Beikov
 */
public class DB2iDialect extends DB2Dialect {

	private final int version;

	public DB2iDialect(DialectResolutionInfo info) {
		this( info.getDatabaseMajorVersion() * 100 + info.getDatabaseMinorVersion() * 10 );
	}

	public DB2iDialect() {
		this( 700 );
	}

	public DB2iDialect(int version) {
		super();
		this.version = version;
	}

	public int getIVersion() {
		return version;
	}

	@Override
	protected UniqueDelegate createUniqueDelegate() {
		if ( getIVersion() >= 730 ) {
			return new DefaultUniqueDelegate( this );
		}
		else {
			return super.createUniqueDelegate();
		}
	}

	/**
	 * No support for sequences.
	 */
	@Override
	public SequenceSupport getSequenceSupport() {
		if ( getIVersion() >= 730 ) {
			return DB2iSequenceSupport.INSTANCE;
		}
		else {
			return NoSequenceSupport.INSTANCE;
		}
	}

	@Override
	public String getQuerySequencesString() {
		if ( getIVersion() >= 730 ) {
			return "select distinct sequence_name from qsys2.syssequences " +
					"where ( current_schema = '*LIBL' and sequence_schema in ( select schema_name from qsys2.library_list_info ) ) " +
					"or sequence_schema = current_schema";
		}
		else {
			return null;
		}
	}

	@Override
	public String getForUpdateString() {
		return " for update with rs";
	}

	@Override
	public LimitHandler getLimitHandler() {
		if ( getIVersion() >= 730) {
			return LegacyDB2LimitHandler.INSTANCE;
		}
		else {
			return FetchLimitHandler.INSTANCE;
		}
	}

	@Override
	public IdentityColumnSupport getIdentityColumnSupport() {
		if ( getIVersion() >= 730) {
			return new DB2IdentityColumnSupport();
		}
		else {
			return new DB2390IdentityColumnSupport();
		}
	}
}
