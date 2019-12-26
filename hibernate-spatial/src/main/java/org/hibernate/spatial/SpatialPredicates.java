/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.spatial;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

import org.hibernate.spatial.criterion.SpatialRestrictions;

import org.locationtech.jts.geom.Geometry;

/**
 * JPA Criteria API support for hibernate-spatial.
 *
 * @author Daniel Shuy
 */
public class SpatialPredicates {

	private SpatialPredicates() {
	}

	/**
	 * Create a predicate for testing the arguments for "spatially equal" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry expression
	 *
	 * @return "spatially equal" predicate
	 *
	 * @see SpatialRestrictions#eq(String, Geometry)
	 */
	public static Predicate eq(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Expression<? extends Geometry> geometry2) {
		return booleanExpressionToPredicate(
				criteriaBuilder,
				criteriaBuilder.function( SpatialFunction.equals.toString(), boolean.class,
						geometry1, geometry2
				)
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially equal" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry value
	 *
	 * @return "spatially equal" predicate
	 *
	 * @see #eq(CriteriaBuilder, Expression, Expression)
	 */
	public static Predicate eq(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Geometry geometry2) {
		return eq( criteriaBuilder, geometry1,
				criteriaBuilder.literal( geometry2 )
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially within" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry expression
	 *
	 * @return "spatially within" predicate
	 *
	 * @see SpatialRestrictions#within(String, Geometry)
	 */
	public static Predicate within(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Expression<? extends Geometry> geometry2) {
		return booleanExpressionToPredicate(
				criteriaBuilder,
				criteriaBuilder.function( SpatialFunction.within.toString(), boolean.class,
						geometry1, geometry2
				)
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially within" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry value
	 *
	 * @return "spatially within" predicate
	 *
	 * @see #within(CriteriaBuilder, Expression, Expression)
	 */
	public static Predicate within(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Geometry geometry2) {
		return within( criteriaBuilder, geometry1,
				criteriaBuilder.literal( geometry2 )
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially contains" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry expression
	 *
	 * @return "spatially contains" predicate
	 *
	 * @see SpatialRestrictions#contains(String, Geometry)
	 */
	public static Predicate contains(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Expression<? extends Geometry> geometry2) {
		return booleanExpressionToPredicate(
				criteriaBuilder,
				criteriaBuilder.function( SpatialFunction.contains.toString(), boolean.class,
						geometry1, geometry2
				)
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially contains" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry value
	 *
	 * @return "spatially contains" predicate
	 *
	 * @see #contains(CriteriaBuilder, Expression, Expression)
	 */
	public static Predicate contains(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Geometry geometry2) {
		return contains( criteriaBuilder, geometry1,
				criteriaBuilder.literal( geometry2 )
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially crosses" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry expression
	 *
	 * @return "spatially crosses" predicate
	 *
	 * @see SpatialRestrictions#crosses(String, Geometry)
	 */
	public static Predicate crosses(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Expression<? extends Geometry> geometry2) {
		return booleanExpressionToPredicate(
				criteriaBuilder,
				criteriaBuilder.function( SpatialFunction.crosses.toString(), boolean.class,
						geometry1, geometry2
				)
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially crosses" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry value
	 *
	 * @return "spatially crosses" predicate
	 *
	 * @see #crosses(CriteriaBuilder, Expression, Expression)
	 */
	public static Predicate crosses(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Geometry geometry2) {
		return crosses( criteriaBuilder, geometry1,
				criteriaBuilder.literal( geometry2 )
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially disjoint" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry expression
	 *
	 * @return "spatially disjoint" predicate
	 *
	 * @see SpatialRestrictions#disjoint(String, Geometry)
	 */
	public static Predicate disjoint(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Expression<? extends Geometry> geometry2) {
		return booleanExpressionToPredicate(
				criteriaBuilder,
				criteriaBuilder.function( SpatialFunction.disjoint.toString(), boolean.class,
						geometry1, geometry2
				)
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially disjoint" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry value
	 *
	 * @return "spatially disjoint" predicate
	 *
	 * @see #disjoint(CriteriaBuilder, Expression, Expression)
	 */
	public static Predicate disjoint(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Geometry geometry2) {
		return disjoint( criteriaBuilder, geometry1,
				criteriaBuilder.literal( geometry2 )
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially intersects" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry expression
	 *
	 * @return "spatially intersects" predicate
	 *
	 * @see SpatialRestrictions#intersects(String, Geometry)
	 */
	public static Predicate intersects(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Expression<? extends Geometry> geometry2) {
		return booleanExpressionToPredicate(
				criteriaBuilder,
				criteriaBuilder.function( SpatialFunction.intersects.toString(), boolean.class,
						geometry1, geometry2
				)
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially intersects" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry value
	 *
	 * @return "spatially intersects" predicate
	 *
	 * @see #intersects(CriteriaBuilder, Expression, Expression)
	 */
	public static Predicate intersects(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Geometry geometry2) {
		return intersects( criteriaBuilder, geometry1,
				criteriaBuilder.literal( geometry2 )
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially overlaps" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry expression
	 *
	 * @return "spatially overlaps" predicate
	 *
	 * @see SpatialRestrictions#overlaps(String, Geometry)
	 */
	public static Predicate overlaps(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Expression<? extends Geometry> geometry2) {
		return booleanExpressionToPredicate(
				criteriaBuilder,
				criteriaBuilder.function( SpatialFunction.overlaps.toString(), boolean.class,
						geometry1, geometry2
				)
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially overlaps" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry value
	 *
	 * @return "spatially overlaps" predicate
	 *
	 * @see #overlaps(CriteriaBuilder, Expression, Expression)
	 */
	public static Predicate overlaps(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Geometry geometry2) {
		return overlaps( criteriaBuilder, geometry1,
				criteriaBuilder.literal( geometry2 )
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially touches" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry expression
	 *
	 * @return "spatially touches" predicate
	 *
	 * @see SpatialRestrictions#touches(String, Geometry)
	 */
	public static Predicate touches(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Expression<? extends Geometry> geometry2) {
		return booleanExpressionToPredicate(
				criteriaBuilder,
				criteriaBuilder.function( SpatialFunction.touches.toString(), boolean.class,
						geometry1, geometry2
				)
		);
	}

	/**
	 * Create a predicate for testing the arguments for "spatially touches" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry value
	 *
	 * @return "spatially touches" predicate
	 *
	 * @see #touches(CriteriaBuilder, Expression, Expression)
	 */
	public static Predicate touches(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Geometry geometry2) {
		return touches( criteriaBuilder, geometry1,
				criteriaBuilder.literal( geometry2 )
		);
	}

	/**
	 * Create a predicate for testing the arguments for "distance within" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry expression
	 * @param distance distance expression
	 *
	 * @return "distance within" predicate
	 *
	 * @see SpatialRestrictions#distanceWithin(String, Geometry, double)
	 */
	public static Predicate distanceWithin(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Expression<? extends Geometry> geometry2, Expression<Double> distance) {
		return booleanExpressionToPredicate(
				criteriaBuilder,
				criteriaBuilder.function( SpatialFunction.dwithin.toString(), boolean.class,
						geometry1, geometry2, distance
				)
		);
	}

	/**
	 * Create a predicate for testing the arguments for "distance within" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry value
	 * @param distance distance expression
	 *
	 * @return "distance within" predicate
	 *
	 * @see #distanceWithin(CriteriaBuilder, Expression, Expression, Expression)
	 */
	public static Predicate distanceWithin(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Geometry geometry2, Expression<Double> distance) {
		return distanceWithin( criteriaBuilder, geometry1,
				criteriaBuilder.literal( geometry2 ), distance
		);
	}

	/**
	 * Create a predicate for testing the arguments for "distance within" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry value
	 * @param distance distance value
	 *
	 * @return "distance within" predicate
	 *
	 * @see #distanceWithin(CriteriaBuilder, Expression, Expression, Expression)
	 */
	public static Predicate distanceWithin(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Geometry geometry2, double distance) {
		return distanceWithin( criteriaBuilder, geometry1,
				criteriaBuilder.literal( geometry2 ), criteriaBuilder.literal( distance )
		);
	}

	/**
	 * Create a predicate for testing the arguments for "distance within" constraint.
	 *
	 * @param geometry1 geometry expression
	 * @param geometry2 geometry expression
	 * @param distance distance value
	 *
	 * @return "distance within" predicate
	 *
	 * @see #distanceWithin(CriteriaBuilder, Expression, Expression, Expression)
	 */
	public static Predicate distanceWithin(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry1,
			Expression<? extends Geometry> geometry2, double distance) {
		return distanceWithin( criteriaBuilder, geometry1, geometry2,
				criteriaBuilder.literal( distance )
		);
	}

	/**
	 * Create a predicate for testing the arguments for "having srid" constraint.
	 *
	 * @param geometry geometry expression
	 * @param srid SRID expression
	 *
	 * @return "having srid" predicate
	 *
	 * @see SpatialRestrictions#havingSRID(String, int)
	 */
	public static Predicate havingSRID(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry,
			Expression<Integer> srid) {
		return criteriaBuilder.equal(
				criteriaBuilder.function( SpatialFunction.srid.toString(), int.class, geometry ),
				srid
		);
	}

	/**
	 * Create a predicate for testing the arguments for "having srid" constraint.
	 *
	 * @param geometry geometry expression
	 * @param srid SRID expression
	 *
	 * @return "having srid" predicate
	 *
	 * @see #havingSRID(CriteriaBuilder, Expression, Expression)
	 */
	public static Predicate havingSRID(
			CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry,
			int srid) {
		return havingSRID( criteriaBuilder, geometry,
				criteriaBuilder.literal( srid )
		);
	}

	/**
	 * Create a predicate for testing the arguments for "is empty" constraint.
	 *
	 * @param geometry geometry expression
	 *
	 * @return "is empty" predicate
	 *
	 * @see SpatialRestrictions#isEmpty(String)
	 */
	public static Predicate isEmpty(CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry) {
		return booleanExpressionToPredicate(
				criteriaBuilder,
				criteriaBuilder.function( SpatialFunction.isempty.toString(), boolean.class,
						geometry
				)
		);
	}

	/**
	 * Create a predicate for testing the arguments for "is not empty" constraint.
	 *
	 * @param geometry geometry expression
	 *
	 * @return "is not empty" predicate
	 *
	 * @see SpatialRestrictions#isNotEmpty(String)
	 */
	public static Predicate isNotEmpty(CriteriaBuilder criteriaBuilder, Expression<? extends Geometry> geometry) {
		return isEmpty( criteriaBuilder, geometry )
				.not();
	}

	private static Predicate booleanExpressionToPredicate(
			CriteriaBuilder criteriaBuilder,
			Expression<Boolean> expression) {
		return criteriaBuilder.equal( expression, true );
	}
}
