/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping.internal;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.loader.ast.internal.MultiNaturalIdLoaderStandard;
import org.hibernate.loader.ast.internal.SimpleNaturalIdLoader;
import org.hibernate.loader.ast.spi.MultiNaturalIdLoader;
import org.hibernate.loader.ast.spi.NaturalIdLoader;
import org.hibernate.mapping.IndexedConsumer;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.MappingType;
import org.hibernate.metamodel.mapping.SelectionConsumer;
import org.hibernate.metamodel.mapping.SingularAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.NavigablePath;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

/**
 * Single-attribute NaturalIdMapping implementation
 */
public class SimpleNaturalIdMapping extends AbstractNaturalIdMapping {
	private final SingularAttributeMapping attribute;

	public SimpleNaturalIdMapping(
			SingularAttributeMapping attribute,
			EntityMappingType declaringType,
			MappingModelCreationProcess creationProcess) {
		super(
				declaringType,
				attribute.getAttributeMetadataAccess().resolveAttributeMetadata( declaringType ).isUpdatable()
		);
		this.attribute = attribute;
	}

	@Override
	public void verifyFlushState(Object id, Object[] currentState, Object[] loadedState, SharedSessionContractImplementor session) {
		if ( isMutable() ) {
			// EARLY EXIT!!!
			// the natural id is mutable (!immutable), no need to do the checks
			return;
		}

		final PersistenceContext persistenceContext = session.getPersistenceContextInternal();
		final EntityPersister persister = getDeclaringType().getEntityPersister();

		final Object naturalId = extractNaturalIdValues( currentState, session );
		final Object snapshot = loadedState == null
				? persistenceContext.getNaturalIdSnapshot( id, persister )
				: persister.getNaturalIdMapping().extractNaturalIdValues( loadedState, session );

		if ( ! areEqual( naturalId, snapshot, session ) ) {
			throw new HibernateException(
					String.format(
							"An immutable natural identifier of entity %s was altered from `%s` to `%s`",
							persister.getEntityName(),
							snapshot,
							naturalId
					)
			);
		}
	}

	@Override
	public Object extractNaturalIdValues(Object[] state, SharedSessionContractImplementor session) {
		return state[ attribute.getStateArrayPosition() ];
	}

	@Override
	public Object extractNaturalIdValues(Object entity, SharedSessionContractImplementor session) {
		return attribute.getPropertyAccess().getGetter().get( entity );
	}

	@Override
	public void validateInternalForm(Object naturalIdValue, SharedSessionContractImplementor session) {
		if ( naturalIdValue == null ) {
			return;
		}

		if ( naturalIdValue.getClass().isArray() ) {
			// be flexible
			final Object[] values = (Object[]) naturalIdValue;
			if ( values.length == 1 ) {
				naturalIdValue = values[0];
			}
		}

		if ( ! getJavaTypeDescriptor().getJavaTypeClass().isInstance( naturalIdValue ) ) {
			throw new IllegalArgumentException(
					"Incoming natural-id value [" + naturalIdValue + "] is not of expected type ["
							+ getJavaTypeDescriptor().getJavaType().getTypeName() + "]"
			);
		}
	}

	@Override
	public int calculateHashCode(Object value, SharedSessionContractImplementor session) {
		return 0;
	}

	@Override
	public Object normalizeInput(Object incoming, SharedSessionContractImplementor session) {
		return normalizeIncomingValue( incoming );
	}

	@SuppressWarnings( "rawtypes" )
	public Object normalizeIncomingValue(Object naturalIdToLoad) {
		if ( naturalIdToLoad instanceof Map ) {
			final Map valueMap = (Map) naturalIdToLoad;
			assert valueMap.size() == 1;
			assert valueMap.containsKey( getAttribute().getAttributeName() );
			return valueMap.get( getAttribute().getAttributeName() );
		}

		if ( naturalIdToLoad instanceof Object[] ) {
			final Object[] values = (Object[]) naturalIdToLoad;
			assert values.length == 1;
			return values[0];
		}

		return naturalIdToLoad;
	}

	public SingularAttributeMapping getAttribute() {
		return attribute;
	}

	@Override
	public List<SingularAttributeMapping> getNaturalIdAttributes() {
		return Collections.singletonList( attribute );
	}

	@Override
	public MappingType getPartMappingType() {
		return attribute.getPartMappingType();
	}

	@Override
	public JavaTypeDescriptor getJavaTypeDescriptor() {
		return attribute.getJavaTypeDescriptor();
	}

	@Override
	public <T> DomainResult<T> createDomainResult(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			String resultVariable,
			DomainResultCreationState creationState) {
		return attribute.createDomainResult( navigablePath, tableGroup, resultVariable, creationState );
	}

	@Override
	public void applySqlSelections(NavigablePath navigablePath, TableGroup tableGroup, DomainResultCreationState creationState) {
		attribute.applySqlSelections( navigablePath, tableGroup, creationState );
	}

	@Override
	public void applySqlSelections(
			NavigablePath navigablePath,
			TableGroup tableGroup,
			DomainResultCreationState creationState,
			BiConsumer<SqlSelection, JdbcMapping> selectionConsumer) {
		attribute.applySqlSelections( navigablePath, tableGroup, creationState, selectionConsumer );
	}

	@Override
	public int forEachSelection(int offset, SelectionConsumer consumer) {
		return attribute.forEachSelection( offset, consumer );
	}

	@Override
	public int getJdbcTypeCount() {
		return attribute.getJdbcTypeCount();
	}

	@Override
	public List<JdbcMapping> getJdbcMappings() {
		return attribute.getJdbcMappings();
	}

	@Override
	public int forEachJdbcType(int offset, IndexedConsumer<JdbcMapping> action) {
		return attribute.forEachJdbcType( offset, action );
	}

	@Override
	public Object disassemble(Object value, SharedSessionContractImplementor session) {
		return attribute.disassemble( value, session );
	}

	@Override
	public void breakDownJdbcValues(Object domainValue, JdbcValueConsumer valueConsumer, SharedSessionContractImplementor session) {
		attribute.breakDownJdbcValues( domainValue, valueConsumer, session );
	}

	@Override
	public int forEachDisassembledJdbcValue(
			Object value,
			Clause clause,
			int offset,
			JdbcValuesConsumer valuesConsumer,
			SharedSessionContractImplementor session) {
		return attribute.forEachDisassembledJdbcValue( value, clause, offset, valuesConsumer, session );
	}

	@Override
	public int forEachJdbcValue(
			Object value,
			Clause clause,
			int offset,
			JdbcValuesConsumer valuesConsumer,
			SharedSessionContractImplementor session) {
		return attribute.forEachJdbcValue( value, clause, offset, valuesConsumer, session );
	}

	@Override
	public NaturalIdLoader<?> makeLoader(EntityMappingType entityDescriptor) {
		return new SimpleNaturalIdLoader<>( this, entityDescriptor );
	}

	@Override
	public MultiNaturalIdLoader<?> makeMultiLoader(EntityMappingType entityDescriptor) {
		return new MultiNaturalIdLoaderStandard<>( entityDescriptor );
	}
}
