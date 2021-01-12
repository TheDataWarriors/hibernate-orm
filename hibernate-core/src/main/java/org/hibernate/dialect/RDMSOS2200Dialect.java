/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import org.hibernate.LockMode;
import org.hibernate.dialect.function.CommonFunctionFactory;
import org.hibernate.dialect.lock.*;
import org.hibernate.dialect.pagination.FetchLimitHandler;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.sequence.RDMSSequenceSupport;
import org.hibernate.dialect.sequence.SequenceSupport;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.persister.entity.Lockable;
import org.hibernate.query.TemporalUnit;
import org.hibernate.query.TrimSpec;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.sql.CaseFragment;
import org.hibernate.sql.DecodeCaseFragment;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.exec.spi.JdbcOperation;

import org.jboss.logging.Logger;

import java.sql.Types;
import javax.persistence.TemporalType;

/**
 * This is the Hibernate dialect for the Unisys 2200 Relational Database (RDMS).
 * This dialect was developed for use with Hibernate 3.0.5. Other versions may
 * require modifications to the dialect.
 * <p/>
 * Version History:
 * Also change the version displayed below in the constructor
 * 1.1
 * 1.0  2005-10-24  CDH - First dated version for use with CP 11
 *
 * @author Ploski and Hanson
 */
public class RDMSOS2200Dialect extends Dialect {
	private static final CoreMessageLogger LOG = Logger.getMessageLogger(
			CoreMessageLogger.class,
			RDMSOS2200Dialect.class.getName()
	);

	/**
	 * Constructs a RDMSOS2200Dialect
	 */
	public RDMSOS2200Dialect() {
		super();
		// Display the dialect version.
		LOG.rdmsOs2200Dialect();

		/*
		 * For a list of column types to register, see section A-1
		 * in 7862 7395, the Unisys JDBC manual.
		 *
		 * Here are column sizes as documented in Table A-1 of
		 * 7831 0760, "Enterprise Relational Database Server
		 * for ClearPath OS2200 Administration Guide"
		 * Numeric - 21
		 * Decimal - 22 (21 digits plus one for sign)
		 * Float   - 60 bits
		 * Char    - 28000
		 * NChar   - 14000
		 * BLOB+   - 4294967296 (4 Gb)
		 * + RDMS JDBC driver does not support BLOBs
		 *
		 * DATE, TIME and TIMESTAMP literal formats are
		 * are all described in section 2.3.4 DATE Literal Format
		 * in 7830 8160.
		 * The DATE literal format is: YYYY-MM-DD
		 * The TIME literal format is: HH:MM:SS[.[FFFFFF]]
		 * The TIMESTAMP literal format is: YYYY-MM-DD HH:MM:SS[.[FFFFFF]]
		 *
		 * Note that $l (dollar-L) will use the length value if provided.
		 * Also new for Hibernate3 is the $p percision and $s (scale) parameters
		 */
		registerColumnType( Types.BIT, 1, "smallint" );
		registerColumnType( Types.BIT, "smallint" );
		registerColumnType( Types.BOOLEAN, "smallint" );
		registerColumnType( Types.TINYINT, "smallint" );
		registerColumnType( Types.BIGINT, "numeric(19,0)" );
		registerColumnType( Types.BLOB, "blob($l)" );

		//no 'binary' nor 'varbinary' so use 'blob'
		registerColumnType( Types.BINARY, "blob($l)");
		registerColumnType( Types.VARBINARY, "blob($l)");

		//'varchar' is not supported in RDMS for OS 2200
		//(but it is for other flavors of RDMS)
		//'character' means ASCII by default, 'unicode(n)'
		//means 'character(n) character set "UCS-2"'
		registerColumnType( Types.CHAR, "unicode($l)");
		registerColumnType( Types.VARCHAR, "unicode($l)");

		registerColumnType( Types.TIMESTAMP_WITH_TIMEZONE, "timestamp($p)");
	}

	@Override
	public int getVersion() {
		return 0;
	}

	@Override
	public int getPreferredSqlTypeCodeForBoolean() {
		return Types.BIT;
	}

	@Override
	public int getDefaultDecimalPrecision() {
		//the (really low) maximum
		return 21;
	}

	@Override
	public void initializeFunctionRegistry(QueryEngine queryEngine) {
		super.initializeFunctionRegistry( queryEngine );

		CommonFunctionFactory.cosh( queryEngine );
		CommonFunctionFactory.sinh( queryEngine );
		CommonFunctionFactory.tanh( queryEngine );
		CommonFunctionFactory.cot( queryEngine );
		CommonFunctionFactory.log( queryEngine );
		CommonFunctionFactory.log10( queryEngine );
		CommonFunctionFactory.pi( queryEngine );
		CommonFunctionFactory.rand( queryEngine );
		CommonFunctionFactory.trunc( queryEngine );
		CommonFunctionFactory.truncate( queryEngine );
		CommonFunctionFactory.soundex( queryEngine );
		CommonFunctionFactory.trim2( queryEngine );
		CommonFunctionFactory.space( queryEngine );
		CommonFunctionFactory.repeat( queryEngine );
//		CommonFunctionFactory.replicate( queryEngine ); //synonym for more common repeat()
		CommonFunctionFactory.initcap( queryEngine );
		CommonFunctionFactory.instr( queryEngine );
		CommonFunctionFactory.substr( queryEngine );
		CommonFunctionFactory.translate( queryEngine );
		CommonFunctionFactory.yearMonthDay( queryEngine );
		CommonFunctionFactory.hourMinuteSecond( queryEngine );
		CommonFunctionFactory.dayofweekmonthyear( queryEngine );
		CommonFunctionFactory.weekQuarter( queryEngine );
		CommonFunctionFactory.daynameMonthname( queryEngine );
		CommonFunctionFactory.lastDay( queryEngine );
		CommonFunctionFactory.ceiling_ceil( queryEngine );
		CommonFunctionFactory.concat_pipeOperator( queryEngine );
		CommonFunctionFactory.ascii( queryEngine );
		CommonFunctionFactory.chr_char( queryEngine );
		CommonFunctionFactory.insert( queryEngine );
		CommonFunctionFactory.addMonths( queryEngine );
		CommonFunctionFactory.monthsBetween( queryEngine );
	}

