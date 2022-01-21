/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.select;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import org.hibernate.metamodel.mapping.CollectionPart;
import org.hibernate.metamodel.model.domain.EmbeddableDomainType;
import org.hibernate.query.sqm.FetchClauseType;
import org.hibernate.query.SemanticException;
import org.hibernate.query.criteria.JpaExpression;
import org.hibernate.query.criteria.JpaOrder;
import org.hibernate.query.criteria.JpaPredicate;
import org.hibernate.query.criteria.JpaQueryStructure;
import org.hibernate.query.criteria.JpaRoot;
import org.hibernate.query.criteria.JpaSelection;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SemanticQueryWalker;
import org.hibernate.query.sqm.tree.SqmNode;
import org.hibernate.query.sqm.tree.domain.SqmEntityValuedSimplePath;
import org.hibernate.query.sqm.tree.expression.SqmAliasedNodeRef;
import org.hibernate.query.sqm.tree.expression.SqmExpression;
import org.hibernate.query.sqm.tree.from.SqmAttributeJoin;
import org.hibernate.query.sqm.tree.from.SqmCrossJoin;
import org.hibernate.query.sqm.tree.from.SqmEntityJoin;
import org.hibernate.query.sqm.tree.from.SqmFrom;
import org.hibernate.query.sqm.tree.from.SqmFromClause;
import org.hibernate.query.sqm.tree.from.SqmFromClauseContainer;
import org.hibernate.query.sqm.tree.from.SqmJoin;
import org.hibernate.query.sqm.tree.from.SqmRoot;
import org.hibernate.query.sqm.tree.predicate.SqmPredicate;
import org.hibernate.query.sqm.tree.predicate.SqmWhereClause;
import org.hibernate.query.sqm.tree.predicate.SqmWhereClauseContainer;

/**
 * Defines the commonality between a root query and a subquery.
 *
 * @author Steve Ebersole
 */
