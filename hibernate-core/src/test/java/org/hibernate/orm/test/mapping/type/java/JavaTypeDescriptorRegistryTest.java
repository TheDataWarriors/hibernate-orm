/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.mapping.type.java;

import java.util.Comparator;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.java.MutabilityPlan;
import org.hibernate.type.descriptor.java.StringTypeDescriptor;
import org.hibernate.type.descriptor.java.spi.JavaTypeDescriptorRegistry;
import org.hibernate.type.descriptor.jdbc.JdbcTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.JdbcTypeDescriptorIndicators;
import org.hibernate.type.spi.TypeConfiguration;

import org.junit.Test;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

/**
 * @author Andrea Boriero
 */
public class JavaTypeDescriptorRegistryTest {

	@Test
	public void testGetJavaTypeDescriptorRegistry() {
		final TypeConfiguration typeConfiguration = new TypeConfiguration();
		final JavaTypeDescriptorRegistry registry = new JavaTypeDescriptorRegistry( typeConfiguration );

		final JavaTypeDescriptor<String> descriptor = registry.getDescriptor( String.class );

		assertThat( descriptor, instanceOf( StringTypeDescriptor.class ) );
	}

	@Test
	public void testRegisterJavaTypeDescriptorRegistry(){
		final TypeConfiguration typeConfiguration = new TypeConfiguration();
		final JavaTypeDescriptorRegistry registry = new JavaTypeDescriptorRegistry( typeConfiguration );

		registry.addDescriptor( new CustomJavaTypeDescriptor() );

		final JavaTypeDescriptor<?> descriptor = registry.getDescriptor( CustomType.class );

		assertThat( descriptor, instanceOf( CustomJavaTypeDescriptor.class ) );
	}

	public static class CustomType {}

	public static class CustomJavaTypeDescriptor implements JavaTypeDescriptor<CustomType> {
		@Override
		public Class<CustomType> getJavaTypeClass() {
			return CustomType.class;
		}

		@Override
		public MutabilityPlan<CustomType> getMutabilityPlan() {
			return null;
		}

		@Override
		public JdbcTypeDescriptor getRecommendedJdbcType(JdbcTypeDescriptorIndicators context) {
			return null;
		}

		@Override
		public Comparator<CustomType> getComparator() {
			return null;
		}

		@Override
		public int extractHashCode(CustomType value) {
			return 0;
		}

		@Override
		public boolean areEqual(CustomType one, CustomType another) {
			return false;
		}

		@Override
		public String extractLoggableRepresentation(CustomType value) {
			return null;
		}

		@Override
		public String toString(CustomType value) {
			return null;
		}

		@Override
		public CustomType fromString(String string) {
			return null;
		}

		@Override
		public CustomType wrap(Object value, WrapperOptions options) {
			return null;
		}

		@Override
		public <X> X unwrap(CustomType value, Class<X> type, WrapperOptions options) {
			return null;
		}
	}
}
