/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.spatial.dialect.postgis;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.spatial.GeolatteGeometryJavaTypeDescriptor;
import org.hibernate.spatial.GeolatteGeometryType;
import org.hibernate.spatial.HSMessageLogger;
import org.hibernate.spatial.JTSGeometryJavaTypeDescriptor;
import org.hibernate.spatial.JTSGeometryType;
import org.hibernate.spatial.contributor.ContributorImplementor;

public class PostgisDialectContributor implements ContributorImplementor {

	private final ServiceRegistry serviceRegistryegistry;

	public PostgisDialectContributor(ServiceRegistry serviceRegistry) {
		this.serviceRegistryegistry = serviceRegistry;
	}

	public void contributeTypes(TypeContributions typeContributions) {
		HSMessageLogger.LOGGER.typeContributions( this.getClass().getCanonicalName() );
		typeContributions.contributeType( new GeolatteGeometryType( PGGeometryTypeDescriptor.INSTANCE_WKB_1 ) );
		typeContributions.contributeType( new JTSGeometryType( PGGeometryTypeDescriptor.INSTANCE_WKB_1 ) );

		//Isn't this redundant?
		typeContributions.contributeJavaTypeDescriptor( GeolatteGeometryJavaTypeDescriptor.INSTANCE );
		typeContributions.contributeJavaTypeDescriptor( JTSGeometryJavaTypeDescriptor.INSTANCE );
	}

	@Override
	public void contributeFunctions(SqmFunctionRegistry functionRegistry) {
		HSMessageLogger.LOGGER.functionContributions( this.getClass().getCanonicalName() );
		PostgisSqmFunctionDescriptors postgisFunctions = new PostgisSqmFunctionDescriptors( getServiceRegistry() );

		postgisFunctions.asMap().forEach( (key, desc) -> {
			functionRegistry.register( key.getName(), desc );
			key.getAltName().ifPresent( altName -> functionRegistry.register( altName, desc ) );
		} );
	}


	@Override
	public ServiceRegistry getServiceRegistry() {
		return this.serviceRegistryegistry;
	}
}
