/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.id;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.ObjectNameNormalizer;
import org.hibernate.boot.model.relational.QualifiedTableName;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.engine.jdbc.env.spi.IdentifierHelper;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.factory.spi.StandardGenerator;
import org.hibernate.internal.CoreLogging;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.mapping.Table;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

/**
 * <b>increment</b>
 * <p>
 * An {@code IdentifierGenerator} that returns a {@code long}, constructed by
 * counting from the maximum primary key value at startup. Not safe for use in a
 * cluster!
 * <p>
 * Mapping parameters supported, but not usually needed: tables, column.
 * (The tables parameter specified a comma-separated list of table names.)
 *
 * @author Gavin King
 * @author Steve Ebersole
 * @author Brett Meyer
 */
public class IncrementGenerator implements StandardGenerator {
	private static final CoreMessageLogger LOG = CoreLogging.messageLogger( IncrementGenerator.class );

	private Class returnClass;
	private String column;
	private List<QualifiedTableName> physicalTableNames;
	private String sql;

	private IntegralDataTypeHolder previousValueHolder;

	/**
	 * @deprecated Exposed for tests only.
	 */
	@Deprecated
	public String[] getAllSqlForTests() {
		return new String[] { sql };
	}

	@Override
	public synchronized Object generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
		if ( sql != null ) {
			initializePreviousValueHolder( session );
		}
		return previousValueHolder.makeValueThenIncrement();
	}

	@Override
	public void configure(Type type, Properties parameters, ServiceRegistry serviceRegistry) throws MappingException {
		returnClass = type.getReturnedClass();

		final JdbcEnvironment jdbcEnvironment = serviceRegistry.getService( JdbcEnvironment.class );
		final ObjectNameNormalizer normalizer =
				(ObjectNameNormalizer) parameters.get( PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER );

		column = parameters.getProperty( "column" );
		if ( column == null ) {
			column = parameters.getProperty( PersistentIdentifierGenerator.PK );
		}
		column = normalizer.normalizeIdentifierQuoting( column ).render( jdbcEnvironment.getDialect() );

		IdentifierHelper identifierHelper = jdbcEnvironment.getIdentifierHelper();

		final String schema = normalizer.toDatabaseIdentifierText(
				parameters.getProperty( PersistentIdentifierGenerator.SCHEMA )
		);
		final String catalog = normalizer.toDatabaseIdentifierText(
				parameters.getProperty( PersistentIdentifierGenerator.CATALOG )
		);

		String tableList = parameters.getProperty( "tables" );
		if ( tableList == null ) {
			tableList = parameters.getProperty( PersistentIdentifierGenerator.TABLES );
		}
		physicalTableNames = new ArrayList<>();
		for ( String tableName : StringHelper.split( ", ", tableList ) ) {
			physicalTableNames.add( new QualifiedTableName( identifierHelper.toIdentifier( catalog ),
					identifierHelper.toIdentifier( schema ), identifierHelper.toIdentifier( tableName ) ) );
		}
	}

	@Override
	public void initialize(SqlStringGenerationContext context) {
		StringBuilder buf = new StringBuilder();
		for ( int i = 0; i < physicalTableNames.size(); i++ ) {
			final String tableName = context.format( physicalTableNames.get( i ) );
			if ( physicalTableNames.size() > 1 ) {
				buf.append( "select max(" ).append( column ).append( ") as mx from " );
			}
			buf.append( tableName );
			if ( i < physicalTableNames.size() - 1 ) {
				buf.append( " union " );
			}
		}
		String maxColumn;
		if ( physicalTableNames.size() > 1 ) {
			buf.insert( 0, "( " ).append( " ) ids_" );
			maxColumn = "ids_.mx";
		}
		else {
			maxColumn = column;
		}

		sql = "select max(" + maxColumn + ") from " + buf.toString();
	}

	private void initializePreviousValueHolder(SharedSessionContractImplementor session) {
		previousValueHolder = IdentifierGeneratorHelper.getIntegralDataTypeHolder( returnClass );

		if ( LOG.isDebugEnabled() ) {
			LOG.debugf( "Fetching initial value: %s", sql );
		}
		try {
			PreparedStatement st = session.getJdbcCoordinator().getStatementPreparer().prepareStatement( sql );
			try {
				ResultSet rs = session.getJdbcCoordinator().getResultSetReturn().extract( st );
				try {
					if ( rs.next() ) {
						previousValueHolder.initialize( rs, 0L ).increment();
					}
					else {
						previousValueHolder.initialize( 1L );
					}
					sql = null;
					if ( LOG.isDebugEnabled() ) {
						LOG.debugf( "First free id: %s", previousValueHolder.makeValue() );
					}
				}
				finally {
					session.getJdbcCoordinator().getLogicalConnection().getResourceRegistry().release( rs, st );
				}
			}
			finally {
				session.getJdbcCoordinator().getLogicalConnection().getResourceRegistry().release( st );
				session.getJdbcCoordinator().afterStatementExecution();
			}
		}
		catch (SQLException sqle) {
			throw session.getJdbcServices().getSqlExceptionHelper().convert(
					sqle,
					"could not fetch initial value for increment generator",
					sql
			);
		}
	}
}
