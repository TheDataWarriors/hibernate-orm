/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type;

import java.io.Serializable;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.metamodel.model.convert.spi.BasicValueConverter;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.BasicJavaType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.JavaTypedExpressible;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.internal.UserTypeJavaTypeWrapper;
import org.hibernate.type.internal.UserTypeSqlTypeAdapter;
import org.hibernate.type.internal.UserTypeVersionJavaTypeWrapper;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.EnhancedUserType;
import org.hibernate.usertype.LoggableUserType;
import org.hibernate.usertype.UserType;
import org.hibernate.usertype.UserVersionType;

/**
 * Adapts {@link UserType} to the generic {@link Type} interface, in order
 * to isolate user code from changes in the internal Type contracts.
 *
 * @apiNote Many of the interfaces implemented here are implemented just to
 * handle the case of the wrapped type implementing them so we can pass them
 * along.
 *
 * todo (6.0) : ^^ this introduces a problem in code that relies on `instance of` checks
 * 		against any of these interfaces when the wrapped type does not
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public class CustomType<J>
		extends AbstractType
		implements ConvertedBasicType<J>, ProcedureParameterNamedBinder<J>, ProcedureParameterExtractionAware<J> {

	private final UserType<J> userType;
	private final String[] registrationKeys;

	private final String name;

	private final BasicJavaType<J> mappedJavaType;
	private final JdbcType jdbcType;

	private final ValueExtractor<J> valueExtractor;
	private final ValueBinder<J> valueBinder;

	public CustomType(UserType<J> userType, TypeConfiguration typeConfiguration) throws MappingException {
		this( userType, ArrayHelper.EMPTY_STRING_ARRAY, typeConfiguration );
	}

	public CustomType(UserType<J> userType, String[] registrationKeys, TypeConfiguration typeConfiguration) throws MappingException {
		this.userType = userType;
		this.name = userType.getClass().getName();

		if ( userType instanceof BasicJavaType ) {
			//noinspection unchecked
			this.mappedJavaType = ( (BasicJavaType<J>) userType );
		}
		else if ( userType instanceof JavaTypedExpressible) {
			//noinspection unchecked
			this.mappedJavaType = (BasicJavaType<J>) ( (JavaTypedExpressible<J>) userType ).getExpressibleJavaType();
		}
		else if ( userType instanceof UserVersionType ) {
			this.mappedJavaType = new UserTypeVersionJavaTypeWrapper<>( (UserVersionType<J>) userType );
		}
		else {
			this.mappedJavaType = new UserTypeJavaTypeWrapper<>( userType );
		}

		// create a JdbcType adapter that uses the UserType binder/extract handling
		this.jdbcType = new UserTypeSqlTypeAdapter<>( userType, mappedJavaType, typeConfiguration);

		this.valueExtractor = jdbcType.getExtractor( mappedJavaType );
		this.valueBinder = jdbcType.getBinder( mappedJavaType );
		this.registrationKeys = registrationKeys;
	}

	public UserType<J> getUserType() {
		return userType;
	}

	@Override
	public ValueExtractor<J> getJdbcValueExtractor() {
		return valueExtractor;
	}

	@Override
	public ValueBinder<J> getJdbcValueBinder() {
		return valueBinder;
	}

	@Override
	public JdbcType getJdbcType() {
		return jdbcType;
	}

	@Override
	public int[] getSqlTypeCodes(Mapping pi) {
		return new int[] { jdbcType.getDefaultSqlTypeCode() };
	}

	@Override
	public String[] getRegistrationKeys() {
		return registrationKeys;
	}

	@Override
	public int getColumnSpan(Mapping session) {
		return 1;
	}

	@Override
	public Class<J> getReturnedClass() {
		return getUserType().returnedClass();
	}

	@Override
	public boolean isEqual(Object x, Object y) throws HibernateException {
		return getUserType().equals( (J) x, (J) y );
	}

	@Override
	public int getHashCode(Object x) {
		return getUserType().hashCode( (J) x );
	}

	@Override
	public Object assemble(Serializable cached, SharedSessionContractImplementor session, Object owner) {
		return getUserType().assemble( cached, owner);
	}

	@Override
	public Serializable disassemble(Object value, SharedSessionContractImplementor session, Object owner) {
		return getUserType().disassemble( (J) value );
	}

	@Override
	public Object replace(
			Object original,
			Object target,
			SharedSessionContractImplementor session,
			Object owner,
			Map<Object, Object> copyCache) throws HibernateException {
		return getUserType().replace( (J) original, (J) target, owner );
	}

	@Override
	public void nullSafeSet(
			PreparedStatement st,
			Object value,
			int index,
			boolean[] settable,
			SharedSessionContractImplementor session) throws SQLException {
		if ( settable[0] ) {
			//noinspection unchecked
			getUserType().nullSafeSet( st, (J) value, index, session );
		}
	}

	@Override
	public void nullSafeSet(
			PreparedStatement st,
			Object value,
			int index,
			SharedSessionContractImplementor session) throws SQLException {
		//noinspection unchecked
		getUserType().nullSafeSet( st, (J) value, index, session );
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Object deepCopy(Object value, SessionFactoryImplementor factory) throws HibernateException {
		return getUserType().deepCopy( (J) value );
	}

	@Override
	public boolean isMutable() {
		return getUserType().isMutable();
	}

	@Override
	public String toLoggableString(Object value, SessionFactoryImplementor factory) {
		if ( value == null ) {
			return "null";
		}
		else if ( userType instanceof LoggableUserType ) {
			return ( (LoggableUserType) userType ).toLoggableString( value, factory );
		}
		else if ( userType instanceof EnhancedUserType<?> ) {
			return ( (EnhancedUserType<Object>) userType ).toString( value );
		}
		else {
			return value.toString();
		}
	}

	@Override
	public boolean[] toColumnNullness(Object value, Mapping mapping) {
		boolean[] result = new boolean[ getColumnSpan(mapping) ];
		if ( value != null ) {
			Arrays.fill(result, true);
		}
		return result;
	}

	@Override
	public boolean isDirty(Object old, Object current, boolean[] checkable, SharedSessionContractImplementor session)
			throws HibernateException {
		return checkable[0] && isDirty(old, current, session);
	}

	@Override
	public boolean canDoSetting() {
		if ( getUserType() instanceof ProcedureParameterNamedBinder ) {
			return ((ProcedureParameterNamedBinder<?>) getUserType() ).canDoSetting();
		}
		return false;
	}

	@Override
	public void nullSafeSet(
			CallableStatement statement, J value, String name, SharedSessionContractImplementor session) throws SQLException {
		if ( canDoSetting() ) {
			((ProcedureParameterNamedBinder<J>) getUserType() ).nullSafeSet( statement, value, name, session );
		}
		else {
			throw new UnsupportedOperationException(
					"Type [" + getUserType() + "] does support parameter binding by name"
			);
		}
	}

	@Override
	public boolean canDoExtraction() {
		if ( getUserType() instanceof ProcedureParameterExtractionAware ) {
			return ((ProcedureParameterExtractionAware<?>) getUserType() ).canDoExtraction();
		}
		return false;
	}

	@Override
	public J extract(CallableStatement statement, int startIndex, SharedSessionContractImplementor session) throws SQLException {
		if ( canDoExtraction() ) {
			//noinspection unchecked
			return ((ProcedureParameterExtractionAware<J>) getUserType() ).extract( statement, startIndex, session );
		}
		else {
			throw new UnsupportedOperationException(
					"Type [" + getUserType() + "] does support parameter value extraction"
			);
		}
	}

	@Override
	public J extract(CallableStatement statement, String paramName, SharedSessionContractImplementor session)
			throws SQLException {
		if ( canDoExtraction() ) {
			//noinspection unchecked
			return ((ProcedureParameterExtractionAware<J>) getUserType() ).extract( statement, paramName, session );
		}
		else {
			throw new UnsupportedOperationException(
					"Type [" + getUserType() + "] does support parameter value extraction"
			);
		}
	}

	@Override
	public int hashCode() {
		return getUserType().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ( obj instanceof CustomType ) && getUserType().equals( ( (CustomType<?>) obj ).getUserType() );
	}

	@Override
	public Class<J> getJavaType() {
		return mappedJavaType.getJavaTypeClass();
	}

	@Override
	public JavaType<J> getMappedJavaType() {
		return mappedJavaType;
	}

	@Override
	public JavaType<J> getExpressibleJavaType() {
		return this.getMappedJavaType();
	}

	@Override
	public JavaType<J> getJavaTypeDescriptor() {
		return this.getMappedJavaType();
	}

	@Override
	public BasicValueConverter<J, Object> getValueConverter() {
		return userType.getValueConverter();
	}
}