public class SqmQuerySpec<T> extends SqmQueryPart<T>
		implements SqmNode, SqmFromClauseContainer, SqmWhereClauseContainer, JpaQueryStructure<T> {
	private SqmFromClause fromClause;
	private SqmSelectClause selectClause;
	private SqmWhereClause whereClause;

	private boolean hasPositionalGroupItem;
	private List<SqmExpression<?>> groupByClauseExpressions = Collections.emptyList();
	private SqmPredicate havingClausePredicate;

	public SqmQuerySpec(NodeBuilder nodeBuilder) {
		super( nodeBuilder );
	}

	@Override
	public <X> X accept(SemanticQueryWalker<X> walker) {
		return walker.visitQuerySpec( this );
	}

	@Override
	public SqmQuerySpec<T> getFirstQuerySpec() {
		return this;
	}

	@Override
	public SqmQuerySpec<T> getLastQuerySpec() {
		return this;
	}

	@Override
	public boolean isSimpleQueryPart() {
		return true;
	}

	@Override
	public SqmFromClause getFromClause() {
		return fromClause;
	}

	public void setFromClause(SqmFromClause fromClause) {
		this.fromClause = fromClause;
	}

	public boolean containsCollectionFetches() {
		final List<SqmFrom<?, ?>> fromNodes = new ArrayList<>( fromClause.getRoots() );
		while ( !fromNodes.isEmpty() ) {
			final SqmFrom<?, ?> fromNode = fromNodes.remove( fromNodes.size() - 1 );
			for ( SqmJoin<?, ?> sqmJoin : fromNode.getSqmJoins() ) {
				if ( sqmJoin instanceof SqmAttributeJoin<?, ?> ) {
					final SqmAttributeJoin<?, ?> join = (SqmAttributeJoin<?, ?>) sqmJoin;
					if ( join.isFetched() && join.getAttribute().isCollection() ) {
						return true;
					}
				}
				fromNodes.add( sqmJoin );
			}
		}
		return false;
	}

	public SqmSelectClause getSelectClause() {
		return selectClause;
	}

	public void setSelectClause(SqmSelectClause selectClause) {
		this.selectClause = selectClause;
	}

	@Override
	public SqmWhereClause getWhereClause() {
		return whereClause;
	}

	public void setWhereClause(SqmWhereClause whereClause) {
		this.whereClause = whereClause;
	}

	@Override
	public void applyPredicate(SqmPredicate predicate) {
		if ( predicate == null ) {
			return;
		}

		if ( whereClause == null ) {
			whereClause = new SqmWhereClause( nodeBuilder() );
		}

		whereClause.applyPredicate( predicate );
	}

	public boolean hasPositionalGroupItem() {
		return hasPositionalGroupItem;
	}

	public List<SqmExpression<?>> getGroupByClauseExpressions() {
		return groupByClauseExpressions;
	}

	public void setGroupByClauseExpressions(List<SqmExpression<?>> groupByClauseExpressions) {
		this.hasPositionalGroupItem = false;
		if ( groupByClauseExpressions == null ) {
			this.groupByClauseExpressions = Collections.emptyList();
		}
		else {
			this.groupByClauseExpressions = groupByClauseExpressions;
			for ( int i = 0; i < groupByClauseExpressions.size(); i++ ) {
				final SqmExpression<?> groupItem = groupByClauseExpressions.get( i );
				if ( groupItem instanceof SqmAliasedNodeRef ) {
					this.hasPositionalGroupItem = true;
					break;
				}
			}
		}
	}

	public SqmPredicate getHavingClausePredicate() {
		return havingClausePredicate;
	}

	public void setHavingClausePredicate(SqmPredicate havingClausePredicate) {
		this.havingClausePredicate = havingClausePredicate;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// JPA

	@Override
	public boolean isDistinct() {
		assert getSelectClause() != null;
		return getSelectClause().isDistinct();
	}

	@Override
	public SqmQuerySpec<T> setDistinct(boolean distinct) {
		assert getSelectClause() != null;
		getSelectClause().makeDistinct( distinct );
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public JpaSelection<T> getSelection() {
		assert getSelectClause() != null;
		return (JpaSelection<T>) getSelectClause().resolveJpaSelection();
	}

	@Override
	public SqmQuerySpec<T> setSelection(JpaSelection<T> selection) {
		final SqmSelectClause selectClause = getSelectClause();
		assert selectClause != null;
		// NOTE : this call comes from JPA which inherently supports just a
		// single (possibly "compound") selection.
		// We have this special case where we return the SqmSelectClause itself if it doesn't have exactly 1 item
		if ( selection instanceof SqmSelectClause ) {
			if ( selection != selectClause ) {
				final SqmSelectClause sqmSelectClause = (SqmSelectClause) selection;
				final List<SqmSelection<?>> selections = sqmSelectClause.getSelections();
				selectClause.setSelection( selections.get( 0 ).getSelectableNode() );
				for ( int i = 1; i < selections.size(); i++ ) {
					selectClause.addSelection( selections.get( i ) );
				}
			}
		}
		else {
			selectClause.setSelection( (SqmSelectableNode<?>) selection );
		}
		return this;
	}

	@Override
	public Set<SqmRoot<?>> getRoots() {
		assert getFromClause() != null;
		return new HashSet<>( getFromClause().getRoots() );
	}

	@Override
	public SqmQuerySpec<T> addRoot(JpaRoot<?> root) {
		if ( getFromClause() == null ) {
			setFromClause( new SqmFromClause() );
		}
		getFromClause().addRoot( (SqmRoot<?>) root );
		return this;
	}

	@Override
	public SqmPredicate getRestriction() {
		if ( getWhereClause() == null ) {
			return null;
		}
		return getWhereClause().getPredicate();
	}

	@Override
	public SqmQuerySpec<T> setRestriction(JpaPredicate restriction) {
		SqmWhereClause whereClause = getWhereClause();
		if ( whereClause == null ) {
			setWhereClause( whereClause = new SqmWhereClause( nodeBuilder() ) );
		}
		whereClause.setPredicate( (SqmPredicate) restriction );
		return this;
	}

	@Override
	public SqmQuerySpec<T> setRestriction(Expression<Boolean> restriction) {
		SqmWhereClause whereClause = getWhereClause();
		if ( whereClause == null ) {
			setWhereClause( whereClause = new SqmWhereClause( nodeBuilder() ) );
		}
		whereClause.setPredicate( nodeBuilder().wrap( restriction ) );
		return this;
	}

	@Override
	public SqmQuerySpec<T> setRestriction(Predicate... restrictions) {
		SqmWhereClause whereClause = getWhereClause();
		if ( whereClause == null ) {
			setWhereClause( whereClause = new SqmWhereClause( nodeBuilder() ) );
		}
		else {
			whereClause.setPredicate( null );
		}
		for ( Predicate restriction : restrictions ) {
			whereClause.applyPredicate( (SqmPredicate) restriction );
		}
		return this;
	}

	@Override
	public List<SqmExpression<?>> getGroupingExpressions() {
		return groupByClauseExpressions;
	}

	@Override
	public SqmQuerySpec<T> setGroupingExpressions(List<? extends JpaExpression<?>> groupExpressions) {
		this.hasPositionalGroupItem = false;
		this.groupByClauseExpressions = new ArrayList<>( groupExpressions.size() );
		for ( JpaExpression<?> groupExpression : groupExpressions ) {
			if ( groupExpression instanceof SqmAliasedNodeRef ) {
				this.hasPositionalGroupItem = true;
			}
			this.groupByClauseExpressions.add( (SqmExpression<?>) groupExpression );
		}
		return this;
	}

	@Override
	public SqmQuerySpec<T> setGroupingExpressions(JpaExpression<?>... groupExpressions) {
		this.hasPositionalGroupItem = false;
		this.groupByClauseExpressions = new ArrayList<>( groupExpressions.length );
		for ( JpaExpression<?> groupExpression : groupExpressions ) {
			if ( groupExpression instanceof SqmAliasedNodeRef ) {
				this.hasPositionalGroupItem = true;
			}
			this.groupByClauseExpressions.add( (SqmExpression<?>) groupExpression );
		}
		return this;
	}

	@Override
	public SqmPredicate getGroupRestriction() {
		return havingClausePredicate;
	}

	@Override
	public SqmQuerySpec<T> setGroupRestriction(JpaPredicate restriction) {
		havingClausePredicate = (SqmPredicate) restriction;
		return this;
	}

	@Override
	public SqmQuerySpec<T> setGroupRestriction(Expression<Boolean> restriction) {
		havingClausePredicate = nodeBuilder().wrap( restriction );
		return this;
	}

	@Override
	public SqmQuerySpec<T> setGroupRestriction(Predicate... restrictions) {
		havingClausePredicate = nodeBuilder().wrap( restrictions );
		return this;
	}

	@Override
	public SqmQuerySpec<T> setSortSpecifications(List<? extends JpaOrder> sortSpecifications) {
		super.setSortSpecifications( sortSpecifications );
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public SqmExpression<?> getOffset() {
		return getOffsetExpression();
	}

	@Override
	public SqmQuerySpec<T> setOffset(JpaExpression<?> offset) {
		setOffsetExpression( (SqmExpression<?>) offset );
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public SqmExpression<?> getFetch() {
		return getFetchExpression();
	}

	@Override
	public SqmQuerySpec<T> setFetch(JpaExpression<?> fetch) {
		setFetchExpression( (SqmExpression<?>) fetch );
		return this;
	}

	@Override
	public SqmQuerySpec<T> setFetch(JpaExpression<?> fetch, FetchClauseType fetchClauseType) {
		setFetchExpression( (SqmExpression<?>) fetch, fetchClauseType );
		return this;
	}

	@Override
	public void validateQueryStructureAndFetchOwners() {
		validateFetchOwners();
	}

	public void validateFetchOwners() {
		if ( getFromClause() == null ) {
			return;
		}
		final Set<SqmFrom<?, ?>> selectedFromSet;
		if ( selectClause == null || selectClause.getSelections().isEmpty() ) {
			selectedFromSet = Collections.singleton( getFromClause().getRoots().get( 0 ) );
		}
		else {
			selectedFromSet = new HashSet<>( selectClause.getSelections().size() );
			for ( SqmSelection<?> selection : selectClause.getSelections() ) {
				collectSelectedFromSet( selectedFromSet, selection.getSelectableNode() );
			}
		}

		for ( SqmRoot<?> root : getFromClause().getRoots() ) {
			validateFetchOwners( selectedFromSet, root );
		}
	}

	private void collectSelectedFromSet(Set<SqmFrom<?, ?>> selectedFromSet, SqmSelectableNode<?> selectableNode) {
		if ( selectableNode instanceof SqmJpaCompoundSelection<?> ) {
			final SqmJpaCompoundSelection<?> compoundSelection = (SqmJpaCompoundSelection<?>) selectableNode;
			for ( SqmSelectableNode<?> selectionItem : compoundSelection.getSelectionItems() ) {
				collectSelectedFromSet( selectedFromSet, selectionItem );
			}
		}
		else if ( selectableNode instanceof SqmDynamicInstantiation<?> ) {
			final SqmDynamicInstantiation<?> instantiation = (SqmDynamicInstantiation<?>) selectableNode;
			for ( SqmDynamicInstantiationArgument<?> selectionItem : instantiation.getArguments() ) {
				collectSelectedFromSet( selectedFromSet, selectionItem.getSelectableNode() );
			}
		}
		else if ( selectableNode instanceof SqmFrom<?, ?> ) {
			collectSelectedFromSet( selectedFromSet, (SqmFrom<?, ?>) selectableNode );
		}
		else if ( selectableNode instanceof SqmEntityValuedSimplePath<?> ) {
			final SqmEntityValuedSimplePath<?> path = (SqmEntityValuedSimplePath<?>) selectableNode;
			if ( CollectionPart.Nature.fromNameExact( path.getReferencedPathSource().getPathName() ) != null
					&& path.getLhs() instanceof SqmFrom<?, ?> ) {
				collectSelectedFromSet( selectedFromSet, (SqmFrom<?, ?>) path.getLhs() );
			}
		}
	}

	private void collectSelectedFromSet(Set<SqmFrom<?, ?>> selectedFromSet, SqmFrom<?, ?> sqmFrom) {
		selectedFromSet.add( sqmFrom );
		for ( SqmJoin<?, ?> sqmJoin : sqmFrom.getSqmJoins() ) {
			if ( sqmJoin.getReferencedPathSource().getSqmPathType() instanceof EmbeddableDomainType<?> ) {
				collectSelectedFromSet( selectedFromSet, sqmJoin );
			}
		}

		for ( SqmFrom<?, ?> sqmTreat : sqmFrom.getSqmTreats() ) {
			collectSelectedFromSet( selectedFromSet, sqmTreat );
		}
	}

	private void validateFetchOwners(Set<SqmFrom<?, ?>> selectedFromSet, SqmFrom<?, ?> owner) {
		for ( SqmJoin<?, ?> sqmJoin : owner.getSqmJoins() ) {
			if ( sqmJoin instanceof SqmAttributeJoin<?, ?> ) {
				final SqmAttributeJoin<?, ?> attributeJoin = (SqmAttributeJoin<?, ?>) sqmJoin;
				if ( attributeJoin.isFetched() ) {
					assertFetchOwner( selectedFromSet, owner, sqmJoin );
					// Only need to check the first level
					continue;
				}
			}
			validateFetchOwners( selectedFromSet, sqmJoin );
		}
		for ( SqmFrom<?, ?> sqmTreat : owner.getSqmTreats() ) {
			validateFetchOwners( selectedFromSet, sqmTreat );
		}
	}

	private void assertFetchOwner(Set<SqmFrom<?, ?>> selectedFromSet, SqmFrom<?, ?> owner, SqmJoin<?, ?> sqmJoin) {
		if ( !selectedFromSet.contains( owner ) ) {
			throw new SemanticException(
					"query specified join fetching, but the owner " +
							"of the fetched association was not present in the select list " +
							"[" + sqmJoin.asLoggableText() + "]"
			);
		}
	}

	@Override
	public void appendHqlString(StringBuilder sb) {
		if ( selectClause != null ) {
			sb.append( "select " );
			if ( selectClause.isDistinct() ) {
				sb.append( "distinct " );
			}
			final List<SqmSelection<?>> selections = selectClause.getSelections();
			selections.get( 0 ).appendHqlString( sb );
			for ( int i = 1; i < selections.size(); i++ ) {
				sb.append( ", " );
				selections.get( i ).appendHqlString( sb );
			}
		}
		if ( fromClause != null ) {
			sb.append( " from " );
			String separator = "";
			for ( SqmRoot<?> root : fromClause.getRoots() ) {
				sb.append( separator );
				if ( root.isCorrelated() ) {
					if ( root.containsOnlyInnerJoins() ) {
						appendJoins( root, root.getCorrelationParent().resolveAlias(), sb );
					}
					else {
						sb.append( root.getCorrelationParent().resolveAlias() );
						sb.append( ' ' ).append( root.resolveAlias() );
						appendJoins( root, sb );
					}
				}
				else {
					sb.append( root.getEntityName() );
					sb.append( ' ' ).append( root.resolveAlias() );
					appendJoins( root, sb );
				}
				separator = ", ";
			}
		}
		if ( whereClause != null && whereClause.getPredicate() != null ) {
			sb.append( " where " );
			whereClause.getPredicate().appendHqlString( sb );
		}
		if ( !groupByClauseExpressions.isEmpty() ) {
			sb.append( " group by " );
			groupByClauseExpressions.get( 0 ).appendHqlString( sb );
			for ( int i = 1; i < groupByClauseExpressions.size(); i++ ) {
				sb.append( ", " );
				groupByClauseExpressions.get( i ).appendHqlString( sb );
			}
		}
		if ( havingClausePredicate != null ) {
			sb.append( " having " );
			havingClausePredicate.appendHqlString( sb );
		}

		super.appendHqlString( sb );
	}

	private void appendJoins(SqmFrom<?, ?> sqmFrom, StringBuilder sb) {
		for ( SqmJoin<?, ?> sqmJoin : sqmFrom.getSqmJoins() ) {
			switch ( sqmJoin.getSqmJoinType() ) {
				case LEFT:
					sb.append( " left join " );
					break;
				case RIGHT:
					sb.append( " right join " );
					break;
				case INNER:
					sb.append( " join " );
					break;
				case FULL:
					sb.append( " full join " );
					break;
				case CROSS:
					sb.append( " cross join " );
					break;
			}
			if ( sqmJoin instanceof SqmAttributeJoin<?, ?> ) {
				final SqmAttributeJoin<?, ?> attributeJoin = (SqmAttributeJoin<?, ?>) sqmJoin;
				sb.append( sqmFrom.resolveAlias() ).append( '.' );
				sb.append( (attributeJoin).getAttribute().getName() );
				sb.append( ' ' ).append( sqmJoin.resolveAlias() );
				if ( attributeJoin.getJoinPredicate() != null ) {
					sb.append( " on " );
					attributeJoin.getJoinPredicate().appendHqlString( sb );
				}
				appendJoins( sqmJoin, sb );
			}
			else if ( sqmJoin instanceof SqmCrossJoin<?> ) {
				sb.append( ( (SqmCrossJoin<?>) sqmJoin ).getEntityName() );
				sb.append( ' ' ).append( sqmJoin.resolveAlias() );
				appendJoins( sqmJoin, sb );
			}
			else if ( sqmJoin instanceof SqmEntityJoin<?> ) {
				final SqmEntityJoin<?> sqmEntityJoin = (SqmEntityJoin<?>) sqmJoin;
				sb.append( (sqmEntityJoin).getEntityName() );
				sb.append( ' ' ).append( sqmJoin.resolveAlias() );
				if ( sqmEntityJoin.getJoinPredicate() != null ) {
					sb.append( " on " );
					sqmEntityJoin.getJoinPredicate().appendHqlString( sb );
				}
				appendJoins( sqmJoin, sb );
			}
			else {
				throw new UnsupportedOperationException( "Unsupported join: " + sqmJoin );
			}
		}
	}

	private void appendJoins(SqmFrom<?, ?> sqmFrom, String correlationPrefix, StringBuilder sb) {
		String separator = "";
		for ( SqmJoin<?, ?> sqmJoin : sqmFrom.getSqmJoins() ) {
			assert sqmJoin instanceof SqmAttributeJoin<?, ?>;
			sb.append( separator );
			sb.append( correlationPrefix ).append( '.' );
			sb.append( ( (SqmAttributeJoin<?, ?>) sqmJoin ).getAttribute().getName() );
			sb.append( ' ' ).append( sqmJoin.resolveAlias() );
			appendJoins( sqmJoin, sb );
			separator = ", ";
		}
	}
}
