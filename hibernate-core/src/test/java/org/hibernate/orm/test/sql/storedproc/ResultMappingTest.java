/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.sql.storedproc;

import org.hibernate.boot.MetadataBuilder;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.procedure.ProcedureCall;
import org.hibernate.procedure.ProcedureOutputs;
import org.hibernate.result.Output;
import org.hibernate.result.ResultSetOutput;

import org.hibernate.testing.orm.junit.BaseSessionFactoryFunctionalTest;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.junit.jupiter.api.Test;

import static org.hibernate.testing.orm.junit.ExtraAssertions.assertTyping;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Steve Ebersole
 */
@RequiresDialect( H2Dialect.class )
public class ResultMappingTest extends BaseSessionFactoryFunctionalTest {

	@Override
	protected Class[] getAnnotatedClasses() {
		return new Class[] {
				H2ProcTesting.MyEntity.class
		};
	}

	@Override
	protected void applyMetadataBuilder(MetadataBuilder metadataBuilder) {
		super.applyMetadataBuilder( metadataBuilder );
		H2ProcTesting.applyProcDefinitions( metadataBuilder );
	}

	@Test
	public void testResultClass() {
		inTransaction(
				session -> {
					final ProcedureCall call = session.createStoredProcedureCall(
							"findOneUser",
							H2ProcTesting.MyEntity.class
					);
					final ProcedureOutputs procedureResult = call.getOutputs();
					final Output currentOutput = procedureResult.getCurrent();
					assertNotNull( currentOutput );
					final ResultSetOutput resultSetReturn = assertTyping( ResultSetOutput.class, currentOutput );
					final Object result = resultSetReturn.getSingleResult();
					assertTyping( H2ProcTesting.MyEntity.class, result );
					assertEquals( "Steve", ( (H2ProcTesting.MyEntity) result ).name );
				}
		);
	}

	@Test
	public void testMappingAllFields() {
		inTransaction(
				session -> {
					final ProcedureCall call = session.createStoredProcedureCall( "findOneUser", "all-fields" );
					final ProcedureOutputs procedureResult = call.getOutputs();
					final Output currentOutput = procedureResult.getCurrent();
					assertNotNull( currentOutput );
					final ResultSetOutput resultSetReturn = assertTyping( ResultSetOutput.class, currentOutput );
					final Object result = resultSetReturn.getSingleResult();
					assertTyping( H2ProcTesting.MyEntity.class, result );
					assertEquals( "Steve", ( (H2ProcTesting.MyEntity) result ).name );
				}
		);
	}

	@Test
	public void testMappingSomeFields() {
		inTransaction(
				session -> {
					final ProcedureCall call = session.createStoredProcedureCall( "findOneUser", "some-fields" );
					final ProcedureOutputs procedureResult = call.getOutputs();
					final Output currentOutput = procedureResult.getCurrent();
					assertNotNull( currentOutput );
					final ResultSetOutput resultSetReturn = assertTyping( ResultSetOutput.class, currentOutput );
					final Object result = resultSetReturn.getSingleResult();
					assertTyping( H2ProcTesting.MyEntity.class, result );
					assertEquals( "Steve", ( (H2ProcTesting.MyEntity) result ).name );
				}
		);
	}

	@Test
	public void testMappingNoFields() {
		inTransaction(
				session -> {
					final ProcedureCall call = session.createStoredProcedureCall( "findOneUser", "no-fields" );
					final ProcedureOutputs procedureResult = call.getOutputs();
					final Output currentOutput = procedureResult.getCurrent();
					assertNotNull( currentOutput );
					final ResultSetOutput resultSetReturn = assertTyping( ResultSetOutput.class, currentOutput );
					final Object result = resultSetReturn.getSingleResult();
					assertTyping( H2ProcTesting.MyEntity.class, result );
					assertEquals( "Steve", ( (H2ProcTesting.MyEntity) result ).name );
				}
		);
	}
}
