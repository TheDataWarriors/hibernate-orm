/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.bytecode.spi.BytecodeProvider;
import org.hibernate.bytecode.spi.ReflectionOptimizer;
import org.hibernate.cfg.Environment;
import org.hibernate.classic.Lifecycle;
import org.hibernate.engine.spi.PersistentAttributeInterceptable;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.CoreLogging;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.mapping.Backref;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.IndexBackref;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Subclass;
import org.hibernate.metamodel.RepresentationMode;
import org.hibernate.metamodel.spi.EntityRepresentationStrategy;
import org.hibernate.metamodel.spi.Instantiator;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.property.access.internal.PropertyAccessBasicImpl;
import org.hibernate.property.access.internal.PropertyAccessStrategyBackRefImpl;
import org.hibernate.property.access.internal.PropertyAccessStrategyEmbeddedImpl;
import org.hibernate.property.access.internal.PropertyAccessStrategyIndexBackRefImpl;
import org.hibernate.property.access.spi.BuiltInPropertyAccessStrategies;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.property.access.spi.PropertyAccessStrategy;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.ProxyFactory;
import org.hibernate.proxy.pojo.ProxyFactoryHelper;
import org.hibernate.tuple.entity.EntityMetamodel;
import org.hibernate.type.CompositeType;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.java.spi.JavaTypeDescriptorRegistry;

/**
 * @author Steve Ebersole
 */
public class StandardPojoEntityRepresentationStrategy implements EntityRepresentationStrategy {
	private static final CoreMessageLogger LOG = CoreLogging.messageLogger( StandardPojoEntityRepresentationStrategy.class );

	private final JavaTypeDescriptor<?> mappedJtd;
	private final JavaTypeDescriptor<?> proxyJtd;

	private final boolean isBytecodeEnhanced;
	private final boolean lifecycleImplementor;

	private final ReflectionOptimizer reflectionOptimizer;
	private final ProxyFactory proxyFactory;
	private final Instantiator instantiator;

	private final StrategySelector strategySelector;

	private final String identifierPropertyName;
	private final PropertyAccess identifierPropertyAccess;
	private final Map<String, PropertyAccess> propertyAccessMap = new ConcurrentHashMap<>();
	private final StandardPojoEmbeddableRepresentationStrategy mapsIdRepresentationStrategy;

	public StandardPojoEntityRepresentationStrategy(
			PersistentClass bootDescriptor,
			EntityPersister runtimeDescriptor,
			RuntimeModelCreationContext creationContext) {
		final SessionFactoryImplementor sessionFactory = creationContext.getSessionFactory();
		final JavaTypeDescriptorRegistry jtdRegistry = creationContext.getTypeConfiguration()
				.getJavaTypeDescriptorRegistry();

		final Class<?> mappedJavaType = bootDescriptor.getMappedClass();
		this.mappedJtd = jtdRegistry.getDescriptor( mappedJavaType );

		final Class<?> proxyJavaType = bootDescriptor.getProxyInterface();
		if ( proxyJavaType != null ) {
			this.proxyJtd = jtdRegistry.getDescriptor( proxyJavaType );
		}
		else {
			this.proxyJtd = null;
		}

		this.lifecycleImplementor = Lifecycle.class.isAssignableFrom( mappedJavaType );
		this.isBytecodeEnhanced = PersistentAttributeInterceptable.class.isAssignableFrom( mappedJavaType );


		final Property identifierProperty = bootDescriptor.getIdentifierProperty();
		if ( identifierProperty == null ) {
			identifierPropertyName = null;
			identifierPropertyAccess = null;

			final KeyValue bootDescriptorIdentifier = bootDescriptor.getIdentifier();

			if ( bootDescriptorIdentifier != null && bootDescriptorIdentifier instanceof Component ) {
				mapsIdRepresentationStrategy = new StandardPojoEmbeddableRepresentationStrategy(
						bootDescriptor.getIdentifierMapper(),
						creationContext
				);
			}
			else {
				mapsIdRepresentationStrategy = null;
			}
		}
		else {
			mapsIdRepresentationStrategy = null;
			identifierPropertyName = identifierProperty.getName();
			identifierPropertyAccess = makePropertyAccess( identifierProperty );
		}

//		final BytecodeProvider bytecodeProvider = creationContext.getBootstrapContext().getBytecodeProvider();
		final BytecodeProvider bytecodeProvider = Environment.getBytecodeProvider();

		final EntityMetamodel entityMetamodel = runtimeDescriptor.getEntityMetamodel();
		ProxyFactory proxyFactory = null;
		if ( proxyJtd != null && entityMetamodel.isLazy() ) {
			proxyFactory = createProxyFactory( bootDescriptor, bytecodeProvider, creationContext );
			if ( proxyFactory == null ) {
				entityMetamodel.setLazy( false );
			}
		}
		this.proxyFactory = proxyFactory;

		this.reflectionOptimizer = resolveReflectionOptimizer( bootDescriptor, bytecodeProvider, sessionFactory );

		if ( reflectionOptimizer != null ) {
			final ReflectionOptimizer.InstantiationOptimizer instantiationOptimizer = reflectionOptimizer.getInstantiationOptimizer();
			if ( instantiationOptimizer != null ) {
				this.instantiator = new OptimizedPojoInstantiatorImpl<>( mappedJtd, instantiationOptimizer );
			}
			else {
				this.instantiator = new PojoInstantiatorImpl<>( mappedJtd );
			}
		}
		else {
			this.instantiator = new PojoInstantiatorImpl<>( mappedJtd );
		}

		this.strategySelector = sessionFactory.getServiceRegistry().getService( StrategySelector.class );
	}

