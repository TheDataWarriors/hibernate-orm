/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.internal.domain;

import java.util.function.BiConsumer;

import org.hibernate.engine.FetchTiming;
import org.hibernate.metamodel.mapping.internal.ToOneAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.results.graph.AssemblerCreationState;
import org.hibernate.sql.results.graph.BiDirectionalFetch;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultAssembler;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.graph.FetchParentAccess;
import org.hibernate.sql.results.graph.Initializer;
import org.hibernate.sql.results.graph.basic.BasicResultAssembler;
import org.hibernate.sql.results.graph.InitializerParent;
import org.hibernate.sql.results.graph.entity.EntityInitializer;
import org.hibernate.sql.results.graph.entity.internal.AbstractNonJoinedEntityFetch;
import org.hibernate.sql.results.graph.entity.internal.EntityDelayedFetchInitializer;
import org.hibernate.sql.results.graph.entity.internal.EntitySelectFetchInitializerBuilder;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesSourceProcessingOptions;
import org.hibernate.sql.results.jdbc.spi.RowProcessingState;
import org.hibernate.type.descriptor.java.JavaType;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * @author Andrea Boriero
 */
public class CircularFetchImpl extends AbstractNonJoinedEntityFetch implements BiDirectionalFetch {
	private final FetchTiming timing;
	private final NavigablePath referencedNavigablePath;

	public CircularFetchImpl(
			ToOneAttributeMapping referencedModelPart,
			FetchTiming timing,
			NavigablePath navigablePath,
			FetchParent fetchParent,
			boolean selectByUniqueKey,
			NavigablePath referencedNavigablePath,
			DomainResult<?> keyResult,
			DomainResultCreationState creationState) {
		super(
				navigablePath,
				referencedModelPart,
				fetchParent,
				keyResult,
				timing == FetchTiming.DELAYED && referencedModelPart.getEntityMappingType()
						.getEntityPersister()
						.isConcreteProxy(),
				selectByUniqueKey,
				creationState
		);
		this.timing = timing;
		this.referencedNavigablePath = referencedNavigablePath;
	}

	@Override
	public NavigablePath getReferencedPath() {
		return referencedNavigablePath;
	}

	@Override
	public FetchTiming getTiming() {
		return timing;
	}

	@Override
	public boolean hasTableGroup() {
		return true;
	}

	@Override
	public DomainResultAssembler<?> createAssembler(
			FetchParentAccess parentAccess,
			AssemblerCreationState creationState) {
		return createAssembler( (InitializerParent) parentAccess, creationState );
	}

	@Override
	public DomainResultAssembler<?> createAssembler(
			InitializerParent parent,
			AssemblerCreationState creationState) {
		return new CircularFetchAssembler(
				getResultJavaType(),
				creationState.resolveInitializer( this, parent, this ).asEntityInitializer()
		);
	}

	@Override
	public EntityInitializer createInitializer(InitializerParent parent, AssemblerCreationState creationState) {
		if ( timing == FetchTiming.IMMEDIATE ) {
			return buildEntitySelectFetchInitializer(
					parent,
					getFetchedMapping(),
					getFetchedMapping().getEntityMappingType().getEntityPersister(),
					getKeyResult(),
					getNavigablePath(),
					isSelectByUniqueKey(),
					creationState
			);
		}
		else {
			return buildEntityDelayedFetchInitializer(
					parent,
					getNavigablePath(),
					getFetchedMapping(),
					isSelectByUniqueKey(),
					getKeyResult().createResultAssembler( parent, creationState ),
					getDiscriminatorFetch() != null
							? (BasicResultAssembler<?>) getDiscriminatorFetch().createResultAssembler( parent, creationState )
							: null
			);
		}
	}

	protected EntityInitializer buildEntitySelectFetchInitializer(
			InitializerParent parent,
			ToOneAttributeMapping fetchable,
			EntityPersister entityPersister,
			DomainResult<?> keyResult,
			NavigablePath navigablePath,
			boolean selectByUniqueKey,
			AssemblerCreationState creationState) {
		return EntitySelectFetchInitializerBuilder.createInitializer(
				parent,
				fetchable,
				entityPersister,
				keyResult,
				navigablePath,
				selectByUniqueKey,
				creationState
		);
	}

	protected EntityInitializer buildEntityDelayedFetchInitializer(
			InitializerParent parent,
			NavigablePath referencedPath,
			ToOneAttributeMapping fetchable,
			boolean selectByUniqueKey,
			DomainResultAssembler<?> resultAssembler,
			BasicResultAssembler<?> discriminatorAssembler) {
		return new EntityDelayedFetchInitializer(
				parent,
				referencedPath,
				fetchable,
				selectByUniqueKey,
				resultAssembler,
				discriminatorAssembler
		);
	}

	private static class CircularFetchAssembler implements DomainResultAssembler<Object> {
		private final EntityInitializer initializer;
		private final JavaType<Object> assembledJavaType;

		public CircularFetchAssembler(JavaType<?> assembledJavaType, EntityInitializer initializer) {
			//noinspection unchecked
			this.assembledJavaType = (JavaType<Object>) assembledJavaType;
			this.initializer = initializer;
		}

		@Override
		public Object assemble(RowProcessingState rowProcessingState, JdbcValuesSourceProcessingOptions options) {
			initializer.resolveInstance();
			return initializer.getInitializedInstance();
		}

		@Override
		public @Nullable Initializer getInitializer() {
			return initializer;
		}

		@Override
		public <X> void forEachResultAssembler(BiConsumer<Initializer, X> consumer, X arg) {
			if ( initializer.isResultInitializer() ) {
				consumer.accept( initializer, arg );
			}
		}

		@Override
		public JavaType<Object> getAssembledJavaType() {
			return assembledJavaType;
		}
	}

}
