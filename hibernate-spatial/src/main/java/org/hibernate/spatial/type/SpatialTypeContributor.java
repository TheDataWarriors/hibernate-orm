/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.spatial.type;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.env.internal.JdbcEnvironmentInitiator;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.spatial.GeolatteGeometryJavaTypeDescriptor;
import org.hibernate.spatial.GeolatteGeometryType;
import org.hibernate.spatial.JTSGeometryJavaTypeDescriptor;
import org.hibernate.spatial.JTSGeometryType;
import org.hibernate.spatial.dialect.postgis.PGGeometryTypeDescriptor;

public class SpatialTypeContributor implements TypeContributor {
	@Override
	public void contribute(
			TypeContributions typeContributions, ServiceRegistry serviceRegistry) {

		typeContributions.contributeType( new GeolatteGeometryType( PGGeometryTypeDescriptor.INSTANCE_WKB_1 ) );
		typeContributions.contributeType( new JTSGeometryType( PGGeometryTypeDescriptor.INSTANCE_WKB_1 ) );

		typeContributions.contributeJavaTypeDescriptor( GeolatteGeometryJavaTypeDescriptor.INSTANCE );
		typeContributions.contributeJavaTypeDescriptor( JTSGeometryJavaTypeDescriptor.INSTANCE );
	}
}