	@Override
	public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
		return new StandardSqlAstTranslatorFactory() {
			@Override
			protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
					SessionFactoryImplementor sessionFactory, Statement statement) {
				return new RDBMSOS2200SqlAstTranslator<>( sessionFactory, statement );
			}
		};
	}

	@Override
	public long getFractionalSecondPrecisionInNanos() {
		return 1_000; //microseconds
	}

	/**
	 * RDMS supports a limited list of temporal fields in the
	 * extract() function, but we can emulate some of them by
	 * using the appropriate named functions instead of
	 * extract().
	 *
	 * Thus, the additional supported fields are
	 * {@link TemporalUnit#DAY_OF_YEAR},
	 * {@link TemporalUnit#DAY_OF_MONTH},
	 * {@link TemporalUnit#DAY_OF_YEAR}.
	 *
	 * In addition, the field {@link TemporalUnit#SECOND} is
	 * redefined to include microseconds.
	 */
	@Override
	public String extractPattern(TemporalUnit unit) {
		switch (unit) {
			case SECOND:
				return "(second(?2)+microsecond(?2)/1e6)";
			case DAY_OF_WEEK:
				return "dayofweek(?2)";
			case DAY_OF_MONTH:
				return "dayofmonth(?2)";
			case DAY_OF_YEAR:
				return "dayofyear(?2)";
			default:
				return "?1(?2)";
		}
	}

	@Override
	public String timestampaddPattern(TemporalUnit unit, TemporalType temporalType) {
		switch (unit) {
			case NANOSECOND:
				return "timestampadd('SQL_TSI_FRAC_SECOND', (?2)/1e3, ?3)";
			case NATIVE:
				return "timestampadd('SQL_TSI_FRAC_SECOND', ?2, ?3)";
			default:
				return "dateadd('?1', ?2, ?3)";
		}
	}

	@Override
	public String timestampdiffPattern(TemporalUnit unit, TemporalType fromTemporalType, TemporalType toTemporalType) {
		switch (unit) {
			case NANOSECOND:
				return "timestampdiff('SQL_TSI_FRAC_SECOND', ?2, ?3)*1e3";
			case NATIVE:
				return "timestampdiff('SQL_TSI_FRAC_SECOND', ?2, ?3)";
			default:
				return "dateadd('?1', ?2, ?3)";
		}
	}

	// Dialect method overrides ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * RDMS does not support qualifing index names with the schema name.
	 * <p/>
	 * {@inheritDoc}
	 */
	@Override
	public boolean qualifyIndexName() {
		return false;
	}

	/**
	 * <TT>FOR UPDATE</TT> only supported for cursors
	 *
	 * @return the empty string
	 */
	@Override
	public String getForUpdateString() {
		// Original Dialect.java returns " for update";
		return "";
	}

	// Verify the state of this new method in Hibernate 3.0 Dialect.java

	/**
	 * RDMS does not support Cascade Deletes.
	 * Need to review this in the future when support is provided.
	 * <p/>
	 * {@inheritDoc}
	 */
	@Override
	public boolean supportsCascadeDelete() {
		return false;
	}

	/**
	 * Currently, RDMS-JDBC does not support ForUpdate.
	 * Need to review this in the future when support is provided.
	 * <p/>
	 * {@inheritDoc}
	 */
	@Override
	public boolean supportsOuterJoinForUpdate() {
		return false;
	}

	@Override
	public String getAddColumnString() {
		return "add";
	}

	@Override
	public String getNullColumnString() {
		// The keyword used to specify a nullable column.
		return " null";
	}

	@Override
	public SequenceSupport getSequenceSupport() {
		return RDMSSequenceSupport.INSTANCE;
	}

	@Override
	public String getCascadeConstraintsString() {
		// Used with DROP TABLE to delete all records in the table.
		return " including contents";
	}

	@Override
	@SuppressWarnings("deprecation")
	public CaseFragment createCaseFragment() {
		return new DecodeCaseFragment();
	}

	@Override
	public LimitHandler getLimitHandler() {
		return FetchLimitHandler.INSTANCE;
	}

	@Override
	public String getFromDual() {
		return "from rdms.rdms_dummy where key_col = 1";
	}

	@Override
	public boolean supportsSelectQueryWithoutFromClause() {
		return false;
	}

	@Override
	public LockingStrategy getLockingStrategy(Lockable lockable, LockMode lockMode) {
		// RDMS has no known variation of a "SELECT ... FOR UPDATE" syntax...
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

	@Override
	public String translateDatetimeFormat(String format) {
		return OracleDialect.datetimeFormat( format, true ) //Does it really support FM?
				.replace("SSSSSS", "MLS")
				.replace("SSSSS", "MLS")
				.replace("SSSS", "MLS")
				.replace("SSS", "MLS")
				.replace("SS", "MLS")
				.replace("S", "MLS")
				.result();
	}

	@Override
	public String trimPattern(TrimSpec specification, char character) {
		return AbstractTransactSQLDialect.replaceLtrimRtrim(specification, character);
	}
}
