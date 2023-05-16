/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.usertype;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.hibernate.HibernateException;
import org.hibernate.Incubating;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * This interface should be implemented by user-defined custom types
 * that extend the set of {@linkplain org.hibernate.type.Type types}
 * defined in {@link org.hibernate.type}.
 * <p>
 * A custom type is <em>not</em> an actual persistent attribute type,
 * rather it is a class responsible for serializing instances of some
 * other class to and from JDBC. This other class should have "value"
 * semantics, since its identity is lost as part of this serialization
 * process.
 * <p>
 * A custom type may be applied to an attribute of an entity either:
 * <ul>
 * <li>explicitly, using {@link org.hibernate.annotations.Type @Type},
 *     or
 * <li>implicitly, using
 *     {@link org.hibernate.annotations.TypeRegistration @TypeRegistration}.
 * </ul>
 * <p>
 * For example, this {@code UserType} persists {@link java.time.Period}
 * to columns of type {@code varchar}:
 * <pre>
 * public class PeriodType implements UserType&lt;Period&gt; {
 *     &#64;Override
 *     public int getSqlType() {
 *         return VARCHAR;
 *     }
 *
 *     &#64;Override
 *     public Class&lt;Period&gt; returnedClass() {
 *         return Period.class;
 *     }
 *
 *     &#64;Override
 *     public boolean equals(Period x, Period y) {
 *         return Objects.equals(x, y);
 *     }
 *
 *     &#64;Override
 *     public int hashCode(Period x) {
 *         return x.hashCode();
 *     }
 *
 *     &#64;Override
 *     public Period nullSafeGet(ResultSet rs, int position,
 *                               SharedSessionContractImplementor session, Object owner)
 *                 throws SQLException {
 *         String string = rs.getString( position );
 *         return rs.wasNull() ? null : Period.parse(string);
 *     }
 *
 *     &#64;Override
 *     public void nullSafeSet(PreparedStatement st, Period value, int index,
 *                             SharedSessionContractImplementor session)
 *                 throws SQLException {
 *         if ( value == null ) {
 *             st.setNull(index, VARCHAR);
 *         }
 *         else {
 *             st.setString(index, value.toString());
 *         }
 *     }
 *
 *     &#64;Override
 *     public boolean isMutable() {
 *         return false;
 *     }
 *
 *     &#64;Override
 *     public Period deepCopy(Period value) {
 *         return value; //Period is immutable
 *     }
 *
 *     &#64;Override
 *     public Serializable disassemble(Period period) {
 *         return period; //Period is immutable
 *     }
 *
 *     &#64;Override
 *     public Period assemble(Serializable cached, Object owner) {
 *         return (Period) cached; //Period is immutable
 *     }
 * }
 * </pre>
 * <p>
 * And it may be used like this:
 * <pre>&#64;Type(PeriodType.class) Period period;</pre>
 * <p>
 * We could even use {@code @Type} as a meta-annotation:
 * <pre>
 * &#64;Type(PeriodType.class)
 * &#64;Target({METHOD, FIELD})
 * &#64;Retention(RUNTIME)
 * public &#64;interface TimePeriod {}
 * </pre>
 * <p>
 * And then use the {@code @TimePeriod} annotation to apply our {@code UserType}:
 * <pre>&#64;TimePeriod Period period;</pre>
 * <p>
 * Finally, we could ask for our custom type to be used by default:
 * <pre>&#64;TypeRegistration(basicClass = Period.class, userType = PeriodType.class)</pre>
 * <p>
 * Which completely relieves us of the need to annotate the field explicitly:
 * <pre>Period period;</pre>
 * <p>
 * But on the other hand, our {@code UserType} is overkill. Like most
 * immutable classes, {@code Period} is much easier to handle using a
 * JPA attribute converter:
 * <pre>
 * &#64;Converter
 * public class PeriodToStringConverter implements AttributeConverter&lt;Period,String&gt; {
 *     &#64;Override
 *     public String convertToDatabaseColumn(Period period) {
 *         return period.toString();
 *     }
 *
 *    &#64;Override
 *    public Period convertToEntityAttribute(String string) {
 *         return Period.parse(string);
 *    }
 * }
 * </pre>
 * <p>
 * A {@code UserType} is much more useful when the persistent attribute
 * type is mutable. For example:
 * <p>
 * <pre>
 * public class BitSetUserType implements UserType&lt;BitSet&gt; {
 *
 *     &#64;Override
 *     public int getSqlType() {
 *         return Types.VARCHAR;
 *     }
 *
 *     &#64;Override
 *     public Class&lt;BitSet&gt; returnedClass() {
 *         return BitSet.class;
 *     }
 *
 *     &#64;Override
 *     public boolean equals(BitSet x, BitSet y) {
 *         return Objects.equals(x, y);
 *     }
 *
 *     &#64;Override
 *     public int hashCode(BitSet x) {
 *         return x.hashCode();
 *     }
 *
 *     &#64;Override
 *     public BitSet nullSafeGet(ResultSet rs, int position,
 *                               SharedSessionContractImplementor session, Object owner)
 *                 throws SQLException {
 *         String string = rs.getString(position);
 *         return rs.wasNull()? null : parseBitSet(columnValue);
 *     }
 *
 *     &#64;Override
 *     public void nullSafeSet(PreparedStatement st, BitSet bitSet, int index,
 *                             SharedSessionContractImplementor session)
 *                 throws SQLException {
 *         if (bitSet == null) {
 *             st.setNull(index, VARCHAR);
 *         }
 *         else {
 *             st.setString(index, formatBitSet(bitSet));
 *         }
 *     }
 *
 *     &#64;Override
 *     public BitSet deepCopy(BitSet value) {
 *         return bitSet == null ? null : (BitSet) bitSet.clone();
 *     }
 *
 *     &#64;Override
 *     public boolean isMutable() {
 *         return true;
 *     }
 *
 *     &#64;Override
 *     public Serializable disassemble(BitSet value) {
 *         return deepCopy(value);
 *     }
 *
 *     &#64;Override
 *     public BitSet assemble(Serializable cached, Object owner)
 *             throws HibernateException {
 *         return deepCopy((BitSet) cached);
 *     }
 * }
 * </pre>
 * <p>
 * Every implementor of {@code UserType} must be immutable and must
 * declare a public default constructor.
 * <p>
 * A custom type implemented as a {@code UserType} is treated as a
 * non-composite value, and does not have persistent attributes which
 * may be used in queries. If a custom type does have attributes, and
 * can be thought of as something more like an embeddable object, it
 * might be better to implement {@link CompositeUserType}.
 *
 * @apiNote
 * This interface:
 * <ul>
 * <li>abstracts user code away from changes to the internal interface
 *     {@link org.hibernate.type.Type},
 * <li>simplifies the implementation of custom types, and
 * <li>hides certain SPI interfaces from user code.
 * </ul>
 * <p>
 * The class {@link org.hibernate.type.CustomType} automatically adapts
 * between {@code UserType} and {@link org.hibernate.type.Type}.
 * In principle, a custom type could implement {@code Type} directly,
 * or extend one of the abstract classes in {@link org.hibernate.type}.
 * But this approach risks breakage resulting from future incompatible
 * changes to classes or interfaces in that package, and is therefore
 * discouraged.
 *
 * @see org.hibernate.type.Type
 * @see org.hibernate.type.CustomType
 *
 * @see org.hibernate.annotations.Type
 * @see org.hibernate.annotations.TypeRegistration
 *
 * @author Gavin King
 */
