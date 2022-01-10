/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.exec.internal;

import org.hibernate.query.AllowableParameterType;
import org.hibernate.sql.exec.spi.JdbcCallFunctionReturn;

import jakarta.persistence.ParameterMode;

/**
 * @author Steve Ebersole
 */
public class JdbcCallFunctionReturnImpl extends JdbcCallParameterRegistrationImpl implements JdbcCallFunctionReturn {
	public JdbcCallFunctionReturnImpl(
			AllowableParameterType ormType,
			JdbcCallParameterExtractorImpl parameterExtractor,
			JdbcCallRefCursorExtractorImpl refCursorExtractor) {
		super(
				null,
				1,
				ParameterMode.REF_CURSOR,
				ormType,
				null,
				parameterExtractor,
				refCursorExtractor
		);
	}
}
