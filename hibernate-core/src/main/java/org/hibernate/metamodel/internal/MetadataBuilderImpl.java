/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.internal;

import javax.persistence.SharedCacheMode;

import org.jboss.jandex.IndexView;
import org.xml.sax.EntityResolver;

import org.hibernate.MultiTenancyStrategy;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.EJB3DTDEntityResolver;
import org.hibernate.cfg.EJB3NamingStrategy;
import org.hibernate.cfg.NamingStrategy;
import org.hibernate.metamodel.Metadata;
import org.hibernate.metamodel.MetadataBuilder;
import org.hibernate.metamodel.MetadataSourceProcessingOrder;
import org.hibernate.metamodel.MetadataSources;
import org.hibernate.metamodel.spi.MetadataSourcesContributor;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.classloading.spi.ClassLoaderService;
import org.hibernate.service.config.spi.ConfigurationService;
import org.hibernate.service.config.spi.StandardConverters;

/**
 * @author Steve Ebersole
 */
public class MetadataBuilderImpl implements MetadataBuilder {
	private final MetadataSources sources;
	private final OptionsImpl options;

	public MetadataBuilderImpl(MetadataSources sources) {
		this.sources = sources;
		for ( MetadataSourcesContributor contributor :
				sources.getServiceRegistry().getService( ClassLoaderService.class )
						.loadJavaServices( MetadataSourcesContributor.class ) ) {
			contributor.contribute( sources, null );
		}
		this.options = new OptionsImpl( sources.getServiceRegistry() );
	}

	@Override
	public MetadataBuilder with(NamingStrategy namingStrategy) {
		this.options.namingStrategy = namingStrategy;
		return this;
	}

	@Override
	public MetadataBuilder with(EntityResolver entityResolver) {
		this.options.entityResolver = entityResolver;
		return this;
	}

	@Override
	public MetadataBuilder with(MetadataSourceProcessingOrder metadataSourceProcessingOrder) {
		this.options.metadataSourceProcessingOrder = metadataSourceProcessingOrder;
		return this;
	}

	@Override
	public MetadataBuilder with(SharedCacheMode sharedCacheMode) {
		this.options.sharedCacheMode = sharedCacheMode;
		return this;
	}

	@Override
	public MetadataBuilder with(AccessType accessType) {
		this.options.defaultCacheAccessType = accessType;
		return this;
	}

	@Override
	public MetadataBuilder with(IndexView jandexView) {
		this.options.jandexView = jandexView;
		return this;
	}

	@Override
	public MetadataBuilder withNewIdentifierGeneratorsEnabled(boolean enabled) {
		this.options.useNewIdentifierGenerators = enabled;
		return this;
	}

	@Override
	public Metadata buildMetadata() {
		return new MetadataImpl( sources, options );
	}

	public static class OptionsImpl implements Metadata.Options {
		private MetadataSourceProcessingOrder metadataSourceProcessingOrder = MetadataSourceProcessingOrder.HBM_FIRST;
		private NamingStrategy namingStrategy = EJB3NamingStrategy.INSTANCE;
		// todo : entity-resolver maybe needed for ServiceRegistry building also
		// 		maybe move there and default to looking up that value somehow?
		private EntityResolver entityResolver = EJB3DTDEntityResolver.INSTANCE;
		private SharedCacheMode sharedCacheMode = SharedCacheMode.ENABLE_SELECTIVE;
		private AccessType defaultCacheAccessType;
        private boolean useNewIdentifierGenerators;
        private boolean globallyQuotedIdentifiers;
		private String defaultSchemaName;
		private String defaultCatalogName;
		private MultiTenancyStrategy multiTenancyStrategy;
		public IndexView jandexView;

		public OptionsImpl(ServiceRegistry serviceRegistry) {
			ConfigurationService configService = serviceRegistry.getService( ConfigurationService.class );

			// cache access type
			defaultCacheAccessType = configService.getSetting(
					AvailableSettings.DEFAULT_CACHE_CONCURRENCY_STRATEGY,
					new ConfigurationService.Converter<AccessType>() {
						@Override
						public AccessType convert(Object value) {
							return AccessType.fromExternalName( value.toString() );
						}
					}
			);

			useNewIdentifierGenerators = configService.getSetting(
					AvailableSettings.USE_NEW_ID_GENERATOR_MAPPINGS,
					StandardConverters.BOOLEAN,
					false
			);

			defaultSchemaName = configService.getSetting(
					AvailableSettings.DEFAULT_SCHEMA,
					StandardConverters.STRING,
					null
			);

			defaultCatalogName = configService.getSetting(
					AvailableSettings.DEFAULT_CATALOG,
					StandardConverters.STRING,
					null
			);

            globallyQuotedIdentifiers = configService.getSetting(
                    AvailableSettings.GLOBALLY_QUOTED_IDENTIFIERS,
					StandardConverters.BOOLEAN,
                    false
            );

			multiTenancyStrategy =  MultiTenancyStrategy.determineMultiTenancyStrategy( configService.getSettings() );
		}


		@Override
		public MetadataSourceProcessingOrder getMetadataSourceProcessingOrder() {
			return metadataSourceProcessingOrder;
		}

		@Override
		public NamingStrategy getNamingStrategy() {
			return namingStrategy;
		}

		@Override
		public EntityResolver getEntityResolver() {
			return entityResolver;
		}

		@Override
		public AccessType getDefaultAccessType() {
			return defaultCacheAccessType;
		}

		@Override
		public SharedCacheMode getSharedCacheMode() {
			return sharedCacheMode;
		}

		@Override
        public boolean useNewIdentifierGenerators() {
            return useNewIdentifierGenerators;
        }

        @Override
        public boolean isGloballyQuotedIdentifiers() {
            return globallyQuotedIdentifiers;
        }

        @Override
		public String getDefaultSchemaName() {
			return defaultSchemaName;
		}

		@Override
		public String getDefaultCatalogName() {
			return defaultCatalogName;
		}

		@Override
		public MultiTenancyStrategy getMultiTenancyStrategy() {
			return multiTenancyStrategy;
		}

		@Override
		public IndexView getJandexView() {
			return jandexView;
		}
	}
}
