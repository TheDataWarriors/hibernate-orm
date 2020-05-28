/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.graph.embeddable.internal;

import org.hibernate.LockMode;
import org.hibernate.metamodel.mapping.EmbeddableMappingType;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.query.NavigablePath;
import org.hibernate.sql.ast.SqlAstJoinType;
import org.hibernate.sql.ast.spi.FromClauseAccess;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.results.graph.AbstractFetchParent;
import org.hibernate.sql.results.graph.AssemblerCreationState;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultAssembler;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.embeddable.EmbeddableInitializer;
import org.hibernate.sql.results.graph.embeddable.EmbeddableResultGraphNode;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

/**
 * @author Steve Ebersole
 */
public class EmbeddableResultImpl<T> extends AbstractFetchParent implements EmbeddableResultGraphNode, DomainResult<T> {
	private final String resultVariable;

	public EmbeddableResultImpl(
			NavigablePath navigablePath,
			EmbeddableValuedModelPart modelPart,
			String resultVariable,
			DomainResultCreationState creationState) {
		super( modelPart.getEmbeddableTypeDescriptor(), navigablePath );
		this.resultVariable = resultVariable;

		final FromClauseAccess fromClauseAccess = creationState.getSqlAstCreationState().getFromClauseAccess();

		fromClauseAccess.resolveTableGroup(
				navigablePath,
				np -> {
					final EmbeddableValuedModelPart embeddedValueMapping = modelPart.getEmbeddableTypeDescriptor().getEmbeddedValueMapping();
					final TableGroupJoin tableGroupJoin = embeddedValueMapping.createTableGroupJoin(
							navigablePath,
							fromClauseAccess.findTableGroup( navigablePath.getParent() ),
							resultVariable,
							SqlAstJoinType.INNER,
							LockMode.NONE,
							creationState.getSqlAstCreationState()
					);

					return tableGroupJoin.getJoinedGroup();
				}
		);

		afterInitialize( creationState );
	}

	@Override
	public String getResultVariable() {
		return resultVariable;
	}

	@Override
	public EmbeddableMappingType getFetchContainer() {
		return (EmbeddableMappingType) super.getFetchContainer();
	}

	@Override
	public JavaTypeDescriptor getResultJavaTypeDescriptor() {
		return getReferencedMappingType().getJavaTypeDescriptor();
	}

	@Override
	public EmbeddableMappingType getReferencedMappingType() {
		return getFetchContainer();
	}

	@Override
	public EmbeddableValuedModelPart getReferencedMappingContainer() {
		return getFetchContainer().getEmbeddedValueMapping();
	}

	@Override
	public DomainResultAssembler<T> createResultAssembler(AssemblerCreationState creationState) {
		final EmbeddableInitializer initializer = (EmbeddableInitializer) creationState.resolveInitializer(
				getNavigablePath(),
				() -> new EmbeddableResultInitializer(
						this,
						creationState
				)
		);

		//noinspection unchecked
		return new EmbeddableAssembler( initializer );
	}
}
