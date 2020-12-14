/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.SqlTuple;

import static org.hibernate.sql.ast.spi.SqlExpressionResolver.createColumnReferenceKey;

/**
 * @author Steve Ebersole
 */
public class MappingModelHelper {
	public static Expression buildColumnReferenceExpression(
			ModelPart modelPart,
			SqlExpressionResolver sqlExpressionResolver,
			SessionFactoryImplementor sessionFactory) {
		final int jdbcTypeCount = modelPart.getJdbcTypeCount();

		if ( modelPart instanceof EmbeddableValuedModelPart ) {
			final List<ColumnReference> columnReferences = new ArrayList<>( jdbcTypeCount );
			modelPart.forEachSelection(
					(columnIndex, selection) -> {
						final ColumnReference colRef;
						if ( sqlExpressionResolver == null ) {
							colRef = new ColumnReference(
									selection.getContainingTableExpression(),
									selection,
									sessionFactory
							);
						}
						else {
							colRef = (ColumnReference) sqlExpressionResolver.resolveSqlExpression(
									createColumnReferenceKey( selection.getContainingTableExpression(), selection.getSelectionExpression() ),
									sqlAstProcessingState -> new ColumnReference(
											selection.getContainingTableExpression(),
											selection,
											sessionFactory
									)
							);
						}
						columnReferences.add( colRef );
					}
			);
			return new SqlTuple( columnReferences, modelPart );
		}
		else {
			assert modelPart instanceof BasicValuedModelPart;
			final BasicValuedModelPart basicPart = (BasicValuedModelPart) modelPart;
			if ( sqlExpressionResolver == null ) {
				return new ColumnReference(
						basicPart.getContainingTableExpression(),
						basicPart,
						sessionFactory
				);
			}
			else {
				return sqlExpressionResolver.resolveSqlExpression(
						createColumnReferenceKey( basicPart.getContainingTableExpression(), basicPart.getSelectionExpression() ),
						sqlAstProcessingState -> new ColumnReference(
								basicPart.getContainingTableExpression(),
								basicPart,
								sessionFactory
						)
				);
			}
		}
	}

	private MappingModelHelper() {
		// disallow direct instantiation
	}
}
