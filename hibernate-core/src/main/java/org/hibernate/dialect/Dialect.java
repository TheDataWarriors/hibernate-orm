/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.MappingException;
import org.hibernate.NullPrecedence;
import org.hibernate.ScrollMode;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.function.CastStrEmulation;
import org.hibernate.dialect.function.CommonFunctionFactory;
import org.hibernate.dialect.function.LocatePositionEmulation;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupportImpl;
import org.hibernate.dialect.lock.LockingStrategy;
import org.hibernate.dialect.lock.OptimisticForceIncrementLockingStrategy;
import org.hibernate.dialect.lock.OptimisticLockingStrategy;
import org.hibernate.dialect.lock.PessimisticForceIncrementLockingStrategy;
import org.hibernate.dialect.lock.PessimisticReadSelectLockingStrategy;
import org.hibernate.dialect.lock.PessimisticWriteSelectLockingStrategy;
import org.hibernate.dialect.lock.SelectLockingStrategy;
import org.hibernate.dialect.pagination.LegacyLimitHandler;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.unique.DefaultUniqueDelegate;
import org.hibernate.dialect.unique.UniqueDelegate;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.config.spi.StandardConverters;
import org.hibernate.engine.jdbc.LobCreator;
import org.hibernate.engine.jdbc.env.internal.DefaultSchemaNameResolver;
import org.hibernate.engine.jdbc.env.spi.AnsiSqlKeywords;
import org.hibernate.engine.jdbc.env.spi.IdentifierHelper;
import org.hibernate.engine.jdbc.env.spi.IdentifierHelperBuilder;
import org.hibernate.engine.jdbc.env.spi.NameQualifierSupport;
import org.hibernate.engine.jdbc.env.spi.SchemaNameResolver;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.exception.spi.ConversionContext;
import org.hibernate.exception.spi.SQLExceptionConversionDelegate;
import org.hibernate.exception.spi.SQLExceptionConverter;
import org.hibernate.exception.spi.ViolatedConstraintNameExtracter;
import org.hibernate.id.IdentityGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.internal.CoreLogging;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.internal.util.io.StreamCopier;
import org.hibernate.loader.BatchLoadSizingStrategy;
import org.hibernate.metamodel.model.domain.spi.EntityHierarchy;
import org.hibernate.metamodel.model.domain.spi.EntityIdentifierComposite;
import org.hibernate.metamodel.model.domain.spi.Lockable;
import org.hibernate.metamodel.model.relational.spi.AuxiliaryDatabaseObject;
import org.hibernate.metamodel.model.relational.spi.ExportableTable;
import org.hibernate.metamodel.model.relational.spi.ForeignKey;
import org.hibernate.metamodel.model.relational.spi.Index;
import org.hibernate.metamodel.model.relational.spi.Sequence;
import org.hibernate.metamodel.model.relational.spi.Size;
import org.hibernate.metamodel.model.relational.spi.UniqueKey;
import org.hibernate.procedure.internal.StandardCallableStatementSupport;
import org.hibernate.procedure.spi.CallableStatementSupport;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.sqm.mutation.spi.SqmMutationStrategy;
import org.hibernate.query.sqm.mutation.spi.idtable.IdTable;
import org.hibernate.query.sqm.mutation.spi.idtable.IdTableExporterImpl;
import org.hibernate.query.sqm.mutation.spi.idtable.PersistentTableStrategy;
import org.hibernate.query.sqm.mutation.spi.inline.InlineMutationStrategy;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.sql.ANSICaseFragment;
import org.hibernate.sql.ANSIJoinFragment;
import org.hibernate.sql.CaseFragment;
import org.hibernate.sql.ForUpdateFragment;
import org.hibernate.sql.JoinFragment;
import org.hibernate.sql.SqlExpressableType;
import org.hibernate.tool.schema.extract.internal.SequenceInformationExtractorLegacyImpl;
import org.hibernate.tool.schema.extract.internal.SequenceInformationExtractorNoOpImpl;
import org.hibernate.tool.schema.extract.spi.SequenceInformationExtractor;
import org.hibernate.tool.schema.internal.StandardAuxiliaryDatabaseObjectExporter;
import org.hibernate.tool.schema.internal.StandardForeignKeyExporter;
import org.hibernate.tool.schema.internal.StandardIndexExporter;
import org.hibernate.tool.schema.internal.StandardSequenceExporter;
import org.hibernate.tool.schema.internal.StandardTableAlterable;
import org.hibernate.tool.schema.internal.StandardTableExporter;
import org.hibernate.tool.schema.internal.StandardUniqueKeyExporter;
import org.hibernate.tool.schema.spi.Alterable;
import org.hibernate.tool.schema.spi.DefaultSizeStrategy;
import org.hibernate.tool.schema.spi.Exporter;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.java.spi.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.spi.ClobSqlDescriptor;
import org.hibernate.type.descriptor.sql.spi.SqlTypeDescriptor;
import org.hibernate.type.spi.StandardSpiBasicTypes;

/**
 * Represents a dialect of SQL implemented by a particular RDBMS.  Subclasses implement Hibernate compatibility
 * with different systems.  Subclasses should provide a public default constructor that register a set of type
 * mappings and default Hibernate properties.  Subclasses should be immutable.
 *
 * @author Gavin King, David Channon
 */
@SuppressWarnings("deprecation")
public abstract class Dialect implements ConversionContext {
	private static final CoreMessageLogger LOG = CoreLogging.messageLogger( Dialect.class );

	/**
	 * Defines a default batch size constant
	 */
	public static final String DEFAULT_BATCH_SIZE = "15";

	/**
	 * Defines a "no batching" batch size constant
	 */
	public static final String NO_BATCH = "0";

	/**
	 * Characters used as opening for quoting SQL identifiers
	 */
	public static final String QUOTE = "`\"[";

	/**
	 * Characters used as closing for quoting SQL identifiers
	 */
	public static final String CLOSED_QUOTE = "`\"]";

	private final TypeNames typeNames = new TypeNames();
	private final TypeNames hibernateTypeNames = new TypeNames();

	private final Properties properties = new Properties();
	private final Set<String> sqlKeywords = new HashSet<>();

	private final UniqueDelegate uniqueDelegate;

	private boolean legacyLimitHandlerBehavior;
	private DefaultSizeStrategy defaultSizeStrategy;


	// constructors and factory methods ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	protected Dialect() {
		LOG.usingDialect( this );

		registerColumnType( Types.BIT, 1, "bit" );
		registerColumnType( Types.BIT, "bit($l)" );
		registerColumnType( Types.BOOLEAN, "boolean" );

		registerColumnType( Types.TINYINT, "tinyint" );
		registerColumnType( Types.SMALLINT, "smallint" );
		registerColumnType( Types.INTEGER, "integer" );
		registerColumnType( Types.BIGINT, "bigint" );

		registerColumnType( Types.REAL, "real" );
		registerColumnType( Types.FLOAT, "float($p)" );
		registerColumnType( Types.DOUBLE, "double precision" );

		//these are pretty much synonyms, but are considered
		//separate types by the ANSI spec, and in some dialects
		registerColumnType( Types.NUMERIC, "numeric($p,$s)" );
		registerColumnType( Types.DECIMAL, "decimal($p,$s)" );

		registerColumnType( Types.DATE, "date" );
		registerColumnType( Types.TIME, "time" );
		registerColumnType( Types.TIMESTAMP, "timestamp($p)" );

		//currently not used:
		registerColumnType( Types.TIME_WITH_TIMEZONE, "time with time zone" );
		registerColumnType( Types.TIMESTAMP_WITH_TIMEZONE, "timestamp($p) with time zone" );

		registerColumnType( Types.BINARY, "binary($l)" );
		registerColumnType( Types.VARBINARY, "varbinary($l)" );
		registerColumnType( Types.LONGVARBINARY, "varbinary($l)" );
		registerColumnType( Types.BLOB, "blob" );

		registerColumnType( Types.CHAR, "char($l)" );
		registerColumnType( Types.VARCHAR, "varchar($l)" );
		registerColumnType( Types.LONGVARCHAR, "varchar($l)" );
		registerColumnType( Types.CLOB, "clob" );

		registerColumnType( Types.NCHAR, "nchar($l)" );
		registerColumnType( Types.NVARCHAR, "nvarchar($l)" );
		registerColumnType( Types.LONGNVARCHAR, "nvarchar($l)" );
		registerColumnType( Types.NCLOB, "nclob" );

		// register hibernate types for default use in scalar sqlquery type auto detection
		registerHibernateType( Types.BIGINT, StandardSpiBasicTypes.BIG_INTEGER.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.BINARY, StandardSpiBasicTypes.BINARY.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.BIT, 64, StandardSpiBasicTypes.LONG.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.BIT, 32, StandardSpiBasicTypes.INTEGER.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.BIT, 16, StandardSpiBasicTypes.SHORT.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.BIT, 8, StandardSpiBasicTypes.BYTE.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.BIT, 1, StandardSpiBasicTypes.BOOLEAN.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.BOOLEAN, StandardSpiBasicTypes.BOOLEAN.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.CHAR, StandardSpiBasicTypes.STRING.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.CHAR, 1, StandardSpiBasicTypes.CHARACTER.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.DATE, StandardSpiBasicTypes.DATE.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.DOUBLE, StandardSpiBasicTypes.DOUBLE.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.FLOAT, StandardSpiBasicTypes.DOUBLE.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.INTEGER, StandardSpiBasicTypes.INTEGER.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.SMALLINT, StandardSpiBasicTypes.SHORT.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.TINYINT, StandardSpiBasicTypes.BYTE.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.TIME, StandardSpiBasicTypes.TIME.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.TIMESTAMP, StandardSpiBasicTypes.TIMESTAMP.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.VARCHAR, StandardSpiBasicTypes.STRING.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.VARCHAR, 1, StandardSpiBasicTypes.CHARACTER.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.NVARCHAR, StandardSpiBasicTypes.NSTRING.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.VARBINARY, StandardSpiBasicTypes.BINARY.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.LONGVARCHAR, StandardSpiBasicTypes.TEXT.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.LONGVARBINARY, StandardSpiBasicTypes.IMAGE.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.NUMERIC, StandardSpiBasicTypes.BIG_DECIMAL.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.DECIMAL, StandardSpiBasicTypes.BIG_DECIMAL.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.BLOB, StandardSpiBasicTypes.BLOB.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.CLOB, StandardSpiBasicTypes.CLOB.getJavaTypeDescriptor().getTypeName() );
		registerHibernateType( Types.REAL, StandardSpiBasicTypes.FLOAT.getJavaTypeDescriptor().getTypeName() );

		if(supportsPartitionBy()) {
			registerKeyword( "PARTITION" );
		}

