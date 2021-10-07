/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg.annotations;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.hibernate.AnnotationException;
import org.hibernate.AssertionFailure;
import org.hibernate.MappingException;
import org.hibernate.annotations.CollectionId;
import org.hibernate.annotations.CollectionIdCustomType;
import org.hibernate.annotations.CollectionIdJavaType;
import org.hibernate.annotations.CollectionIdJdbcType;
import org.hibernate.annotations.CollectionIdJdbcTypeCode;
import org.hibernate.annotations.CollectionIdMutability;
import org.hibernate.annotations.CustomType;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JavaType;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ListIndexJavaType;
import org.hibernate.annotations.ListIndexJdbcType;
import org.hibernate.annotations.ListIndexJdbcTypeCode;
import org.hibernate.annotations.MapKeyCustomType;
import org.hibernate.annotations.MapKeyJavaType;
import org.hibernate.annotations.MapKeyJdbcType;
import org.hibernate.annotations.MapKeyJdbcTypeCode;
import org.hibernate.annotations.MapKeyMutability;
import org.hibernate.annotations.Mutability;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.annotations.internal.NoJavaTypeDescriptor;
import org.hibernate.annotations.internal.NoJdbcTypeDescriptor;
import org.hibernate.annotations.internal.NoMutabilityPlan;
import org.hibernate.annotations.internal.NoUserType;
import org.hibernate.boot.model.TypeDefinition;
import org.hibernate.boot.model.convert.spi.ConverterDescriptor;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.AccessType;
import org.hibernate.cfg.Ejb3Column;
import org.hibernate.cfg.Ejb3JoinColumn;
import org.hibernate.cfg.PkDrivenByDefaultMapsIdSecondPass;
import org.hibernate.cfg.SetBasicValueTypeSecondPass;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.NationalizationSupport;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Table;
import org.hibernate.resource.beans.spi.ManagedBean;
import org.hibernate.resource.beans.spi.ManagedBeanRegistry;
import org.hibernate.type.BasicType;
import org.hibernate.type.SerializableToBlobType;
import org.hibernate.type.descriptor.java.BasicJavaDescriptor;
import org.hibernate.type.descriptor.java.ImmutableMutabilityPlan;
import org.hibernate.type.descriptor.java.MutabilityPlan;
import org.hibernate.type.descriptor.jdbc.JdbcTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.JdbcTypeDescriptorIndicators;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.UserType;

import org.jboss.logging.Logger;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.MapKeyTemporal;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Version;

/**
 * @author Steve Ebersole
 * @author Emmanuel Bernard
 */
public class BasicValueBinder<T> implements JdbcTypeDescriptorIndicators {

	// todo (6.0) : In light of how we want to build Types (specifically BasicTypes) moving forward this class should undergo major changes
	//		see the comments in #setType
	//		but as always the "design" of these classes make it unclear exactly how to change it properly.

	private static final CoreMessageLogger LOG = Logger.getMessageLogger( CoreMessageLogger.class, BasicValueBinder.class.getName() );

	public enum Kind {
		ATTRIBUTE( ValueMappingAccess.INSTANCE ),
		MAP_KEY( MapKeyMappingAccess.INSTANCE ),
		COLLECTION_ELEMENT( ValueMappingAccess.INSTANCE ),
		COLLECTION_ID( CollectionIdMappingAccess.INSTANCE ),
		LIST_INDEX( ListIndexMappingAccess.INSTANCE );

		private final BasicMappingAccess mappingAccess;

		Kind(BasicMappingAccess mappingAccess) {
			this.mappingAccess = mappingAccess;
		}
	}

	private final Kind kind;
	private final MetadataBuildingContext buildingContext;

	private final ClassLoaderService classLoaderService;
	private final StrategySelector strategySelector;



	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// in-flight info

	private String explicitBasicTypeName;
	private Class<? extends UserType<?>> explicitCustomType;
	private Map explicitLocalTypeParams;

	private Function<TypeConfiguration, JdbcTypeDescriptor> explicitJdbcTypeAccess;
	private Function<TypeConfiguration, BasicJavaDescriptor> explicitJtdAccess;
	private Function<TypeConfiguration, MutabilityPlan> explicitMutabilityAccess;
	private Function<TypeConfiguration, java.lang.reflect.Type> implicitJavaTypeAccess;

	private XProperty xproperty;
	private AccessType accessType;

	private ConverterDescriptor converterDescriptor;

	private boolean isVersion;
	private boolean isNationalized;
	private boolean isLob;
	private EnumType enumType;
	private TemporalType temporalPrecision;

	private Table table;
	private Ejb3Column[] columns;

	private BasicValue basicValue;

	private String timeStampVersionType;
	private String persistentClassName;
	private String propertyName;
	private String returnedClassName;
	private String referencedEntityName;


	public BasicValueBinder(Kind kind, MetadataBuildingContext buildingContext) {
		assert kind != null;
		assert  buildingContext != null;

		this.kind = kind;
		this.buildingContext = buildingContext;

		this.classLoaderService = buildingContext.getBootstrapContext()
				.getServiceRegistry()
				.getService( ClassLoaderService.class );

		this.strategySelector = buildingContext.getBootstrapContext()
				.getServiceRegistry()
				.getService( StrategySelector.class );
	}


