/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import org.hibernate.dialect.function.CommonFunctionFactory;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.pagination.OffsetFetchLimitHandler;
import org.hibernate.dialect.sequence.PostgreSQLSequenceSupport;
import org.hibernate.dialect.sequence.SequenceSupport;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.TemporalUnit;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.type.StandardBasicTypes;

import java.sql.Types;

import javax.persistence.TemporalType;

import static org.hibernate.query.TemporalUnit.DAY;
import static org.hibernate.query.TemporalUnit.NATIVE;
import static org.hibernate.type.descriptor.DateTimeUtils.wrapAsAnsiDateLiteral;
import static org.hibernate.type.descriptor.DateTimeUtils.wrapAsAnsiTimeLiteral;

/**
 * A dialect for CockroachDB.
 *
 * @author Gavin King
 */
public class CockroachDialect extends Dialect {

	// KNOWN LIMITATIONS:

	// * no support for java.sql.Clob

	private int version;

	public CockroachDialect() {
		this(192);
	}

	public CockroachDialect(DialectResolutionInfo info) {
		this( info.getDatabaseMajorVersion() * 100 + info.getDatabaseMinorVersion() * 10 );
	}

	public CockroachDialect(int version) {
		super();

		this.version = version;

		registerColumnType( Types.TINYINT, "smallint" ); //no tinyint

		//no binary/varbinary
		registerColumnType( Types.VARBINARY, "bytes" );
		registerColumnType( Types.BINARY, "bytes" );

		//no clob
		registerColumnType( Types.LONGVARCHAR, "string" );
		registerColumnType( Types.CLOB, "string" );

		//no nchar/nvarchar
		registerColumnType( Types.NCHAR, "string($l)" );
		registerColumnType( Types.NVARCHAR, "string($l)" );

		//no nclob
		registerColumnType( Types.LONGNVARCHAR, "string" );
		registerColumnType( Types.NCLOB, "string" );

		registerColumnType( Types.JAVA_OBJECT, "json" );
	}

	@Override
	public int getVersion() {
		return version;
	}

	@Override
	public void initializeFunctionRegistry(QueryEngine queryEngine) {
		super.initializeFunctionRegistry(queryEngine);

		CommonFunctionFactory.ascii( queryEngine );
		CommonFunctionFactory.char_chr( queryEngine );
		CommonFunctionFactory.overlay( queryEngine );
		CommonFunctionFactory.position( queryEngine );
		CommonFunctionFactory.substringFromFor( queryEngine );
		CommonFunctionFactory.locate_positionSubstring( queryEngine );
		CommonFunctionFactory.trim2( queryEngine );
		CommonFunctionFactory.substr( queryEngine );
		CommonFunctionFactory.reverse( queryEngine );
		CommonFunctionFactory.repeat( queryEngine );
		CommonFunctionFactory.md5( queryEngine );
		CommonFunctionFactory.sha1( queryEngine );
		CommonFunctionFactory.octetLength( queryEngine );
		CommonFunctionFactory.bitLength( queryEngine );
		CommonFunctionFactory.cbrt( queryEngine );
		CommonFunctionFactory.cot( queryEngine );
		CommonFunctionFactory.degrees( queryEngine );
		CommonFunctionFactory.radians( queryEngine );
		CommonFunctionFactory.pi( queryEngine );
		CommonFunctionFactory.trunc( queryEngine ); //TODO: emulate second arg

		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder("format", "experimental_strftime")
				.setInvariantType( StandardBasicTypes.STRING )
				.setExactArgumentCount( 2 )
				.setArgumentListSignature("(datetime as pattern)")
				.register();
	}

	@Override
	public boolean supportsTimezoneTypes() {
		return true;
	}

	@Override
	public String toBooleanValueString(boolean bool) {
		return String.valueOf( bool );
	}

	@Override
	public String getCascadeConstraintsString() {
		return " cascade";
	}

	@Override
	public boolean supportsIfExistsBeforeTableName() {
		return true;
	}

	@Override
	public boolean supportsIfExistsBeforeConstraintName() {
		return true;
	}

	@Override
	public boolean supportsIfExistsAfterAlterTable() {
		return true;
	}

	@Override
	public boolean qualifyIndexName() {
		return false;
	}

	@Override
	public boolean supportsValuesList() {
		return true;
	}

	@Override
	public boolean supportsRowValueConstructorSyntax() {
		return true;
	}

	@Override
	public boolean supportsRowValueConstructorSyntaxInInList() {
		return true;
	}

	@Override
	public boolean supportsPartitionBy() {
		return true;
	}

	@Override
	public boolean supportsNonQueryWithCTE() {
		return true;
	}

	@Override
	public String getNoColumnsInsertString() {
		return "default values";
	}

	@Override
	public String getCaseInsensitiveLike(){
		return "ilike";
	}

