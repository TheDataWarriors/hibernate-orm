/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.tool.schema.spi;

import java.util.Map;

import org.hibernate.Incubating;
import org.hibernate.service.Service;
import org.hibernate.tool.schema.internal.exec.GenerationTarget;
import org.hibernate.tool.schema.internal.exec.JdbcContext;

/**
 * Contract for schema management tool integration.
 *
 * @author Steve Ebersole
 */
@Incubating
public interface SchemaManagementTool extends Service {
	SchemaCreator getSchemaCreator(Map<String,Object> options);
	SchemaDropper getSchemaDropper(Map<String,Object> options);
	SchemaMigrator getSchemaMigrator(Map<String,Object> options);
	SchemaValidator getSchemaValidator(Map<String,Object> options);
	SchemaTruncator getSchemaTruncator(Map<String,Object> options);

	/**
	 * This allows to set an alternative implementation for the Database
	 * generation target.
	 * Used by Hibernate Reactive so that it can use the reactive database
	 * access rather than needing a JDBC connection.
	 * @param generationTarget the custom instance to use.
	 */
	void setCustomDatabaseGenerationTarget(GenerationTarget generationTarget);

	ExtractionTool getExtractionTool();

	/**
	 * Resolves the {@linkplain GenerationTarget targets} to which to
	 * send the DDL commands based on configuration
	 */
	GenerationTarget[] buildGenerationTargets(
			TargetDescriptor targetDescriptor,
			JdbcContext jdbcContext,
			Map<String, Object> options,
			boolean needsAutoCommit);
}
