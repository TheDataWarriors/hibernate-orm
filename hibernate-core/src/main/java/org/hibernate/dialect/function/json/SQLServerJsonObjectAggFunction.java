/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.dialect.function.json;

import org.hibernate.QueryException;
import org.hibernate.query.ReturnableType;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.JsonNullBehavior;
import org.hibernate.sql.ast.tree.expression.JsonObjectAggUniqueKeysBehavior;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * SQL Server json_objectagg function.
 */
public class SQLServerJsonObjectAggFunction extends JsonObjectAggFunction {

	public SQLServerJsonObjectAggFunction(TypeConfiguration typeConfiguration) {
		super( ",", false, typeConfiguration );
	}

	@Override
	protected void render(
			SqlAppender sqlAppender,
			JsonObjectAggArguments arguments,
			Predicate filter,
			ReturnableType<?> returnType,
			SqlAstTranslator<?> translator) {
		final boolean caseWrapper = filter != null;
		if ( arguments.uniqueKeysBehavior() == JsonObjectAggUniqueKeysBehavior.WITH ) {
			throw new QueryException( "Can't emulate json_objectagg 'with unique keys' clause." );
		}
		sqlAppender.appendSql( "'{'+string_agg(" );
		renderArgument( sqlAppender, arguments.key(), arguments.nullBehavior(), translator );
		sqlAppender.appendSql( "+':'+" );
		if ( caseWrapper ) {
			if ( arguments.nullBehavior() != JsonNullBehavior.ABSENT ) {
				throw new QueryException( "Can't emulate json_objectagg filter clause when using 'null on null' clause." );
			}
			translator.getCurrentClauseStack().push( Clause.WHERE );
			sqlAppender.appendSql( "case when " );
			filter.accept( translator );
			translator.getCurrentClauseStack().pop();
			sqlAppender.appendSql( " then " );
			renderArgument( sqlAppender, arguments.value(), arguments.nullBehavior(), translator );
			sqlAppender.appendSql( " else null end)" );
		}
		else {
			renderArgument( sqlAppender, arguments.value(), arguments.nullBehavior(), translator );
		}
		sqlAppender.appendSql( ",',')+'}'" );
	}

	@Override
	protected void renderArgument(
			SqlAppender sqlAppender,
			Expression arg,
			JsonNullBehavior nullBehavior,
			SqlAstTranslator<?> translator) {
		// Convert SQL type to JSON type
		if ( nullBehavior != JsonNullBehavior.NULL ) {
			sqlAppender.appendSql( "nullif(" );
		}
		sqlAppender.appendSql( "substring(json_array(" );
		arg.accept( translator );
		sqlAppender.appendSql( " null on null),2,len(json_array(" );
		arg.accept( translator );
		sqlAppender.appendSql( " null on null))-2)" );
		if ( nullBehavior != JsonNullBehavior.NULL ) {
			sqlAppender.appendSql( ",'null')" );
		}
	}
}
