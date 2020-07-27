/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.sql.internal;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.hibernate.NotYetImplementedFor6Exception;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.SemanticException;
import org.hibernate.query.sqm.SemanticQueryWalker;
import org.hibernate.query.sqm.tree.domain.SqmBasicValuedSimplePath;
import org.hibernate.sql.ast.SqlAstWalker;
import org.hibernate.sql.ast.spi.SqlAstCreationContext;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.SqlSelectionExpression;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.update.Assignment;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;

/**
 * @author Steve Ebersole
 */
public class BasicValuedPathInterpretation<T> implements AssignableSqmPathInterpretation<T>, DomainResultProducer<T> {
	/**
	 * Static factory
	 */
	public static <T> BasicValuedPathInterpretation<T> from(
			SqmBasicValuedSimplePath<T> sqmPath,
			SqlAstCreationState sqlAstCreationState,
			SemanticQueryWalker sqmWalker) {
		final TableGroup tableGroup = sqlAstCreationState.getFromClauseAccess().getTableGroup( sqmPath.getLhs().getNavigablePath() );

		final BasicValuedModelPart mapping = (BasicValuedModelPart) tableGroup.getModelPart().findSubPart(
				sqmPath.getReferencedPathSource().getPathName(),
				null
		);

		if ( mapping == null ) {
			throw new SemanticException( "`" + sqmPath.getNavigablePath().getFullPath() + "` did not reference a known model part" );
		}

		final TableReference tableReference = tableGroup.resolveTableReference( mapping.getContainingTableExpression() );

		final Expression expression = sqlAstCreationState.getSqlExpressionResolver().resolveSqlExpression(
				SqlExpressionResolver.createColumnReferenceKey(
						tableReference,
						mapping.getMappedColumnExpression()
				),
				sacs -> new ColumnReference(
						tableReference.getIdentificationVariable(),
						mapping.getMappedColumnExpression(),
						mapping.isMappedColumnExpressionFormula(),
						mapping.getJdbcMapping(),
						sqlAstCreationState.getCreationContext().getSessionFactory()
				)
		);

		final ColumnReference columnReference;
		if ( expression instanceof ColumnReference ) {
			columnReference = ( (ColumnReference) expression );
		}
		else if ( expression instanceof SqlSelectionExpression ) {
			final Expression selectedExpression = ( (SqlSelectionExpression) expression ).getExpression();
			assert selectedExpression instanceof ColumnReference;
			columnReference = (ColumnReference) selectedExpression;
		}
		else {
			throw new UnsupportedOperationException( "Unsupported basic-valued path expression : " + expression );
		}

		return new BasicValuedPathInterpretation<>( columnReference, sqmPath, mapping, tableGroup );
	}

	private final ColumnReference columnReference;

	private final SqmBasicValuedSimplePath<T> sqmPath;
	private final BasicValuedModelPart mapping;
	private final TableGroup tableGroup;

	private BasicValuedPathInterpretation(
			ColumnReference columnReference,
			SqmBasicValuedSimplePath<T> sqmPath,
			BasicValuedModelPart mapping,
			TableGroup tableGroup) {
		assert columnReference != null;
		this.columnReference = columnReference;

		assert sqmPath != null;
		this.sqmPath = sqmPath;

		assert mapping != null;
		this.mapping = mapping;

		assert tableGroup != null;
		this.tableGroup = tableGroup;
	}

	@Override
	public NavigablePath getNavigablePath() {
		return sqmPath.getNavigablePath();
	}

	@Override
	public ModelPart getExpressionType() {
		return mapping;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// DomainResultProducer

	@Override
	public DomainResult<T> createDomainResult(
			String resultVariable,
			DomainResultCreationState creationState) {
		return mapping.createDomainResult( getNavigablePath(), tableGroup, resultVariable, creationState );
	}

	@Override
	public void applySqlSelections(DomainResultCreationState creationState) {
		mapping.applySqlSelections( getNavigablePath(), tableGroup, creationState );
	}

	@Override
	public void applySqlAssignments(
			Expression newValueExpression,
			AssignmentContext assignmentProcessingState,
			Consumer<Assignment> assignmentConsumer,
			SqlAstCreationContext creationContext) {
		throw new NotYetImplementedFor6Exception( getClass() );
	}

	@Override
	public void accept(SqlAstWalker sqlTreeWalker) {
		columnReference.accept( sqlTreeWalker );
	}

	@Override
	public String toString() {
		return "BasicValuedPathInterpretation(" + sqmPath.getNavigablePath().getFullPath() + ')';
	}

	@Override
	public void visitColumnReferences(Consumer<ColumnReference> columnReferenceConsumer) {
		columnReferenceConsumer.accept( columnReference );
	}

	@Override
	public List<ColumnReference> getColumnReferences() {
		return Collections.singletonList( columnReference );
	}
}
