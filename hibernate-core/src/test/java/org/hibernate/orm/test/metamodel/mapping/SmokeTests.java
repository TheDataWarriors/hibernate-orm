/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.metamodel.mapping;

import java.sql.Statement;
import java.sql.Types;
import javax.persistence.AttributeConverter;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.internal.BasicValuedSingularAttributeMapping;
import org.hibernate.metamodel.mapping.internal.EmbeddedAttributeMapping;
import org.hibernate.metamodel.mapping.internal.ToOneAttributeMapping;
import org.hibernate.metamodel.model.convert.internal.NamedEnumValueConverter;
import org.hibernate.metamodel.model.convert.internal.OrdinalEnumValueConverter;
import org.hibernate.metamodel.model.convert.spi.BasicValueConverter;
import org.hibernate.metamodel.model.convert.spi.JpaAttributeConverter;
import org.hibernate.persister.entity.EntityPersister;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Steve Ebersole
 */
@SuppressWarnings("WeakerAccess")
@DomainModel(
		annotatedClasses = { SmokeTests.SimpleEntity.class, SmokeTests.OtherEntity.class }
)
@ServiceRegistry
@SessionFactory
public class SmokeTests {

	@Test
	public void testSimpleEntity(SessionFactoryScope scope) {
		final EntityPersister entityDescriptor = scope.getSessionFactory()
				.getDomainModel()
				.getEntityDescriptor( SimpleEntity.class );

		final EntityIdentifierMapping identifierMapping = entityDescriptor.getIdentifierMapping();
		assertThat(
				identifierMapping.getMappedType().getMappedJavaTypeDescriptor().getJavaTypeClass(),
				sameInstance( Integer.class )
		);

		{
			final ModelPart namePart = entityDescriptor.findSubPart( "name" );
			assert namePart instanceof BasicValuedSingularAttributeMapping;
			assert "mapping_simple_entity".equals( ( (BasicValuedSingularAttributeMapping) namePart ).getContainingTableExpression() );
			assert "name".equals( ( (BasicValuedSingularAttributeMapping) namePart ).getSelectionExpression() );
		}

		{
			final ModelPart genderPart = entityDescriptor.findSubPart( "gender" );
			assert genderPart instanceof BasicValuedSingularAttributeMapping;
			final BasicValuedSingularAttributeMapping genderAttrMapping = (BasicValuedSingularAttributeMapping) genderPart;
			assert "mapping_simple_entity".equals( genderAttrMapping.getContainingTableExpression() );
			assert "gender".equals( genderAttrMapping.getSelectionExpression() );

			assertThat( genderAttrMapping.getJavaTypeDescriptor().getJavaTypeClass(), equalTo( Gender.class ) );

			final BasicValueConverter valueConverter = genderAttrMapping.getValueConverter();
			assertThat( valueConverter, instanceOf( OrdinalEnumValueConverter.class ) );
			assertThat( valueConverter.getDomainJavaDescriptor(), is( genderAttrMapping.getJavaTypeDescriptor() ) );
			assertThat( valueConverter.getRelationalJavaDescriptor().getJavaTypeClass(), equalTo( Integer.class ) );

			assertThat( genderAttrMapping.getJdbcMapping().getSqlTypeDescriptor().getJdbcTypeCode(), is( Types.TINYINT ) );
		}

		{
			final ModelPart part = entityDescriptor.findSubPart( "gender2" );
			assert part instanceof BasicValuedSingularAttributeMapping;
			final BasicValuedSingularAttributeMapping attrMapping = (BasicValuedSingularAttributeMapping) part;
			assert "mapping_simple_entity".equals( attrMapping.getContainingTableExpression() );
			assert "gender2".equals( attrMapping.getSelectionExpression() );

			assertThat( attrMapping.getJavaTypeDescriptor().getJavaTypeClass(), equalTo( Gender.class ) );

			final BasicValueConverter valueConverter = attrMapping.getValueConverter();
			assertThat( valueConverter, instanceOf( NamedEnumValueConverter.class ) );
			assertThat( valueConverter.getDomainJavaDescriptor(), is( attrMapping.getJavaTypeDescriptor() ) );
			assertThat( valueConverter.getRelationalJavaDescriptor().getJavaTypeClass(), equalTo( String.class ) );

			assertThat( attrMapping.getJdbcMapping().getSqlTypeDescriptor().getJdbcTypeCode(), is( Types.VARCHAR ) );
		}

		{
			final ModelPart part = entityDescriptor.findSubPart( "gender3" );
			assert part instanceof BasicValuedSingularAttributeMapping;
			final BasicValuedSingularAttributeMapping attrMapping = (BasicValuedSingularAttributeMapping) part;
			assert "mapping_simple_entity".equals( attrMapping.getContainingTableExpression() );
			assert "gender3".equals( attrMapping.getSelectionExpression() );

			assertThat( attrMapping.getJavaTypeDescriptor().getJavaTypeClass(), equalTo( Gender.class ) );

			final BasicValueConverter valueConverter = attrMapping.getValueConverter();
			assertThat( valueConverter, instanceOf( JpaAttributeConverter.class ) );
			assertThat( valueConverter.getDomainJavaDescriptor(), is( attrMapping.getJavaTypeDescriptor() ) );
			assertThat( valueConverter.getRelationalJavaDescriptor().getJavaTypeClass(), equalTo( Character.class ) );

			assertThat( attrMapping.getJdbcMapping().getSqlTypeDescriptor().getJdbcTypeCode(), is( Types.CHAR ) );
		}

		{
			final ModelPart part = entityDescriptor.findSubPart( "component" );
			assert part instanceof EmbeddedAttributeMapping;
			final EmbeddedAttributeMapping attrMapping = (EmbeddedAttributeMapping) part;
			assertThat( attrMapping.getContainingTableExpression(), is( "mapping_simple_entity" ) );
			assertThat( attrMapping.getEmbeddableTypeDescriptor().getJdbcTypeCount(), is( 4 ) );
			assertThat(
					attrMapping.getEmbeddableTypeDescriptor().getSelectionMapping( 0 ).getSelectionExpression(),
					is( "attribute1" )
			);
			assertThat(
					attrMapping.getEmbeddableTypeDescriptor().getSelectionMapping( 1 ).getSelectionExpression(),
					is( "attribute2" )
			);
		}
	}