	@Override
	public boolean supportsCaseInsensitiveLike() {
		return true;
	}

	@Override
	public boolean requiresParensForTupleDistinctCounts() {
		return true;
	}

	@Override
	public String getNativeIdentifierGeneratorStrategy() {
		return "sequence";
	}

	@Override
	public SequenceSupport getSequenceSupport() {
		return PostgreSQLSequenceSupport.INSTANCE;
	}

	@Override
	public String getQuerySequencesString() {
		return "select sequence_name, sequence_schema, sequence_catalog, start_value, minimum_value, maximum_value, increment from information_schema.sequences";
	}

	@Override
	public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
		return new StandardSqlAstTranslatorFactory() {
			@Override
			protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
					SessionFactoryImplementor sessionFactory, Statement statement) {
				return new CockroachSqlAstTranslator<>( sessionFactory, statement );
			}
		};
	}

	@Override
	public boolean supportsNationalizedTypes() {
		return false;
	}

	@Override
	protected String wrapDateLiteral(String date) {
		return wrapAsAnsiDateLiteral(date);
	}

	@Override
	protected String wrapTimeLiteral(String time) {
		return wrapAsAnsiTimeLiteral(time);
	}

	@Override
	protected String wrapTimestampLiteral(String timestamp) {
		return "timestamp with time zone '" + timestamp + "'";
	}

	/**
	 * The {@code extract()} function returns {@link TemporalUnit#DAY_OF_WEEK}
	 * numbered from 0 to 6. This isn't consistent with what most other
	 * databases do, so here we adjust the result by generating
	 * {@code (extract(dayofweek,arg)+1))}.
	 */
	@Override
	public String extractPattern(TemporalUnit unit) {
		switch ( unit ) {
			case DAY_OF_WEEK:
				return "(" + super.extractPattern(unit) + "+1)";
			case SECOND:
				return "(extract(second from ?2)+extract(microsecond from ?2)/1e6)";
			default:
				return super.extractPattern(unit);
		}
	}

	@Override
	public String translateExtractField(TemporalUnit unit) {
		switch ( unit ) {
			case DAY_OF_MONTH: return "day";
			case DAY_OF_YEAR: return "dayofyear";
			case DAY_OF_WEEK: return "dayofweek";
			default: return super.translateExtractField( unit );
		}
	}

	/**
	 * {@code microsecond} is the smallest unit for an {@code interval},
	 * and the highest precision for a {@code timestamp}.
	 */
	@Override
	public long getFractionalSecondPrecisionInNanos() {
		return 1_000; //microseconds
	}

	@Override
	public String timestampaddPattern(TemporalUnit unit, TemporalType temporalType) {
		switch ( unit ) {
			case NANOSECOND:
				return "(?3 + (?2)/1e3 * interval '1 microsecond')";
			case NATIVE:
				return "(?3 + (?2) * interval '1 microsecond')";
			case QUARTER: //quarter is not supported in interval literals
				return "(?3 + (?2) * interval '3 month')";
			case WEEK: //week is not supported in interval literals
				return "(?3 + (?2) * interval '7 day')";
			default:
				return "(?3 + (?2) * interval '1 ?1')";
		}
	}

	@Override
	public String timestampdiffPattern(TemporalUnit unit, TemporalType fromTemporalType, TemporalType toTemporalType) {
		switch (unit) {
			case YEAR:
				return "(extract(year from ?3)-extract(year from ?2))";
			case QUARTER:
				return "(extract(year from ?3)*4-extract(year from ?2)*4+extract(month from ?3)//3-extract(month from ?2)//3)";
			case MONTH:
				return "(extract(year from ?3)*12-extract(year from ?2)*12+extract(month from ?3)-extract(month from ?2))";
		}
		if ( toTemporalType != TemporalType.TIMESTAMP && fromTemporalType != TemporalType.TIMESTAMP ) {
			// special case: subtraction of two dates
			// results in an integer number of days
			// instead of an INTERVAL
			return "(?3-?2)" + DAY.conversionFactor( unit, this );
		}
		else {
			switch (unit) {
				case WEEK:
					return "extract_duration(hour from ?3-?2)/168";
				case DAY:
					return "extract_duration(hour from ?3-?2)/24";
				case NANOSECOND:
					return "extract_duration(microsecond from ?3-?2)*1e3";
				default:
					return "extract_duration(?1 from ?3-?2)";
			}
		}
	}

	@Override
	public String translateDurationField(TemporalUnit unit) {
		return unit==NATIVE
				? "microsecond"
				: super.translateDurationField(unit);
	}

	@Override
	public String translateDatetimeFormat(String format) {
		return SpannerDialect.datetimeFormat( format ).result();
	}

	@Override
	public LimitHandler getLimitHandler() {
		return OffsetFetchLimitHandler.INSTANCE;
	}

}
