/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type.descriptor.java.spi;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.hibernate.type.descriptor.java.AbstractClassTypeDescriptor;
import org.hibernate.type.descriptor.java.EnumJavaTypeDescriptor;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.java.SerializableTypeDescriptor;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.type.spi.TypeConfigurationAware;

import org.jboss.logging.Logger;

/**
 * Basically a map from {@link Class} -> {@link JavaTypeDescriptor}
 *
 * @author Steve Ebersole
 * @author Andrea Boriero
 *
 * @since 5.3
 */
public class JavaTypeDescriptorRegistry implements JavaTypeDescriptorBaseline.BaselineTarget, Serializable {
	private static final Logger log = Logger.getLogger( JavaTypeDescriptorRegistry.class );

	private final TypeConfiguration typeConfiguration;
	private final ConcurrentHashMap<Type, JavaTypeDescriptor<?>> descriptorsByType = new ConcurrentHashMap<>();

	@SuppressWarnings("unused")
	public JavaTypeDescriptorRegistry(TypeConfiguration typeConfiguration) {
		this.typeConfiguration = typeConfiguration;
		JavaTypeDescriptorBaseline.prime( this );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// baseline descriptors

	@Override
	public void addBaselineDescriptor(JavaTypeDescriptor<?> descriptor) {
		if ( descriptor.getJavaType() == null ) {
			throw new IllegalStateException( "Illegal to add BasicJavaTypeDescriptor with null Java type" );
		}
		addBaselineDescriptor( descriptor.getJavaType(), descriptor );
	}

	@Override
	public void addBaselineDescriptor(Type describedJavaType, JavaTypeDescriptor<?> descriptor) {
		performInjections( descriptor );
		descriptorsByType.put( describedJavaType, descriptor );
	}

	private void performInjections(JavaTypeDescriptor<?> descriptor) {
		if ( descriptor instanceof TypeConfigurationAware ) {
			// would be nice to make the JavaTypeDescriptor for an entity, e.g., aware of the the TypeConfiguration
			( (TypeConfigurationAware) descriptor ).setTypeConfiguration( typeConfiguration );
		}
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// descriptor access

	public <T> JavaTypeDescriptor<T> getDescriptor(Type javaType) {
		return resolveDescriptor( javaType );
//		return RegistryHelper.INSTANCE.resolveDescriptor(
//				descriptorsByClass,
//				javaType,
//				() -> {
//					log.debugf(
//							"Could not find matching scoped JavaTypeDescriptor for requested Java class [%s]; " +
//									"falling back to static registry",
//							javaType.getName()
//					);
//
//					if ( Serializable.class.isAssignableFrom( javaType ) ) {
//						return new SerializableTypeDescriptor( javaType );
//					}
//
//					if ( !AttributeConverter.class.isAssignableFrom( javaType ) ) {
//						log.debugf(
//								"Could not find matching JavaTypeDescriptor for requested Java class [%s]; using fallback.  " +
//										"This means Hibernate does not know how to perform certain basic operations in relation to this Java type." +
//										"",
//								javaType.getName()
//						);
//						checkEqualsAndHashCode( javaType );
//					}
//
//					return new FallbackJavaTypeDescriptor<>( javaType );
//				}
//		);
	}

	public void addDescriptor(JavaTypeDescriptor<?> descriptor) {
		JavaTypeDescriptor<?> old = descriptorsByType.put( descriptor.getJavaType(), descriptor );
		if ( old != null ) {
			log.debugf(
					"JavaTypeDescriptorRegistry entry replaced : %s -> %s (was %s)",
					descriptor.getJavaType(),
					descriptor,
					old
			);
		}
		performInjections( descriptor );
	}

	public <J> JavaTypeDescriptor<J> resolveDescriptor(Type javaType, Supplier<JavaTypeDescriptor<J>> creator) {
		final JavaTypeDescriptor<?> cached = descriptorsByType.get( javaType );
		if ( cached != null ) {
			//noinspection unchecked
			return (JavaTypeDescriptor<J>) cached;
		}

		final JavaTypeDescriptor<J> created = creator.get();
		descriptorsByType.put( javaType, created );
		return created;
	}

	@SuppressWarnings("unchecked")
	public <J> JavaTypeDescriptor<J> resolveDescriptor(Type javaType) {
		return resolveDescriptor(
				javaType,
				() -> {
					// the fallback will always be a basic type
					final AbstractClassTypeDescriptor<J> fallbackDescriptor;
					final Class<?> javaTypeClass;
					if ( javaType instanceof Class<?> ) {
						javaTypeClass = (Class<?>) javaType;
					}
					else {
						final ParameterizedType parameterizedType = (ParameterizedType) javaType;
						javaTypeClass = (Class<?>) parameterizedType.getRawType();
					}

					if ( javaTypeClass.isEnum() ) {
						//noinspection rawtypes
						fallbackDescriptor = new EnumJavaTypeDescriptor( javaTypeClass );
					}
					else if ( Serializable.class.isAssignableFrom( javaTypeClass ) ) {
						//noinspection rawtypes
						fallbackDescriptor = new SerializableTypeDescriptor( javaTypeClass );
					}
					else {
						//noinspection rawtypes
						fallbackDescriptor = new JavaTypeDescriptorBasicAdaptor( javaTypeClass );
					}

					// todo (6.0) : here we assume that all temporal type descriptors are registered
					//		ahead of time.  Allow for on-the-fly temporal types?  The 2 impediments for that are:
					//			1) How can we recognize non-JDK date/time types?
					//			2) What is the temporal precision for the types we have deemed temporal?

					return fallbackDescriptor;
				}
		);
	}

	public JavaTypeDescriptor<?> resolveDynamicEntityDescriptor(String typeName) {
		return new DynamicModelJtd();
	}

}
