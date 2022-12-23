/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.persister.collection;
import org.hibernate.MappingException;
import org.hibernate.Remove;
import org.hibernate.boot.Metadata;
import org.hibernate.persister.entity.AbstractPropertyMapping;
import org.hibernate.type.CompositeType;
import org.hibernate.type.Type;

/**
 * @author Gavin King
 *
 * @deprecated Replaced by {@link org.hibernate.metamodel.mapping.internal.EmbeddedCollectionPart}
 */
@Deprecated(since = "6", forRemoval = true)
@Remove
public class CompositeElementPropertyMapping extends AbstractPropertyMapping {

	private final CompositeType compositeType;
	
	public CompositeElementPropertyMapping(
			String[] elementColumns,
			String[] elementColumnReaders,
			String[] elementColumnReaderTemplates, 
			String[] elementFormulaTemplates, 
			CompositeType compositeType,
			Metadata factory)
	throws MappingException {

		this.compositeType = compositeType;

		initComponentPropertyPaths(null, compositeType, elementColumns, elementColumnReaders,
				elementColumnReaderTemplates, elementFormulaTemplates, factory );

	}

	public Type getType() {
		return compositeType;
	}

	protected String getEntityName() {
		return compositeType.getName();
	}

}
