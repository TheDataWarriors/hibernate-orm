/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.id.usertype;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.HibernateException;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.LongType;
import org.hibernate.usertype.UserType;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

@DomainModel(
		annotatedClasses = UserTypeComparableIdTest.SomeEntity.class
)
@SessionFactory
@ServiceRegistry(
		settings = @Setting(name = AvailableSettings.ORDER_UPDATES, value = "true")
)
public class UserTypeComparableIdTest {

	@Test
	@TestForIssue(jiraKey = "HHH-8999")
	public void testUserTypeId(SessionFactoryScope scope) {
		SomeEntity e1 = new SomeEntity();
		SomeEntity e2 = new SomeEntity();
		scope.inTransaction(
				session -> {
					CustomId e1Id = new CustomId( 1L );
					e1.setCustomId( e1Id );
					CustomId e2Id = new CustomId( 2L );
					e2.setCustomId( e2Id );
					session.persist( e1 );
					session.persist( e2 );
				}
		);

		scope.inTransaction(
				session -> {
					session.delete( session.get( SomeEntity.class, e1.getCustomId() ) );
					session.delete( session.get( SomeEntity.class, e2.getCustomId() ) );
				}
		);
	}


	@TypeDef(
			name = "customId",
			typeClass = CustomIdType.class
	)
	@Entity
	@Table(name = "some_entity")
	public static class SomeEntity {

		@Id
		@Type(type = "customId")
		@Column(name = "id")
		private CustomId customId;

		public CustomId getCustomId() {
			return customId;
		}

		public void setCustomId(final CustomId customId) {
			this.customId = customId;
		}
	}

	public static class CustomId implements Serializable, Comparable<CustomId> {

		private final Long value;

		public CustomId(final Long value) {
			this.value = value;
		}

		public Long getValue() {
			return value;
		}

		@Override
		public boolean equals(Object o) {
			if ( this == o ) {
				return true;
			}
			if ( o == null || getClass() != o.getClass() ) {
				return false;
			}

			CustomId customId = (CustomId) o;

			return !( value != null ? !value.equals( customId.value ) : customId.value != null );

		}

		@Override
		public int hashCode() {
			return value != null ? value.hashCode() : 0;
		}

		@Override
		public int compareTo(CustomId o) {
			return value.compareTo( o.value );
		}
	}

	public static class CustomIdType implements UserType, Comparator<CustomId> {

		public static final LongType SQL_TYPE = LongType.INSTANCE;

		@Override
		public int[] sqlTypes() {
			return new int[] { SQL_TYPE.getJdbcTypeCode() };
		}

		@Override
		public Object nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
				throws SQLException {
			Long value = rs.getLong( position );

			return new CustomId( value );
		}

		@Override
		public void nullSafeSet(
				PreparedStatement preparedStatement,
				Object value,
				int index,
				SharedSessionContractImplementor sessionImplementor) throws HibernateException, SQLException {
			CustomId customId = (CustomId) value;

			if ( customId == null ) {
				preparedStatement.setNull( index, SQL_TYPE.getJdbcTypeCode() );
			}
			else {
				preparedStatement.setLong( index, customId.getValue() );
			}
		}

		@Override
		public Class returnedClass() {
			return CustomId.class;
		}

		@Override
		public int compare(CustomId o1, CustomId o2) {
			return o1.getValue().compareTo( o2.getValue() );
		}

		@Override
		public boolean equals(Object x, Object y) throws HibernateException {
			return x.equals( y );
		}

		@Override
		public int hashCode(Object x) throws HibernateException {
			return x.hashCode();
		}

		@Override
		public Object deepCopy(Object value) throws HibernateException {
			return value;
		}

		@Override
		public boolean isMutable() {
			return true;
		}

		@Override
		public Serializable disassemble(Object value) throws HibernateException {
			return (Serializable) value;
		}

		@Override
		public Object assemble(Serializable cached, Object owner) throws HibernateException {
			return cached;
		}

		@Override
		public Object replace(Object original, Object target, Object owner) throws HibernateException {
			return original;
		}
	}
}