		uniqueDelegate = new DefaultUniqueDelegate( this );
		defaultSizeStrategy = new DefaultSizeStrategyImpl();
	}

	/**
	 * Useful conversion for databases which represent the
	 * precision of a float(p) using p expressed in decimal
	 * digits instead of the usual (standard) binary digits.
	 */
	static Size binaryToDecimalPrecision(int code, Size size) {
		return code == Types.FLOAT
				&& size != null
				&& size.getPrecision() != null
				? Size.Builder.precision( (int) Math.ceil( size.getPrecision() / 53.0 * 17.0 ) )
				: size;
	}

	/**
	 * Initialize the given registry with any dialect-specific functions.
	 *
	 * Note that support for certain functions is required, and if the
	 * database does not support a required function, then the dialect
	 * must define a way to emulate it.
	 *
	 * These required functions include the functions defined by the JPA
	 * query language specification:
	 *
	 * 		* avg
	 * 		* count
	 * 		* max, min
	 * 		* sum
	 *
	 * 		* coalesce
	 * 		* nullif
	 *
	 * 		* concat
	 * 		* locate
	 * 		* substring
	 * 		* trim
	 * 		* lower, upper
	 * 		* length
	 *
	 * 		* abs
	 * 		* mod
	 * 		* sqrt
	 *
	 * 		* current_date
	 * 		* current_time
	 * 		* current_timestamp
	 *
	 * Along with an additional set of functions defined by ANSI SQL:
	 *
	 * 		* cast
	 * 		* extract
	 * 	    * position (alternative syntax for locate)
	 *      * ln, exp
	 *      * power
	 *      * floor, ceiling
	 *
	 * And a number of additional "standard" functions:
	 *
	 * 	    * ifnull (two-argument synonym for coalesce)
	 *      * replace
	 *      * least, greatest
	 *      * sign
	 *      * sin, cos, tan, asin, acos, atan, atan2
	 *      * round
	 * 	    * current_instant
	 * 		* str 			- defined as `cast(?1 as CHAR )`
	 * 		* second		- defined as `extract(second from ?1)`
	 * 		* minute		- defined as `extract(minute from ?1)`
	 * 		* hour			- defined as `extract(hour from ?1)`
	 * 		* day			- defined as `extract(day from ?1)`
	 * 		* month			- defined as `extract(month from ?1)`
	 * 		* year			- defined as `extract(year from ?1)`
	 *
	 */
	public void initializeFunctionRegistry(QueryEngine queryEngine) {

		//aggregate functions, supported on every database

		CommonFunctionFactory.aggregates(queryEngine);

		//math functions supported on almost every database

		CommonFunctionFactory.math(queryEngine);

		//trig functions supported on almost every database

		CommonFunctionFactory.trigonometry(queryEngine);

		//coalesce() function, must be redefined in terms of nvl() where not supported

		CommonFunctionFactory.coalesce(queryEngine);

		//nullif() function, supported on almost every database

		CommonFunctionFactory.nullif(queryEngine);

		//string functions, must be redefined where not supported

		CommonFunctionFactory.locate(queryEngine);
		CommonFunctionFactory.substring(queryEngine);
		CommonFunctionFactory.replace(queryEngine);
		CommonFunctionFactory.concat(queryEngine);
		CommonFunctionFactory.lowerUpper(queryEngine);

		//JPA string length() function, a synonym for ANSI SQL character_length()

		CommonFunctionFactory.length_characterLength(queryEngine);

		//Very few databases support ANSI-style position() function, so define
		//it here as an alias for locate()

		queryEngine.getSqmFunctionRegistry().register("position", new LocatePositionEmulation());

		//ANSI SQL functions with weird syntax, not supported on every database

		CommonFunctionFactory.trim(queryEngine);
		CommonFunctionFactory.cast(queryEngine);
		CommonFunctionFactory.extract(queryEngine);

		//ANSI current date/time functions, supported on almost every database

		CommonFunctionFactory.currentDateTimeTimestamp(queryEngine);

		//comparison functions supported on every known database

		CommonFunctionFactory.leastGreatest(queryEngine);

		//legacy Hibernate convenience function for casting to string

		queryEngine.getSqmFunctionRegistry().register("str", new CastStrEmulation());

	}

	/**
	 * Get an instance of the dialect specified by the current <tt>System</tt> properties.
	 *
	 * @return The specified Dialect
	 * @throws HibernateException If no dialect was specified, or if it could not be instantiated.
	 */
	public static Dialect getDialect() throws HibernateException {
		return instantiateDialect( Environment.getProperties().getProperty( Environment.DIALECT ) );
	}

	/**
	 * Get an instance of the dialect specified by the given properties or by
	 * the current <tt>System</tt> properties.
	 *
	 * @param props The properties to use for finding the dialect class to use.
	 * @return The specified Dialect
	 * @throws HibernateException If no dialect was specified, or if it could not be instantiated.
	 */
	public static Dialect getDialect(Properties props) throws HibernateException {
		final String dialectName = props.getProperty( Environment.DIALECT );
		if ( dialectName == null ) {
			return getDialect();
		}
		return instantiateDialect( dialectName );
	}

	private static Dialect instantiateDialect(String dialectName) throws HibernateException {
		if ( dialectName == null ) {
			throw new HibernateException( "The dialect was not set. Set the property hibernate.dialect." );
		}
		try {
			return (Dialect) ReflectHelper.classForName( dialectName ).newInstance();
		}
		catch ( ClassNotFoundException cnfe ) {
			throw new HibernateException( "Dialect class not found: " + dialectName );
		}
		catch ( Exception e ) {
			throw new HibernateException( "Could not instantiate given dialect class: " + dialectName, e );
		}
	}

	/**
	 * Retrieve a set of default Hibernate properties for this database.
	 *
	 * @return a set of Hibernate properties
	 */
	public final Properties getDefaultProperties() {
		return properties;
	}

	@Override
	public String toString() {
		return getClass().getName();
	}


	// database type mapping support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Allows the Dialect to contribute additional types
	 *
	 * @param typeContributions Callback to contribute the types
	 * @param serviceRegistry The service registry
	 */
	public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
		// by default, nothing to do
		//
		// well, there are no types to register by default.  5.3 hacked in
		// here though to call `resolveLegacyLimitHandlerBehavior`.  Ultimately
		// this should be covered by the "initializable Dialect" notes below
		resolveLegacyLimitHandlerBehavior( serviceRegistry );
	}

