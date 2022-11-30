/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.tuple;

import org.hibernate.FetchMode;
import org.hibernate.engine.spi.CascadeStyle;

/**
 * @author Steve Ebersole
 */
public interface NonIdentifierAttribute extends Attribute {
	boolean isLazy();

	boolean isInsertable();

	boolean isUpdateable();

	Generator getValueGenerationStrategy();

	boolean isNullable();

	/**
	 * @deprecated Use {@link NonIdentifierAttribute#isDirtyCheckable()} instead
	 */
	@Deprecated
	boolean isDirtyCheckable(boolean hasUninitializedProperties);

	boolean isDirtyCheckable();

	boolean isVersionable();

	CascadeStyle getCascadeStyle();

	FetchMode getFetchMode();
}
