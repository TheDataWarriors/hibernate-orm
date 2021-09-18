/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.spatial.dialect.postgis;

import java.util.Map;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.service.ServiceRegistry;

@Deprecated
public class PostgisPG10Dialect  extends PostgreSQLDialect {

	public PostgisPG10Dialect(DialectResolutionInfo resolutionInfo) {
		super( resolutionInfo );
	}

	public PostgisPG10Dialect() {
		super( 100 );
	}

}

