/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.spi;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hibernate.MappingException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.model.IdentifierGeneratorDefinition;
import org.hibernate.boot.model.domain.EntityMappingHierarchy;
import org.hibernate.boot.model.query.spi.NamedHqlQueryDefinition;
import org.hibernate.boot.model.query.spi.NamedNativeQueryDefinition;
import org.hibernate.boot.model.query.spi.NamedQueryDefinition;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.relational.MappedTable;
import org.hibernate.boot.model.resultset.spi.ResultSetMappingDefinition;
import org.hibernate.cfg.annotations.NamedEntityGraphDefinition;
import org.hibernate.cfg.annotations.NamedProcedureCallDefinition;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.envers.boot.spi.AuditMetadataBuilderImplementor;
import org.hibernate.mapping.FetchProfile;
import org.hibernate.mapping.MappedSuperclass;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.query.spi.NamedQueryRepository;
import org.hibernate.query.sqm.produce.function.SqmFunctionTemplate;
import org.hibernate.type.Type;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Convenience base class for custom implementors of {@link MetadataImplementor} using delegation.
 *
 * @author Gunnar Morling
 *
 */
public abstract class AbstractDelegatingMetadata implements MetadataImplementor {

	private final MetadataImplementor delegate;

	public AbstractDelegatingMetadata(MetadataImplementor delegate) {
		this.delegate = delegate;
	}

	protected MetadataImplementor delegate() {
		return delegate;
	}

	@Override
	public SessionFactoryBuilder getSessionFactoryBuilder() {
		return delegate.getSessionFactoryBuilder();
	}

	@Override
	public SessionFactory buildSessionFactory() {
		return delegate.buildSessionFactory();
	}

	@Override
	public UUID getUUID() {
		return delegate.getUUID();
	}

	@Override
	public Database getDatabase() {
		return delegate.getDatabase();
	}

	@Override
	public Collection<PersistentClass> getEntityBindings() {
		return delegate.getEntityBindings();
	}

	@Override
	public PersistentClass getEntityBinding(String entityName) {
		return delegate.getEntityBinding( entityName );
	}

	@Override
	public Collection<org.hibernate.mapping.Collection> getCollectionBindings() {
		return delegate.getCollectionBindings();
	}

	@Override
	public org.hibernate.mapping.Collection getCollectionBinding(String role) {
		return delegate.getCollectionBinding( role );
	}

	@Override
	public Map<String, String> getImports() {
		return delegate.getImports();
	}

	@Override
	public NamedQueryDefinition getNamedQueryDefinition(String name) {
		return delegate.getNamedQueryDefinition( name );
	}

	@Override
	public Collection<NamedQueryDefinition> getNamedQueryDefinitions() {
		return delegate.getNamedQueryDefinitions();
	}

	@Override
	public NamedNativeQueryDefinition getNamedNativeQueryDefinition(String name) {
		return delegate.getNamedNativeQueryDefinition( name );
	}

	@Override
	public Collection<NamedNativeQueryDefinition> getNamedNativeQueryDefinitions() {
		return delegate.getNamedNativeQueryDefinitions();
	}

	@Override
	public Collection<NamedProcedureCallDefinition> getNamedProcedureCallDefinitions() {
		return delegate.getNamedProcedureCallDefinitions();
	}

	@Override
	public ResultSetMappingDefinition getResultSetMapping(String name) {
		return delegate.getResultSetMapping( name );
	}

	@Override
	public Map<String, ResultSetMappingDefinition> getResultSetMappingDefinitions() {
		return delegate.getResultSetMappingDefinitions();
	}

	@Override
	public Map<String, FilterDefinition> getFilterDefinitions() {
		return delegate.getFilterDefinitions();
	}

	@Override
	public FilterDefinition getFilterDefinition(String name) {
		return delegate.getFilterDefinition( name );
	}

	@Override
	public FetchProfile getFetchProfile(String name) {
		return delegate.getFetchProfile( name );
	}

	@Override
	public Collection<FetchProfile> getFetchProfiles() {
		return delegate.getFetchProfiles();
	}

	@Override
	public NamedEntityGraphDefinition getNamedEntityGraph(String name) {
		return delegate.getNamedEntityGraph( name );
	}

	@Override
	public Map<String, NamedEntityGraphDefinition> getNamedEntityGraphs() {
		return delegate.getNamedEntityGraphs();
	}

	@Override
	public IdentifierGeneratorDefinition getIdentifierGenerator(String name) {
		return delegate.getIdentifierGenerator( name );
	}

	@Override
	public Collection<Table> collectTableMappings() {
		return delegate.collectTableMappings();
	}

	@Override
	public Map<String, SqmFunctionTemplate> getSqlFunctionMap() {
		return delegate.getSqlFunctionMap();
	}

	@Override
	public MetadataBuildingOptions getMetadataBuildingOptions() {
		return delegate.getMetadataBuildingOptions();
	}

	/**
	 * Retrieve the {@link Type} resolver associated with this factory.
	 *
	 * @return The type resolver
	 *
	 * @deprecated (since 5.3) No replacement, access to and handling of Types will be much different in 6.0
	 */
	@Deprecated
	@Override
	public TypeConfiguration getTypeConfiguration() {
		return delegate.getTypeConfiguration();
	}

	@Override
	public NamedQueryRepository buildNamedQueryRepository(SessionFactoryImplementor sessionFactory) {
		return delegate.buildNamedQueryRepository( sessionFactory );
	}

	@Override
	public void validate() throws MappingException {
		delegate.validate();
	}

	@Override
	public Set<MappedSuperclass> getMappedSuperclassMappingsCopy() {
		return delegate.getMappedSuperclassMappingsCopy();
	}

	@Override
	public AuditMetadataBuilderImplementor getAuditMetadataBuilder() {
		return delegate.getAuditMetadataBuilder();
	}

	@Override
	public Collection<EntityMappingHierarchy> getEntityHierarchies() {
		return delegate.getEntityHierarchies();
	}

	@Override
	public NamedHqlQueryDefinition getNamedHqlQueryDefinition(String name) {
		return delegate.getNamedHqlQueryDefinition( name );
	}

	@Override
	public Collection<NamedHqlQueryDefinition> getNamedHqlQueryDefinitions() {
		return delegate.getNamedHqlQueryDefinitions();
	}

	@Override
	public Collection<MappedTable> collectMappedTableMappings() {
		return delegate.collectMappedTableMappings();
	}
}