	@Override
	public TypeConfiguration getTypeConfiguration() {
		return buildingContext.getBootstrapContext().getTypeConfiguration();
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SqlTypeDescriptorIndicators

	@Override
	public EnumType getEnumeratedType() {
		return enumType;
	}

	@Override
	public boolean isLob() {
		return isLob;
	}

	@Override
	public TemporalType getTemporalPrecision() {
		return temporalPrecision;
	}

	@Override
	public int getPreferredSqlTypeCodeForBoolean() {
		return buildingContext.getPreferredSqlTypeCodeForBoolean();
	}

	@Override
	public boolean isNationalized() {
		return isNationalized;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// in-flight handling

	public void setVersion(boolean isVersion) {
		this.isVersion = isVersion;
		if ( isVersion && basicValue != null ) {
			basicValue.makeVersion();
		}
	}

	public void setTimestampVersionType(String versionType) {
		this.timeStampVersionType = versionType;
	}

	public void setReferencedEntityName(String referencedEntityName) {
		this.referencedEntityName = referencedEntityName;
	}

	public void setPropertyName(String propertyName) {
		this.propertyName = propertyName;
	}

	public void setReturnedClassName(String returnedClassName) {
		this.returnedClassName = returnedClassName;
	}

	public void setTable(Table table) {
		this.table = table;
	}

	public void setColumns(Ejb3Column[] columns) {
		this.columns = columns;
	}

	public void setPersistentClassName(String persistentClassName) {
		this.persistentClassName = persistentClassName;
	}

	public void setAccessType(AccessType accessType) {
		this.accessType = accessType;
	}


	public void setType(
			XProperty modelXProperty,
			XClass modelPropertyTypeXClass,
			String declaringClassName,
			ConverterDescriptor converterDescriptor) {
		this.xproperty = modelXProperty;
		boolean isArray = modelXProperty.isArray();
		if ( modelPropertyTypeXClass == null && !isArray ) {
			// we cannot guess anything
			return;
		}

		if ( columns == null ) {
			throw new AssertionFailure( "`BasicValueBinder#setColumns` should be called before `BasicValueBinder#setType`" );
		}

		if ( columns.length != 1 ) {
			throw new AssertionFailure( "Expecting just one column, but found `" + Arrays.toString( columns ) + "`" );
		}

		final XClass modelTypeXClass = isArray
				? modelXProperty.getElementClass()
				: modelPropertyTypeXClass;

		// If we get into this method we know that there is a Java type for the value
		//		and that it is safe to load on the app classloader.
		final Class modelJavaType = resolveJavaType( modelTypeXClass, buildingContext );
		if ( modelJavaType == null ) {
			throw new IllegalStateException( "BasicType requires Java type" );
		}

		final Class modelPropertyJavaType = buildingContext.getBootstrapContext()
				.getReflectionManager()
				.toClass( modelXProperty.getType() );

		final boolean isMap = Map.class.isAssignableFrom( modelPropertyJavaType );

		if ( kind != Kind.LIST_INDEX && kind != Kind.MAP_KEY  ) {
			isLob = modelXProperty.isAnnotationPresent( Lob.class );
		}

		if ( getDialect().getNationalizationSupport() == NationalizationSupport.EXPLICIT ) {
			isNationalized = modelXProperty.isAnnotationPresent( Nationalized.class )
					|| buildingContext.getBuildingOptions().useNationalizedCharacterData();
		}

		applyJpaConverter( modelXProperty, converterDescriptor );

		final Class<? extends UserType> userTypeImpl = kind.mappingAccess.customType( modelXProperty );
		if ( userTypeImpl != null ) {
			applyExplicitType( userTypeImpl, kind.mappingAccess.customTypeParameters( modelXProperty ) );

			// An explicit custom UserType has top precedence when we get to BasicValue resolution.
			return;
		}

		switch ( kind ) {
			case ATTRIBUTE: {
				prepareBasicAttribute( declaringClassName, modelXProperty, modelPropertyTypeXClass );
				break;
			}
			case COLLECTION_ID: {
				prepareCollectionId( modelXProperty );
				break;
			}
			case LIST_INDEX: {
				prepareListIndex( modelXProperty );
				break;
			}
			case MAP_KEY: {
				prepareMapKey( modelXProperty, modelPropertyTypeXClass );
				break;
			}
			case COLLECTION_ELEMENT: {
				prepareCollectionElement( modelXProperty, modelPropertyTypeXClass );
				break;
			}
			default: {
				throw new IllegalArgumentException( "Unexpected binder type : " + kind );
			}
		}

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void applyExplicitType(Class<? extends UserType> impl, Parameter[] params) {
		this.explicitCustomType = (Class) impl;
		this.explicitLocalTypeParams = extractTypeParams( params );
	}

	@SuppressWarnings("unchecked")
	private Map extractTypeParams(Parameter[] parameters) {
		if ( parameters == null || parameters.length == 0 ) {
			return Collections.emptyMap();
		}

		if ( parameters.length == 1 ) {
			return Collections.singletonMap( parameters[0].name(), parameters[0].value() );
		}

		final Map map = new HashMap();
		for ( Parameter parameter: parameters ) {
			map.put( parameter.name(), parameter.value() );
		}

		return map;
	}

	private void prepareCollectionId(XProperty modelXProperty) {
		final CollectionId collectionIdAnn = modelXProperty.getAnnotation( CollectionId.class );
		if ( collectionIdAnn == null ) {
			throw new MappingException( "idbag mapping missing @CollectionId" );
		}

		final ManagedBeanRegistry beanRegistry = buildingContext.getBootstrapContext()
				.getServiceRegistry()
				.getService( ManagedBeanRegistry.class );

		explicitBasicTypeName = null;
		implicitJavaTypeAccess = (typeConfiguration) -> null;

		explicitJtdAccess = (typeConfiguration) -> {
			final CollectionIdJavaType javaTypeAnn = modelXProperty.getAnnotation( CollectionIdJavaType.class );
			if ( javaTypeAnn != null ) {
				final Class<? extends BasicJavaDescriptor<?>> javaType = normalizeJavaType( javaTypeAnn.value() );
				if ( javaType != null ) {
					final ManagedBean<? extends BasicJavaDescriptor<?>> bean = beanRegistry.getBean( javaType );
					return bean.getBeanInstance();
				}
			}

			return null;
		};

		explicitJdbcTypeAccess = (typeConfiguration) -> {
			final CollectionIdJdbcType jdbcTypeAnn = modelXProperty.getAnnotation( CollectionIdJdbcType.class );
			if ( jdbcTypeAnn != null ) {
				final Class<? extends JdbcTypeDescriptor> jdbcType = normalizeJdbcType( jdbcTypeAnn.value() );
				if ( jdbcType != null ) {
					final ManagedBean<? extends JdbcTypeDescriptor> managedBean = beanRegistry.getBean( jdbcType );
					return managedBean.getBeanInstance();
				}
			}

			final CollectionIdJdbcTypeCode jdbcTypeCodeAnn = modelXProperty.getAnnotation( CollectionIdJdbcTypeCode.class );
			if ( jdbcTypeCodeAnn != null ) {
				if ( jdbcTypeCodeAnn.value() != Integer.MIN_VALUE ) {
					return typeConfiguration.getJdbcTypeDescriptorRegistry().getDescriptor( jdbcTypeCodeAnn.value() );
				}
			}

			return null;
		};

		explicitMutabilityAccess = (typeConfiguration) -> {
			final CollectionIdMutability mutabilityAnn = modelXProperty.getAnnotation( CollectionIdMutability.class );
			if ( mutabilityAnn != null ) {
				final Class<? extends MutabilityPlan<?>> mutability = normalizeMutability( mutabilityAnn.value() );
				if ( mutability != null ) {
					final ManagedBean<? extends MutabilityPlan<?>> jtdBean = beanRegistry
							.getBean( mutability );
					return jtdBean.getBeanInstance();
				}
			}

			// see if the value's type Class is annotated `@Immutable`
			if ( implicitJavaTypeAccess != null ) {
				final Class<?> attributeType = ReflectHelper.getClass( implicitJavaTypeAccess.apply( typeConfiguration ) );
				if ( attributeType != null ) {
					if ( attributeType.isAnnotationPresent( Immutable.class ) ) {
						return ImmutableMutabilityPlan.instance();
					}
				}
			}

			// if the value is converted, see if the converter Class is annotated `@Immutable`
			if ( converterDescriptor != null ) {
				if ( converterDescriptor.getAttributeConverterClass().isAnnotationPresent( Immutable.class ) ) {
					return ImmutableMutabilityPlan.instance();
				}
			}

			final Class<? extends UserType> customTypeImpl = Kind.ATTRIBUTE.mappingAccess.customType( modelXProperty );
			if ( customTypeImpl.isAnnotationPresent( Immutable.class ) ) {
				return ImmutableMutabilityPlan.instance();
			}

			// generally, this will trigger usage of the `JavaTypeDescriptor#getMutabilityPlan`
			return null;
		};

		// todo (6.0) - handle generator
		final String generator = collectionIdAnn.generator();
	}

	private void prepareMapKey(
			XProperty mapAttribute,
			XClass modelPropertyTypeXClass) {
		final XClass mapKeyClass;
		if ( modelPropertyTypeXClass == null ) {
			mapKeyClass = mapAttribute.getMapKey();
		}
		else {
			mapKeyClass = modelPropertyTypeXClass;
		}
		final Class<?> implicitJavaType = buildingContext.getBootstrapContext()
				.getReflectionManager()
				.toClass( mapKeyClass );

		implicitJavaTypeAccess = (typeConfiguration) -> implicitJavaType;

		final MapKeyEnumerated mapKeyEnumeratedAnn = mapAttribute.getAnnotation( MapKeyEnumerated.class );
		if ( mapKeyEnumeratedAnn != null ) {
			enumType = mapKeyEnumeratedAnn.value();
		}

		final MapKeyTemporal mapKeyTemporalAnn = mapAttribute.getAnnotation( MapKeyTemporal.class );
		if ( mapKeyTemporalAnn != null ) {
			temporalPrecision = mapKeyTemporalAnn.value();
		}

		final ManagedBeanRegistry managedBeanRegistry = buildingContext.getBootstrapContext()
				.getServiceRegistry()
				.getService( ManagedBeanRegistry.class );

		explicitJdbcTypeAccess = typeConfiguration -> {
			final MapKeyJdbcType jdbcTypeAnn = mapAttribute.getAnnotation( MapKeyJdbcType.class );
			if ( jdbcTypeAnn != null ) {
				final Class<? extends JdbcTypeDescriptor> jdbcTypeImpl = normalizeJdbcType( jdbcTypeAnn.value() );
				if ( jdbcTypeImpl != null ) {
					final ManagedBean<? extends JdbcTypeDescriptor> jdbcTypeBean = managedBeanRegistry.getBean( jdbcTypeImpl );
					return jdbcTypeBean.getBeanInstance();
				}
			}

			final MapKeyJdbcTypeCode jdbcTypeCodeAnn = mapAttribute.getAnnotation( MapKeyJdbcTypeCode.class );
			if ( jdbcTypeCodeAnn != null ) {
				final int jdbcTypeCode = jdbcTypeCodeAnn.value();
				if ( jdbcTypeCode != Integer.MIN_VALUE ) {
					return typeConfiguration.getJdbcTypeDescriptorRegistry().getDescriptor( jdbcTypeCode );
				}
			}

			return null;
		};

		explicitJtdAccess = typeConfiguration -> {
			final MapKeyJavaType javaTypeAnn = mapAttribute.getAnnotation( MapKeyJavaType.class );
			if ( javaTypeAnn != null ) {
				final Class<? extends BasicJavaDescriptor<?>> jdbcTypeImpl = normalizeJavaType( javaTypeAnn.value() );
				if ( jdbcTypeImpl != null ) {
					final ManagedBean<? extends BasicJavaDescriptor> jdbcTypeBean = managedBeanRegistry.getBean( jdbcTypeImpl );
					return jdbcTypeBean.getBeanInstance();
				}
			}

			return null;
		};

		explicitMutabilityAccess = typeConfiguration -> {
			final MapKeyMutability mutabilityAnn = mapAttribute.getAnnotation( MapKeyMutability.class );
			if ( mutabilityAnn != null ) {
				final Class<? extends MutabilityPlan<?>> mutability = normalizeMutability( mutabilityAnn.value() );
				if ( mutability != null ) {
					final ManagedBean<? extends MutabilityPlan<?>> jtdBean = managedBeanRegistry.getBean( mutability );
					return jtdBean.getBeanInstance();
				}
			}

			// see if the value's type Class is annotated `@Immutable`
			if ( implicitJavaTypeAccess != null ) {
				final Class<?> attributeType = ReflectHelper.getClass( implicitJavaTypeAccess.apply( typeConfiguration ) );
				if ( attributeType != null ) {
					if ( attributeType.isAnnotationPresent( Immutable.class ) ) {
						return ImmutableMutabilityPlan.instance();
					}
				}
			}

			// if the value is converted, see if the converter Class is annotated `@Immutable`
			if ( converterDescriptor != null ) {
				if ( converterDescriptor.getAttributeConverterClass().isAnnotationPresent( Immutable.class ) ) {
					return ImmutableMutabilityPlan.instance();
				}
			}

			final Class<? extends UserType> customTypeImpl = Kind.MAP_KEY.mappingAccess.customType( mapAttribute );
			if ( customTypeImpl != null ) {
				if ( customTypeImpl.isAnnotationPresent( Immutable.class ) ) {
					return ImmutableMutabilityPlan.instance();
				}
			}

			// generally, this will trigger usage of the `JavaTypeDescriptor#getMutabilityPlan`
			return null;
		};
		mapKeySupplementalDetails( mapAttribute );
	}

	private void prepareListIndex(XProperty listAttribute) {
		implicitJavaTypeAccess = typeConfiguration -> Integer.class;

		final ManagedBeanRegistry beanRegistry = buildingContext
				.getBootstrapContext()
				.getServiceRegistry()
				.getService( ManagedBeanRegistry.class );

		explicitJtdAccess = (typeConfiguration) -> {
			final ListIndexJavaType javaTypeAnn = listAttribute.getAnnotation( ListIndexJavaType.class );
			if ( javaTypeAnn != null ) {
				final Class<? extends BasicJavaDescriptor<?>> javaType = normalizeJavaType( javaTypeAnn.value() );
				if ( javaType != null ) {
					final ManagedBean<? extends BasicJavaDescriptor<?>> bean = beanRegistry.getBean( javaType );
					return bean.getBeanInstance();
				}
			}

			return null;
		};

		explicitJdbcTypeAccess = (typeConfiguration) -> {
			final ListIndexJdbcType jdbcTypeAnn = listAttribute.getAnnotation( ListIndexJdbcType.class );
			if ( jdbcTypeAnn != null ) {
				final Class<? extends JdbcTypeDescriptor> jdbcType = normalizeJdbcType( jdbcTypeAnn.value() );
				if ( jdbcType != null ) {
					final ManagedBean<? extends JdbcTypeDescriptor> bean = beanRegistry.getBean( jdbcType );
					return bean.getBeanInstance();
				}
			}

			final ListIndexJdbcTypeCode jdbcTypeCodeAnn = listAttribute.getAnnotation( ListIndexJdbcTypeCode.class );
			if ( jdbcTypeCodeAnn != null ) {
				return typeConfiguration.getJdbcTypeDescriptorRegistry().getDescriptor( jdbcTypeCodeAnn.value() );
			}

			return null;
		};
	}

	private void prepareCollectionElement(XProperty attributeXProperty, XClass elementTypeXClass) {

		// todo (6.0) : @SqlType / @SqlTypeDescriptor

		Class<T> javaType;
		//noinspection unchecked
		if ( elementTypeXClass == null && attributeXProperty.isArray() ) {
			javaType = buildingContext.getBootstrapContext()
					.getReflectionManager()
					.toClass( attributeXProperty.getElementClass() );
		}
		else {
			javaType = buildingContext.getBootstrapContext()
					.getReflectionManager()
					.toClass( elementTypeXClass );
		}

		implicitJavaTypeAccess = typeConfiguration -> javaType;

		final Temporal temporalAnn = attributeXProperty.getAnnotation( Temporal.class );
		if ( temporalAnn != null ) {
			temporalPrecision = temporalAnn.value();
			if ( temporalPrecision == null ) {
				throw new IllegalStateException(
						"No jakarta.persistence.TemporalType defined for @jakarta.persistence.Temporal " +
								"associated with attribute " + attributeXProperty.getDeclaringClass().getName() +
								'.' + attributeXProperty.getName()
				);
			}
		}
		else {
			temporalPrecision = null;
		}

		if ( javaType.isEnum() ) {
			final Enumerated enumeratedAnn = attributeXProperty.getAnnotation( Enumerated.class );
			if ( enumeratedAnn != null ) {
				enumType = enumeratedAnn.value();
				if ( enumType == null ) {
					throw new IllegalStateException(
							"jakarta.persistence.EnumType was null on @jakarta.persistence.Enumerated " +
									" associated with attribute " + attributeXProperty.getDeclaringClass().getName() +
									'.' + attributeXProperty.getName()
					);
				}
			}
		}
		else {
			enumType = null;
		}

		normalSupplementalDetails( attributeXProperty, buildingContext );
	}

	@SuppressWarnings("unchecked")
	private void prepareBasicAttribute(String declaringClassName, XProperty attributeDescriptor, XClass attributeType) {
		final Class<T> javaType = buildingContext.getBootstrapContext()
				.getReflectionManager()
				.toClass( attributeType );

		implicitJavaTypeAccess = typeConfiguration -> javaType;

		final Temporal temporalAnn = attributeDescriptor.getAnnotation( Temporal.class );
		if ( temporalAnn != null ) {
			this.temporalPrecision = temporalAnn.value();
			if ( this.temporalPrecision == null ) {
				throw new IllegalStateException(
						"No jakarta.persistence.TemporalType defined for @jakarta.persistence.Temporal " +
								"associated with attribute " + attributeDescriptor.getDeclaringClass().getName() +
								'.' + attributeDescriptor.getName()
				);
			}
		}
		else {
			this.temporalPrecision = null;
		}

		if ( javaType.isEnum() ) {
			final Enumerated enumeratedAnn = attributeDescriptor.getAnnotation( Enumerated.class );
			if ( enumeratedAnn != null ) {
				this.enumType = enumeratedAnn.value();
				if ( this.enumType == null ) {
					throw new IllegalStateException(
							"jakarta.persistence.EnumType was null on @jakarta.persistence.Enumerated " +
									" associated with attribute " + attributeDescriptor.getDeclaringClass().getName() +
									'.' + attributeDescriptor.getName()
					);
				}
			}
		}
		else {
			if ( attributeDescriptor.isAnnotationPresent( Enumerated.class ) ) {
				throw new AnnotationException(
						String.format(
								"Attribute [%s.%s] was annotated as enumerated, but its java type is not an enum [%s]",
								declaringClassName,
								attributeDescriptor.getName(),
								attributeType.getName()
						)
				);
			}
			this.enumType = null;
		}

		normalSupplementalDetails( attributeDescriptor, buildingContext );
	}

	private void mapKeySupplementalDetails(XProperty attributeXProperty) {
	}


	private void normalSupplementalDetails(
			XProperty attributeXProperty,
			MetadataBuildingContext buildingContext) {
		final ManagedBeanRegistry managedBeanRegistry = buildingContext.getBootstrapContext()
				.getServiceRegistry()
				.getService( ManagedBeanRegistry.class );

		explicitJdbcTypeAccess = typeConfiguration -> {
			final JdbcType jdbcTypeAnn = attributeXProperty.getAnnotation( JdbcType.class );
			if ( jdbcTypeAnn != null ) {
				final Class<? extends JdbcTypeDescriptor> jdbcType = normalizeJdbcType( jdbcTypeAnn.value() );
				if ( jdbcType != null ) {
					final ManagedBean<? extends JdbcTypeDescriptor> jdbcTypeBean = managedBeanRegistry.getBean( jdbcType );
					return jdbcTypeBean.getBeanInstance();
				}
			}

			final JdbcTypeCode jdbcTypeCodeAnn = attributeXProperty.getAnnotation( JdbcTypeCode.class );
			if ( jdbcTypeCodeAnn != null ) {
				final int jdbcTypeCode = jdbcTypeCodeAnn.value();
				if ( jdbcTypeCode != Integer.MIN_VALUE ) {
					return typeConfiguration.getJdbcTypeDescriptorRegistry().getDescriptor( jdbcTypeCode );
				}
			}

			return null;
		};

		explicitJtdAccess = typeConfiguration -> {
			final JavaType javaTypeAnn = attributeXProperty.getAnnotation( JavaType.class );
			if ( javaTypeAnn != null ) {
				final Class<? extends BasicJavaDescriptor<?>> javaType = normalizeJavaType( javaTypeAnn.value() );

				if ( javaType != null ) {
					final ManagedBean<? extends BasicJavaDescriptor<?>> jtdBean = managedBeanRegistry.getBean( javaType );
					return jtdBean.getBeanInstance();
				}
			}

			return null;
		};

		explicitMutabilityAccess = typeConfiguration -> {
			final Mutability mutabilityAnn = attributeXProperty.getAnnotation( Mutability.class );
			if ( mutabilityAnn != null ) {
				final Class<? extends MutabilityPlan<?>> mutability = normalizeMutability( mutabilityAnn.value() );
				if ( mutability != null ) {
					final ManagedBean<? extends MutabilityPlan<?>> jtdBean = managedBeanRegistry.getBean( mutability );
					return jtdBean.getBeanInstance();
				}
			}

			final Immutable immutableAnn = attributeXProperty.getAnnotation( Immutable.class );
			if ( immutableAnn != null ) {
				return ImmutableMutabilityPlan.instance();
			}

			// see if the value's type Class is annotated `@Immutable`
			if ( implicitJavaTypeAccess != null ) {
				final Class<?> attributeType = ReflectHelper.getClass( implicitJavaTypeAccess.apply( typeConfiguration ) );
				if ( attributeType != null ) {
					if ( attributeType.isAnnotationPresent( Immutable.class ) ) {
						return ImmutableMutabilityPlan.instance();
					}
				}
			}

			// if the value is converted, see if the converter Class is annotated `@Immutable`
			if ( converterDescriptor != null ) {
				if ( converterDescriptor.getAttributeConverterClass().isAnnotationPresent( Immutable.class ) ) {
					return ImmutableMutabilityPlan.instance();
				}
			}

			final Class<? extends UserType> customTypeImpl = Kind.ATTRIBUTE.mappingAccess.customType( attributeXProperty );
			if ( customTypeImpl != null ) {
				if ( customTypeImpl.isAnnotationPresent( Immutable.class ) ) {
					return ImmutableMutabilityPlan.instance();
				}
			}

			// generally, this will trigger usage of the `JavaTypeDescriptor#getMutabilityPlan`
			return null;
		};

		final Enumerated enumeratedAnn = attributeXProperty.getAnnotation( Enumerated.class );
		if ( enumeratedAnn != null ) {
			enumType = enumeratedAnn.value();
		}

		final Temporal temporalAnn = attributeXProperty.getAnnotation( Temporal.class );
		if ( temporalAnn != null ) {
			temporalPrecision = temporalAnn.value();
		}
	}


	private static Class<? extends UserType<?>> normalizeUserType(CustomType customTypeAnn) {
		if ( customTypeAnn == null ) {
			return null;
		}
		return normalizeUserType( (Class) customTypeAnn.value() );
	}

	private static Class<? extends UserType<?>> normalizeUserType(Class<? extends UserType<?>> userType) {
		if ( userType == null ) {
			return null;
		}

		if ( NoUserType.class.isAssignableFrom( userType ) ) {
			return null;
		}

		return userType;
	}

	private Class<? extends JdbcTypeDescriptor> normalizeJdbcType(Class<? extends JdbcTypeDescriptor> jdbcType) {
		if ( jdbcType == null ) {
			return null;
		}

		if ( NoJdbcTypeDescriptor.class.isAssignableFrom( jdbcType ) ) {
			return null;
		}

		return jdbcType;
	}

	private static Class<? extends BasicJavaDescriptor<?>> normalizeJavaType(Class<? extends BasicJavaDescriptor<?>> javaType) {
		if ( javaType == null ) {
			return null;
		}

		if ( NoJavaTypeDescriptor.class.isAssignableFrom( javaType ) ) {
			return null;
		}

		return javaType;
	}

	private Class<? extends MutabilityPlan<?>> normalizeMutability(Class<? extends MutabilityPlan<?>> mutability) {
		if ( mutability == null ) {
			return null;
		}

		if ( NoMutabilityPlan.class.isAssignableFrom( mutability ) ) {
			return null;
		}

		return mutability;
	}

	private static Class resolveJavaType(XClass returnedClassOrElement, MetadataBuildingContext buildingContext) {
		return buildingContext.getBootstrapContext()
					.getReflectionManager()
					.toClass( returnedClassOrElement );
	}

	private Dialect getDialect() {
		return buildingContext.getBuildingOptions()
				.getServiceRegistry()
				.getService( JdbcServices.class )
				.getJdbcEnvironment()
				.getDialect();
	}

	private void applyJpaConverter(XProperty property, ConverterDescriptor attributeConverterDescriptor) {
		if ( attributeConverterDescriptor == null ) {
			return;
		}

		LOG.debugf( "Applying JPA converter [%s:%s]", persistentClassName, property.getName() );

		if ( property.isAnnotationPresent( Id.class ) ) {
			LOG.debugf( "Skipping AttributeConverter checks for Id attribute [%s]", property.getName() );
			return;
		}

		if ( property.isAnnotationPresent( Version.class ) ) {
			LOG.debugf( "Skipping AttributeConverter checks for version attribute [%s]", property.getName() );
			return;
		}

		if ( kind == Kind.MAP_KEY ) {
			if ( property.isAnnotationPresent( MapKeyTemporal.class ) ) {
				LOG.debugf( "Skipping AttributeConverter checks for map-key annotated as MapKeyTemporal [%s]", property.getName() );
				return;
			}

			if ( property.isAnnotationPresent( MapKeyEnumerated.class ) ) {
				LOG.debugf( "Skipping AttributeConverter checks for map-key annotated as MapKeyEnumerated [%s]", property.getName() );
				return;
			}
		}
		else {
			if ( property.isAnnotationPresent( Temporal.class ) ) {
				LOG.debugf( "Skipping AttributeConverter checks for Temporal attribute [%s]", property.getName() );
				return;
			}

			if ( property.isAnnotationPresent( Enumerated.class ) ) {
				LOG.debugf( "Skipping AttributeConverter checks for Enumerated attribute [%s]", property.getName() );
				return;
			}
		}

		if ( isAssociation() ) {
			LOG.debugf( "Skipping AttributeConverter checks for association attribute [%s]", property.getName() );
			return;
		}

		this.converterDescriptor = attributeConverterDescriptor;
	}

	private boolean isAssociation() {
		// todo : this information is only known to caller(s), need to pass that information in somehow.
		// or, is this enough?
		return referencedEntityName != null;
	}

	public void setExplicitType(String explicitType) {
		this.explicitBasicTypeName = explicitType;
	}

	private void validate() {
		Ejb3Column.checkPropertyConsistency( columns, propertyName );
	}

	public BasicValue make() {
		if ( basicValue != null ) {
			return basicValue;
		}

		validate();

		LOG.debugf( "building BasicValue for %s", propertyName );

		if ( table == null ) {
			table = columns[0].getTable();
		}

		basicValue = new BasicValue( buildingContext, table );

		if ( isNationalized ) {
			basicValue.makeNationalized();
		}

		if ( isLob ) {
			basicValue.makeLob();
		}

		if ( enumType != null ) {
			basicValue.setEnumerationStyle( enumType );
		}

		if ( temporalPrecision != null ) {
			basicValue.setTemporalPrecision( temporalPrecision );
		}

		// todo (6.0) : explicit SqlTypeDescriptor / JDBC type-code
		// todo (6.0) : explicit mutability / immutable
		// todo (6.0) : explicit Comparator

		linkWithValue();

		boolean isInSecondPass = buildingContext.getMetadataCollector().isInSecondPass();
		if ( !isInSecondPass ) {
			//Defer this to the second pass
			buildingContext.getMetadataCollector().addSecondPass( new SetBasicValueTypeSecondPass( this ) );
		}
		else {
			//We are already in second pass
			fillSimpleValue();
		}

		return basicValue;
	}

	public void linkWithValue() {
		if ( columns[0].isNameDeferred() && !buildingContext.getMetadataCollector().isInSecondPass() && referencedEntityName != null ) {
			buildingContext.getMetadataCollector().addSecondPass(
					new PkDrivenByDefaultMapsIdSecondPass(
							referencedEntityName, (Ejb3JoinColumn[]) columns, basicValue
					)
			);
		}
		else {
			for ( Ejb3Column column : columns ) {
				column.linkWithValue( basicValue );
			}
		}
	}

	public void fillSimpleValue() {
		LOG.debugf( "Starting `BasicValueBinder#fillSimpleValue` for %s", propertyName );

		basicValue.setExplicitTypeName( explicitBasicTypeName );
		basicValue.setExplicitTypeParams( explicitLocalTypeParams );

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// todo (6.0) : we are dropping support for @Type and @TypeDef from annotations
		//		so this handling can go away.  most of this (enum, temporal, ect) will be
		//		handled by BasicValue already.  this stuff is all just to drive
		// 		DynamicParameterizedType handling - just pass them (or a Supplier?) into
		//		BasicValue so that it has access to them as needed

		Class<?> typeClass = null;

		if ( explicitBasicTypeName != null ) {
			final TypeDefinition typeDefinition = buildingContext
					.getTypeDefinitionRegistry()
					.resolve( explicitBasicTypeName );
			if ( typeDefinition == null ) {
				final BasicType<?> registeredType = getTypeConfiguration()
						.getBasicTypeRegistry()
						.getRegisteredType( explicitBasicTypeName );
				if ( registeredType == null ) {
					typeClass = buildingContext
							.getBootstrapContext()
							.getClassLoaderAccess()
							.classForName( explicitBasicTypeName );
				}
			}
			else {
				typeClass = typeDefinition.getTypeImplementorClass();
			}
		}
		// Enum type is parameterized and prior to Hibernate 6 we always resolved the type class
		else if ( enumType != null || isEnum() ) {
			typeClass = org.hibernate.type.EnumType.class;
		}
		// The Lob type is parameterized and prior to Hibernate 6 we always resolved the type class
		else if ( isLob || isSerializable() ) {
			typeClass = SerializableToBlobType.class;
		}

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

		if ( ( explicitCustomType != null && DynamicParameterizedType.class.isAssignableFrom( explicitCustomType ) )
				|| ( typeClass != null && DynamicParameterizedType.class.isAssignableFrom( typeClass ) ) ) {
			final Map<String, Object> parameters = createDynamicParameterizedTypeParameters();
			basicValue.setTypeParameters( (Map) parameters );
		}

		basicValue.setJpaAttributeConverterDescriptor( converterDescriptor );

		basicValue.setImplicitJavaTypeAccess( implicitJavaTypeAccess );
		basicValue.setExplicitJavaTypeAccess( explicitJtdAccess );
		basicValue.setExplicitJdbcTypeAccess( explicitJdbcTypeAccess );
		basicValue.setExplicitMutabilityPlanAccess( explicitMutabilityAccess );

		if ( enumType != null ) {
			basicValue.setEnumerationStyle( enumType );
		}

		if ( temporalPrecision != null ) {
			basicValue.setTemporalPrecision( temporalPrecision );
		}

		if ( isLob ) {
			basicValue.makeLob();
		}

		if ( isNationalized ) {
			basicValue.makeNationalized();
		}

		if ( explicitCustomType != null ) {
			basicValue.setExplicitCustomType( explicitCustomType );
		}
	}

	private Map<String, Object> createDynamicParameterizedTypeParameters() {
		final Map<String, Object> parameters = new HashMap<>();

		if ( returnedClassName == null ) {
			throw new MappingException( "Returned class name not specified for basic mapping: " + xproperty.getName() );
		}

		parameters.put( DynamicParameterizedType.RETURNED_CLASS, returnedClassName );
		parameters.put( DynamicParameterizedType.XPROPERTY, xproperty );
		parameters.put( DynamicParameterizedType.PROPERTY, xproperty.getName() );

		parameters.put( DynamicParameterizedType.IS_DYNAMIC, Boolean.toString( true ) );
		parameters.put( DynamicParameterizedType.IS_PRIMARY_KEY, Boolean.toString( kind == Kind.MAP_KEY ) );

		if ( persistentClassName != null ) {
			parameters.put( DynamicParameterizedType.ENTITY, persistentClassName );
		}

		if ( returnedClassName != null ) {
			parameters.put( DynamicParameterizedType.RETURNED_CLASS, returnedClassName );
		}

		if ( accessType != null ) {
			parameters.put( DynamicParameterizedType.ACCESS_TYPE, accessType.getType() );
		}

		if ( explicitLocalTypeParams != null ) {
			parameters.putAll( explicitLocalTypeParams );
		}

		return parameters;
	}

	private boolean isEnum() {
		Class<?> clazz = null;
		if ( implicitJavaTypeAccess != null ) {
			java.lang.reflect.Type type = implicitJavaTypeAccess.apply( getTypeConfiguration() );
			if ( type instanceof ParameterizedType ) {
				type = ( (ParameterizedType) type ).getRawType();
			}
			if ( type instanceof Class<?> ) {
				clazz = (Class<?>) type;
			}
		}
		return clazz != null && clazz.isEnum();
	}

	private boolean isSerializable() {
		Class<?> clazz = null;
		if ( implicitJavaTypeAccess != null ) {
			java.lang.reflect.Type type = implicitJavaTypeAccess.apply( getTypeConfiguration() );
			if ( type instanceof ParameterizedType ) {
				type = ( (ParameterizedType) type ).getRawType();
			}
			if ( type instanceof Class<?> ) {
				clazz = (Class<?>) type;
			}
		}
		return clazz != null && Serializable.class.isAssignableFrom( clazz );
	}




	/**
	 * Access to detail of basic value mappings based on {@link Kind}
	 */
	private interface BasicMappingAccess {
		Class<? extends BasicJavaDescriptor<?>> javaType(XProperty xProperty);

		Class<? extends JdbcTypeDescriptor> jdbcType(XProperty xProperty);
		int jdbcTypeCode(XProperty xProperty);

		Class<? extends UserType> customType(XProperty xProperty);
		Parameter[] customTypeParameters(XProperty xProperty);
	}

	private static class ValueMappingAccess implements BasicMappingAccess {
		public static final ValueMappingAccess INSTANCE = new ValueMappingAccess();

		@Override
		public Class<? extends BasicJavaDescriptor<?>> javaType(XProperty xProperty) {
			final JavaType javaType = xProperty.getAnnotation( JavaType.class );
			if ( javaType == null ) {
				return null;
			}
			return normalizeJavaType( javaType.value() );
		}

		@Override
		public Class<? extends JdbcTypeDescriptor> jdbcType(XProperty xProperty) {
			final JdbcType jdbcType = xProperty.getAnnotation( JdbcType.class );
			if ( jdbcType == null ) {
				return null;
			}
			return jdbcType.value();
		}

		@Override
		public int jdbcTypeCode(XProperty xProperty) {
			final JdbcTypeCode jdbcTypeCode = xProperty.getAnnotation( JdbcTypeCode.class );
			if ( jdbcTypeCode != null ) {
				return jdbcTypeCode.value();
			}
			return Integer.MIN_VALUE;
		}

		@Override
		public Class<? extends UserType<?>> customType(XProperty xProperty) {
			final CustomType customType = xProperty.getAnnotation( CustomType.class );
			if ( customType == null ) {
				return null;
			}

			return normalizeUserType( (Class) customType.value() );
		}

		@Override
		public Parameter[] customTypeParameters(XProperty xProperty) {
			final CustomType customType = xProperty.getAnnotation( CustomType.class );
			if ( customType == null ) {
				return null;
			}
			return customType.parameters();
		}
	}

	private static class MapKeyMappingAccess implements BasicMappingAccess {
		public static final MapKeyMappingAccess INSTANCE = new MapKeyMappingAccess();

		@Override
		public Class<? extends BasicJavaDescriptor<?>> javaType(XProperty xProperty) {
			final MapKeyJavaType javaType = xProperty.getAnnotation( MapKeyJavaType.class );
			if ( javaType == null ) {
				return null;
			}
			return normalizeJavaType( javaType.value() );
		}

		@Override
		public Class<? extends JdbcTypeDescriptor> jdbcType(XProperty xProperty) {
			final MapKeyJdbcType jdbcType = xProperty.getAnnotation( MapKeyJdbcType.class );
			if ( jdbcType == null ) {
				return null;
			}
			return jdbcType.value();
		}

		@Override
		public int jdbcTypeCode(XProperty xProperty) {
			final MapKeyJdbcTypeCode jdbcTypeCode = xProperty.getAnnotation( MapKeyJdbcTypeCode.class );
			if ( jdbcTypeCode != null ) {
				return jdbcTypeCode.value();
			}
			return Integer.MIN_VALUE;
		}

		@Override
		public Class<? extends UserType<?>> customType(XProperty xProperty) {
			final MapKeyCustomType customType = xProperty.getAnnotation( MapKeyCustomType.class );
			if ( customType == null ) {
				return null;
			}

			return normalizeUserType( customType.value() );
		}

		@Override
		public Parameter[] customTypeParameters(XProperty xProperty) {
			final MapKeyCustomType customType = xProperty.getAnnotation( MapKeyCustomType.class );
			if ( customType == null ) {
				return null;
			}
			return customType.parameters();
		}
	}

	private static class CollectionIdMappingAccess implements BasicMappingAccess {
		public static final CollectionIdMappingAccess INSTANCE = new CollectionIdMappingAccess();

		@Override
		public Class<? extends BasicJavaDescriptor<?>> javaType(XProperty xProperty) {
			final CollectionIdJavaType javaType = xProperty.getAnnotation( CollectionIdJavaType.class );
			if ( javaType == null ) {
				return null;
			}
			return normalizeJavaType( javaType.value() );
		}

		@Override
		public Class<? extends JdbcTypeDescriptor> jdbcType(XProperty xProperty) {
			final CollectionIdJdbcType jdbcType = xProperty.getAnnotation( CollectionIdJdbcType.class );
			if ( jdbcType == null ) {
				return null;
			}
			return jdbcType.value();
		}

		@Override
		public int jdbcTypeCode(XProperty xProperty) {
			final CollectionIdJdbcTypeCode jdbcTypeCode = xProperty.getAnnotation( CollectionIdJdbcTypeCode.class );
			if ( jdbcTypeCode != null ) {
				return jdbcTypeCode.value();
			}
			return Integer.MIN_VALUE;
		}

		@Override
		public Class<? extends UserType<?>> customType(XProperty xProperty) {
			final CollectionIdCustomType customType = xProperty.getAnnotation( CollectionIdCustomType.class );
			if ( customType == null ) {
				return null;
			}

			return normalizeUserType( customType.value() );
		}

		@Override
		public Parameter[] customTypeParameters(XProperty xProperty) {
			final CollectionIdCustomType customType = xProperty.getAnnotation( CollectionIdCustomType.class );
			if ( customType == null ) {
				return null;
			}
			return customType.parameters();
		}
	}

	private static class ListIndexMappingAccess implements BasicMappingAccess {
		public static final ListIndexMappingAccess INSTANCE = new ListIndexMappingAccess();

		@Override
		public Class<? extends BasicJavaDescriptor<?>> javaType(XProperty xProperty) {
			final ListIndexJavaType javaType = xProperty.getAnnotation( ListIndexJavaType.class );
			if ( javaType == null ) {
				return null;
			}
			return normalizeJavaType( javaType.value() );
		}

		@Override
		public Class<? extends JdbcTypeDescriptor> jdbcType(XProperty xProperty) {
			final ListIndexJdbcType jdbcType = xProperty.getAnnotation( ListIndexJdbcType.class );
			if ( jdbcType == null ) {
				return null;
			}
			return jdbcType.value();
		}

		@Override
		public int jdbcTypeCode(XProperty xProperty) {
			final ListIndexJdbcTypeCode jdbcTypeCode = xProperty.getAnnotation( ListIndexJdbcTypeCode.class );
			if ( jdbcTypeCode != null ) {
				return jdbcTypeCode.value();
			}
			return Integer.MIN_VALUE;
		}

		@Override
		public Class<? extends UserType<?>> customType(XProperty xProperty) {
			return null;
		}

		@Override
		public Parameter[] customTypeParameters(XProperty xProperty) {
			return null;
		}
	}
}
