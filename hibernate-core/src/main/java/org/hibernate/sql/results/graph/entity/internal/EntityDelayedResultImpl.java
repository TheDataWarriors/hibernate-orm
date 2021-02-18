/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.graph.entity.internal;

import org.hibernate.metamodel.mapping.EntityAssociationMapping;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.EntityValuedModelPart;
import org.hibernate.query.EntityIdentifierNavigablePath;
import org.hibernate.query.NavigablePath;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.results.graph.AssemblerCreationState;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultAssembler;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.FetchableContainer;
import org.hibernate.sql.results.graph.entity.AbstractEntityResultGraphNode;
import org.hibernate.sql.results.graph.entity.EntityInitializer;
import org.hibernate.sql.results.graph.entity.EntityResult;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

import static org.hibernate.query.results.ResultsHelper.attributeName;

/**
 * Selects just the FK and builds a proxy
 *
 * @author Christian Beikov
 */
public class EntityDelayedResultImpl implements DomainResult {

	private final NavigablePath navigablePath;
	private final EntityAssociationMapping entityValuedModelPart;
	private final DomainResult identifierResult;

	public EntityDelayedResultImpl(
			NavigablePath navigablePath,
			EntityAssociationMapping entityValuedModelPart,
			TableGroup rootTableGroup,
			DomainResultCreationState creationState) {
		this.navigablePath = navigablePath;
		this.entityValuedModelPart = entityValuedModelPart;
		this.identifierResult = entityValuedModelPart.getForeignKeyDescriptor()
				.createDomainResult(
						navigablePath.append( EntityIdentifierMapping.ROLE_LOCAL_NAME ),
						rootTableGroup,
						null,
						creationState
				);
	}

	@Override
	public JavaTypeDescriptor getResultJavaTypeDescriptor() {
		return entityValuedModelPart.getAssociatedEntityMappingType().getMappedJavaTypeDescriptor();
	}

	@Override
	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	@Override
	public String getResultVariable() {
		return null;
	}

	@Override
	public DomainResultAssembler createResultAssembler(AssemblerCreationState creationState) {
		final EntityInitializer initializer = (EntityInitializer) creationState.resolveInitializer(
				getNavigablePath(),
				entityValuedModelPart,
				() -> new EntityDelayedFetchInitializer(
						getNavigablePath(),
						(EntityValuedModelPart) entityValuedModelPart,
						identifierResult.createResultAssembler( creationState )
				)
		);

		return new EntityAssembler( getResultJavaTypeDescriptor(), initializer );
	}

	@Override
	public String toString() {
		return "EntityDelayedResultImpl {" + getNavigablePath() + "}";
	}
}
