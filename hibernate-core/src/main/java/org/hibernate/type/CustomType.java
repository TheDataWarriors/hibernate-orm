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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.BasicJavaDescriptor;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.java.JavaTypedExpressable;
import org.hibernate.type.descriptor.jdbc.JdbcTypeDescriptor;
import org.hibernate.type.internal.UserTypeJavaTypeWrapper;
import org.hibernate.type.internal.UserTypeSqlTypeAdapter;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.EnhancedUserType;
import org.hibernate.usertype.Sized;
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
public class CustomType
		extends AbstractType
		implements BasicType, IdentifierType, DiscriminatorType, VersionType, StringRepresentableType, ProcedureParameterNamedBinder, ProcedureParameterExtractionAware {

	private final UserType<Object> userType;
	private final String[] registrationKeys;

	private final String name;

	private final BasicJavaDescriptor<Object> mappedJavaTypeDescriptor;
	private final JdbcTypeDescriptor jdbcTypeDescriptor;

	private final ValueExtractor<Object> valueExtractor;
	private final ValueBinder<Object> valueBinder;

	private final Size dictatedSize;
	private final Size defaultSize;

	public CustomType(UserType userType, TypeConfiguration typeConfiguration) throws MappingException {
		this( userType, ArrayHelper.EMPTY_STRING_ARRAY, typeConfiguration );
	}

	public CustomType(UserType userType, String[] registrationKeys, TypeConfiguration typeConfiguration) throws MappingException {
		this.userType = userType;
		this.name = userType.getClass().getName();

		if ( userType instanceof BasicJavaDescriptor ) {
			//noinspection rawtypes
			this.mappedJavaTypeDescriptor = ( (BasicJavaDescriptor) userType );
		}
		else if ( userType instanceof JavaTypedExpressable ) {
			//noinspection rawtypes
			this.mappedJavaTypeDescriptor = (BasicJavaDescriptor) ( (JavaTypedExpressable) userType ).getExpressableJavaTypeDescriptor();
		}
		else {
			this.mappedJavaTypeDescriptor = new UserTypeJavaTypeWrapper<>( userType );
		}

		// create a JdbcTypeDescriptor adapter that uses the UserType binde/extract handling
		this.jdbcTypeDescriptor = new UserTypeSqlTypeAdapter<>( userType, mappedJavaTypeDescriptor );

		this.valueExtractor = jdbcTypeDescriptor.getExtractor( mappedJavaTypeDescriptor );
		this.valueBinder = jdbcTypeDescriptor.getBinder( mappedJavaTypeDescriptor );

		if ( userType instanceof Sized ) {
			final Sized sized = (Sized) userType;
			this.dictatedSize = sized.dictatedSizes()[0];
			this.defaultSize = sized.defaultSizes()[0];
		}
		else {
			this.dictatedSize = null;
			this.defaultSize = null;
		}

		this.registrationKeys = registrationKeys;
	}

	public UserType getUserType() {
		return userType;
	}

	@Override
	public ValueExtractor getJdbcValueExtractor() {
		return valueExtractor;
	}

	@Override
	public ValueBinder<?> getJdbcValueBinder() {
		return valueBinder;
	}

	@Override
	public JdbcTypeDescriptor getJdbcTypeDescriptor() {
		return jdbcTypeDescriptor;
	}

	@Override
	public int[] getSqlTypeCodes(Mapping pi) {
		return new int[] { jdbcTypeDescriptor.getDefaultSqlTypeCode() };
	}

	@Override
	public String[] getRegistrationKeys() {
		return registrationKeys;
	}

	@Override
	public Size[] dictatedSizes(Mapping mapping) throws MappingException {
		return new Size[] {dictatedSize};
	}

	@Override
	public Size[] defaultSizes(Mapping mapping) throws MappingException {
		return new Size[] {defaultSize};
	}

	@Override
	public int getColumnSpan(Mapping session) {
		return 1;
	}

	@Override
	public Class getReturnedClass() {
		return getUserType().returnedClass();
	}

	@Override
	public boolean isEqual(Object x, Object y) throws HibernateException {
		return getUserType().equals( x, y );
	}

	@Override
	public int getHashCode(Object x) {
		return getUserType().hashCode( x);
	}

	@Override
	public Object nullSafeGet(
			ResultSet rs,
			String[] names,
			SharedSessionContractImplementor session,
			Object owner) throws SQLException {
		throw new UnsupportedOperationException( "Reading from ResultSet by name is no longer supported" );
	}

	@Override
	public Object nullSafeGet(
			ResultSet rs,
			String columnName,
			SharedSessionContractImplementor session,
			Object owner) throws SQLException {
		throw new UnsupportedOperationException( "Reading from ResultSet by name is no longer supported" );
	}

	@Override
	public Object assemble(Serializable cached, SharedSessionContractImplementor session, Object owner) {
		return getUserType().assemble( cached, owner);
	}

	@Override
	public Serializable disassemble(Object value, SharedSessionContractImplementor session, Object owner) {
		return getUserType().disassemble( value);
	}

	@Override
	public Object replace(
			Object original,
			Object target,
			SharedSessionContractImplementor session,
			Object owner,
			Map copyCache) throws HibernateException {
		return getUserType().replace( original, target, owner );
	}

	@Override
	public void nullSafeSet(
			PreparedStatement st,
			Object value,
			int index,
			boolean[] settable,
			SharedSessionContractImplementor session) throws SQLException {
		if ( settable[0] ) {
			getUserType().nullSafeSet( st, value, index, session );
		}
	}

	@Override
	public void nullSafeSet(
			PreparedStatement st,
			Object value,
			int index,
			SharedSessionContractImplementor session) throws SQLException {
		getUserType().nullSafeSet( st, value, index, session );
	}

	@SuppressWarnings({ "UnusedDeclaration" })
	public String toXMLString(Object value, SessionFactoryImplementor factory) {
		return toString( value );
	}

	@SuppressWarnings({ "UnusedDeclaration" })
	public Object fromXMLString(String xml, Mapping factory) {
		return fromStringValue( xml );
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Object deepCopy(Object value, SessionFactoryImplementor factory) throws HibernateException {
		return getUserType().deepCopy( value);
	}

	@Override
	public boolean isMutable() {
		return getUserType().isMutable();
	}

	@Override
	public Object stringToObject(CharSequence sequence) {
		return fromStringValue( sequence );
	}

	@Override
	public Comparator getComparator() {
		return (Comparator) getUserType();
	}

	@Override
	public Object next(Object current, SharedSessionContractImplementor session) {
		return ( (UserVersionType) getUserType() ).next( current, session );
	}

	@Override
	public Object seed(SharedSessionContractImplementor session) {
		return ( (UserVersionType) getUserType() ).seed( session );
	}

	@Override
	public String toLoggableString(Object value, SessionFactoryImplementor factory) {
		if ( value == null ) {
			return "null";
		}
		else {
			return toXMLString( value, factory );
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
	@SuppressWarnings("unchecked")
	public String toString(Object value) throws HibernateException {
		if ( getUserType() instanceof StringRepresentableType ) {
			return ( (StringRepresentableType) getUserType() ).toString( value );
		}
		if ( value == null ) {
			return null;
		}
		if ( getUserType() instanceof EnhancedUserType ) {
			//noinspection deprecation
			return ( (EnhancedUserType) getUserType() ).toXMLString( value );
		}
		return value.toString();
	}

	@Override
	public Object fromStringValue(CharSequence sequence) throws HibernateException {
		if ( getUserType() instanceof StringRepresentableType ) {
			return ( (StringRepresentableType) getUserType() ).fromStringValue( sequence );
		}
		if ( getUserType() instanceof EnhancedUserType ) {
			//noinspection deprecation
			return ( (EnhancedUserType) getUserType() ).fromXMLString( sequence );
		}
		throw new HibernateException(
				String.format(
						"Could not process #fromStringValue, UserType class [%s] did not implement %s or %s",
						name,
						StringRepresentableType.class.getName(),
						EnhancedUserType.class.getName()
				)
		);
	}

	@Override
	public boolean canDoSetting() {
		if ( getUserType() instanceof ProcedureParameterNamedBinder ) {
			return ((ProcedureParameterNamedBinder) getUserType() ).canDoSetting();
		}
		return false;
	}

	@Override
	public void nullSafeSet(
			CallableStatement statement, Object value, String name, SharedSessionContractImplementor session) throws SQLException {
		if ( canDoSetting() ) {
			((ProcedureParameterNamedBinder) getUserType() ).nullSafeSet( statement, value, name, session );
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
			return ((ProcedureParameterExtractionAware) getUserType() ).canDoExtraction();
		}
		return false;
	}

	@Override
	public Object extract(CallableStatement statement, int startIndex, SharedSessionContractImplementor session) throws SQLException {
		if ( canDoExtraction() ) {
			return ((ProcedureParameterExtractionAware) getUserType() ).extract( statement, startIndex, session );
		}
		else {
			throw new UnsupportedOperationException(
					"Type [" + getUserType() + "] does support parameter value extraction"
			);
		}
	}

	@Override
	public Object extract(CallableStatement statement, String paramName, SharedSessionContractImplementor session)
			throws SQLException {
		if ( canDoExtraction() ) {
			return ((ProcedureParameterExtractionAware) getUserType() ).extract( statement, paramName, session );
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
		return ( obj instanceof CustomType ) && getUserType().equals( ( (CustomType) obj ).getUserType() );
	}

	@Override
	public Class getJavaType() {
		return mappedJavaTypeDescriptor.getJavaTypeClass();
	}

	@Override
	public JavaTypeDescriptor getMappedJavaTypeDescriptor() {
		return mappedJavaTypeDescriptor;
	}

	@Override
	public JavaTypeDescriptor getExpressableJavaTypeDescriptor() {
		return getMappedJavaTypeDescriptor();
	}

	@Override
	public JavaTypeDescriptor getJavaTypeDescriptor() {
		return getMappedJavaTypeDescriptor();
	}
}