//	public static interface Initializable {
//		void initialize(
//				Connection jdbcConnection,
//				ExtractedDatabaseMetadata extractedMeta,
//				ServiceRegistry registry) {
//
//		}
//	}


	// todo (6.0) : new overall Dialect design?
	//		original (and currently) Dialect is designed/intended to be completely
	// 			static information - that works great until databases have have
	//			deviations between versison for the information we need (which
	//			is highly likely.  note that "static" here means information that
	//			the Dialect can know about the underlying database, but without
	//			actually being able to query the db through JDBC, which would be
	// 			an example of doing it dynamically
	//		so might be better to design a better intention, such that Dialect
	//			is built with has access to information about the underlying
	//			database either through the JDBC Connection or some form of
	//			"extracted metadata".  I think the former is both more flexible/powerful
	//			and simple (if there is no Connection the Dialect can just do what it
	//			does today
	//
	// todo (6.0) : a related point is to consider a singular JDBC Connection
	// 		that is:
	//			1) opened once at the stat of bootstrapping (at what specific point?)
	//			2) can be accessed by different bootstrapping aspects - only ever opening
	// 				that one for all of bootstrap.  practically this may have to be
	//				2 Connections because of the "break" between the boot service registry
	// 				(building JdbcServices, etc) and handling metadata.
	//			3) closed at conclusion


	/**
	 * Get the name of the database type associated with the given
	 * {@link java.sql.Types} typecode, with no length, precision,
	 * or scale.
	 *
	 * @param code The {@link java.sql.Types} typecode
	 * @return the database type name
	 * @throws HibernateException If no mapping was specified for that type.
	 */
	public String getRawTypeName(int code) throws HibernateException {
		final String result = typeNames.get( code );
		if ( result == null ) {
			throw new HibernateException( "No default type mapping for (java.sql.Types) " + code );
		}
		//trim off the length/precision/scale
		final int paren = result.indexOf('(');
		return paren>0 ? result.substring(0, paren) : result;
	}

	public String getRawTypeName(SqlTypeDescriptor sqlTypeDescriptor) throws HibernateException {
		return getRawTypeName( sqlTypeDescriptor.getJdbcTypeCode() );
	}

	/**
	 * Get the name of the database type associated with the given
	 * <tt>java.sql.Types</tt> typecode.
	 *
	 * @param code <tt>java.sql.Types</tt> typecode
	 * @param size the length, precision, scale of the column
	 *
	 * @return the database type name
	 *
	 * @throws HibernateException
	 */
	public String getTypeName(int code, Size size) throws HibernateException {
		if ( size == null ) {
			return getRawTypeName( code );
		}
		else {
			String result = typeNames.get( code, size.getLength(), size.getPrecision(), size.getScale() );
			if ( result == null ) {
				throw new HibernateException(
						String.format(
								"No type mapping for java.sql.Types code: %s, length: %s",
								code,
								size.getLength()
						)
				);
			}
			return result;
		}
	}

	/**
	 * Get the name of the database type associated with the given
	 * <tt>SqlTypeDescriptor</tt>.
	 *
	 * @param sqlTypeDescriptor the SQL type
	 * @param size the length, precision, scale of the column
	 *
	 * @return the database type name
	 *
	 * @throws HibernateException
	 */
	public String getTypeName(SqlTypeDescriptor sqlTypeDescriptor, Size size) {
		return getTypeName( sqlTypeDescriptor.getJdbcTypeCode(), size );
	}

	/**
	 * Get the name of the database type appropriate for casting operations
	 * (via the CAST() SQL function) for the given {@link SqlExpressableType}
	 * SQL type.
	 *
	 * @return The database type name
	 */
	public String getCastTypeName(SqlExpressableType type, Long length, Integer precision, Integer scale) {
		Size size;
		if ( length == null && precision == null ) {
			//use defaults
			size = getDefaultSizeStrategy().resolveDefaultSize(
					type.getSqlTypeDescriptor(),
					type.getJavaTypeDescriptor()
			);
		}
		else {
			//use the given length/precision/scale
			if ( precision != null && scale == null ) {
				//needed for cast(x as BigInteger(p))
				scale = type.getJavaTypeDescriptor().getDefaultSqlScale();
			}
			size = new Size.Builder().setLength( length )
					.setPrecision( precision )
					.setScale( scale )
					.cast();
		}

		return getTypeName( type.getSqlTypeDescriptor(), size );
	}

	/**
	 * Subclasses register a type name for the given type code and maximum
	 * column length. <tt>$l</tt> in the type name with be replaced by the
	 * column length (if appropriate).
	 *
	 * @param code The {@link java.sql.Types} typecode
	 * @param capacity The maximum length of database type
	 * @param name The database type name
	 */
	protected void registerColumnType(int code, long capacity, String name) {
		typeNames.put( code, capacity, name );
	}

	/**
	 * Subclasses register a type name for the given type code. <tt>$l</tt> in
	 * the type name with be replaced by the column length (if appropriate).
	 *
	 * @param code The {@link java.sql.Types} typecode
	 * @param name The database type name
	 */
	protected void registerColumnType(int code, String name) {
		typeNames.put( code, name );
	}

	/**
	 * Allows the dialect to override a {@link SqlTypeDescriptor}.
	 * <p/>
	 * If the passed {@code sqlTypeDescriptor} allows itself to be remapped (per
	 * {@link SqlTypeDescriptor#canBeRemapped()}), then this method uses
	 * {@link #getSqlTypeDescriptorOverride}  to get an optional override based on the SQL code returned by
	 * {@link SqlTypeDescriptor#getJdbcTypeCode()}.
	 * <p/>
	 * If this dialect does not provide an override or if the {@code sqlTypeDescriptor} does not allow itself to be
	 * remapped, then this method simply returns the original passed {@code sqlTypeDescriptor}
	 *
	 * @param sqlTypeDescriptor The {@link SqlTypeDescriptor} to override
	 * @return The {@link SqlTypeDescriptor} that should be used for this dialect;
	 *         if there is no override, then original {@code sqlTypeDescriptor} is returned.
	 * @throws IllegalArgumentException if {@code sqlTypeDescriptor} is null.
	 *
	 * @see #getSqlTypeDescriptorOverride
	 */
	public SqlTypeDescriptor remapSqlTypeDescriptor(SqlTypeDescriptor sqlTypeDescriptor) {
		if ( sqlTypeDescriptor == null ) {
			throw new IllegalArgumentException( "sqlTypeDescriptor is null" );
		}
		if ( ! sqlTypeDescriptor.canBeRemapped() ) {
			return sqlTypeDescriptor;
		}

		final SqlTypeDescriptor overridden = getSqlTypeDescriptorOverride( sqlTypeDescriptor.getJdbcTypeCode() );
		return overridden == null ? sqlTypeDescriptor : overridden;
	}

	/**
	 * Returns the {@link SqlTypeDescriptor} that should be used to handle the given JDBC type code.  Returns
	 * {@code null} if there is no override.
	 *
	 * @param sqlCode A {@link Types} constant indicating the SQL column type
	 * @return The {@link SqlTypeDescriptor} to use as an override, or {@code null} if there is no override.
	 */
	protected SqlTypeDescriptor getSqlTypeDescriptorOverride(int sqlCode) {
		SqlTypeDescriptor descriptor;
		switch ( sqlCode ) {
			case Types.CLOB: {
				descriptor = useInputStreamToInsertBlob() ? ClobSqlDescriptor.STREAM_BINDING : null;
				break;
			}
			default: {
				descriptor = null;
				break;
			}
		}
		return descriptor;
	}

	/**
	 * The legacy behavior of Hibernate.  LOBs are not processed by merge
	 */
	@SuppressWarnings( {"UnusedDeclaration"})
	protected static final LobMergeStrategy LEGACY_LOB_MERGE_STRATEGY = new LobMergeStrategy() {
		@Override
		public Blob mergeBlob(Blob original, Blob target, SharedSessionContractImplementor session) {
			return target;
		}

		@Override
		public Clob mergeClob(Clob original, Clob target, SharedSessionContractImplementor session) {
			return target;
		}

		@Override
		public NClob mergeNClob(NClob original, NClob target, SharedSessionContractImplementor session) {
			return target;
		}
	};

	/**
	 * Merge strategy based on transferring contents based on streams.
	 */
	@SuppressWarnings( {"UnusedDeclaration"})
	protected static final LobMergeStrategy STREAM_XFER_LOB_MERGE_STRATEGY = new LobMergeStrategy() {
		@Override
		public Blob mergeBlob(Blob original, Blob target, SharedSessionContractImplementor session) {
			if ( original != target ) {
				try {
					// the BLOB just read during the load phase of merge
					final OutputStream connectedStream = target.setBinaryStream( 1L );
					// the BLOB from the detached state
					final InputStream detachedStream = original.getBinaryStream();
					StreamCopier.copy( detachedStream, connectedStream );
					return target;
				}
				catch (SQLException e ) {
					throw session.getFactory().getSQLExceptionHelper().convert( e, "unable to merge BLOB data" );
				}
			}
			else {
				return NEW_LOCATOR_LOB_MERGE_STRATEGY.mergeBlob( original, target, session );
			}
		}

		@Override
		public Clob mergeClob(Clob original, Clob target, SharedSessionContractImplementor session) {
			if ( original != target ) {
				try {
					// the CLOB just read during the load phase of merge
					final OutputStream connectedStream = target.setAsciiStream( 1L );
					// the CLOB from the detached state
					final InputStream detachedStream = original.getAsciiStream();
					StreamCopier.copy( detachedStream, connectedStream );
					return target;
				}
				catch (SQLException e ) {
					throw session.getFactory().getSQLExceptionHelper().convert( e, "unable to merge CLOB data" );
				}
			}
			else {
				return NEW_LOCATOR_LOB_MERGE_STRATEGY.mergeClob( original, target, session );
			}
		}

		@Override
		public NClob mergeNClob(NClob original, NClob target, SharedSessionContractImplementor session) {
			if ( original != target ) {
				try {
					// the NCLOB just read during the load phase of merge
					final OutputStream connectedStream = target.setAsciiStream( 1L );
					// the NCLOB from the detached state
					final InputStream detachedStream = original.getAsciiStream();
					StreamCopier.copy( detachedStream, connectedStream );
					return target;
				}
				catch (SQLException e ) {
					throw session.getFactory().getSQLExceptionHelper().convert( e, "unable to merge NCLOB data" );
				}
			}
			else {
				return NEW_LOCATOR_LOB_MERGE_STRATEGY.mergeNClob( original, target, session );
			}
		}
	};

	/**
	 * Merge strategy based on creating a new LOB locator.
	 */
	protected static final LobMergeStrategy NEW_LOCATOR_LOB_MERGE_STRATEGY = new LobMergeStrategy() {
		@Override
		public Blob mergeBlob(Blob original, Blob target, SharedSessionContractImplementor session) {
			if ( original == null && target == null ) {
				return null;
			}
			try {
				final LobCreator lobCreator = session.getFactory().getServiceRegistry().getService( JdbcServices.class ).getLobCreator(
						session
				);
				return original == null
						? lobCreator.createBlob( ArrayHelper.EMPTY_BYTE_ARRAY )
						: lobCreator.createBlob( original.getBinaryStream(), original.length() );
			}
			catch (SQLException e) {
				throw session.getFactory().getSQLExceptionHelper().convert( e, "unable to merge BLOB data" );
			}
		}

		@Override
		public Clob mergeClob(Clob original, Clob target, SharedSessionContractImplementor session) {
			if ( original == null && target == null ) {
				return null;
			}
			try {
				final LobCreator lobCreator = session.getFactory().getServiceRegistry().getService( JdbcServices.class ).getLobCreator( session );
				return original == null
						? lobCreator.createClob( "" )
						: lobCreator.createClob( original.getCharacterStream(), original.length() );
			}
			catch (SQLException e) {
				throw session.getFactory().getSQLExceptionHelper().convert( e, "unable to merge CLOB data" );
			}
		}

		@Override
		public NClob mergeNClob(NClob original, NClob target, SharedSessionContractImplementor session) {
			if ( original == null && target == null ) {
				return null;
			}
			try {
				final LobCreator lobCreator = session.getFactory().getServiceRegistry().getService( JdbcServices.class ).getLobCreator( session );
				return original == null
						? lobCreator.createNClob( "" )
						: lobCreator.createNClob( original.getCharacterStream(), original.length() );
			}
			catch (SQLException e) {
				throw session.getFactory().getSQLExceptionHelper().convert( e, "unable to merge NCLOB data" );
			}
		}
	};

	public LobMergeStrategy getLobMergeStrategy() {
		return NEW_LOCATOR_LOB_MERGE_STRATEGY;
	}


	// hibernate type mapping support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Get the name of the Hibernate {@link Type} associated with the given
	 * {@link java.sql.Types} type code.
	 *
	 * @param code The {@link java.sql.Types} type code
	 * @return The Hibernate {@link Type} name.
	 * @throws HibernateException If no mapping was specified for that type.
	 */
	@SuppressWarnings( {"UnusedDeclaration"})
	public String getHibernateTypeName(int code) throws HibernateException {
		final String result = hibernateTypeNames.get( code );
		if ( result == null ) {
			throw new HibernateException( "No Hibernate type mapping for java.sql.Types code: " + code );
		}
		return result;
	}

	/**
	 * Whether or not the given type name has been registered for this dialect (including both hibernate type names and
	 * custom-registered type names).
	 *
	 * @param typeName the type name.
	 *
	 * @return true if the given string has been registered either as a hibernate type or as a custom-registered one
	 */
	public boolean isTypeNameRegistered(final String typeName) {
		return this.typeNames.containsTypeName( typeName );
	}

	/**
	 * Get the name of the Hibernate {@link Type} associated
	 * with the given {@link java.sql.Types} typecode with the given storage
	 * specification parameters.
	 *
	 * @param code The {@link java.sql.Types} typecode
	 * @param length The datatype length
	 * @param precision The datatype precision
	 * @param scale The datatype scale
	 * @return The Hibernate {@link Type} name.
	 * @throws HibernateException If no mapping was specified for that type.
	 */
	public String getHibernateTypeName(int code, Integer length, Integer precision, Integer scale) throws HibernateException {
		final String result = hibernateTypeNames.get( code, length.longValue(), precision, scale );
		if ( result == null ) {
			throw new HibernateException(
					String.format(
							"No Hibernate type mapping for type [code=%s, length=%s]",
							code,
							length
					)
			);
		}
		return result;
	}

	/**
	 * Registers a Hibernate {@link Type} name for the given
	 * {@link java.sql.Types} type code and maximum column length.
	 *
	 * @param code The {@link java.sql.Types} typecode
	 * @param capacity The maximum length of database type
	 * @param name The Hibernate {@link Type} name
	 */
	protected void registerHibernateType(int code, long capacity, String name) {
		hibernateTypeNames.put( code, capacity, name );
	}

	/**
	 * Registers a Hibernate {@link Type} name for the given
	 * {@link java.sql.Types} type code.
	 *
	 * @param code The {@link java.sql.Types} typecode
	 * @param name The Hibernate {@link Type} name
	 */
	protected void registerHibernateType(int code, String name) {
		hibernateTypeNames.put( code, name );
	}


	// native identifier generation ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * The class (which implements {@link org.hibernate.id.IdentifierGenerator})
	 * which acts as this dialects native generation strategy.
	 * <p/>
	 * Comes into play whenever the user specifies the native generator.
	 *
	 * @return The native generator class.
	 * @deprecated use {@link #getNativeIdentifierGeneratorStrategy()} instead
	 */
	@Deprecated
	public Class getNativeIdentifierGeneratorClass() {
		if ( getIdentityColumnSupport().supportsIdentityColumns() ) {
			return IdentityGenerator.class;
		}
		else {
			return SequenceStyleGenerator.class;
		}
	}

	/**
	 * Resolves the native generation strategy associated to this dialect.
	 * <p/>
	 * Comes into play whenever the user specifies the native generator.
	 *
	 * @return The native generator strategy.
	 */
	public String getNativeIdentifierGeneratorStrategy() {
		if ( getIdentityColumnSupport().supportsIdentityColumns() ) {
			return "identity";
		}
		else {
			return "sequence";
		}
	}

	// IDENTITY support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Get the appropriate {@link IdentityColumnSupport}
	 *
	 * @return the IdentityColumnSupport
	 * @since 5.1
	 */
	public IdentityColumnSupport getIdentityColumnSupport(){
		return new IdentityColumnSupportImpl();
	}

	// SEQUENCE support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Does this dialect support sequences?
	 *
	 * @return True if sequences supported; false otherwise.
	 */
	public boolean supportsSequences() {
		return false;
	}

	/**
	 * Does this dialect support "pooled" sequences.  Not aware of a better
	 * name for this.  Essentially can we specify the initial and increment values?
	 *
	 * @return True if such "pooled" sequences are supported; false otherwise.
	 * @see #getCreateSequenceStrings(String, int, int)
	 * @see #getCreateSequenceString(String, int, int)
	 */
	public boolean supportsPooledSequences() {
		return false;
	}

	/**
	 * Generate the appropriate select statement to to retrieve the next value
	 * of a sequence.
	 * <p/>
	 * This should be a "stand alone" select statement.
	 *
	 * @param sequenceName the name of the sequence
	 * @return String The "nextval" select string.
	 * @throws MappingException If sequences are not supported.
	 */
	public String getSequenceNextValString(String sequenceName) throws MappingException {
		throw new MappingException( getClass().getName() + " does not support sequences" );
	}

	/**
	 * Generate the select expression fragment that will retrieve the next
	 * value of a sequence as part of another (typically DML) statement.
	 * <p/>
	 * This differs from {@link #getSequenceNextValString(String)} in that this
	 * should return an expression usable within another statement.
	 *
	 * @param sequenceName the name of the sequence
	 * @return The "nextval" fragment.
	 * @throws MappingException If sequences are not supported.
	 */
	public String getSelectSequenceNextValString(String sequenceName) throws MappingException {
		throw new MappingException( getClass().getName() + " does not support sequences" );
	}

	/**
	 * The multiline script used to create a sequence.
	 *
	 * @param sequenceName The name of the sequence
	 * @return The sequence creation commands
	 * @throws MappingException If sequences are not supported.
	 * @deprecated Use {@link #getCreateSequenceString(String, int, int)} instead
	 */
	@Deprecated
	public String[] getCreateSequenceStrings(String sequenceName) throws MappingException {
		return new String[] { getCreateSequenceString( sequenceName ) };
	}

	/**
	 * An optional multi-line form for databases which {@link #supportsPooledSequences()}.
	 *
	 * @param sequenceName The name of the sequence
	 * @param initialValue The initial value to apply to 'create sequence' statement
	 * @param incrementSize The increment value to apply to 'create sequence' statement
	 * @return The sequence creation commands
	 * @throws MappingException If sequences are not supported.
	 */
	public String[] getCreateSequenceStrings(String sequenceName, int initialValue, int incrementSize) throws MappingException {
		return new String[] { getCreateSequenceString( sequenceName, initialValue, incrementSize ) };
	}

	/**
	 * Typically dialects which support sequences can create a sequence
	 * with a single command.  This is convenience form of
	 * {@link #getCreateSequenceStrings} to help facilitate that.
	 * <p/>
	 * Dialects which support sequences and can create a sequence in a
	 * single command need *only* override this method.  Dialects
	 * which support sequences but require multiple commands to create
	 * a sequence should instead override {@link #getCreateSequenceStrings}.
	 *
	 * @param sequenceName The name of the sequence
	 * @return The sequence creation command
	 * @throws MappingException If sequences are not supported.
	 */
	protected String getCreateSequenceString(String sequenceName) throws MappingException {
		throw new MappingException( getClass().getName() + " does not support sequences" );
	}

	/**
	 * Overloaded form of {@link #getCreateSequenceString(String)}, additionally
	 * taking the initial value and increment size to be applied to the sequence
	 * definition.
	 * </p>
	 * The default definition is to suffix {@link #getCreateSequenceString(String)}
	 * with the string: " start with {initialValue} increment by {incrementSize}" where
	 * {initialValue} and {incrementSize} are replacement placeholders.  Generally
	 * dialects should only need to override this method if different key phrases
	 * are used to apply the allocation information.
	 *
	 * @param sequenceName The name of the sequence
	 * @param initialValue The initial value to apply to 'create sequence' statement
	 * @param incrementSize The increment value to apply to 'create sequence' statement
	 * @return The sequence creation command
	 * @throws MappingException If sequences are not supported.
	 */
	protected String getCreateSequenceString(String sequenceName, int initialValue, int incrementSize) throws MappingException {
		if ( supportsPooledSequences() ) {
			return getCreateSequenceString( sequenceName ) + " start with " + initialValue + " increment by " + incrementSize;
		}
		throw new MappingException( getClass().getName() + " does not support pooled sequences" );
	}

	/**
	 * The multiline script used to drop a sequence.
	 *
	 * @param sequenceName The name of the sequence
	 * @return The sequence drop commands
	 * @throws MappingException If sequences are not supported.
	 */
	public String[] getDropSequenceStrings(String sequenceName) throws MappingException {
		return new String[]{getDropSequenceString( sequenceName )};
	}

	/**
	 * Typically dialects which support sequences can drop a sequence
	 * with a single command.  This is convenience form of
	 * {@link #getDropSequenceStrings} to help facilitate that.
	 * <p/>
	 * Dialects which support sequences and can drop a sequence in a
	 * single command need *only* override this method.  Dialects
	 * which support sequences but require multiple commands to drop
	 * a sequence should instead override {@link #getDropSequenceStrings}.
	 *
	 * @param sequenceName The name of the sequence
	 * @return The sequence drop commands
	 * @throws MappingException If sequences are not supported.
	 */
	protected String getDropSequenceString(String sequenceName) throws MappingException {
		throw new MappingException( getClass().getName() + " does not support sequences" );
	}

	/**
	 * Get the select command used retrieve the names of all sequences.
	 *
	 * @return The select command; or null if sequences are not supported.
	 * @see org.hibernate.tool.hbm2ddl.SchemaUpdate
	 */
	public String getQuerySequencesString() {
		return null;
	}

	public SequenceInformationExtractor getSequenceInformationExtractor() {
		if ( getQuerySequencesString() == null ) {
			return SequenceInformationExtractorNoOpImpl.INSTANCE;
		}
		else {
			return SequenceInformationExtractorLegacyImpl.INSTANCE;
		}
	}


	// GUID support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Get the command used to select a GUID from the underlying database.
	 * <p/>
	 * Optional operation.
	 *
	 * @return The appropriate command.
	 */
	public String getSelectGUIDString() {
		throw new UnsupportedOperationException( getClass().getName() + " does not support GUIDs" );
	}


	// limit/offset support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Returns the delegate managing LIMIT clause.
	 *
	 * @return LIMIT clause delegate.
	 */
	public LimitHandler getLimitHandler() {
		return new LegacyLimitHandler( this );
	}

	/**
	 * Does this dialect support some form of limiting query results
	 * via a SQL clause?
	 *
	 * @return True if this dialect supports some form of LIMIT.
	 * @deprecated {@link #getLimitHandler()} should be overridden instead.
	 */
	@Deprecated
	public boolean supportsLimit() {
		return false;
	}

	/**
	 * Does this dialect's LIMIT support (if any) additionally
	 * support specifying an offset?
	 *
	 * @return True if the dialect supports an offset within the limit support.
	 * @deprecated {@link #getLimitHandler()} should be overridden instead.
	 */
	@Deprecated
	public boolean supportsLimitOffset() {
		return supportsLimit();
	}

	/**
	 * Does this dialect support bind variables (i.e., prepared statement
	 * parameters) for its limit/offset?
	 *
	 * @return True if bind variables can be used; false otherwise.
	 * @deprecated {@link #getLimitHandler()} should be overridden instead.
	 */
	@Deprecated
	public boolean supportsVariableLimit() {
		return supportsLimit();
	}

	/**
	 * ANSI SQL defines the LIMIT clause to be in the form LIMIT offset, limit.
	 * Does this dialect require us to bind the parameters in reverse order?
	 *
	 * @return true if the correct order is limit, offset
	 * @deprecated {@link #getLimitHandler()} should be overridden instead.
	 */
	@Deprecated
	public boolean bindLimitParametersInReverseOrder() {
		return false;
	}

	/**
	 * Does the <tt>LIMIT</tt> clause come at the start of the
	 * <tt>SELECT</tt> statement, rather than at the end?
	 *
	 * @return true if limit parameters should come before other parameters
	 * @deprecated {@link #getLimitHandler()} should be overridden instead.
	 */
	@Deprecated
	public boolean bindLimitParametersFirst() {
		return false;
	}

	/**
	 * Does the <tt>LIMIT</tt> clause take a "maximum" row number instead
	 * of a total number of returned rows?
	 * <p/>
	 * This is easiest understood via an example.  Consider you have a table
	 * with 20 rows, but you only want to retrieve rows number 11 through 20.
	 * Generally, a limit with offset would say that the offset = 11 and the
	 * limit = 10 (we only want 10 rows at a time); this is specifying the
	 * total number of returned rows.  Some dialects require that we instead
	 * specify offset = 11 and limit = 20, where 20 is the "last" row we want
	 * relative to offset (i.e. total number of rows = 20 - 11 = 9)
	 * <p/>
	 * So essentially, is limit relative from offset?  Or is limit absolute?
	 *
	 * @return True if limit is relative from offset; false otherwise.
	 * @deprecated {@link #getLimitHandler()} should be overridden instead.
	 */
	@Deprecated
	public boolean useMaxForLimit() {
		return false;
	}

	/**
	 * Generally, if there is no limit applied to a Hibernate query we do not apply any limits
	 * to the SQL query.  This option forces that the limit be written to the SQL query.
	 *
	 * @return True to force limit into SQL query even if none specified in Hibernate query; false otherwise.
	 * @deprecated {@link #getLimitHandler()} should be overridden instead.
	 */
	@Deprecated
	public boolean forceLimitUsage() {
		return false;
	}

	/**
	 * Given a limit and an offset, apply the limit clause to the query.
	 *
	 * @param query The query to which to apply the limit.
	 * @param offset The offset of the limit
	 * @param limit The limit of the limit ;)
	 * @return The modified query statement with the limit applied.
	 * @deprecated {@link #getLimitHandler()} should be overridden instead.
	 */
	@Deprecated
	public String getLimitString(String query, int offset, int limit) {
		return getLimitString( query, ( offset > 0 || forceLimitUsage() )  );
	}

	/**
	 * Apply s limit clause to the query.
	 * <p/>
	 * Typically dialects utilize {@link #supportsVariableLimit() variable}
	 * limit clauses when they support limits.  Thus, when building the
	 * select command we do not actually need to know the limit or the offest
	 * since we will just be using placeholders.
	 * <p/>
	 * Here we do still pass along whether or not an offset was specified
	 * so that dialects not supporting offsets can generate proper exceptions.
	 * In general, dialects will override one or the other of this method and
	 * {@link #getLimitString(String, int, int)}.
	 *
	 * @param query The query to which to apply the limit.
	 * @param hasOffset Is the query requesting an offset?
	 * @return the modified SQL
	 * @deprecated {@link #getLimitHandler()} should be overridden instead.
	 */
	@Deprecated
	protected String getLimitString(String query, boolean hasOffset) {
		throw new UnsupportedOperationException( "Paged queries not supported by " + getClass().getName());
	}

	/**
	 * Hibernate APIs explicitly state that setFirstResult() should be a zero-based offset. Here we allow the
	 * Dialect a chance to convert that value based on what the underlying db or driver will expect.
	 * <p/>
	 * NOTE: what gets passed into {@link #getLimitString(String,int,int)} is the zero-based offset.  Dialects which
	 * do not {@link #supportsVariableLimit} should take care to perform any needed first-row-conversion calls prior
	 * to injecting the limit values into the SQL string.
	 *
	 * @param zeroBasedFirstResult The user-supplied, zero-based first row offset.
	 * @return The corresponding db/dialect specific offset.
	 * @see org.hibernate.query.Query#setFirstResult
	 * @deprecated {@link #getLimitHandler()} should be overridden instead.
	 */
	@Deprecated
	public int convertToFirstRowValue(int zeroBasedFirstResult) {
		return zeroBasedFirstResult;
	}


	// lock acquisition support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Informational metadata about whether this dialect is known to support
	 * specifying timeouts for requested lock acquisitions.
	 *
	 * @return True is this dialect supports specifying lock timeouts.
	 */
	public boolean supportsLockTimeouts() {
		return true;

	}

	/**
	 * If this dialect supports specifying lock timeouts, are those timeouts
	 * rendered into the <tt>SQL</tt> string as parameters.  The implication
	 * is that Hibernate will need to bind the timeout value as a parameter
	 * in the {@link java.sql.PreparedStatement}.  If true, the param position
	 * is always handled as the last parameter; if the dialect specifies the
	 * lock timeout elsewhere in the <tt>SQL</tt> statement then the timeout
	 * value should be directly rendered into the statement and this method
	 * should return false.
	 *
	 * @return True if the lock timeout is rendered into the <tt>SQL</tt>
	 * string as a parameter; false otherwise.
	 */
	public boolean isLockTimeoutParameterized() {
		return false;
	}

	/**
	 * Get a strategy instance which knows how to acquire a database-level lock
	 * of the specified mode for this dialect.
	 *
	 * @param lockable The persister for the entity to be locked.
	 * @param lockMode The type of lock to be acquired.
	 * @return The appropriate locking strategy.
	 * @since 3.2
	 */
	public LockingStrategy getLockingStrategy(Lockable lockable, LockMode lockMode) {
		switch ( lockMode ) {
			case PESSIMISTIC_FORCE_INCREMENT:
				return new PessimisticForceIncrementLockingStrategy( lockable, lockMode );
			case PESSIMISTIC_WRITE:
				return new PessimisticWriteSelectLockingStrategy( lockable, lockMode );
			case PESSIMISTIC_READ:
				return new PessimisticReadSelectLockingStrategy( lockable, lockMode );
			case OPTIMISTIC:
				return new OptimisticLockingStrategy( lockable, lockMode );
			case OPTIMISTIC_FORCE_INCREMENT:
				return new OptimisticForceIncrementLockingStrategy( lockable, lockMode );
			default:
				return new SelectLockingStrategy( lockable, lockMode );
		}
	}

	/**
	 * Given LockOptions (lockMode, timeout), determine the appropriate for update fragment to use.
	 *
	 * @param lockOptions contains the lock mode to apply.
	 * @return The appropriate for update fragment.
	 */
	public String getForUpdateString(LockOptions lockOptions) {
		final LockMode lockMode = lockOptions.getLockMode();
		return getForUpdateString( lockMode, lockOptions.getTimeOut() );
	}

	@SuppressWarnings( {"deprecation"})
	private String getForUpdateString(LockMode lockMode, int timeout){
		switch ( lockMode ) {
			case UPGRADE:
				return getForUpdateString();
			case PESSIMISTIC_READ:
				return getReadLockString( timeout );
			case PESSIMISTIC_WRITE:
				return getWriteLockString( timeout );
			case UPGRADE_NOWAIT:
			case FORCE:
			case PESSIMISTIC_FORCE_INCREMENT:
				return getForUpdateNowaitString();
			case UPGRADE_SKIPLOCKED:
				return getForUpdateSkipLockedString();
			default:
				return "";
		}
	}

	/**
	 * Given a lock mode, determine the appropriate for update fragment to use.
	 *
	 * @param lockMode The lock mode to apply.
	 * @return The appropriate for update fragment.
	 */
	public String getForUpdateString(LockMode lockMode) {
		return getForUpdateString( lockMode, LockOptions.WAIT_FOREVER );
	}

	/**
	 * Get the string to append to SELECT statements to acquire locks
	 * for this dialect.
	 *
	 * @return The appropriate <tt>FOR UPDATE</tt> clause string.
	 */
	public String getForUpdateString() {
		return " for update";
	}

	/**
	 * Get the string to append to SELECT statements to acquire WRITE locks
	 * for this dialect.  Location of the of the returned string is treated
	 * the same as getForUpdateString.
	 *
	 * @param timeout in milliseconds, -1 for indefinite wait and 0 for no wait.
	 * @return The appropriate <tt>LOCK</tt> clause string.
	 */
	public String getWriteLockString(int timeout) {
		return getForUpdateString();
	}

	/**
	 * Get the string to append to SELECT statements to acquire WRITE locks
	 * for this dialect given the aliases of the columns to be write locked.
	 * Location of the of the returned string is treated
	 * the same as getForUpdateString.
	 *
	 * @param aliases The columns to be read locked.
	 * @param timeout in milliseconds, -1 for indefinite wait and 0 for no wait.
	 * @return The appropriate <tt>LOCK</tt> clause string.
	 */
	public String getWriteLockString(String aliases, int timeout) {
		// by default we simply return the getWriteLockString(timeout) result since
		// the default is to say no support for "FOR UPDATE OF ..."
		return getWriteLockString( timeout );
	}

	/**
	 * Get the string to append to SELECT statements to acquire READ locks
	 * for this dialect.  Location of the of the returned string is treated
	 * the same as getForUpdateString.
	 *
	 * @param timeout in milliseconds, -1 for indefinite wait and 0 for no wait.
	 * @return The appropriate <tt>LOCK</tt> clause string.
	 */
	public String getReadLockString(int timeout) {
		return getForUpdateString();
	}

	/**
	 * Get the string to append to SELECT statements to acquire READ locks
	 * for this dialect given the aliases of the columns to be read locked.
	 * Location of the of the returned string is treated
	 * the same as getForUpdateString.
	 *
	 * @param aliases The columns to be read locked.
	 * @param timeout in milliseconds, -1 for indefinite wait and 0 for no wait.
	 * @return The appropriate <tt>LOCK</tt> clause string.
	 */
	public String getReadLockString(String aliases, int timeout) {
		// by default we simply return the getReadLockString(timeout) result since
		// the default is to say no support for "FOR UPDATE OF ..."
		return getReadLockString( timeout );
	}

	/**
	 * Is <tt>FOR UPDATE OF</tt> syntax supported?
	 *
	 * @return True if the database supports <tt>FOR UPDATE OF</tt> syntax;
	 * false otherwise.
	 */
	public boolean forUpdateOfColumns() {
		// by default we report no support
		return false;
	}

	/**
	 * Does this dialect support <tt>FOR UPDATE</tt> in conjunction with
	 * outer joined rows?
	 *
	 * @return True if outer joined rows can be locked via <tt>FOR UPDATE</tt>.
	 */
	public boolean supportsOuterJoinForUpdate() {
		return true;
	}

	/**
	 * Get the <tt>FOR UPDATE OF column_list</tt> fragment appropriate for this
	 * dialect given the aliases of the columns to be write locked.
	 *
	 * @param aliases The columns to be write locked.
	 * @return The appropriate <tt>FOR UPDATE OF column_list</tt> clause string.
	 */
	public String getForUpdateString(String aliases) {
		// by default we simply return the getForUpdateString() result since
		// the default is to say no support for "FOR UPDATE OF ..."
		return getForUpdateString();
	}

	/**
	 * Get the <tt>FOR UPDATE OF column_list</tt> fragment appropriate for this
	 * dialect given the aliases of the columns to be write locked.
	 *
	 * @param aliases The columns to be write locked.
	 * @param lockOptions the lock options to apply
	 * @return The appropriate <tt>FOR UPDATE OF column_list</tt> clause string.
	 */
	@SuppressWarnings({"unchecked", "UnusedParameters"})
	public String getForUpdateString(String aliases, LockOptions lockOptions) {
		LockMode lockMode = lockOptions.getLockMode();
		final Iterator<Map.Entry<String, LockMode>> itr = lockOptions.getAliasLockIterator();
		while ( itr.hasNext() ) {
			// seek the highest lock mode
			final Map.Entry<String, LockMode>entry = itr.next();
			final LockMode lm = entry.getValue();
			if ( lm.greaterThan( lockMode ) ) {
				lockMode = lm;
			}
		}
		lockOptions.setLockMode( lockMode );
		return getForUpdateString( lockOptions );
	}

	/**
	 * Retrieves the <tt>FOR UPDATE NOWAIT</tt> syntax specific to this dialect.
	 *
	 * @return The appropriate <tt>FOR UPDATE NOWAIT</tt> clause string.
	 */
	public String getForUpdateNowaitString() {
		// by default we report no support for NOWAIT lock semantics
		return getForUpdateString();
	}

	/**
	 * Retrieves the <tt>FOR UPDATE SKIP LOCKED</tt> syntax specific to this dialect.
	 *
	 * @return The appropriate <tt>FOR UPDATE SKIP LOCKED</tt> clause string.
	 */
	public String getForUpdateSkipLockedString() {
		// by default we report no support for SKIP_LOCKED lock semantics
		return getForUpdateString();
	}

	/**
	 * Get the <tt>FOR UPDATE OF column_list NOWAIT</tt> fragment appropriate
	 * for this dialect given the aliases of the columns to be write locked.
	 *
	 * @param aliases The columns to be write locked.
	 * @return The appropriate <tt>FOR UPDATE OF colunm_list NOWAIT</tt> clause string.
	 */
	public String getForUpdateNowaitString(String aliases) {
		return getForUpdateString( aliases );
	}

	/**
	 * Get the <tt>FOR UPDATE OF column_list SKIP LOCKED</tt> fragment appropriate
	 * for this dialect given the aliases of the columns to be write locked.
	 *
	 * @param aliases The columns to be write locked.
	 * @return The appropriate <tt>FOR UPDATE colunm_list SKIP LOCKED</tt> clause string.
	 */
	public String getForUpdateSkipLockedString(String aliases) {
		return getForUpdateString( aliases );
	}

	/**
	 * Some dialects support an alternative means to <tt>SELECT FOR UPDATE</tt>,
	 * whereby a "lock hint" is appends to the table name in the from clause.
	 * <p/>
	 * contributed by <a href="http://sourceforge.net/users/heschulz">Helge Schulz</a>
	 *
	 * @param mode The lock mode to apply
	 * @param tableName The name of the table to which to apply the lock hint.
	 * @return The table with any required lock hints.
	 * @deprecated use {@code appendLockHint(LockOptions,String)} instead
	 */
	@Deprecated
	public String appendLockHint(LockMode mode, String tableName) {
		return appendLockHint( new LockOptions( mode ), tableName );
	}
	/**
	 * Some dialects support an alternative means to <tt>SELECT FOR UPDATE</tt>,
	 * whereby a "lock hint" is appends to the table name in the from clause.
	 * <p/>
	 * contributed by <a href="http://sourceforge.net/users/heschulz">Helge Schulz</a>
	 *
	 * @param lockOptions The lock options to apply
	 * @param tableName The name of the table to which to apply the lock hint.
	 * @return The table with any required lock hints.
	 */
	public String appendLockHint(LockOptions lockOptions, String tableName){
		return tableName;
	}

	/**
	 * Modifies the given SQL by applying the appropriate updates for the specified
	 * lock modes and key columns.
	 * <p/>
	 * The behavior here is that of an ANSI SQL <tt>SELECT FOR UPDATE</tt>.  This
	 * method is really intended to allow dialects which do not support
	 * <tt>SELECT FOR UPDATE</tt> to achieve this in their own fashion.
	 *
	 * @param sql the SQL string to modify
	 * @param aliasedLockOptions lock options indexed by aliased table names.
	 * @param keyColumnNames a map of key columns indexed by aliased table names.
	 * @return the modified SQL string.
	 */
	public String applyLocksToSql(String sql, LockOptions aliasedLockOptions, Map<String, String[]> keyColumnNames) {
		return sql + new ForUpdateFragment( this, aliasedLockOptions, keyColumnNames ).toFragmentString();
	}


	// table support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Command used to create a table.
	 *
	 * @return The command used to create a table.
	 */
	public String getCreateTableString() {
		return "create table";
	}

	/**
	 * Command used to alter a table.
	 *
	 * @param tableName The name of the table to alter
	 * @return The command used to alter a table.
	 * @since 5.2.11
	 */
	public String getAlterTableString(String tableName) {
		final StringBuilder sb = new StringBuilder( "alter table " );
		if ( supportsIfExistsAfterAlterTable() ) {
			sb.append( "if exists " );
		}
		sb.append( tableName );
		return sb.toString();
	}

	/**
	 * Slight variation on {@link #getCreateTableString}.  Here, we have the
	 * command used to create a table when there is no primary key and
	 * duplicate rows are expected.
	 * <p/>
	 * Most databases do not care about the distinction; originally added for
	 * Teradata support which does care.
	 *
	 * @return The command used to create a multiset table.
	 */
	public String getCreateMultisetTableString() {
		return getCreateTableString();
	}

	public SqmMutationStrategy getFallbackSqmMutationStrategy(EntityHierarchy hierarchy) {
		if ( hierarchy.getIdentifierDescriptor() instanceof EntityIdentifierComposite ) {
			if ( !supportsTuplesInSubqueries() ) {
				return new InlineMutationStrategy();
			}
		}
		return getDefaultIdTableStrategy();
	}

	public SqmMutationStrategy getDefaultIdTableStrategy() {
		return new PersistentTableStrategy( getIdTableExporter() );
	}

	protected Exporter<IdTable> getIdTableExporter() {
		return new IdTableExporterImpl();
	}


	// callable statement support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Registers a parameter (either OUT, or the new REF_CURSOR param type available in Java 8) capable of
	 * returning {@link java.sql.ResultSet} *by position*.  Pre-Java 8, registering such ResultSet-returning
	 * parameters varied greatly across database and drivers; hence its inclusion as part of the Dialect contract.
	 *
	 * @param statement The callable statement.
	 * @param position The bind position at which to register the output param.
	 *
	 * @return The number of (contiguous) bind positions used.
	 *
	 * @throws SQLException Indicates problems registering the param.
	 */
	public int registerResultSetOutParameter(CallableStatement statement, int position) throws SQLException {
		throw new UnsupportedOperationException(
				getClass().getName() +
						" does not support resultsets via stored procedures"
		);
	}

	/**
	 * Registers a parameter (either OUT, or the new REF_CURSOR param type available in Java 8) capable of
	 * returning {@link java.sql.ResultSet} *by name*.  Pre-Java 8, registering such ResultSet-returning
	 * parameters varied greatly across database and drivers; hence its inclusion as part of the Dialect contract.
	 *
	 * @param statement The callable statement.
	 * @param name The parameter name (for drivers which support named parameters).
	 *
	 * @return The number of (contiguous) bind positions used.
	 *
	 * @throws SQLException Indicates problems registering the param.
	 */
	@SuppressWarnings("UnusedParameters")
	public int registerResultSetOutParameter(CallableStatement statement, String name) throws SQLException {
		throw new UnsupportedOperationException(
				getClass().getName() +
						" does not support resultsets via stored procedures"
		);
	}

	/**
	 * Given a callable statement previously processed by {@link #registerResultSetOutParameter},
	 * extract the {@link java.sql.ResultSet} from the OUT parameter.
	 *
	 * @param statement The callable statement.
	 * @return The extracted result set.
	 * @throws SQLException Indicates problems extracting the result set.
	 */
	public ResultSet getResultSet(CallableStatement statement) throws SQLException {
		throw new UnsupportedOperationException(
				getClass().getName() + " does not support resultsets via stored procedures"
		);
	}

	/**
	 * Given a callable statement previously processed by {@link #registerResultSetOutParameter},
	 * extract the {@link java.sql.ResultSet}.
	 *
	 * @param statement The callable statement.
	 * @param position The bind position at which to register the output param.
	 *
	 * @return The extracted result set.
	 *
	 * @throws SQLException Indicates problems extracting the result set.
	 */
	@SuppressWarnings("UnusedParameters")
	public ResultSet getResultSet(CallableStatement statement, int position) throws SQLException {
		throw new UnsupportedOperationException(
				getClass().getName() + " does not support resultsets via stored procedures"
		);
	}

	/**
	 * Given a callable statement previously processed by {@link #registerResultSetOutParameter},
	 * extract the {@link java.sql.ResultSet} from the OUT parameter.
	 *
	 * @param statement The callable statement.
	 * @param name The parameter name (for drivers which support named parameters).
	 *
	 * @return The extracted result set.
	 *
	 * @throws SQLException Indicates problems extracting the result set.
	 */
	@SuppressWarnings("UnusedParameters")
	public ResultSet getResultSet(CallableStatement statement, String name) throws SQLException {
		throw new UnsupportedOperationException(
				getClass().getName() + " does not support resultsets via stored procedures"
		);
	}

	// current timestamp support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Does this dialect support a way to retrieve the database's current
	 * timestamp value?
	 *
	 * @return True if the current timestamp can be retrieved; false otherwise.
	 */
	public boolean supportsCurrentTimestampSelection() {
		return false;
	}

	/**
	 * Should the value returned by {@link #getCurrentTimestampSelectString}
	 * be treated as callable.  Typically this indicates that JDBC escape
	 * syntax is being used...
	 *
	 * @return True if the {@link #getCurrentTimestampSelectString} return
	 * is callable; false otherwise.
	 */
	public boolean isCurrentTimestampSelectStringCallable() {
		throw new UnsupportedOperationException( "Database not known to define a current timestamp function" );
	}

	/**
	 * Retrieve the command used to retrieve the current timestamp from the
	 * database.
	 *
	 * @return The command.
	 */
	public String getCurrentTimestampSelectString() {
		throw new UnsupportedOperationException( "Database not known to define a current timestamp function" );
	}

	/**
	 * The name of the database-specific SQL function for retrieving the
	 * current timestamp.
	 *
	 * @return The function name.
	 */
	public String getCurrentTimestampSQLFunctionName() {
		// the standard SQL function name is current_timestamp...
		return "current_timestamp";
	}


	// SQLException support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Build an instance of the SQLExceptionConverter preferred by this dialect for
	 * converting SQLExceptions into Hibernate's JDBCException hierarchy.
	 * <p/>
	 * The preferred method is to not override this method; if possible,
	 * {@link #buildSQLExceptionConversionDelegate()} should be overridden
	 * instead.
	 *
	 * If this method is not overridden, the default SQLExceptionConverter
	 * implementation executes 3 SQLException converter delegates:
	 * <ol>
	 *     <li>a "static" delegate based on the JDBC 4 defined SQLException hierarchy;</li>
	 *     <li>the vendor-specific delegate returned by {@link #buildSQLExceptionConversionDelegate()};
	 *         (it is strongly recommended that specific Dialect implementations
	 *         override {@link #buildSQLExceptionConversionDelegate()})</li>
	 *     <li>a delegate that interprets SQLState codes for either X/Open or SQL-2003 codes,
	 *         depending on java.sql.DatabaseMetaData#getSQLStateType</li>
	 * </ol>
	 * <p/>
	 * If this method is overridden, it is strongly recommended that the
	 * returned {@link SQLExceptionConverter} interpret SQL errors based on
	 * vendor-specific error codes rather than the SQLState since the
	 * interpretation is more accurate when using vendor-specific ErrorCodes.
	 *
	 * @return The Dialect's preferred SQLExceptionConverter, or null to
	 * indicate that the default {@link SQLExceptionConverter} should be used.
	 *
	 * @see {@link #buildSQLExceptionConversionDelegate()}
	 * @deprecated {@link #buildSQLExceptionConversionDelegate()} should be
	 * overridden instead.
	 */
	@Deprecated
	public SQLExceptionConverter buildSQLExceptionConverter() {
		return null;
	}

	/**
	 * Build an instance of a {@link SQLExceptionConversionDelegate} for
	 * interpreting dialect-specific error or SQLState codes.
	 * <p/>
	 * When {@link #buildSQLExceptionConverter} returns null, the default 
	 * {@link SQLExceptionConverter} is used to interpret SQLState and
	 * error codes. If this method is overridden to return a non-null value,
	 * the default {@link SQLExceptionConverter} will use the returned
	 * {@link SQLExceptionConversionDelegate} in addition to the following 
	 * standard delegates:
	 * <ol>
	 *     <li>a "static" delegate based on the JDBC 4 defined SQLException hierarchy;</li>
	 *     <li>a delegate that interprets SQLState codes for either X/Open or SQL-2003 codes,
	 *         depending on java.sql.DatabaseMetaData#getSQLStateType</li>
	 * </ol>
	 * <p/>
	 * It is strongly recommended that specific Dialect implementations override this
	 * method, since interpretation of a SQL error is much more accurate when based on
	 * the a vendor-specific ErrorCode rather than the SQLState.
	 * <p/>
	 * Specific Dialects may override to return whatever is most appropriate for that vendor.
	 *
	 * @return The SQLExceptionConversionDelegate for this dialect
	 */
	public SQLExceptionConversionDelegate buildSQLExceptionConversionDelegate() {
		return null;
	}

	private static final ViolatedConstraintNameExtracter EXTRACTER = new ViolatedConstraintNameExtracter() {
		public String extractConstraintName(SQLException sqle) {
			return null;
		}
	};

	public ViolatedConstraintNameExtracter getViolatedConstraintNameExtracter() {
		return EXTRACTER;
	}


	// union subclass support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Given a {@link java.sql.Types} type code, determine an appropriate
	 * null value to use in a select clause.
	 * <p/>
	 * One thing to consider here is that certain databases might
	 * require proper casting for the nulls here since the select here
	 * will be part of a UNION/UNION ALL.
	 *
	 * @param sqlType The {@link java.sql.Types} type code.
	 * @return The appropriate select clause value fragment.
	 */
	public String getSelectClauseNullString(int sqlType) {
		return "null";
	}

	/**
	 * Does this dialect support UNION ALL, which is generally a faster
	 * variant of UNION?
	 *
	 * @return True if UNION ALL is supported; false otherwise.
	 */
	public boolean supportsUnionAll() {
		return false;
	}


	// miscellaneous support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


	/**
	 * Create a {@link org.hibernate.sql.JoinFragment} strategy responsible
	 * for handling this dialect's variations in how joins are handled.
	 *
	 * @return This dialect's {@link org.hibernate.sql.JoinFragment} strategy.
	 */
	public JoinFragment createOuterJoinFragment() {
		return new ANSIJoinFragment();
	}

	/**
	 * Create a {@link org.hibernate.sql.CaseFragment} strategy responsible
	 * for handling this dialect's variations in how CASE statements are
	 * handled.
	 *
	 * @return This dialect's {@link org.hibernate.sql.CaseFragment} strategy.
	 */
	public CaseFragment createCaseFragment() {
		return new ANSICaseFragment();
	}

	/**
	 * The fragment used to insert a row without specifying any column values.
	 * This is not possible on some databases.
	 *
	 * @return The appropriate empty values clause.
	 */
	public String getNoColumnsInsertString() {
		return "values ( )";
	}

	/**
	 * Check if the INSERT statement is allowed to contain no column.
	 *
	 * @return if the Dialect supports no-column INSERT.
	 */
	public boolean supportsNoColumnsInsert() {
		return true;
	}

	/**
	 * The name of the SQL function that transforms a string to
	 * lowercase
	 *
	 * @return The dialect-specific lowercase function.
	 */
	public String getLowercaseFunction() {
		return "lower";
	}

	/**
	 * The name of the SQL function that can do case insensitive <b>like</b> comparison.
	 *
	 * @return  The dialect-specific "case insensitive" like function.
	 */
	public String getCaseInsensitiveLike(){
		return "like";
	}

	/**
	 * Does this dialect support case insensitive LIKE restrictions?
	 *
	 * @return {@code true} if the underlying database supports case insensitive like comparison,
	 * {@code false} otherwise.  The default is {@code false}.
	 */
	public boolean supportsCaseInsensitiveLike(){
		return false;
	}

	/**
	 * Meant as a means for end users to affect the select strings being sent
	 * to the database and perhaps manipulate them in some fashion.
	 * <p/>
	 * The recommend approach is to instead use
	 * {@link org.hibernate.Interceptor#onPrepareStatement(String)}.
	 *
	 * @param select The select command
	 * @return The mutated select command, or the same as was passed in.
	 */
	public String transformSelectString(String select) {
		return select;
	}

	/**
	 * What is the maximum length Hibernate can use for generated aliases?
	 * <p/>
	 * The maximum here should account for the fact that Hibernate often needs to append "uniqueing" information
	 * to the end of generated aliases.  That "uniqueing" information will be added to the end of a identifier
	 * generated to the length specified here; so be sure to leave some room (generally speaking 5 positions will
	 * suffice).
	 *
	 * @return The maximum length.
	 */
	public int getMaxAliasLength() {
		return 10;
	}

	/**
	 * The SQL literal value to which this database maps boolean values.
	 *
	 * @param bool The boolean value
	 * @return The appropriate SQL literal.
	 */
	public String toBooleanValueString(boolean bool) {
		return bool ? "1" : "0";
	}


	// keyword support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	protected void registerKeyword(String word) {
		// When tokens are checked for keywords, they are always compared against the lower-case version of the token.
		// For instance, Template#renderWhereStringTemplate transforms all tokens to lower-case too.
		sqlKeywords.add( word.toLowerCase( Locale.ROOT ) );
	}

	/**
	 * @deprecated These are only ever used (if at all) from the code that handles identifier quoting.  So
	 * see {@link #buildIdentifierHelper} instead
	 */
	@Deprecated
	public Set<String> getKeywords() {
		return sqlKeywords;
	}

	/**
	 * Build the IdentifierHelper indicated by this Dialect for handling identifier conversions.
	 * Returning {@code null} is allowed and indicates that Hibernate should fallback to building a
	 * "standard" helper.  In the fallback path, any changes made to the IdentifierHelperBuilder
	 * during this call will still be incorporated into the built IdentifierHelper.
	 * <p/>
	 * The incoming builder will have the following set:<ul>
	 *     <li>{@link IdentifierHelperBuilder#isGloballyQuoteIdentifiers()}</li>
	 *     <li>{@link IdentifierHelperBuilder#getUnquotedCaseStrategy()} - initialized to UPPER</li>
	 *     <li>{@link IdentifierHelperBuilder#getQuotedCaseStrategy()} - initialized to MIXED</li>
	 * </ul>
	 * <p/>
	 * By default Hibernate will do the following:<ul>
	 *     <li>Call {@link IdentifierHelperBuilder#applyIdentifierCasing(DatabaseMetaData)}
	 *     <li>Call {@link IdentifierHelperBuilder#applyReservedWords(DatabaseMetaData)}
	 *     <li>Applies {@link AnsiSqlKeywords#sql2003()} as reserved words</li>
	 *     <li>Applies the {#link #sqlKeywords} collected here as reserved words</li>
	 *     <li>Applies the Dialect's NameQualifierSupport, if it defines one</li>
	 * </ul>
	 *
	 * @param builder A semi-configured IdentifierHelper builder.
	 * @param dbMetaData Access to the metadata returned from the driver if needed and if available.  WARNING: may be {@code null}
	 *
	 * @return The IdentifierHelper instance to use, or {@code null} to indicate Hibernate should use its fallback path
	 *
	 * @throws SQLException Accessing the DatabaseMetaData can throw it.  Just re-throw and Hibernate will handle.
	 *
	 * @see #getNameQualifierSupport()
	 */
	public IdentifierHelper buildIdentifierHelper(
			IdentifierHelperBuilder builder,
			DatabaseMetaData dbMetaData) throws SQLException {
		builder.applyIdentifierCasing( dbMetaData );

		builder.applyReservedWords( dbMetaData );
		builder.applyReservedWords( AnsiSqlKeywords.INSTANCE.sql2003() );
		builder.applyReservedWords( sqlKeywords );

		builder.setNameQualifierSupport( getNameQualifierSupport() );

		return builder.build();
	}


	// identifier quoting support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * The character specific to this dialect used to begin a quoted identifier.
	 *
	 * @return The dialect's specific open quote character.
	 */
	public char openQuote() {
		return '"';
	}

	/**
	 * The character specific to this dialect used to close a quoted identifier.
	 *
	 * @return The dialect's specific close quote character.
	 */
	public char closeQuote() {
		return '"';
	}

	/**
	 * Apply dialect-specific quoting.
	 * <p/>
	 * By default, the incoming value is checked to see if its first character
	 * is the back-tick (`).  If so, the dialect specific quoting is applied.
	 *
	 * @param name The value to be quoted.
	 * @return The quoted (or unmodified, if not starting with back-tick) value.
	 * @see #openQuote()
	 * @see #closeQuote()
	 */
	public final String quote(String name) {
		if ( name == null ) {
			return null;
		}

		if ( name.charAt( 0 ) == '`' ) {
			return openQuote() + name.substring( 1, name.length() - 1 ) + closeQuote();
		}
		else {
			return name;
		}
	}


	// DDL support ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	private StandardTableExporter tableExporter;
	private StandardSequenceExporter sequenceExporter;
	private StandardIndexExporter indexExporter;
	private StandardForeignKeyExporter foreignKeyExporter;
	private StandardUniqueKeyExporter uniqueKeyExporter;
	private StandardAuxiliaryDatabaseObjectExporter auxiliaryObjectExporter;
	private StandardTableAlterable tableAlter;

	public Exporter<ExportableTable> getTableExporter() {
		if ( tableExporter == null ) {
			tableExporter = new StandardTableExporter( this );
		}
		return tableExporter;
	}

	public Exporter<Sequence> getSequenceExporter() {
		if ( sequenceExporter == null ) {
			sequenceExporter = new StandardSequenceExporter( this );
		}
		return sequenceExporter;
	}

	public Exporter<Index> getIndexExporter() {
		if ( indexExporter == null ) {
			indexExporter = new StandardIndexExporter( this );
		}
		return indexExporter;
	}

	public Exporter<ForeignKey> getForeignKeyExporter() {
		if ( foreignKeyExporter == null ) {
			foreignKeyExporter = new StandardForeignKeyExporter( this );
		}
		return foreignKeyExporter;
	}

	public Exporter<UniqueKey> getUniqueKeyExporter() {
		if ( uniqueKeyExporter == null ) {
			uniqueKeyExporter = new StandardUniqueKeyExporter( this );
		}
		return uniqueKeyExporter;
	}

	public Exporter<AuxiliaryDatabaseObject> getAuxiliaryDatabaseObjectExporter() {
		if ( auxiliaryObjectExporter == null ) {
			auxiliaryObjectExporter = new StandardAuxiliaryDatabaseObjectExporter();
		}
		return auxiliaryObjectExporter;
	}

	public Alterable<ExportableTable> getTableAlterable() {
		if ( tableAlter == null ) {
			tableAlter = new StandardTableAlterable( this );
		}
		return tableAlter;
	}

	/**
	 * Does this dialect support catalog creation?
	 *
	 * @return True if the dialect supports catalog creation; false otherwise.
	 */
	public boolean canCreateCatalog() {
		return false;
	}

	/**
	 * Get the SQL command used to create the named catalog
	 *
	 * @param catalogName The name of the catalog to be created.
	 *
	 * @return The creation commands
	 */
	public String[] getCreateCatalogCommand(String catalogName) {
		throw new UnsupportedOperationException( "No create catalog syntax supported by " + getClass().getName() );
	}

	/**
	 * Get the SQL command used to drop the named catalog
	 *
	 * @param catalogName The name of the catalog to be dropped.
	 *
	 * @return The drop commands
	 */
	public String[] getDropCatalogCommand(String catalogName) {
		throw new UnsupportedOperationException( "No drop catalog syntax supported by " + getClass().getName() );
	}

	/**
	 * Does this dialect support schema creation?
	 *
	 * @return True if the dialect supports schema creation; false otherwise.
	 */
	public boolean canCreateSchema() {
		return true;
	}

	/**
	 * Get the SQL command used to create the named schema
	 *
	 * @param schemaName The name of the schema to be created.
	 *
	 * @return The creation commands
	 */
	public String[] getCreateSchemaCommand(String schemaName) {
		return new String[] {"create schema " + schemaName};
	}

	/**
	 * Get the SQL command used to drop the named schema
	 *
	 * @param schemaName The name of the schema to be dropped.
	 *
	 * @return The drop commands
	 */
	public String[] getDropSchemaCommand(String schemaName) {
		return new String[] {"drop schema " + schemaName};
	}

	/**
	 * Get the SQL command used to retrieve the current schema name.  Works in conjunction
	 * with {@link #getSchemaNameResolver()}, unless the return from there does not need this
	 * information.  E.g., a custom impl might make use of the Java 1.7 addition of
	 * the {@link java.sql.Connection#getSchema()} method
	 *
	 * @return The current schema retrieval SQL
	 */
	public String getCurrentSchemaCommand() {
		return null;
	}

	/**
	 * Get the strategy for determining the schema name of a Connection
	 *
	 * @return The schema name resolver strategy
	 */
	public SchemaNameResolver getSchemaNameResolver() {
		return DefaultSchemaNameResolver.INSTANCE;
	}

	/**
	 * Does this dialect support the <tt>ALTER TABLE</tt> syntax?
	 *
	 * @return True if we support altering of tables; false otherwise.
	 */
	public boolean hasAlterTable() {
		return true;
	}

	/**
	 * Do we need to drop constraints before dropping tables in this dialect?
	 *
	 * @return True if constraints must be dropped prior to dropping
	 * the table; false otherwise.
	 */
	public boolean dropConstraints() {
		return true;
	}

	/**
	 * Do we need to qualify index names with the schema name?
	 *
	 * @return boolean
	 */
	public boolean qualifyIndexName() {
		return true;
	}

	/**
	 * The syntax used to add a column to a table (optional).
	 *
	 * @return The "add column" fragment.
	 */
	public String getAddColumnString() {
		throw new UnsupportedOperationException( "No add column syntax supported by " + getClass().getName() );
	}

	/**
	 * The syntax for the suffix used to add a column to a table (optional).
	 *
	 * @return The suffix "add column" fragment.
	 */
	public String getAddColumnSuffixString() {
		return "";
	}

	public String getDropForeignKeyString() {
		return " drop constraint ";
	}

	public String getTableTypeString() {
		// grrr... for differentiation of mysql storage engines
		return "";
	}

	/**
	 * The syntax used to add a foreign key constraint to a table.
	 *
	 * @param constraintName The FK constraint name.
	 * @param foreignKey The names of the columns comprising the FK
	 * @param referencedTable The table referenced by the FK
	 * @param primaryKey The explicit columns in the referencedTable referenced
	 * by this FK.
	 * @param referencesPrimaryKey if false, constraint should be
	 * explicit about which column names the constraint refers to
	 *
	 * @return the "add FK" fragment
	 */
	public String getAddForeignKeyConstraintString(
			String constraintName,
			String[] foreignKey,
			String referencedTable,
			String[] primaryKey,
			boolean referencesPrimaryKey) {
		final StringBuilder res = new StringBuilder( 30 );

		res.append( " add constraint " )
				.append( quote( constraintName ) )
				.append( " foreign key (" )
				.append( String.join( ", ", foreignKey ) )
				.append( ") references " )
				.append( referencedTable );

		if ( !referencesPrimaryKey ) {
			res.append( " (" )
					.append( String.join( ", ", primaryKey ) )
					.append( ')' );
		}

		return res.toString();
	}

	public String getAddForeignKeyConstraintString(
			String constraintName,
			String foreignKeyDefinition) {
		return new StringBuilder( 30 )
				.append( " add constraint " )
				.append( quote( constraintName ) )
				.append( " " )
				.append( foreignKeyDefinition )
				.toString();
	}

	/**
	 * The syntax used to add a primary key constraint to a table.
	 *
	 * @param constraintName The name of the PK constraint.
	 * @return The "add PK" fragment
	 */
	public String getAddPrimaryKeyConstraintString(String constraintName) {
		return " add constraint " + constraintName + " primary key ";
	}

	/**
	 * Does the database/driver have bug in deleting rows that refer to other rows being deleted in the same query?
	 *
	 * @return {@code true} if the database/driver has this bug
	 */
	public boolean hasSelfReferentialForeignKeyBug() {
		return false;
	}

	/**
	 * The keyword used to specify a nullable column.
	 *
	 * @return String
	 */
	public String getNullColumnString() {
		return "";
	}

	/**
	 * Does this dialect/database support commenting on tables, columns, etc?
	 *
	 * @return {@code true} if commenting is supported
	 */
	public boolean supportsCommentOn() {
		return false;
	}

	/**
	 * Get the comment into a form supported for table definition.
	 *
	 * @param comment The comment to apply
	 *
	 * @return The comment fragment
	 */
	public String getTableComment(String comment) {
		return "";
	}

	/**
	 * Get the comment into a form supported for column definition.
	 *
	 * @param comment The comment to apply
	 *
	 * @return The comment fragment
	 */
	public String getColumnComment(String comment) {
		return "";
	}

	/**
	 * For dropping a table, can the phrase "if exists" be applied before the table name?
	 * <p/>
	 * NOTE : Only one or the other (or neither) of this and {@link #supportsIfExistsAfterTableName} should return true
	 *
	 * @return {@code true} if the "if exists" can be applied before the table name
	 */
	public boolean supportsIfExistsBeforeTableName() {
		return false;
	}

	/**
	 * For dropping a table, can the phrase "if exists" be applied after the table name?
	 * <p/>
	 * NOTE : Only one or the other (or neither) of this and {@link #supportsIfExistsBeforeTableName} should return true
	 *
	 * @return {@code true} if the "if exists" can be applied after the table name
	 */
	public boolean supportsIfExistsAfterTableName() {
		return false;
	}

	/**
	 * For dropping a constraint with an "alter table", can the phrase "if exists" be applied before the constraint name?
	 * <p/>
	 * NOTE : Only one or the other (or neither) of this and {@link #supportsIfExistsAfterConstraintName} should return true
	 *
	 * @return {@code true} if the "if exists" can be applied before the constraint name
	 */
	public boolean supportsIfExistsBeforeConstraintName() {
		return false;
	}

	/**
	 * For dropping a constraint with an "alter table", can the phrase "if exists" be applied after the constraint name?
	 * <p/>
	 * NOTE : Only one or the other (or neither) of this and {@link #supportsIfExistsBeforeConstraintName} should return true
	 *
	 * @return {@code true} if the "if exists" can be applied after the constraint name
	 */
	public boolean supportsIfExistsAfterConstraintName() {
		return false;
	}

	/**
	 * For an "alter table", can the phrase "if exists" be applied?
	 *
	 * @return {@code true} if the "if exists" can be applied after ALTER TABLE
	 * @since 5.2.11
	 */
	public boolean supportsIfExistsAfterAlterTable() {
		return false;
	}

	/**
	 * Generate a DROP TABLE statement
	 *
	 * @param tableName The name of the table to drop
	 *
	 * @return The DROP TABLE command
	 */
	public String getDropTableString(String tableName) {
		final StringBuilder buf = new StringBuilder( "drop table " );
		if ( supportsIfExistsBeforeTableName() ) {
			buf.append( "if exists " );
		}
		buf.append( tableName ).append( getCascadeConstraintsString() );
		if ( supportsIfExistsAfterTableName() ) {
			buf.append( " if exists" );
		}
		return buf.toString();
	}

	/**
	 * Does this dialect support column-level check constraints?
	 *
	 * @return True if column-level CHECK constraints are supported; false
	 * otherwise.
	 */
	public boolean supportsColumnCheck() {
		return true;
	}

	/**
	 * Does this dialect support table-level check constraints?
	 *
	 * @return True if table-level CHECK constraints are supported; false
	 * otherwise.
	 */
	public boolean supportsTableCheck() {
		return true;
	}

	/**
	 * Does this dialect support cascaded delete on foreign key definitions?
	 *
	 * @return {@code true} indicates that the dialect does support cascaded delete on foreign keys.
	 */
	public boolean supportsCascadeDelete() {
		return true;
	}

	/**
	 * Completely optional cascading drop clause
	 *
	 * @return String
	 */
	public String getCascadeConstraintsString() {
		return "";
	}

	/**
	 * Returns the separator to use for defining cross joins when translating HQL queries.
	 * <p/>
	 * Typically this will be either [<tt> cross join </tt>] or [<tt>, </tt>]
	 * <p/>
	 * Note that the spaces are important!
	 *
	 * @return The cross join separator
	 */
	public String getCrossJoinSeparator() {
		return " cross join ";
	}

	public ColumnAliasExtractor getColumnAliasExtractor() {
		return ColumnAliasExtractor.COLUMN_LABEL_EXTRACTOR;
	}


	// Informational metadata ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Does this dialect support empty IN lists?
	 * <p/>
	 * For example, is [where XYZ in ()] a supported construct?
	 *
	 * @return True if empty in lists are supported; false otherwise.
	 * @since 3.2
	 */
	public boolean supportsEmptyInList() {
		return true;
	}

	/**
	 * Are string comparisons implicitly case insensitive.
	 * <p/>
	 * In other words, does [where 'XYZ' = 'xyz'] resolve to true?
	 *
	 * @return True if comparisons are case insensitive.
	 * @since 3.2
	 */
	public boolean areStringComparisonsCaseInsensitive() {
		return false;
	}

	/**
	 * Is this dialect known to support what ANSI-SQL terms "row value
	 * constructor" syntax; sometimes called tuple syntax.
	 * <p/>
	 * Basically, does it support syntax like
	 * "... where (FIRST_NAME, LAST_NAME) = ('Steve', 'Ebersole') ...".
	 *
	 * @return True if this SQL dialect is known to support "row value
	 * constructor" syntax; false otherwise.
	 * @since 3.2
	 */
	public boolean supportsRowValueConstructorSyntax() {
		// return false here, as most databases do not properly support this construct...
		return false;
	}

	/**
	 * If the dialect supports {@link #supportsRowValueConstructorSyntax() row values},
	 * does it offer such support in IN lists as well?
	 * <p/>
	 * For example, "... where (FIRST_NAME, LAST_NAME) IN ( (?, ?), (?, ?) ) ..."
	 *
	 * @return True if this SQL dialect is known to support "row value
	 * constructor" syntax in the IN list; false otherwise.
	 * @since 3.2
	 */
	public boolean supportsRowValueConstructorSyntaxInInList() {
		return false;
	}

	/**
	 * Should LOBs (both BLOB and CLOB) be bound using stream operations (i.e.
	 * {@link java.sql.PreparedStatement#setBinaryStream}).
	 *
	 * @return True if BLOBs and CLOBs should be bound using stream operations.
	 * @since 3.2
	 */
	public boolean useInputStreamToInsertBlob() {
		return true;
	}

	/**
	 * Does this dialect support parameters within the <tt>SELECT</tt> clause of
	 * <tt>INSERT ... SELECT ...</tt> statements?
	 *
	 * @return True if this is supported; false otherwise.
	 * @since 3.2
	 */
	public boolean supportsParametersInInsertSelect() {
		return true;
	}

	/**
	 * Does this dialect require that references to result variables
	 * (i.e, select expresssion aliases) in an ORDER BY clause be
	 * replaced by column positions (1-origin) as defined
	 * by the select clause?

	 * @return true if result variable references in the ORDER BY
	 *              clause should be replaced by column positions;
	 *         false otherwise.
	 */
	public boolean replaceResultVariableInOrderByClauseWithPosition() {
		return false;
	}

	/**
	 * Renders an ordering fragment
	 *
	 * @param expression The SQL order expression. In case of {@code @OrderBy} annotation user receives property placeholder
	 * (e.g. attribute name enclosed in '{' and '}' signs).
	 * @param collation Collation string in format {@code collate IDENTIFIER}, or {@code null}
	 * if expression has not been explicitly specified.
	 * @param order Order direction. Possible values: {@code asc}, {@code desc}, or {@code null}
	 * if expression has not been explicitly specified.
	 * @param nulls Nulls precedence. Default value: {@link NullPrecedence#NONE}.
	 * @return Renders single element of {@code ORDER BY} clause.
	 */
	public String renderOrderByElement(String expression, String collation, String order, NullPrecedence nulls) {
		final StringBuilder orderByElement = new StringBuilder( expression );
		if ( collation != null ) {
			orderByElement.append( " " ).append( collation );
		}
		if ( order != null ) {
			orderByElement.append( " " ).append( order );
		}
		if ( nulls != NullPrecedence.NONE ) {
			orderByElement.append( " nulls " ).append( nulls.name().toLowerCase( Locale.ROOT ) );
		}
		return orderByElement.toString();
	}

	/**
	 * Does this dialect require that parameters appearing in the <tt>SELECT</tt> clause be wrapped in <tt>cast()</tt>
	 * calls to tell the db parser the expected type.
	 *
	 * @return True if select clause parameter must be cast()ed
	 * @since 3.2
	 */
	public boolean requiresCastingOfParametersInSelectClause() {
		return false;
	}

	/**
	 * Does this dialect support asking the result set its positioning
	 * information on forward only cursors.  Specifically, in the case of
	 * scrolling fetches, Hibernate needs to use
	 * {@link java.sql.ResultSet#isAfterLast} and
	 * {@link java.sql.ResultSet#isBeforeFirst}.  Certain drivers do not
	 * allow access to these methods for forward only cursors.
	 * <p/>
	 * NOTE : this is highly driver dependent!
	 *
	 * @return True if methods like {@link java.sql.ResultSet#isAfterLast} and
	 * {@link java.sql.ResultSet#isBeforeFirst} are supported for forward
	 * only cursors; false otherwise.
	 * @since 3.2
	 */
	public boolean supportsResultSetPositionQueryMethodsOnForwardOnlyCursor() {
		return true;
	}

	/**
	 * Does this dialect support definition of cascade delete constraints
	 * which can cause circular chains?
	 *
	 * @return True if circular cascade delete constraints are supported; false
	 * otherwise.
	 * @since 3.2
	 */
	public boolean supportsCircularCascadeDeleteConstraints() {
		return true;
	}

	/**
	 * Are subselects supported as the left-hand-side (LHS) of
	 * IN-predicates.
	 * <p/>
	 * In other words, is syntax like "... <subquery> IN (1, 2, 3) ..." supported?
	 *
	 * @return True if subselects can appear as the LHS of an in-predicate;
	 * false otherwise.
	 * @since 3.2
	 */
	public boolean  supportsSubselectAsInPredicateLHS() {
		return true;
	}

	/**
	 * Expected LOB usage pattern is such that I can perform an insert
	 * via prepared statement with a parameter binding for a LOB value
	 * without crazy casting to JDBC driver implementation-specific classes...
	 * <p/>
	 * Part of the trickiness here is the fact that this is largely
	 * driver dependent.  For example, Oracle (which is notoriously bad with
	 * LOB support in their drivers historically) actually does a pretty good
	 * job with LOB support as of the 10.2.x versions of their drivers...
	 *
	 * @return True if normal LOB usage patterns can be used with this driver;
	 * false if driver-specific hookiness needs to be applied.
	 * @since 3.2
	 */
	public boolean supportsExpectedLobUsagePattern() {
		return true;
	}

	/**
	 * Does the dialect support propagating changes to LOB
	 * values back to the database?  Talking about mutating the
	 * internal value of the locator as opposed to supplying a new
	 * locator instance...
	 * <p/>
	 * For BLOBs, the internal value might be changed by:
	 * {@link java.sql.Blob#setBinaryStream},
	 * {@link java.sql.Blob#setBytes(long, byte[])},
	 * {@link java.sql.Blob#setBytes(long, byte[], int, int)},
	 * or {@link java.sql.Blob#truncate(long)}.
	 * <p/>
	 * For CLOBs, the internal value might be changed by:
	 * {@link java.sql.Clob#setAsciiStream(long)},
	 * {@link java.sql.Clob#setCharacterStream(long)},
	 * {@link java.sql.Clob#setString(long, String)},
	 * {@link java.sql.Clob#setString(long, String, int, int)},
	 * or {@link java.sql.Clob#truncate(long)}.
	 * <p/>
	 * NOTE : I do not know the correct answer currently for
	 * databases which (1) are not part of the cruise control process
	 * or (2) do not {@link #supportsExpectedLobUsagePattern}.
	 *
	 * @return True if the changes are propagated back to the
	 * database; false otherwise.
	 * @since 3.2
	 */
	public boolean supportsLobValueChangePropogation() {
		// todo : pretty sure this is the same as the java.sql.DatabaseMetaData.locatorsUpdateCopy method added in JDBC 4, see HHH-6046
		return true;
	}

	/**
	 * Is it supported to materialize a LOB locator outside the transaction in
	 * which it was created?
	 * <p/>
	 * Again, part of the trickiness here is the fact that this is largely
	 * driver dependent.
	 * <p/>
	 * NOTE: all database I have tested which {@link #supportsExpectedLobUsagePattern()}
	 * also support the ability to materialize a LOB outside the owning transaction...
	 *
	 * @return True if unbounded materialization is supported; false otherwise.
	 * @since 3.2
	 */
	public boolean supportsUnboundedLobLocatorMaterialization() {
		return true;
	}

	/**
	 * Does this dialect support referencing the table being mutated in
	 * a subquery.  The "table being mutated" is the table referenced in
	 * an UPDATE or a DELETE query.  And so can that table then be
	 * referenced in a subquery of said UPDATE/DELETE query.
	 * <p/>
	 * For example, would the following two syntaxes be supported:<ul>
	 * <li>delete from TABLE_A where ID not in ( select ID from TABLE_A )</li>
	 * <li>update TABLE_A set NON_ID = 'something' where ID in ( select ID from TABLE_A)</li>
	 * </ul>
	 *
	 * @return True if this dialect allows references the mutating table from
	 * a subquery.
	 */
	public boolean supportsSubqueryOnMutatingTable() {
		return true;
	}

	/**
	 * Does the dialect support an exists statement in the select clause?
	 *
	 * @return True if exists checks are allowed in the select clause; false otherwise.
	 */
	public boolean supportsExistsInSelect() {
		return true;
	}

	/**
	 * For the underlying database, is READ_COMMITTED isolation implemented by
	 * forcing readers to wait for write locks to be released?
	 *
	 * @return True if writers block readers to achieve READ_COMMITTED; false otherwise.
	 */
	public boolean doesReadCommittedCauseWritersToBlockReaders() {
		return false;
	}

	/**
	 * For the underlying database, is REPEATABLE_READ isolation implemented by
	 * forcing writers to wait for read locks to be released?
	 *
	 * @return True if readers block writers to achieve REPEATABLE_READ; false otherwise.
	 */
	public boolean doesRepeatableReadCauseReadersToBlockWriters() {
		return false;
	}

	/**
	 * Does this dialect support using a JDBC bind parameter as an argument
	 * to a function or procedure call?
	 *
	 * @return Returns {@code true} if the database supports accepting bind params as args, {@code false} otherwise. The
	 * default is {@code true}.
	 */
	@SuppressWarnings( {"UnusedDeclaration"})
	public boolean supportsBindAsCallableArgument() {
		return true;
	}

	/**
	 * Does this dialect support `count(a,b)`?
	 *
	 * @return True if the database supports counting tuples; false otherwise.
	 */
	public boolean supportsTupleCounts() {
		return false;
	}

	/**
	 * Does this dialect support `count(distinct a,b)`?
	 *
	 * @return True if the database supports counting distinct tuples; false otherwise.
	 */
	public boolean supportsTupleDistinctCounts() {
		// oddly most database in fact seem to, so true is the default.
		return true;
	}

	/**
	 * If {@link #supportsTupleDistinctCounts()} is true, does the Dialect require the tuple to be wrapped with parens?
	 *
	 * @return boolean
	 */
	public boolean requiresParensForTupleDistinctCounts() {
		return false;
	}

	/**
	 * Return the limit that the underlying database places on the number of elements in an {@code IN} predicate.
	 * If the database defines no such limits, simply return zero or less-than-zero.
	 *
	 * @return int The limit, or zero-or-less to indicate no limit.
	 */
	public int getInExpressionCountLimit() {
		return 0;
	}

	/**
	 * HHH-4635
	 * Oracle expects all Lob values to be last in inserts and updates.
	 *
	 * @return boolean True if Lob values should be last, false if it
	 * does not matter.
	 */
	public boolean forceLobAsLastValue() {
		return false;
	}

	/**
	 * Return whether the dialect considers an empty-string value as null.
	 *
	 * @return boolean True if an empty string is treated as null, false othrwise.
	 */
	public boolean isEmptyStringTreatedAsNull() {
		return false;
	}

	/**
	 * Some dialects have trouble applying pessimistic locking depending upon what other query options are
	 * specified (paging, ordering, etc).  This method allows these dialects to request that locking be applied
	 * by subsequent selects.
	 *
	 * @return {@code true} indicates that the dialect requests that locking be applied by subsequent select;
	 * {@code false} (the default) indicates that locking should be applied to the main SQL statement..
	 *
	 * @since 5.2
	 */
	public boolean useFollowOnLocking(String sql, QueryOptions queryOptions) {
		return false;
	}

	/**
	 * Negate an expression
	 *
	 * @param expression The expression to negate
	 *
	 * @return The negated expression
	 */
	public String getNotExpression(String expression) {
		return "not " + expression;
	}

	/**
	 * Get the UniqueDelegate supported by this dialect
	 *
	 * @return The UniqueDelegate
	 */
	public UniqueDelegate getUniqueDelegate() {
		return uniqueDelegate;
	}

	/**
	 * Does this dialect support the <tt>UNIQUE</tt> column syntax?
	 *
	 * @return boolean
	 *
	 * @deprecated {@link #getUniqueDelegate()} should be overridden instead.
	 */
	@Deprecated
	public boolean supportsUnique() {
		return true;
	}

	/**
	 * Does this dialect support adding Unique constraints via create and alter table ?
	 *
	 * @return boolean
	 *
	 * @deprecated {@link #getUniqueDelegate()} should be overridden instead.
	 */
	@Deprecated
	public boolean supportsUniqueConstraintInCreateAlterTable() {
		return true;
	}

	/**
	 * The syntax used to add a unique constraint to a table.
	 *
	 * @param constraintName The name of the unique constraint.
	 * @return The "add unique" fragment
	 *
	 * @deprecated {@link #getUniqueDelegate()} should be overridden instead.
	 */
	@Deprecated
	public String getAddUniqueConstraintString(String constraintName) {
		return " add constraint " + constraintName + " unique ";
	}

	/**
	 * Is the combination of not-null and unique supported?
	 *
	 * @return deprecated
	 *
	 * @deprecated {@link #getUniqueDelegate()} should be overridden instead.
	 */
	@Deprecated
	public boolean supportsNotNullUnique() {
		return true;
	}

	/**
	 * Apply a hint to the query.  The entire query is provided, allowing the Dialect full control over the placement
	 * and syntax of the hint.  By default, ignore the hint and simply return the query.
	 *
	 * @param query The query to which to apply the hint.
	 * @param hintList The  hints to apply
	 * @return The modified SQL
	 */
	public String getQueryHintString(String query, List<String> hintList) {
		final String hints = String.join( ", ", hintList );

		if ( StringHelper.isEmpty( hints ) ) {
			return query;
		}

		return getQueryHintString( query, hints );
	}

	/**
	 * Apply a hint to the query.  The entire query is provided, allowing the Dialect full control over the placement
	 * and syntax of the hint.  By default, ignore the hint and simply return the query.
	 *
	 * @param query The query to which to apply the hint.
	 * @param hints The  hints to apply
	 * @return The modified SQL
	 */
	public String getQueryHintString(String query, String hints) {
		return query;
	}

	/**
	 * Certain dialects support a subset of ScrollModes.  Provide a default to be used by Criteria and Query.
	 *
	 * @return ScrollMode
	 */
	public ScrollMode defaultScrollMode() {
		return ScrollMode.SCROLL_INSENSITIVE;
	}

	/**
	 * Does this dialect support tuples in subqueries?  Ex:
	 * delete from Table1 where (col1, col2) in (select col1, col2 from Table2)
	 *
	 * @return boolean
	 */
	public boolean supportsTuplesInSubqueries() {
		return true;
	}

	public CallableStatementSupport getCallableStatementSupport() {
		// most databases do not support returning cursors (ref_cursor)...
		return StandardCallableStatementSupport.NO_REF_CURSOR_INSTANCE;
	}

	/**
	 * By default interpret this based on DatabaseMetaData.
	 *
	 * @return
	 */
	public NameQualifierSupport getNameQualifierSupport() {
		return null;
	}

	protected final BatchLoadSizingStrategy STANDARD_DEFAULT_BATCH_LOAD_SIZING_STRATEGY = new BatchLoadSizingStrategy() {
		@Override
		public int determineOptimalBatchLoadSize(int numberOfKeyColumns, int numberOfKeys) {
			return 50;
		}
	};

	public BatchLoadSizingStrategy getDefaultBatchLoadSizingStrategy() {
		return STANDARD_DEFAULT_BATCH_LOAD_SIZING_STRATEGY;
	}

	/**
	 * Does the fetching JDBC statement warning for logging is enabled by default
	 *
	 * @return boolean
	 *
	 * @since 5.1
	 */
	public boolean isJdbcLogWarningsEnabledByDefault() {
		return true;
	}

	public void augmentRecognizedTableTypes(List<String> tableTypesList) {
		// noihing to do
	}

	/**
	 * Does the underlying database support partition by
	 *
	 * @return boolean
	 *
	 * @since 5.2
	 */
	public boolean supportsPartitionBy() {
		return false;
	}

	/**
	 * Override the DatabaseMetaData#supportsNamedParameters()
	 *
	 * @return boolean
	 *
	 * @throws SQLException Accessing the DatabaseMetaData can throw it.  Just re-throw and Hibernate will handle.
	 */
	public boolean supportsNamedParameters(DatabaseMetaData databaseMetaData) throws SQLException {
		return databaseMetaData != null && databaseMetaData.supportsNamedParameters();
	}

	/**
	 * Does this dialect supports Nationalized Types
	 *
	 * @return boolean
	 */
	public boolean supportsNationalizedTypes() {
		return true;
	}

	public int getPreferredSqlTypeCodeForBoolean() {
		// BIT is the safest option as most databases do not support a
		// boolean data-type.  And BIT happens to be the JDBC recommended
		// mapping
		return Types.BIT;
	}

	/**
	 * Does this dialect/database support non-query statements (e.g. INSERT, UPDATE, DELETE) with CTE (Common Table Expressions)?
	 *
	 * @return {@code true} if non-query statements are supported with CTE
	 */
	public boolean supportsNonQueryWithCTE() {
		return false;
	}

	/**
	 * Does this dialect/database support VALUES list (e.g. VALUES (1), (2), (3) )
	 *
	 * @return {@code true} if VALUES list are supported
	 */
	public boolean supportsValuesList() {
		return false;
	}

	/**
	 * Does this dialect/database support SKIP_LOCKED timeout.
	 *
	 * @return {@code true} if SKIP_LOCKED is supported
	 */
	public boolean supportsSkipLocked() {
		return false;
	}

	/**
	 * Does this dialect/database support NO_WAIT timeout.
	 *
	 * @return {@code true} if NO_WAIT is supported
	 */
	public boolean supportsNoWait() {
		return false;
	}

	public boolean isLegacyLimitHandlerBehaviorEnabled() {
		return legacyLimitHandlerBehavior;
	}

	/**
	 * Inline String literal.
	 *
	 * @return escaped String
	 */
	public String inlineLiteral(String literal) {
		return String.format( "\'%s\'", escapeLiteral( literal ) );
	}

	/**
	 * Check whether the JDBC {@link java.sql.Connection} supports creating LOBs via {@link Connection#createBlob()},
	 * {@link Connection#createNClob()} or {@link Connection#createClob()}.
	 *
	 * @param databaseMetaData JDBC {@link DatabaseMetaData} which can be used if LOB creation is supported only starting from a given Driver version
	 *
	 * @return {@code true} if LOBs can be created via the JDBC Connection.
	 */
	public boolean supportsJdbcConnectionLobCreation(DatabaseMetaData databaseMetaData) {
		return true;
	}

	/**
	 * Escape String literal.
	 *
	 * @return escaped String
	 */
	protected String escapeLiteral(String literal) {
		return literal.replace("'", "''");
	}

	private void resolveLegacyLimitHandlerBehavior(ServiceRegistry serviceRegistry) {
		// HHH-11194
		// Temporary solution to set whether legacy limit handler behavior should be used.
		final ConfigurationService configurationService = serviceRegistry.getService( ConfigurationService.class );
		legacyLimitHandlerBehavior = configurationService.getSetting(
				AvailableSettings.USE_LEGACY_LIMIT_HANDLERS,
				StandardConverters.BOOLEAN,
				false
		);
	}

	/**
	 * Modify the SQL, adding hints or comments, if necessary
	 */
	public String addSqlHintOrComment(
			String sql,
//			QueryParameters parameters,
			boolean commentsEnabled) {

		// Keep this here, rather than moving to Select.  Some Dialects may need the hint to be appended to the very
		// end or beginning of the finalized SQL statement, so wait until everything is processed.
//		if ( parameters.getQueryHints() != null && parameters.getQueryHints().size() > 0 ) {
//			sql = getQueryHintString( sql, parameters.getQueryHints() );
//		}
//		if ( commentsEnabled && parameters.getComment() != null ){
//			sql = prependComment( sql, parameters.getComment() );
//		}

		return sql;
	}

	protected String prependComment(String sql, String comment) {
		return  "/* " + comment + " */ " + sql;
	}

	public DefaultSizeStrategy getDefaultSizeStrategy(){
		return defaultSizeStrategy;
	}

	public void setDefaultSizeStrategy(DefaultSizeStrategy defaultSizeStrategy){
		this.defaultSizeStrategy = defaultSizeStrategy;
	}

	/**
	 * This is the default precision for a generated
	 * column mapped to a BigInteger or BigDecimal.
	 *
	 * Usually returns the maximum precision of the
	 * database, except when there is no such maximum
	 * precision, or the maximum precision is very high.
	 */
	public int getDefaultDecimalPrecision() {
		//this is the maximum for Oracle, SQL Server,
		//Sybase, and Teradata, so it makes a reasonable
		//default (uses 17 bytes on SQL Server and MySQL)
		return 38;
	}

	public class DefaultSizeStrategyImpl implements DefaultSizeStrategy {
		@Override
		public Size resolveDefaultSize(SqlTypeDescriptor sqlType, JavaTypeDescriptor javaType) {
			final Size.Builder builder = new Size.Builder();
			int jdbcTypeCode = sqlType.getJdbcTypeCode();

			if ( jdbcTypeCode == Types.BIT
					|| jdbcTypeCode == Types.CHAR
					|| jdbcTypeCode == Types.NCHAR
					|| jdbcTypeCode == Types.BINARY
					|| jdbcTypeCode == Types.VARCHAR
					|| jdbcTypeCode == Types.NVARCHAR
					|| jdbcTypeCode == Types.VARBINARY
					|| jdbcTypeCode == Types.LONGVARCHAR
					|| jdbcTypeCode == Types.LONGNVARCHAR
					|| jdbcTypeCode == Types.LONGVARBINARY ) {
				builder.setLength( javaType.getDefaultSqlLength(Dialect.this) );
				return builder.build();
			}
			else if ( jdbcTypeCode == Types.FLOAT
					|| jdbcTypeCode == Types.DOUBLE
					|| jdbcTypeCode == Types.REAL
					|| jdbcTypeCode == Types.TIMESTAMP
					|| jdbcTypeCode == Types.TIMESTAMP_WITH_TIMEZONE ) {
				builder.setPrecision( javaType.getDefaultSqlPrecision(Dialect.this) );
				return builder.build();
			}
			else if ( jdbcTypeCode == Types.NUMERIC
					|| jdbcTypeCode == Types.DECIMAL ) {
				builder.setPrecision( javaType.getDefaultSqlPrecision(Dialect.this) );
				builder.setScale( javaType.getDefaultSqlScale() );
				return builder.build();
			}
			return null;
		}
	}
}
