/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg;
import java.util.Locale;
import org.hibernate.AssertionFailure;
import org.hibernate.internal.util.StringHelper;

/**
 * @author Emmanuel Bernard
 */
public class DefaultComponentSafeNamingStrategy extends PersistenceStandardNamingStrategy {
	public static final NamingStrategy INSTANCE = new DefaultComponentSafeNamingStrategy();

	protected static String addUnderscores(String name) {
		return name.replace( '.', '_' ).toLowerCase(Locale.ROOT);
	}

	@Override
	public String propertyToColumnName(String propertyName) {
		return addUnderscores( propertyName );
	}

	@Override
	public String collectionTableName(
			String ownerEntity, String ownerEntityTable, String associatedEntity, String associatedEntityTable,
			String propertyName
	) {
		String entityTableName = associatedEntityTable != null ? associatedEntityTable : addUnderscores(propertyName);
		return tableName( ownerEntityTable + "_" + entityTableName );
	}


	public String foreignKeyColumnName(
			String propertyName, String propertyEntityName, String propertyTableName, String referencedColumnName
	) {
		String header = propertyName != null ? addUnderscores( propertyName ) : propertyTableName;
		if ( header == null ) throw new AssertionFailure( "NamingStrategy not properly filled" );
		return columnName( header + "_" + referencedColumnName );
	}

	@Override
	public String logicalColumnName(String columnName, String propertyName) {
		return StringHelper.isNotEmpty( columnName ) ? columnName : propertyName;
	}

	@Override
	public String logicalCollectionTableName(
			String tableName, String ownerEntityTable, String associatedEntityTable, String propertyName
	) {
		if ( tableName != null ) {
			return tableName;
		}
		else {
			String entityTableName = associatedEntityTable != null ? associatedEntityTable : propertyName;
			return ownerEntityTable + "_" + entityTableName;
		}

	}

	@Override
	public String logicalCollectionColumnName(String columnName, String propertyName, String referencedColumn) {
		return StringHelper.isNotEmpty( columnName ) ?
				columnName :
				propertyName + "_" + referencedColumn;
	}

}
