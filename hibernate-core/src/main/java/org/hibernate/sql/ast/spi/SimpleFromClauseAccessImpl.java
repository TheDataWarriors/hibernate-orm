/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast.spi;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.SqlTreeCreationLogger;
import org.hibernate.sql.ast.tree.from.TableGroup;

import org.jboss.logging.Logger;

/**
 * Simple implementation of FromClauseAccess
 *
 * @author Steve Ebersole
 */
public class SimpleFromClauseAccessImpl implements FromClauseAccess {

	protected final FromClauseAccess parent;
	protected final Map<NavigablePath, TableGroup> tableGroupMap = new HashMap<>();

	public SimpleFromClauseAccessImpl() {
		this( null );
	}

	public SimpleFromClauseAccessImpl(FromClauseAccess parent) {
		this.parent = parent;
	}

	@Override
	public TableGroup findTableGroupOnCurrentFromClause(NavigablePath navigablePath) {
		return tableGroupMap.get( navigablePath );
	}

	@Override
	public TableGroup findTableGroup(NavigablePath navigablePath) {
		final TableGroup tableGroup = tableGroupMap.get( navigablePath );
		if ( tableGroup != null || parent == null ) {
			return tableGroup;
		}
		return parent.findTableGroup( navigablePath );
	}

	@Override
	public void registerTableGroup(NavigablePath navigablePath, TableGroup tableGroup) {
		final Logger logger = SqlTreeCreationLogger.LOGGER;
		final boolean debugEnabled = logger.isDebugEnabled();
		if ( debugEnabled ) {
			logger.debugf(
					"Registration of TableGroup [%s] with identifierForTableGroup [%s] for NavigablePath [%s] ",
					tableGroup,
					tableGroup.getNavigablePath().getIdentifierForTableGroup(),
					navigablePath.getIdentifierForTableGroup()
			);
		}
		final TableGroup previous = tableGroupMap.put( navigablePath, tableGroup );
		if ( debugEnabled && previous != null ) {
			logger.debugf(
					"Registration of TableGroup [%s] for NavigablePath [%s] overrode previous registration : %s",
					tableGroup,
					navigablePath,
					previous
			);
		}
	}
}
