/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.mapping;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.type.Type;

/**
 * A value which is "typed" by reference to some other
 * value (for example, a foreign key is typed by the
 * referenced primary key).
 *
 * @author Gavin King
 */
public class DependantValue extends SimpleValue implements Resolvable {
	private KeyValue wrappedValue;
	private boolean nullable;
	private boolean updateable;

	public DependantValue(MetadataBuildingContext buildingContext, Table table, KeyValue prototype) {
		super( buildingContext, table );
		this.wrappedValue = prototype;
	}

	public Type getType() throws MappingException {
		return wrappedValue.getType();
	}

	@Override
	public void setTypeUsingReflection(String className, String propertyName) {}

	@Override
	public Object accept(ValueVisitor visitor) {
		return visitor.accept(this);
	}

	@Override
	public boolean isNullable() {
		return nullable;
	
	}
	
	public void setNullable(boolean nullable) {
		this.nullable = nullable;
	}

	@Override
	public boolean isUpdateable() {
		return updateable;
	}
	
	public void setUpdateable(boolean updateable) {
		this.updateable = updateable;
	}

	@Override
	public boolean isSame(SimpleValue other) {
		return other instanceof DependantValue && isSame( (DependantValue) other );
	}

	public boolean isSame(DependantValue other) {
		return super.isSame( other )
				&& isSame( wrappedValue, other.wrappedValue );
	}

	@Override
	public boolean resolve(MetadataBuildingContext buildingContext) {
		resolve();
		return true;
	}

	@Override
	public BasicValue.Resolution<?> resolve() {
		if ( wrappedValue instanceof BasicValue ) {
			return ( (BasicValue) wrappedValue ).resolve();
		}
		// not sure it is ever possible
		throw new UnsupportedOperationException("Trying to resolve the wrapped value but it is non a BasicValue");
	}

}
