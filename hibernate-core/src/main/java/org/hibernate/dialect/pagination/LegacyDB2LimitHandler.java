/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect.pagination;

import org.hibernate.engine.spi.RowSelection;
import org.hibernate.query.Limit;

/**
 * A {@link LimitHandler} for DB2. Uses {@code FETCH FIRST n ROWS ONLY},
 * together with {@code ROWNUMBER()} when there is an offset. (DB2 does
 * not support the ANSI syntax {@code OFFSET n ROWS}.)
 */
public class LegacyDB2LimitHandler extends AbstractLimitHandler {

	public static final LegacyDB2LimitHandler INSTANCE = new LegacyDB2LimitHandler();

	@Override
	public String processSql(String sql, RowSelection selection) {
		if ( hasFirstRow( selection ) ) {
			//nest the main query in an outer select
			return "select * from ( select row_.*, rownumber() over(order by order of row_) as rownumber_ from ( "
					+ sql + fetchFirstRows( selection )
					+ " ) as row_ ) as query_ where rownumber_ > "
					+ selection.getFirstRow()
					+ " order by rownumber_";
		}
		else {
			//on DB2, offset/fetch comes after all the
			//various "for update"ish clauses
			return insertAtEnd( fetchFirstRows( selection ), sql );
		}
	}

	private String fetchFirstRows(RowSelection limit) {
		return " fetch first " + getMaxOrLimit( limit ) + " rows only";
	}

	@Override
	public String processSql(String sql, Limit limit) {
		if ( hasFirstRow( limit ) ) {
			//nest the main query in an outer select
			return "select * from ( select row_.*, rownumber() over(order by order of row_) as rownumber_ from ( "
					+ sql + fetchFirstRows( limit )
					+ " ) as row_ ) as query_ where rownumber_ > "
					+ limit.getFirstRow()
					+ " order by rownumber_";
		}
		else {
			//on DB2, offset/fetch comes after all the
			//various "for update"ish clauses
			return insertAtEnd( fetchFirstRows( limit ), sql );
		}
	}

	private String fetchFirstRows(Limit limit) {
		return " fetch first " + getMaxOrLimit( limit ) + " rows only";
	}

	@Override
	public final boolean supportsLimit() {
		return true;
	}

	@Override
	public final boolean useMaxForLimit() {
		return true;
	}

	@Override
	public final boolean supportsVariableLimit() {
		return false;
	}
}