	private PropertyAccess resolveIdentifierPropertyAccess(PersistentClass bootDescriptor) {
		final Property identifierProperty = bootDescriptor.getIdentifierProperty();

		if ( identifierProperty == null ) {
			return PropertyAccessStrategyEmbeddedImpl.INSTANCE.buildPropertyAccess(
					proxyJtd != null ? proxyJtd.getJavaTypeClass() : mappedJtd.getJavaTypeClass(),
					"id"
			);
		}

		return makePropertyAccess( identifierProperty );
	}

	private ProxyFactory createProxyFactory(
			PersistentClass bootDescriptor,
			BytecodeProvider bytecodeProvider,
			RuntimeModelCreationContext creationContext) {

		final Set<Class> proxyInterfaces = new java.util.HashSet<>();

		final Class mappedClass = mappedJtd.getJavaTypeClass();
		Class proxyInterface;
		if ( proxyJtd != null ) {
			proxyInterface = proxyJtd.getJavaTypeClass();
		}
		else {
			proxyInterface = null;
		}

		if ( proxyInterface != null && ! mappedClass.equals( proxyInterface ) ) {
			if ( ! proxyInterface.isInterface() ) {
				throw new MappingException(
						"proxy must be either an interface, or the class itself: " + bootDescriptor.getEntityName()
				);
			}
			proxyInterfaces.add( proxyInterface );
		}

		if ( mappedClass.isInterface() ) {
			proxyInterfaces.add( mappedClass );
		}

		//noinspection unchecked
		final Iterator<Subclass> subclasses = bootDescriptor.getSubclassIterator();
		while ( subclasses.hasNext() ) {
			final Subclass subclass = subclasses.next();
			final Class subclassProxy = subclass.getProxyInterface();
			final Class subclassClass = subclass.getMappedClass();
			if ( subclassProxy != null && !subclassClass.equals( subclassProxy ) ) {
				if ( !subclassProxy.isInterface() ) {
					throw new MappingException(
							"proxy must be either an interface, or the class itself: " + subclass.getEntityName()
					);
				}
				proxyInterfaces.add( subclassProxy );
			}
		}

		proxyInterfaces.add( HibernateProxy.class );

		Iterator properties = bootDescriptor.getPropertyIterator();
		Class clazz = bootDescriptor.getMappedClass();
		while ( properties.hasNext() ) {
			Property property = (Property) properties.next();
			ProxyFactoryHelper.validateGetterSetterMethodProxyability( "Getter", property.getGetter( clazz ).getMethod() );
			ProxyFactoryHelper.validateGetterSetterMethodProxyability( "Setter", property.getSetter( clazz ).getMethod() );
		}

		final Method idGetterMethod = identifierPropertyAccess == null ? null : identifierPropertyAccess.getGetter().getMethod();
		final Method idSetterMethod = identifierPropertyAccess == null ? null : identifierPropertyAccess.getSetter().getMethod();

		final Method proxyGetIdentifierMethod = idGetterMethod == null || proxyInterface == null
				? null
				: ReflectHelper.getMethod( proxyInterface, idGetterMethod );
		final Method proxySetIdentifierMethod = idSetterMethod == null || proxyInterface == null
				? null
				: ReflectHelper.getMethod( proxyInterface, idSetterMethod );

		ProxyFactory pf = bytecodeProvider.getProxyFactoryFactory().buildProxyFactory( creationContext.getSessionFactory() );
		try {
			pf.postInstantiate(
					bootDescriptor.getEntityName(),
					mappedClass,
					proxyInterfaces,
					proxyGetIdentifierMethod,
					proxySetIdentifierMethod,
					bootDescriptor.hasEmbeddedIdentifier() ?
							(CompositeType) bootDescriptor.getIdentifier().getType() :
							null
			);

			return pf;
		}
		catch (HibernateException he) {
			LOG.unableToCreateProxyFactory( bootDescriptor.getEntityName(), he );
			return null;
		}
	}