public interface UserType<J> {

	/**
	 * The JDBC/SQL type code for the database column mapped by this
	 * custom type.
	 * <p>
	 * The type code is usually one of the standard type codes
	 * declared by {@link org.hibernate.type.SqlTypes}, but it could
	 * be a database-specific code.
	 *
	 * @see org.hibernate.type.SqlTypes
	 */
	int getSqlType();

	/**
	 * The class returned by {@code nullSafeGet()}.
	 *
	 * @return Class
	 */
	Class<J> returnedClass();

	/**
	 * Compare two instances of the Java class mapped by this custom
	 * type for persistence "equality", that is, equality of their
	 * persistent state.
	 */
	boolean equals(J x, J y);

	/**
	 * Get a hash code for the given instance of the Java class mapped
	 * by this custom type, consistent with the definition of
	 * {@linkplain #equals(Object, Object) persistence "equality"} for
	 * this custom type.
	 */
	int hashCode(J x);

	/**
	 * Read an instance of the Java class mapped by this custom type
	 * from the given JDBC {@link ResultSet}. Implementors must handle
	 * null column values.
	 *
	 * @param owner in Hibernate 6, this is always null
	 */
	J nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, @Deprecated Object owner)
			throws SQLException;

	/**
	 * Write an instance of the Java class mapped by this custom type
	 * to the given JDBC {@link PreparedStatement}. Implementors must
	 * handle null values of the Java class. A multi-column type should
	 * be written to parameters starting from {@code index}.
	 */
	void nullSafeSet(PreparedStatement st, J value, int index, SharedSessionContractImplementor session)
			throws SQLException;

	/**
	 * Bind a value represented by an instance of the
	 * to the given JDBC {@link PreparedStatement}.
	 * Implementors should handle the possibility of null values.
	 * A multi-column type should bind parameters starting from {@code index}.
	 *
	 * @param st The JDBC prepared statement to which to bind
	 * @param value the object to write
	 * @param index starting parameter bind index
	 * @param session The originating session
	 *
	 * @throws HibernateException An error from Hibernate
	 * @throws SQLException An error from the JDBC driver
	 */
	default void nullSafeSet(PreparedStatement st, Serializable id, J value, int index,
							SharedSessionContractImplementor session) throws HibernateException, SQLException{
		nullSafeSet(st, value, index, session);
	}

	/**
	 * Return a clone of the given instance of the Java class mapped
	 * by this custom type.
	 * <ul>
	 * <li>It's not necessary to clone immutable objects. If the Java
	 *     class mapped by this custom type is an immutable class,
	 *     this method may safely just return its argument.
	 * <li>For mutable objects, it's necessary to deep copy persistent
	 *     state, stopping at associations to other entities, and at
	 *     persistent collections.
	 * <li>If the argument is a reference to an entity, just return
	 *     the argument.
	 * <li>Finally, if the argument is null, just return null.
	 * </ul>
	 *
	 * @param value the object to be cloned, which may be null
	 * @return a clone
	 */
	J deepCopy(J value);

	/**
	 * Are instances of the Java class mapped by this custom type
	 * mutable or immutable?
	 *
	 * @return {@code true} if instances are mutable
	 */
	boolean isMutable();

	/**
	 * Transform the given value into a destructured representation,
	 * suitable for storage in the {@linkplain org.hibernate.Cache
	 * second-level cache}. This method is called only during the
	 * process of writing the properties of an entity to the
	 * second-level cache.
	 * <p>
	 * If the value is mutable then, at the very least, this method
	 * should perform a deep copy. That may not be enough for some
	 * types, however. For example, associations must be cached as
	 * identifier values.
	 * <p>
	 * This is an optional operation, but, if left unimplemented,
	 * this type will not be cacheable in the second-level cache.
	 *
	 * @param value the object to be cached
	 * @return a cacheable representation of the object
	 *
	 * @see org.hibernate.Cache
	 */
	Serializable disassemble(J value);

	/**
	 * Reconstruct a value from its destructured representation,
	 * during the process of reading the properties of an entity
	 * from the {@linkplain org.hibernate.Cache second-level cache}.
	 * <p>
	 * If the value is mutable then, at the very least, this method
	 * should perform a deep copy. That may not be enough for some
	 * types, however. For example, associations must be cached as
	 * identifier values.
	 * <p>
	 * This is an optional operation, but, if left unimplemented,
	 * this type will not be cacheable in the second-level cache.
	 *
	 * @param cached the object to be cached
	 * @param owner the owner of the cached object
	 * @return a reconstructed object from the cacheable representation
	 *
	 * @see org.hibernate.Cache
	 */
	J assemble(Serializable cached, Object owner);

	/**
	 * During merge, replace the existing (target) value in the
	 * managed entity we are merging to with a new (original) value
	 * from the detached entity we are merging.
	 * <ul>
	 * <li>For immutable objects, or null values, it's safe to simply
	 *     return the first argument.
	 * <li>For mutable objects, it's enough to return a copy of the
	 *     first argument.
	 * <li>For objects with component values, it might make sense to
	 *     recursively replace component values.
	 * </ul>
	 *
	 * @param detached the value from the detached entity being merged
	 * @param managed the value in the managed entity
	 *
	 * @return the value to be merged
	 *
	 * @see org.hibernate.Session#merge(Object)
	 */
	default J replace(J detached, J managed, Object owner) {
		return deepCopy( detached );
	}

	/**
	 * The default column length, for use in DDL generation.
	 */
	default long getDefaultSqlLength(Dialect dialect, JdbcType jdbcType) {
		return Size.DEFAULT_LENGTH;
	}

	/**
	 * The default column precision, for use in DDL generation.
	 */
	default int getDefaultSqlPrecision(Dialect dialect, JdbcType jdbcType) {
		return Size.DEFAULT_PRECISION;
	}

	/**
	 * The default column scale, for use in DDL generation.
	 */
	default int getDefaultSqlScale(Dialect dialect, JdbcType jdbcType) {
		return Size.DEFAULT_SCALE;
	}

	/**
	 * A mapped {@link JdbcType}. By default, the {@code JdbcType}
	 * registered under our {@link #getSqlType() type code}.
	 */
	@Incubating
	default JdbcType getJdbcType(TypeConfiguration typeConfiguration) {
		return typeConfiguration.getJdbcTypeRegistry().getDescriptor( getSqlType() );
	}

	/**
	 * Returns the converter that this custom type uses for transforming
	 * from the domain type to the relational type, or <code>null</code>
	 * if there is no conversion.
	 * <p>
	 * Note that it is vital to provide a converter if a column should
	 * be mapped to multiple domain types, as Hibernate will only select
	 * a column once and materialize values as instances of the Java type
	 * given by {@link JdbcMapping#getJdbcJavaType()}. Support for multiple
	 * domain type representations works by converting objects of that type
	 * to the domain type.
	 */
	@Incubating
	default BasicValueConverter<J, Object> getValueConverter() {
		return null;
	}
}