	@Test
	public void testEntityBasedManyToOne(SessionFactoryScope scope) {
		final EntityPersister entityDescriptor = scope.getSessionFactory()
				.getDomainModel()
				.getEntityDescriptor( OtherEntity.class );

		final EntityPersister simpleEntityDescriptor = scope.getSessionFactory()
				.getDomainModel()
				.getEntityDescriptor( SimpleEntity.class );

		final ModelPart part = entityDescriptor.findSubPart( "simpleEntity" );
		assertThat( part, notNullValue() );
		assertThat( part, instanceOf( ToOneAttributeMapping.class ) );
		final ToOneAttributeMapping attrMapping = (ToOneAttributeMapping) part;
		assertThat( attrMapping.getAttributeName(), is( "simpleEntity" ) );
		assertThat( attrMapping.getMappedType(), is( simpleEntityDescriptor ) );
		assertThat(
				attrMapping.getJavaTypeDescriptor(),
				is( simpleEntityDescriptor.getJavaTypeDescriptor() )
		);

		assertThat( attrMapping.getDeclaringType(), is( entityDescriptor ) );
	}

	@BeforeEach
	public void setUp(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					SimpleEntity simpleEntity = new SimpleEntity();
					simpleEntity.setId( 1 );
					simpleEntity.setGender( Gender.FEMALE );
					simpleEntity.setName( "Fab" );
					simpleEntity.setGender2( Gender.MALE );
					simpleEntity.setComponent( new Component( "a1", "a2" ) );
					session.save( simpleEntity );
					OtherEntity otherEntity = new OtherEntity();
					otherEntity.setId( 2 );
					otherEntity.setName( "Bar" );
					otherEntity.setSimpleEntity( simpleEntity );
					session.save( otherEntity );
				}
		);
	}

	@AfterEach
	public void tearDown(SessionFactoryScope scope) {
		scope.inTransaction(
				session ->
						session.doWork(
								work -> {
									Statement statement = work.createStatement();
									statement.execute( "delete from mapping_other_entity" );
									statement.execute( "delete from mapping_simple_entity" );
									statement.close();
								}
						)
		);
	}

	public enum Gender {
		MALE,
		FEMALE
	}

	@Entity(name = "OtherEntity")
	@Table(name = "mapping_other_entity")
	@SuppressWarnings("unused")
	public static class OtherEntity {
		private Integer id;
		private String name;
		private SimpleEntity simpleEntity;

		@Id
		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@ManyToOne
		public SimpleEntity getSimpleEntity() {
			return simpleEntity;
		}

		public void setSimpleEntity(SimpleEntity simpleEntity) {
			this.simpleEntity = simpleEntity;
		}
	}

	@Entity(name = "SimpleEntity")
	@Table(name = "mapping_simple_entity")
	@SuppressWarnings("unused")
	public static class SimpleEntity {
		private Integer id;
		private String name;
		private Gender gender;
		private Gender gender2;
		private Gender gender3;
		private Component component;

		@Id
		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Enumerated
		public Gender getGender() {
			return gender;
		}

		public void setGender(Gender gender) {
			this.gender = gender;
		}

		@Enumerated(EnumType.STRING)
		public Gender getGender2() {
			return gender2;
		}

		public void setGender2(Gender gender2) {
			this.gender2 = gender2;
		}

		@Convert( converter = GenderConverter.class )
		@Column( length = 1 )
		public Gender getGender3() {
			return gender3;
		}

		public void setGender3(Gender gender3) {
			this.gender3 = gender3;
		}

		@Embedded
		public Component getComponent() {
			return component;
		}

		public void setComponent(Component component) {
			this.component = component;
		}
	}

	static class GenderConverter implements AttributeConverter<Gender,Character> {

		@Override
		public Character convertToDatabaseColumn(Gender attribute) {
			if ( attribute == null ) {
				return null;
			}

			if ( attribute == Gender.MALE ) {
				return 'M';
			}

			return 'F';
		}

		@Override
		public Gender convertToEntityAttribute(Character dbData) {
			if ( dbData == null ) {
				return null;
			}

			if ( 'M' == dbData ) {
				return Gender.MALE;
			}

			return Gender.FEMALE;
		}
	}

	@Embeddable
	static class SubComponent {
		private String subAttribute1;
		private String subAttribute2;

		public SubComponent() {
		}

		public SubComponent(String subAttribute1, String subAttribute2) {
			this.subAttribute1 = subAttribute1;
			this.subAttribute2 = subAttribute2;
		}

		public String getSubAttribute1() {
			return subAttribute1;
		}

		public void setSubAttribute1(String subAttribute1) {
			this.subAttribute1 = subAttribute1;
		}

		public String getSubAttribute2() {
			return subAttribute2;
		}

		public void setSubAttribute2(String subAttribute2) {
			this.subAttribute2 = subAttribute2;
		}
	}

	@Embeddable
	public static class Component {
		private String attribute1;
		private String attribute2;

		private SubComponent subComponent;

		public Component() {
		}

		public Component(String attribute1, String attribute2) {
			this.attribute1 = attribute1;
			this.attribute2 = attribute2;
		}

		public String getAttribute1() {
			return attribute1;
		}

		public void setAttribute1(String attribute1) {
			this.attribute1 = attribute1;
		}

		public String getAttribute2() {
			return attribute2;
		}

		public void setAttribute2(String attribute2) {
			this.attribute2 = attribute2;
		}

		@Embedded
		public SubComponent getSubComponent() {
			return subComponent;
		}

		public void setSubComponent(SubComponent subComponent) {
			this.subComponent = subComponent;
		}
	}

}
