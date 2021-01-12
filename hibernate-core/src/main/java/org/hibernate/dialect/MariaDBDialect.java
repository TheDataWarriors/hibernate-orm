/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import org.hibernate.dialect.sequence.MariaDBSequenceSupport;
import org.hibernate.dialect.sequence.SequenceSupport;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.tool.schema.extract.internal.SequenceInformationExtractorMariaDBDatabaseImpl;
import org.hibernate.tool.schema.extract.spi.SequenceInformationExtractor;
import org.hibernate.type.StandardBasicTypes;

/**
 * SQL dialect for MariaDB
 *
 * @author Vlad Mihalcea
 * @author Gavin King
 */
public class MariaDBDialect extends MySQLDialect {

	private final int version;

	public MariaDBDialect() {
		this(500);
	}

	public MariaDBDialect(DialectResolutionInfo info) {
		this( info.getDatabaseMajorVersion() * 100 + info.getDatabaseMinorVersion() * 10 );
	}

	public MariaDBDialect(int version) {
		super( version < 530 ? 500 : 570 );
		this.version = version;
	}

	@Override
	public int getVersion() {
		return version;
	}

	@Override
	public void initializeFunctionRegistry(QueryEngine queryEngine) {
		super.initializeFunctionRegistry(queryEngine);

		if ( getVersion() >= 1020 ) {
			queryEngine.getSqmFunctionRegistry().registerNamed("json_valid", StandardBasicTypes.NUMERIC_BOOLEAN);
		}
	}

	@Override
	public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
		return new StandardSqlAstTranslatorFactory() {
			@Override
			protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
					SessionFactoryImplementor sessionFactory, Statement statement) {
				return new MariaDBSqlAstTranslator<>( sessionFactory, statement );
			}
		};
	}

	public boolean supportsRowValueConstructorSyntaxInInList() {
		return true;
	}

	@Override
	public boolean supportsColumnCheck() {
		return getVersion() >= 1020;
	}

	@Override
	protected MySQLStorageEngine getDefaultMySQLStorageEngine() {
		return InnoDBStorageEngine.INSTANCE;
	}

	@Override
	public boolean supportsIfExistsBeforeConstraintName() {
		return getVersion() >= 1000;
	}

	@Override
	public SequenceSupport getSequenceSupport() {
		return getVersion() < 1030
				? super.getSequenceSupport()
				: MariaDBSequenceSupport.INSTANCE;
	}

	@Override
	public String getQuerySequencesString() {
		return getSequenceSupport().supportsSequences()
				? "select table_name from information_schema.TABLES where table_schema = database() and table_type = 'SEQUENCE'"
				: super.getQuerySequencesString(); //fancy way to write "null"
	}

	@Override
	public SequenceInformationExtractor getSequenceInformationExtractor() {
		return getSequenceSupport().supportsSequences()
				? SequenceInformationExtractorMariaDBDatabaseImpl.INSTANCE
				: super.getSequenceInformationExtractor();
	}

	@Override
	public boolean supportsSkipLocked() {
		//only supported on MySQL
		return false;
	}

	@Override
	public boolean supportsNoWait() {
		return getVersion() >= 1030;
	}

	@Override
	public boolean supportsWait() {
		return getVersion() >= 1030;
	}

	@Override
	public GroupBySummarizationRenderingStrategy getGroupBySummarizationRenderingStrategy() {
		return GroupBySummarizationRenderingStrategy.CLAUSE;
	}

	@Override
	boolean supportsForShare() {
		//only supported on MySQL
		return false;
	}

	@Override
	boolean supportsAliasLocks() {
		//only supported on MySQL
		return false;
	}

}
