/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.model.domain.internal;

import java.util.Collections;
import java.util.List;

import org.hibernate.boot.model.domain.BasicValueMapping;
import org.hibernate.mapping.IndexedCollection;
import org.hibernate.metamodel.model.creation.spi.RuntimeModelCreationContext;
import org.hibernate.metamodel.model.domain.spi.AbstractCollectionIndex;
import org.hibernate.metamodel.model.domain.spi.CollectionIndexBasic;
import org.hibernate.metamodel.model.domain.spi.ConvertibleNavigable;
import org.hibernate.metamodel.model.domain.spi.PersistentCollectionDescriptor;
import org.hibernate.metamodel.model.relational.spi.Column;
import org.hibernate.sql.ast.tree.spi.expression.Expression;
import org.hibernate.sql.results.internal.ScalarQueryResultImpl;
import org.hibernate.sql.results.spi.QueryResult;
import org.hibernate.sql.results.spi.QueryResultCreationContext;
import org.hibernate.type.converter.spi.AttributeConverterDefinition;
import org.hibernate.type.descriptor.spi.ValueBinder;
import org.hibernate.type.descriptor.spi.ValueExtractor;
import org.hibernate.type.spi.BasicType;

/**
 * @author Steve Ebersole
 */
public class CollectionIndexBasicImpl<J>
		extends AbstractCollectionIndex<J>
		implements CollectionIndexBasic<J>, ConvertibleNavigable<J> {

	private final BasicType<J> basicType;
	private final Column column;
	private final AttributeConverterDefinition attributeConverter;

	public CollectionIndexBasicImpl(
			PersistentCollectionDescriptor persister,
			IndexedCollection mappingBinding,
			RuntimeModelCreationContext creationContext) {
		super( persister );

		final BasicValueMapping valueMapping = (BasicValueMapping) mappingBinding.getIndex();
		this.column  = creationContext.getDatabaseObjectResolver().resolveColumn( valueMapping.getMappedColumn() );

		this.attributeConverter = valueMapping.getAttributeConverterDefinition();

		// todo (6.0) : SimpleValue -> BasicType
		this.basicType = null;
	}

	@Override
	public AttributeConverterDefinition getAttributeConverter() {
		return attributeConverter;
	}

	@Override
	public Column getBoundColumn() {
		return column;
	}

	@Override
	public List<Column> getColumns() {
		return Collections.singletonList( getBoundColumn() );
	}

	@Override
	public BasicType<J> getBasicType() {
		return basicType;
	}

	@Override
	public QueryResult createQueryResult(
			Expression expression,
			String resultVariable,
			QueryResultCreationContext creationContext) {
		return new ScalarQueryResultImpl(
				resultVariable,
				creationContext.getSqlSelectionResolver().resolveSqlSelection(
						creationContext.getSqlSelectionResolver().resolveSqlExpression(
								creationContext.currentColumnReferenceSource(),
								column
						)
				),
				this
	  	);
	}

	@Override
	public ValueBinder getValueBinder() {
		return basicType.getValueBinder();
	}

	@Override
	public ValueExtractor getValueExtractor() {
		return basicType.getValueExtractor();
	}
}
