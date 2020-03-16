/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.graph.basic;

import java.util.function.Consumer;

import org.hibernate.engine.FetchTiming;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.metamodel.model.convert.spi.BasicValueConverter;
import org.hibernate.query.NavigablePath;
import org.hibernate.sql.results.graph.AssemblerCreationState;
import org.hibernate.sql.results.graph.DomainResultAssembler;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.graph.FetchParentAccess;
import org.hibernate.sql.results.graph.Fetchable;
import org.hibernate.sql.results.graph.Initializer;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

/**
 * @author Steve Ebersole
 */
public class BasicFetch<T> implements Fetch, BasicResultGraphNode<T> {
	private final NavigablePath navigablePath;
	private final FetchParent fetchParent;
	private final BasicValuedModelPart valuedMapping;
	private final boolean nullable;

	private final BasicResultAssembler<T> assembler;

	private final FetchTiming fetchTiming;

	public BasicFetch(
			int valuesArrayPosition,
			FetchParent fetchParent,
			NavigablePath fetchablePath,
			BasicValuedModelPart valuedMapping,
			boolean nullable,
			BasicValueConverter valueConverter,
			FetchTiming fetchTiming,
			DomainResultCreationState creationState) {
		this.nullable = nullable;
		this.navigablePath = fetchablePath;

		this.fetchParent = fetchParent;
		this.valuedMapping = valuedMapping;
		this.fetchTiming = fetchTiming;

		// todo (6.0) : account for lazy basic attributes (bytecode)

		//noinspection unchecked
		this.assembler = new BasicResultAssembler(
				valuesArrayPosition,
				valuedMapping.getJavaTypeDescriptor(),
				valueConverter
		);

	}

	@Override
	public FetchTiming getTiming() {
		return fetchTiming;
	}

	@Override
	public boolean hasTableGroup() {
		return fetchTiming == FetchTiming.IMMEDIATE;
	}

	@Override
	public FetchParent getFetchParent() {
		return fetchParent;
	}

	@Override
	public Fetchable getFetchedMapping() {
		return valuedMapping;
	}

	@Override
	public JavaTypeDescriptor getResultJavaTypeDescriptor() {
		return null;
	}

	@Override
	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	@Override
	public DomainResultAssembler createAssembler(
			FetchParentAccess parentAccess,
			Consumer<Initializer> collector,
			AssemblerCreationState creationState) {
		return assembler;
	}

	@Override
	public DomainResultAssembler<T> createResultAssembler(
			Consumer<Initializer> initializerCollector,
			AssemblerCreationState creationState) {
		return assembler;
	}

	@Override
	public String getResultVariable() {
		// a basic value used as a fetch will never have a result variable in the domain result
		return null;
	}
}
