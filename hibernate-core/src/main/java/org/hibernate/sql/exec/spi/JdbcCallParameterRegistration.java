/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */

package org.hibernate.sql.exec.spi;

import java.sql.CallableStatement;
import jakarta.persistence.ParameterMode;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.query.BindableType;
import org.hibernate.query.OutputableType;
import org.hibernate.sql.exec.internal.JdbcCallRefCursorExtractorImpl;

/**
 * @author Steve Ebersole
 */
public interface JdbcCallParameterRegistration {

	String getName();

	ParameterMode getParameterMode();

	void registerParameter(
			CallableStatement callableStatement,
			SharedSessionContractImplementor session);

	JdbcParameterBinder getParameterBinder();

	JdbcCallParameterExtractor<?> getParameterExtractor();

	JdbcCallRefCursorExtractorImpl getRefCursorExtractor();

	OutputableType<?> getParameterType();
}
