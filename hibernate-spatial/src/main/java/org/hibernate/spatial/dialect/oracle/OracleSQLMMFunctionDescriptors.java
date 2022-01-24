/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.spatial.dialect.oracle;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.query.sqm.function.NamedSqmFunctionDescriptor;
import org.hibernate.query.sqm.function.SqmFunctionDescriptor;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.spatial.CommonSpatialFunction;
import org.hibernate.spatial.FunctionKey;
import org.hibernate.spatial.KeyedSqmFunctionDescriptors;
import org.hibernate.type.BasicTypeReference;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.StandardBasicTypes;

public class OracleSQLMMFunctionDescriptors implements KeyedSqmFunctionDescriptors {
	private final Map<FunctionKey, SqmFunctionDescriptor> map = new HashMap<>();
	private final BasicTypeRegistry typeRegistry;

	public OracleSQLMMFunctionDescriptors(FunctionContributions functionContributions) {
		typeRegistry = functionContributions.getTypeConfiguration().getBasicTypeRegistry();
		registerSQLMMFunctions();
	}

	private void registerSQLMMFunctions() {
		addSTFunction( CommonSpatialFunction.ST_ASTEXT, "GET_WKT", StandardBasicTypes.STRING );
		addSTFunction( CommonSpatialFunction.ST_GEOMETRYTYPE, StandardBasicTypes.STRING );
		addSTFunction( CommonSpatialFunction.ST_ASBINARY, "GET_WKB", StandardBasicTypes.BINARY );
		addSTFunction( CommonSpatialFunction.ST_DIMENSION, StandardBasicTypes.INTEGER );
		addSTFunction( CommonSpatialFunction.ST_ISEMPTY, StandardBasicTypes.BOOLEAN );
		addSTFunction( CommonSpatialFunction.ST_SRID, StandardBasicTypes.INTEGER );
		addSTFunction( CommonSpatialFunction.ST_ISSIMPLE, StandardBasicTypes.BOOLEAN );
		addSTFunction( CommonSpatialFunction.ST_OVERLAPS, "ST_OVERLAP", StandardBasicTypes.BOOLEAN );
		addSTFunction( CommonSpatialFunction.ST_INTERSECTS, StandardBasicTypes.BOOLEAN );
		addSTFunction( CommonSpatialFunction.ST_CONTAINS, StandardBasicTypes.BOOLEAN );
		addSTFunction( CommonSpatialFunction.ST_DISJOINT, StandardBasicTypes.BOOLEAN );
		addSTFunction( CommonSpatialFunction.ST_CROSSES, StandardBasicTypes.BOOLEAN );
		addSTFunction( CommonSpatialFunction.ST_CONTAINS, StandardBasicTypes.BOOLEAN );
		addSTFunction( CommonSpatialFunction.ST_TOUCHES, StandardBasicTypes.BOOLEAN );
		addSTFunction( CommonSpatialFunction.ST_WITHIN, StandardBasicTypes.BOOLEAN );
		addSTFunction( CommonSpatialFunction.ST_EQUALS, StandardBasicTypes.BOOLEAN );
		addSTFunction( CommonSpatialFunction.ST_DISTANCE, StandardBasicTypes.DOUBLE );
		addSTFunction( CommonSpatialFunction.ST_RELATE, new STRelateFunction( typeRegistry ) );
		addSTFunction( CommonSpatialFunction.ST_DIFFERENCE );
		addSTFunction( CommonSpatialFunction.ST_INTERSECTION );
		addSTFunction( CommonSpatialFunction.ST_SYMDIFFERENCE );
		addSTFunction( CommonSpatialFunction.ST_BUFFER );
		addSTFunction( CommonSpatialFunction.ST_UNION );
		addSTFunction( CommonSpatialFunction.ST_BOUNDARY );
		addSTFunction( CommonSpatialFunction.ST_CONVEXHULL );
		addSTFunction( CommonSpatialFunction.ST_ENVELOPE );


	}

	private <T> void addSTFunction(CommonSpatialFunction func, String stMethod, BasicTypeReference<T> tpe) {
		map.put( func.getKey(), new OracleSpatialSQLMMFunction(
				func.getKey().getName(),
				stMethod,
				func.getNumArgs(),
				StandardFunctionReturnTypeResolvers.invariant(
						typeRegistry.resolve( tpe ) )
		) );
	}

	private void addSTFunction(CommonSpatialFunction func, String stMethod) {
		map.put(
				func.getKey(),
				new OracleSpatialSQLMMFunction( func.getKey().getName(), stMethod, func.getNumArgs(), null, true )
		);
	}

	private <T> void addSTFunction(CommonSpatialFunction func, BasicTypeReference<T> tpe) {
		addSTFunction( func, func.getKey().getName().toUpperCase( Locale.ROOT ), tpe );
	}

	private void addSTFunction(CommonSpatialFunction func) {
		addSTFunction( func, func.getKey().getName().toUpperCase( Locale.ROOT ) );
	}

	private void addSTFunction(CommonSpatialFunction func, OracleSpatialFunction descriptor) {
		map.put( func.getKey(), descriptor );
	}



	@Override
	public Map<FunctionKey, SqmFunctionDescriptor> asMap() {
		return Collections.unmodifiableMap( map );
	}
}

