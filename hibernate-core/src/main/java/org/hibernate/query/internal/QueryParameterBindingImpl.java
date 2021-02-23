/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.query.internal;

import java.util.Arrays;
import java.util.Collection;
import javax.persistence.TemporalType;

import org.hibernate.metamodel.mapping.MappingModelExpressable;
import org.hibernate.metamodel.model.domain.AllowableParameterType;
import org.hibernate.query.QueryParameter;
import org.hibernate.query.spi.QueryParameterBinding;
import org.hibernate.query.spi.QueryParameterBindingTypeResolver;
import org.hibernate.query.spi.QueryParameterBindingValidator;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * The standard Hibernate QueryParameterBinding implementation
 *
 * @author Steve Ebersole
 */
public class QueryParameterBindingImpl<T> implements QueryParameterBinding<T> {
	private final QueryParameter<T> queryParameter;
	private final QueryParameterBindingTypeResolver typeResolver;
	private final boolean isBindingValidationRequired;

	private boolean isBound;
	private boolean isMultiValued;

	private AllowableParameterType<T> bindType;
	private MappingModelExpressable<T> type;
	private TemporalType explicitTemporalPrecision;

	private T bindValue;
	private Collection<T> bindValues;

	// todo (6.0) : add TemporalType to QueryParameter and use to default precision here

	public QueryParameterBindingImpl(
			QueryParameter<T> queryParameter,
			QueryParameterBindingTypeResolver typeResolver,
			boolean isBindingValidationRequired) {
		this.queryParameter = queryParameter;
		this.typeResolver = typeResolver;
		this.isBindingValidationRequired = isBindingValidationRequired;
		this.bindType = queryParameter.getHibernateType();
	}

	public QueryParameterBindingImpl(
			QueryParameter<T> queryParameter,
			QueryParameterBindingTypeResolver typeResolver,
			AllowableParameterType<T> bindType,
			boolean isBindingValidationRequired) {
		this.queryParameter = queryParameter;
		this.typeResolver = typeResolver;
		this.isBindingValidationRequired = isBindingValidationRequired;
		this.bindType = bindType;
	}

	@Override
	public AllowableParameterType<T> getBindType() {
		return bindType;
	}

	@Override
	public TemporalType getExplicitTemporalPrecision() {
		return explicitTemporalPrecision;
	}

	@Override
	public boolean isBound() {
		return isBound;
	}

	@Override
	public boolean isMultiValued() {
		return isMultiValued;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// single-valued binding support

	@Override
	public T getBindValue() {
		if ( isMultiValued ) {
			throw new IllegalStateException( "Binding is multi-valued; illegal call to #getBindValue" );
		}

		return bindValue;
	}

	@Override
	public void setBindValue(T value) {
		if ( handleAsMultiValue( value ) ) {
			return;
		}

		if ( isBindingValidationRequired ) {
			validate( value );
		}

		bindValue( value );
	}

	private boolean handleAsMultiValue(T value) {
		if ( ! queryParameter.allowsMultiValuedBinding() ) {
			return false;
		}

		if ( value == null ) {
			return false;
		}

		if ( value instanceof Collection ) {
			setBindValues( (Collection) value );
			return true;
		}

		if ( value.getClass().isArray() ) {
			setBindValues( (Collection) Arrays.asList( (Object[]) value ) );
			return true;
		}

		return false;
	}

	private void bindValue(T value) {
		this.isBound = true;
		this.bindValue = value;

		if ( bindType == null ) {
			//noinspection unchecked
			this.bindType = (AllowableParameterType) typeResolver.resolveParameterBindType( value );
		}
	}

	@Override
	public void setBindValue(T value, AllowableParameterType<T> clarifiedType) {
		if ( handleAsMultiValue( value ) ) {
			return;
		}

		if ( isBindingValidationRequired ) {
			validate( value, clarifiedType );
		}

		bindValue( value );

		if ( clarifiedType != null ) {
			this.bindType = clarifiedType;
		}
	}

	@Override
	public void setBindValue(T value, TemporalType temporalTypePrecision) {
		if ( handleAsMultiValue( value ) ) {
			return;
		}

		if ( isBindingValidationRequired ) {
			validate( value, temporalTypePrecision );
		}

		bindValue( value );

		//noinspection unchecked
		this.bindType = (AllowableParameterType) BindingTypeHelper.INSTANCE.resolveDateTemporalTypeVariant(
				getBindType().getExpressableJavaTypeDescriptor().getJavaTypeClass(),
				getBindType()
		);

		this.explicitTemporalPrecision = temporalTypePrecision;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// multi-valued binding support

	@Override
	public Collection<T> getBindValues() {
		if ( !isMultiValued ) {
			throw new IllegalStateException( "Binding is not multi-valued; illegal call to #getBindValues" );
		}

		return bindValues;
	}

	@Override
	public void setBindValues(Collection<T> values) {
		this.isBound = true;
		this.isMultiValued = true;

		this.bindValue = null;
		this.bindValues = values;

		if ( bindType == null && !values.isEmpty() ) {
			//noinspection unchecked
			this.bindType = (AllowableParameterType) typeResolver.resolveParameterBindType( values.iterator().next() );
		}

	}

	@Override
	public void setBindValues(Collection<T> values, AllowableParameterType<T> clarifiedType) {
		setBindValues( values );
		if ( clarifiedType != null ) {
			this.bindType = clarifiedType;
		}
	}

	@Override
	public void setBindValues(
			Collection<T> values,
			TemporalType temporalTypePrecision,
			TypeConfiguration typeConfiguration) {
		setBindValues( values );

		this.bindType = BindingTypeHelper.INSTANCE.resolveTemporalPrecision(
				temporalTypePrecision,
				bindType,
				typeConfiguration
		);

		this.explicitTemporalPrecision = temporalTypePrecision;
	}

	@Override
	public MappingModelExpressable getType() {
		return type;
	}

	@Override
	public void setType(MappingModelExpressable type) {
		this.type = type;
	}

	private void validate(T value) {
		QueryParameterBindingValidator.INSTANCE.validate( getBindType(), value );
	}

	private void validate(T value, AllowableParameterType clarifiedType) {
		QueryParameterBindingValidator.INSTANCE.validate( clarifiedType, value );
	}

	private void validate(T value, TemporalType clarifiedTemporalType) {
		QueryParameterBindingValidator.INSTANCE.validate( getBindType(), value, clarifiedTemporalType );
	}
}
