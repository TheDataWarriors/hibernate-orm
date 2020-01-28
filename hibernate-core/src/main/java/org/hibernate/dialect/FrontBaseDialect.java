/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import org.hibernate.LockMode;
import org.hibernate.dialect.lock.*;
import org.hibernate.persister.entity.Lockable;

import java.sql.Types;

/**
 * An SQL Dialect for Frontbase.  Assumes you're using the latest version
 * of the FrontBase JDBC driver, available from <tt>http://frontbase.com/</tt>
 * <p>
 * <b>NOTE</b>: The latest JDBC driver is not always included with the
 * latest release of FrontBase.  Download the driver separately, and enjoy
 * the informative release notes.
 * <p>
 * This dialect was tested with JDBC driver version 2.3.1.  This driver
 * contains a bug that causes batches of updates to fail.  (The bug should be
 * fixed in the next release of the JDBC driver.)  If you are using JDBC driver
 * 2.3.1, you can work-around this problem by setting the following in your
 * <tt>hibernate.properties</tt> file: <tt>hibernate.jdbc.batch_size=15</tt>
 *
 * @author Ron Lussier <tt>rlussier@lenscraft.com</tt>
 */
public class FrontBaseDialect extends Dialect {

	/**
	 * Constructs a FrontBaseDialect
	 */
	public FrontBaseDialect() {
		super();

		registerColumnType( Types.BOOLEAN, "bit" );
		registerColumnType( Types.BIGINT, "longint" );

		//FrontBase has no 'binary' nor 'varbinary'
		registerColumnType( Types.BINARY, "bit($l)");
		registerColumnType( Types.VARBINARY, "bit varying($l)");

		//no precision
		registerColumnType( Types.TIMESTAMP, "timestamp" );
		registerColumnType( Types.TIMESTAMP_WITH_TIMEZONE, "timestamp with time zone" );
	}

	@Override
	public int getPreferredSqlTypeCodeForBoolean() {
		return Types.BIT;
	}

	@Override
	public String getCascadeConstraintsString() {
		return " cascade";
	}

	@Override
	public boolean dropConstraints() {
		return false;
	}

	/**
	 * FrontBase doesn't support this syntax, which was dropped with SQL92.
	 * <p/>
	 * {@inheritDoc}
	 */
	@Override
	public String getForUpdateString() {
		return "";
	}

	@Override
	public String getCurrentTimestampSelectString() {
		// TODO : not sure this is correct, could not find docs on how to do this.
		return "{?= call current_timestamp}";
	}

	@Override
	public boolean isCurrentTimestampSelectStringCallable() {
		return true;
	}

	@Override
	public LockingStrategy getLockingStrategy(Lockable lockable, LockMode lockMode) {
		// Frontbase has no known variation of a "SELECT ... FOR UPDATE" syntax...
		switch (lockMode) {
			case PESSIMISTIC_FORCE_INCREMENT:
				return new PessimisticForceIncrementLockingStrategy(lockable, lockMode);
			case PESSIMISTIC_WRITE:
				return new PessimisticWriteUpdateLockingStrategy(lockable, lockMode);
			case PESSIMISTIC_READ:
				return new PessimisticReadUpdateLockingStrategy(lockable, lockMode);
			case OPTIMISTIC:
				return new OptimisticLockingStrategy(lockable, lockMode);
			case OPTIMISTIC_FORCE_INCREMENT:
				return new OptimisticForceIncrementLockingStrategy(lockable, lockMode);
		}
		if ( lockMode.greaterThan( LockMode.READ ) ) {
			return new UpdateLockingStrategy( lockable, lockMode );
		}
		else {
			return new SelectLockingStrategy( lockable, lockMode );
		}
	}
}
