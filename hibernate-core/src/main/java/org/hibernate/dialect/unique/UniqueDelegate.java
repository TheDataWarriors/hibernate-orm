/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect.unique;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UniqueKey;

/**
 * Dialect-level delegate in charge of applying "uniqueness" to a column.  Uniqueness can be defined
 * in 1 of 3 ways:<ol>
 *     <li>
 *         Add a unique constraint via separate alter table statements.  See {@link #getAlterTableToAddUniqueKeyCommand}.
 *         Also, see {@link #getAlterTableToDropUniqueKeyCommand}
 *     </li>
 *     <li>
 *			Add a unique constraint via dialect-specific syntax in table create statement.  See
 *			{@link #getTableCreationUniqueConstraintsFragment}
 *     </li>
 *     <li>
 *         Add "unique" syntax to the column itself.  See {@link #getColumnDefinitionUniquenessFragment}
 *     </li>
 * </ol>
 *
 * #1 & #2 are preferred, if possible; #3 should be solely a fall-back.
 * 
 * See HHH-7797.
 * 
 * @author Brett Meyer
 */
public interface UniqueDelegate {
	/**
	 * Get the fragment that can be used to make a column unique as part of its column definition.
	 * <p/>
	 * This is intended for dialects which do not support unique constraints
	 *
	 * @param column The column to which to apply the unique
	 * @return The fragment (usually "unique"), empty string indicates the uniqueness will be indicated using a
	 * different approach
	 * @deprecated Implement {@link #getColumnDefinitionUniquenessFragment(Column, SqlStringGenerationContext)} instead.
	 */
	@Deprecated
	default String getColumnDefinitionUniquenessFragment(Column column) {
		throw new IllegalStateException("getColumnDefinitionUniquenessFragment(...) was not implemented!");
	}

	/**
	 * Get the fragment that can be used to make a column unique as part of its column definition.
	 * <p/>
	 * This is intended for dialects which do not support unique constraints
	 * 
	 * @param column The column to which to apply the unique
	 * @param context A context for SQL string generation
	 * @return The fragment (usually "unique"), empty string indicates the uniqueness will be indicated using a
	 * different approach
	 */
	default String getColumnDefinitionUniquenessFragment(Column column, SqlStringGenerationContext context) {
		return getColumnDefinitionUniquenessFragment( column );
	}

	/**
	 * Get the fragment that can be used to apply unique constraints as part of table creation.  The implementation
	 * should iterate over the {@link org.hibernate.mapping.UniqueKey} instances for the given table (see
	 * {@link org.hibernate.mapping.Table#getUniqueKeyIterator()} and generate the whole fragment for all
	 * unique keys
	 * <p/>
	 * Intended for Dialects which support unique constraint definitions, but just not in separate ALTER statements.
	 *
	 * @param table The table for which to generate the unique constraints fragment
	 * @return The fragment, typically in the form {@code ", unique(col1, col2), unique( col20)"}.  NOTE: The leading
	 * comma is important!
	 * @deprecated Implement {@link #getTableCreationUniqueConstraintsFragment(Table, SqlStringGenerationContext)} instead.
	 */
	@Deprecated
	default String getTableCreationUniqueConstraintsFragment(Table table) {
		throw new IllegalStateException("getTableCreationUniqueConstraintsFragment(...) was not implemented!");
	}

	/**
	 * Get the fragment that can be used to apply unique constraints as part of table creation.  The implementation
	 * should iterate over the {@link org.hibernate.mapping.UniqueKey} instances for the given table (see
	 * {@link org.hibernate.mapping.Table#getUniqueKeyIterator()} and generate the whole fragment for all
	 * unique keys
	 * <p/>
	 * Intended for Dialects which support unique constraint definitions, but just not in separate ALTER statements.
	 *
	 * @param table The table for which to generate the unique constraints fragment
	 * @param context A context for SQL string generation
	 * @return The fragment, typically in the form {@code ", unique(col1, col2), unique( col20)"}.  NOTE: The leading
	 * comma is important!
	 */
	default String getTableCreationUniqueConstraintsFragment(Table table, SqlStringGenerationContext context) {
		return getTableCreationUniqueConstraintsFragment( table );
	}

	/**
	 * Get the SQL ALTER TABLE command to be used to create the given UniqueKey.
	 *
	 * @param uniqueKey The UniqueKey instance.  Contains all information about the columns
	 * @param metadata Access to the bootstrap mapping information
	 * @return The ALTER TABLE command
	 * @deprecated Implement {@link #getAlterTableToAddUniqueKeyCommand(UniqueKey, Metadata, SqlStringGenerationContext)} instead.
	 */
	@Deprecated
	default String getAlterTableToAddUniqueKeyCommand(UniqueKey uniqueKey, Metadata metadata) {
		throw new IllegalStateException("getAlterTableToAddUniqueKeyCommand(...) was not implemented!");
	}

	/**
	 * Get the SQL ALTER TABLE command to be used to create the given UniqueKey.
	 *
	 * @param uniqueKey The UniqueKey instance.  Contains all information about the columns
	 * @param metadata Access to the bootstrap mapping information
	 * @param context A context for SQL string generation
	 * @return The ALTER TABLE command
	 */
	default String getAlterTableToAddUniqueKeyCommand(UniqueKey uniqueKey, Metadata metadata,
			SqlStringGenerationContext context) {
		return getAlterTableToAddUniqueKeyCommand( uniqueKey, metadata );
	}

	/**
	 * Get the SQL ALTER TABLE command to be used to drop the given UniqueKey.
	 *
	 * @param uniqueKey The UniqueKey instance.  Contains all information about the columns
	 * @param metadata Access to the bootstrap mapping information
	 * @return The ALTER TABLE command
	 * @deprecated Implement {@link #getAlterTableToDropUniqueKeyCommand(UniqueKey, Metadata, SqlStringGenerationContext)} instead.
	 */
	@Deprecated
	default String getAlterTableToDropUniqueKeyCommand(UniqueKey uniqueKey, Metadata metadata) {
		throw new IllegalStateException("getAlterTableToDropUniqueKeyCommand(...) was not implemented!");
	}

	/**
	 * Get the SQL ALTER TABLE command to be used to drop the given UniqueKey.
	 *
	 * @param uniqueKey The UniqueKey instance.  Contains all information about the columns
	 * @param metadata Access to the bootstrap mapping information
	 * @param context A context for SQL string generation
	 * @return The ALTER TABLE command
	 */
	default String getAlterTableToDropUniqueKeyCommand(UniqueKey uniqueKey, Metadata metadata,
			SqlStringGenerationContext context) {
		return getAlterTableToDropUniqueKeyCommand( uniqueKey, metadata );
	}

}
