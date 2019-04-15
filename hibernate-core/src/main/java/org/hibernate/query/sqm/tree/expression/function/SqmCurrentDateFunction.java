/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.expression.function;

import java.sql.Date;

import org.hibernate.metamodel.model.domain.spi.AllowableFunctionReturnType;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.consume.spi.SemanticQueryWalker;
import org.hibernate.type.spi.StandardSpiBasicTypes;

/**
 * @author Steve Ebersole
 */
public class SqmCurrentDateFunction extends AbstractSqmFunction<Date> {
	public static final String NAME = "current_date";

	public SqmCurrentDateFunction(NodeBuilder nodeBuilder) {
		super( (AllowableFunctionReturnType) StandardSpiBasicTypes.DATE, nodeBuilder );
	}

	public SqmCurrentDateFunction(AllowableFunctionReturnType<Date> resultType, NodeBuilder nodeBuilder) {
		super( resultType, nodeBuilder );
	}

	@Override
	public String getFunctionName() {
		return NAME;
	}

	@Override
	public boolean hasArguments() {
		return false;
	}

	@Override
	public <T> T accept(SemanticQueryWalker<T> walker) {
		return walker.visitCurrentDateFunction( this );
	}

	@Override
	public String asLoggableText() {
		return NAME;
	}
}
