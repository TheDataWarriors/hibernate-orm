/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.spi;

import java.util.Collections;

import org.hibernate.NotYetImplementedFor6Exception;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.SqlAstSelectTranslator;
import org.hibernate.sql.ast.tree.cte.CteColumn;
import org.hibernate.sql.ast.tree.cte.CteStatement;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.JdbcSelect;
import org.hibernate.sql.results.jdbc.internal.JdbcValuesMappingProducerStandard;

import static org.hibernate.sql.ast.SqlTreePrinter.logSqlAst;
import static org.hibernate.sql.results.graph.DomainResultGraphPrinter.logDomainResultGraph;

/**
 * The final phase of query translation.  Here we take the SQL-AST an
 * "interpretation".  For a select query, that means an instance of
 * {@link JdbcSelect}.
 *
 * @author Steve Ebersole
 */
public class StandardSqlAstSelectTranslator
		extends AbstractSqlAstTranslator
		implements SqlAstSelectTranslator {

	public StandardSqlAstSelectTranslator(SessionFactoryImplementor sessionFactory) {
		super( sessionFactory );
	}

	@Override
	public JdbcSelect translate(CteStatement sqlAst) {
		assert sqlAst.getCteConsumer() instanceof QuerySpec;

		appendSql( "with " );
		appendSql( sqlAst.getCteLabel() );

		appendSql( " (" );

		String separator = "";

		for ( CteColumn cteColumn : sqlAst.getCteTable().getCteColumns() ) {
			appendSql( separator );
			appendSql( cteColumn.getColumnExpression() );
			separator = ", ";
		}

		appendSql( ") as (" );

		visitQuerySpec( sqlAst.getCteDefinition() );

		appendSql( ") " );

		translate( (QuerySpec) sqlAst.getCteConsumer() );

		throw new NotYetImplementedFor6Exception( getClass() );
	}

	@Override
	public JdbcSelect translate(QuerySpec querySpec) {
		try {
			visitQuerySpec( querySpec );

			return new JdbcSelect(
					getSql(),
					getParameterBinders(),
					new JdbcValuesMappingProducerStandard(
							querySpec.getSelectClause().getSqlSelections(),
							Collections.emptyList()
					),
					getAffectedTableNames(),
					getFilterJdbcParameters()
			);
		}
		finally {
			cleanup();
		}
	}

	@Override
	public JdbcSelect translate(SelectStatement sqlAstSelect) {
		try {
			logDomainResultGraph( sqlAstSelect.getDomainResultDescriptors() );
			logSqlAst( sqlAstSelect );

			visitQuerySpec( sqlAstSelect.getQuerySpec() );

			return new JdbcSelect(
					getSql(),
					getParameterBinders(),
					new JdbcValuesMappingProducerStandard(
							sqlAstSelect.getQuerySpec().getSelectClause().getSqlSelections(),
							sqlAstSelect.getDomainResultDescriptors()
					),
					getAffectedTableNames(),
					getFilterJdbcParameters()
			);
		}
		finally {
			cleanup();
		}
	}

}
