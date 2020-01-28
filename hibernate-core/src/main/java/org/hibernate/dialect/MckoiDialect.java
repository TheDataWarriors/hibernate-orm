/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import org.hibernate.LockMode;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.function.CommonFunctionFactory;
import org.hibernate.dialect.lock.*;
import org.hibernate.dialect.sequence.MckoiSequenceSupport;
import org.hibernate.dialect.sequence.SequenceSupport;
import org.hibernate.persister.entity.Lockable;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.sql.CaseFragment;
import org.hibernate.sql.MckoiCaseFragment;

import java.sql.Types;

/**
 * An SQL dialect compatible with McKoi SQL
 *
 * @author Doug Currie
 * @author Gabe Hicks
 */
public class MckoiDialect extends Dialect {
	/**
	 * Constructs a MckoiDialect
	 */
	public MckoiDialect() {
		super();

		//Note: there is no single-precision type
		//'float' and 'double' are the exact same
		//double precision type.
		registerColumnType( Types.FLOAT, "float" ); //precision argument not supported
		registerColumnType( Types.DOUBLE, "double" ); //'double precision' not supported

		//no explicit precision
		registerColumnType(Types.TIMESTAMP, "timestamp");
		registerColumnType(Types.TIMESTAMP_WITH_TIMEZONE, "timestamp");

		getDefaultProperties().setProperty( Environment.STATEMENT_BATCH_SIZE, NO_BATCH );
	}

	@Override
	public void initializeFunctionRegistry(QueryEngine queryEngine) {
		super.initializeFunctionRegistry(queryEngine);

		CommonFunctionFactory.characterLength_length( queryEngine );
		CommonFunctionFactory.trim1( queryEngine );

		queryEngine.getSqmFunctionRegistry().patternDescriptorBuilder( "nullif", "if(?1=?2, null, ?1)" )
				.setExactArgumentCount(2)
				.register();
	}

	@Override
	public SequenceSupport getSequenceSupport() {
		return MckoiSequenceSupport.INSTANCE;
	}

	@Override
	public String getForUpdateString() {
		return "";
	}

	@Override
	@SuppressWarnings("deprecation")
	public CaseFragment createCaseFragment() {
		return new MckoiCaseFragment();
	}

	@Override
	public LockingStrategy getLockingStrategy(Lockable lockable, LockMode lockMode) {
		// Mckoi has no known variation of a "SELECT ... FOR UPDATE" syntax...
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
