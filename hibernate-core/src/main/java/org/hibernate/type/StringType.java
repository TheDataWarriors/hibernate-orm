/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type;
import org.hibernate.dialect.Dialect;
import org.hibernate.type.descriptor.java.StringTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.JdbcTypeDescriptorIndicators;
import org.hibernate.type.descriptor.jdbc.VarcharTypeDescriptor;

/**
 * A type that maps between {@link java.sql.Types#VARCHAR VARCHAR} and {@link String}
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public class StringType
		extends AbstractSingleColumnStandardBasicType<String>
		implements DiscriminatorType<String>, SqlTypeDescriptorIndicatorCapable<String> {

	public static final StringType INSTANCE = new StringType();

	public StringType() {
		super( VarcharTypeDescriptor.INSTANCE, StringTypeDescriptor.INSTANCE );
	}

	public String getName() {
		return "string";
	}

	@Override
	protected boolean registerUnderJavaType() {
		return true;
	}

	public String objectToSQLString(String value, Dialect dialect) throws Exception {
		return '\'' + value + '\'';
	}

	public String stringToObject(String xml) throws Exception {
		return xml;
	}

	public String toString(String value) {
		return value;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Override
	public BasicType resolveIndicatedType(JdbcTypeDescriptorIndicators indicators) {
		if ( indicators.isLob() ) {
			return indicators.isNationalized() ? MaterializedNClobType.INSTANCE : MaterializedClobType.INSTANCE;
		}
		else {
			return indicators.isNationalized() ? StringNVarcharType.INSTANCE : this;
		}
	}
}
