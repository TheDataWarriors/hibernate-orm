/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect;

import org.hibernate.internal.log.SubSystemLogging;

import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import static org.jboss.logging.Logger.Level.INFO;

/**
 * Logging related to Hibernate dialects
 */
@SubSystemLogging(
		name = DialectLogging.LOGGER_NAME,
		description = "Logging related to the dialects of SQL implemented by particular RDBMS"
)
@MessageLogger(projectCode = "HHH")
public interface DialectLogging {
	String LOGGER_NAME = SubSystemLogging.BASE + ".dialect";
	Logger DIALECT_LOGGER = Logger.getLogger(LOGGER_NAME);
	DialectLogging DIALECT_MESSAGE_LOGGER = Logger.getMessageLogger(DialectLogging.class, LOGGER_NAME);

	boolean DEBUG_ENABLED = DIALECT_LOGGER.isDebugEnabled();
	boolean TRACE_ENABLED = DIALECT_LOGGER.isTraceEnabled();

	@LogMessage(level = INFO)
	@Message(value = "Using dialect: %s", id = 400)
	void usingDialect(Dialect dialect);
}