	private ReflectionOptimizer resolveReflectionOptimizer(
			PersistentClass bootType,
			BytecodeProvider bytecodeProvider,
			@SuppressWarnings("unused") SessionFactoryImplementor sessionFactory) {
		final Class javaTypeToReflect;
		if ( proxyFactory != null ) {
			assert proxyJtd != null;
			javaTypeToReflect = proxyJtd.getJavaTypeClass();
		}
		else {
			javaTypeToReflect = mappedJtd.getJavaTypeClass();
		}

		final List<String> getterNames = new ArrayList<>();
		final List<String> setterNames = new ArrayList<>();
		final List<Class> getterTypes = new ArrayList<>();

		boolean foundCustomAccessor = false;

		//noinspection unchecked
		final Iterator<Property> itr = bootType.getPropertyClosureIterator();
		int i = 0;
		while ( itr.hasNext() ) {
			//TODO: redesign how PropertyAccessors are acquired...
			final Property property = itr.next();
			final PropertyAccess propertyAccess = makePropertyAccess( property );

			propertyAccessMap.put( property.getName(), propertyAccess );

			if ( ! (propertyAccess instanceof PropertyAccessBasicImpl) ) {
				foundCustomAccessor = true;
			}

			getterNames.add( propertyAccess.getGetter().getMethodName() );
			getterTypes.add( propertyAccess.getGetter().getReturnType() );

			setterNames.add( propertyAccess.getSetter().getMethodName() );

			i++;
		}

		if ( foundCustomAccessor || ! Environment.useReflectionOptimizer() ) {
			return null;
		}

		return bytecodeProvider.getReflectionOptimizer(
				javaTypeToReflect,
				getterNames.toArray( new String[0] ),
				setterNames.toArray( new String[0] ),
				getterTypes.toArray( new Class[0] )
		);
	}

	private PropertyAccess makePropertyAccess(Property bootAttributeDescriptor) {
		PropertyAccessStrategy strategy = null;

		final String propertyAccessorName = bootAttributeDescriptor.getPropertyAccessorName();
		final BuiltInPropertyAccessStrategies namedStrategy = BuiltInPropertyAccessStrategies.interpret(
				propertyAccessorName );

		if ( namedStrategy != null ) {
			strategy = namedStrategy.getStrategy();
		}

		if ( strategy == null ) {
			if ( StringHelper.isNotEmpty( propertyAccessorName ) ) {
				// handle explicitly specified attribute accessor
				strategy = strategySelector.resolveStrategy( PropertyAccessStrategy.class, propertyAccessorName );
			}
			else {
				if ( bootAttributeDescriptor instanceof Backref ) {
					final Backref backref = (Backref) bootAttributeDescriptor;
					strategy = new PropertyAccessStrategyBackRefImpl( backref.getCollectionRole(), backref
							.getEntityName() );
				}
				else if ( bootAttributeDescriptor instanceof IndexBackref ) {
					final IndexBackref indexBackref = (IndexBackref) bootAttributeDescriptor;
					strategy = new PropertyAccessStrategyIndexBackRefImpl(
							indexBackref.getCollectionRole(),
							indexBackref.getEntityName()
					);
				}
				else {
					// for now...
					strategy = BuiltInPropertyAccessStrategies.MIXED.getStrategy();
				}
			}
		}

		if ( strategy == null ) {
			throw new HibernateException(
					String.format(
							Locale.ROOT,
							"Could not resolve PropertyAccess for attribute `%s#%s`",
							mappedJtd.getJavaType().getTypeName(),
							bootAttributeDescriptor.getName()
					)
			);
		}

		return strategy.buildPropertyAccess( mappedJtd.getJavaTypeClass(), bootAttributeDescriptor.getName() );
	}

	@Override
	public RepresentationMode getMode() {
		return RepresentationMode.POJO;
	}

	@Override
	public ReflectionOptimizer getReflectionOptimizer() {
		return reflectionOptimizer;
	}

	@Override
	public Instantiator getInstantiator() {
		return instantiator;
	}

	@Override
	public ProxyFactory getProxyFactory() {
		return proxyFactory;
	}

	@Override
	public boolean isLifecycleImplementor() {
		return lifecycleImplementor;
	}

	@Override
	public boolean isBytecodeEnhanced() {
		return isBytecodeEnhanced;
	}

	@Override
	public JavaTypeDescriptor<?> getMappedJavaTypeDescriptor() {
		return mappedJtd;
	}

	@Override
	public JavaTypeDescriptor<?> getProxyJavaTypeDescriptor() {
		return proxyJtd;
	}

	@Override
	public PropertyAccess resolvePropertyAccess(Property bootAttributeDescriptor) {
		if ( bootAttributeDescriptor.getName().equals( identifierPropertyName ) ) {
			return identifierPropertyAccess;
		}

		PropertyAccess propertyAccess = propertyAccessMap.get( bootAttributeDescriptor.getName() );
		if ( propertyAccess != null ) {
			return propertyAccess;
		}
		return mapsIdRepresentationStrategy.resolvePropertyAccess( bootAttributeDescriptor );
	}
}
