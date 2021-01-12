/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.ast;

import org.hibernate.Incubating;
import org.hibernate.query.sqm.tree.expression.Conversion;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.delete.DeleteStatement;
import org.hibernate.sql.ast.tree.expression.Any;
import org.hibernate.sql.ast.tree.expression.BinaryArithmeticExpression;
import org.hibernate.sql.ast.tree.expression.CaseSearchedExpression;
import org.hibernate.sql.ast.tree.expression.CaseSimpleExpression;
import org.hibernate.sql.ast.tree.expression.CastTarget;
import org.hibernate.sql.ast.tree.expression.Collate;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Distinct;
import org.hibernate.sql.ast.tree.expression.Duration;
import org.hibernate.sql.ast.tree.expression.DurationUnit;
import org.hibernate.sql.ast.tree.expression.EntityTypeLiteral;
import org.hibernate.sql.ast.tree.expression.Every;
import org.hibernate.sql.ast.tree.expression.ExtractUnit;
import org.hibernate.sql.ast.tree.expression.Format;
import org.hibernate.sql.ast.tree.expression.JdbcLiteral;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.expression.QueryLiteral;
import org.hibernate.sql.ast.tree.expression.SelfRenderingExpression;
import org.hibernate.sql.ast.tree.expression.SqlSelectionExpression;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.Star;
import org.hibernate.sql.ast.tree.expression.Summarization;
import org.hibernate.sql.ast.tree.expression.TrimSpecification;
import org.hibernate.sql.ast.tree.expression.UnaryOperation;
import org.hibernate.sql.ast.tree.from.FromClause;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.from.TableReferenceJoin;
import org.hibernate.sql.ast.tree.insert.InsertStatement;
import org.hibernate.sql.ast.tree.predicate.BetweenPredicate;
import org.hibernate.sql.ast.tree.predicate.ComparisonPredicate;
import org.hibernate.sql.ast.tree.predicate.ExistsPredicate;
import org.hibernate.sql.ast.tree.predicate.FilterPredicate;
import org.hibernate.sql.ast.tree.predicate.GroupedPredicate;
import org.hibernate.sql.ast.tree.predicate.InListPredicate;
import org.hibernate.sql.ast.tree.predicate.InSubQueryPredicate;
import org.hibernate.sql.ast.tree.predicate.Junction;
import org.hibernate.sql.ast.tree.predicate.LikePredicate;
import org.hibernate.sql.ast.tree.predicate.NegatedPredicate;
import org.hibernate.sql.ast.tree.predicate.NullnessPredicate;
import org.hibernate.sql.ast.tree.predicate.SelfRenderingPredicate;
import org.hibernate.sql.ast.tree.select.QueryGroup;
import org.hibernate.sql.ast.tree.select.QueryPart;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.ast.tree.select.SortSpecification;
import org.hibernate.sql.ast.tree.update.Assignment;
import org.hibernate.sql.ast.tree.update.UpdateStatement;

/**
 * @author Steve Ebersole
 * @author Andrea Boriero
 */
@Incubating
public interface SqlAstWalker {

	void visitSelectStatement(SelectStatement statement);

	void visitDeleteStatement(DeleteStatement statement);

	void visitUpdateStatement(UpdateStatement statement);

	void visitInsertStatement(InsertStatement statement);

	void visitAssignment(Assignment assignment);

	void visitQueryGroup(QueryGroup queryGroup);

	void visitQuerySpec(QuerySpec querySpec);

	void visitSortSpecification(SortSpecification sortSpecification);

	void visitOffsetFetchClause(QueryPart querySpec);

	void visitSelectClause(SelectClause selectClause);

	void visitSqlSelection(SqlSelection sqlSelection);

	void visitFromClause(FromClause fromClause);

	void visitTableGroup(TableGroup tableGroup);

	void visitTableGroupJoin(TableGroupJoin tableGroupJoin);

	void visitTableReference(TableReference tableReference);

	void visitTableReferenceJoin(TableReferenceJoin tableReferenceJoin);

	void visitColumnReference(ColumnReference columnReference);

	void visitExtractUnit(ExtractUnit extractUnit);

	void visitFormat(Format format);

	void visitDistinct(Distinct distinct);

	void visitStar(Star star);

	void visitTrimSpecification(TrimSpecification trimSpecification);

	void visitCastTarget(CastTarget castTarget);

	void visitBinaryArithmeticExpression(BinaryArithmeticExpression arithmeticExpression);

	void visitCaseSearchedExpression(CaseSearchedExpression caseSearchedExpression);

	void visitCaseSimpleExpression(CaseSimpleExpression caseSimpleExpression);

	void visitAny(Any any);

	void visitEvery(Every every);

	void visitSummarization(Summarization every);

	void visitSelfRenderingExpression(SelfRenderingExpression expression);

	void visitSqlSelectionExpression(SqlSelectionExpression expression);

	void visitEntityTypeLiteral(EntityTypeLiteral expression);

	void visitTuple(SqlTuple tuple);

	void visitCollate(Collate collate);

	void visitParameter(JdbcParameter jdbcParameter);

	void visitJdbcLiteral(JdbcLiteral jdbcLiteral);

	void visitQueryLiteral(QueryLiteral queryLiteral);

	void visitUnaryOperationExpression(UnaryOperation unaryOperationExpression);

	void visitBetweenPredicate(BetweenPredicate betweenPredicate);

	void visitFilterPredicate(FilterPredicate filterPredicate);

	void visitGroupedPredicate(GroupedPredicate groupedPredicate);

	void visitInListPredicate(InListPredicate inListPredicate);

	void visitInSubQueryPredicate(InSubQueryPredicate inSubQueryPredicate);

	void visitExistsPredicate(ExistsPredicate existsPredicate);

	void visitJunction(Junction junction);

	void visitLikePredicate(LikePredicate likePredicate);

	void visitNegatedPredicate(NegatedPredicate negatedPredicate);

	void visitNullnessPredicate(NullnessPredicate nullnessPredicate);

	void visitRelationalPredicate(ComparisonPredicate comparisonPredicate);

	void visitSelfRenderingPredicate(SelfRenderingPredicate selfRenderingPredicate);

	void visitDurationUnit(DurationUnit durationUnit);

	void visitDuration(Duration duration);

	void visitConversion(Conversion conversion);

}
