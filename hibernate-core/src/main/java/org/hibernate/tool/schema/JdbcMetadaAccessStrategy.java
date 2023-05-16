/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.tool.schema;

import java.util.Locale;
import java.util.Map;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.internal.util.config.ConfigurationHelper;

/**
 * Determines how JDBC metadata is read by the schema management tooling.
 *
 * @author Andrea Boriero
 */
public enum JdbcMetadaAccessStrategy {
	/**
	 * The {@link org.hibernate.tool.schema.spi.SchemaMigrator} and {@link org.hibernate.tool.schema.spi.SchemaValidator}
	 * execute one {@link java.sql.DatabaseMetaData#getTables(String, String, String, String[])} call for each
	 * {@link jakarta.persistence.Entity} in order to determine if a corresponding database table exists.
	 */
	INDIVIDUALLY,

	/**
	 * The {@link org.hibernate.tool.schema.spi.SchemaMigrator} and {@link org.hibernate.tool.schema.spi.SchemaValidator}
	 * execute a single {@link java.sql.DatabaseMetaData#getTables(String, String, String, String[])} call to retrieve
	 * all the database table in order to determine all the {@link jakarta.persistence.Entity} have a mapped database
	 * tables.
	 * <p>
	 * This strategy is the default one, and it may require {@value AvailableSettings#DEFAULT_CATALOG} and/or
	 * {@value AvailableSettings#DEFAULT_SCHEMA} values to be provided.
	 */
	GROUPED;

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}

	public static JdbcMetadaAccessStrategy interpretSetting(Map options) {
		if ( options == null ) {
			return interpretHbm2ddlSetting( null );
		}
		else if ( ConfigurationHelper.getBoolean( AvailableSettings.ENABLE_SYNONYMS, options ) ) {
			// Use of synonyms can cause issues during schema validation or schema update when GROUPED strategy is used (especially in Oracle)
			return INDIVIDUALLY;
		}
		else {
			return interpretHbm2ddlSetting( options.get( AvailableSettings.HBM2DDL_JDBC_METADATA_EXTRACTOR_STRATEGY ) );
		}
	}

	public static JdbcMetadaAccessStrategy interpretHbm2ddlSetting(Object value) {
		if ( value == null ) {
			return GROUPED;
		}
		else {
			final String name = value.toString().trim();
			if ( name.isEmpty() ) {
				return GROUPED;
			}
			for ( JdbcMetadaAccessStrategy strategy: values() ) {
				if ( strategy.toString().equalsIgnoreCase(name) ) {
					return strategy;
				}
			}
			throw new IllegalArgumentException( "Unrecognized '" + AvailableSettings.HBM2DDL_JDBC_METADATA_EXTRACTOR_STRATEGY + "' value: '" + value + "'");
		}
	}
}
