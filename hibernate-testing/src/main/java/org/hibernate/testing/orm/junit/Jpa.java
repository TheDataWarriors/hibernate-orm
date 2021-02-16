/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.testing.orm.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.PersistenceUnitTransactionType;

import org.hibernate.testing.orm.domain.DomainModelDescriptor;
import org.hibernate.testing.orm.domain.StandardDomainModel;
import org.hibernate.testing.orm.jpa.NonStringValueSettingProvider;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;



/**
 * @author Steve Ebersole
 */
@Inherited
@Target( ElementType.TYPE )
@Retention( RetentionPolicy.RUNTIME )

@TestInstance( TestInstance.Lifecycle.PER_CLASS )

@ExtendWith( EntityManagerFactoryExtension.class )
@ExtendWith( EntityManagerFactoryParameterResolver.class )

@ExtendWith( FailureExpectedExtension.class )
public @interface Jpa {

	/**
	 * Used to mimic container integration
	 */
	Setting[] integrationSettings() default {};

	Class<? extends NonStringValueSettingProvider>[] nonStringValueSettingProviders() default {};

	String persistenceUnitName() default "test-pu";

	// todo : multiple persistence units?

	/**
	 * Persistence unit properties
	 */
	Setting[] properties() default {};

	boolean generateStatistics() default false;
	boolean exportSchema() default true;

	PersistenceUnitTransactionType transactionType() default PersistenceUnitTransactionType.RESOURCE_LOCAL;
	SharedCacheMode sharedCacheMode() default SharedCacheMode.UNSPECIFIED;
	ValidationMode validationMode() default ValidationMode.NONE;

	boolean excludeUnlistedClasses() default false;

	StandardDomainModel[] standardModels() default {};
	Class<? extends DomainModelDescriptor>[] modelDescriptorClasses() default {};
	Class[] annotatedClasses() default {};
	String[] annotatedClassNames() default {};
	String[] annotatedPackageNames() default {};
	String[] xmlMappings() default {};
}
