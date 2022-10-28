/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.mapping;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.MappingException;
import org.hibernate.TimeZoneStorageStrategy;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.boot.model.TypeDefinition;
import org.hibernate.boot.model.TypeDefinitionRegistry;
import org.hibernate.boot.model.convert.internal.ClassBasedConverterDescriptor;
import org.hibernate.boot.model.convert.spi.ConverterDescriptor;
import org.hibernate.boot.model.convert.spi.JpaAttributeConverterCreationContext;
import org.hibernate.boot.model.process.internal.InferredBasicValueResolution;
import org.hibernate.boot.model.process.internal.InferredBasicValueResolver;
import org.hibernate.boot.model.process.internal.NamedBasicTypeResolution;
import org.hibernate.boot.model.process.internal.NamedConverterResolution;
import org.hibernate.boot.model.process.internal.UserTypeResolution;
import org.hibernate.boot.model.process.internal.VersionResolution;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.registry.classloading.spi.ClassLoadingException;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.internal.CoreLogging;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.model.convert.spi.BasicValueConverter;
import org.hibernate.resource.beans.spi.BeanInstanceProducer;
import org.hibernate.resource.beans.spi.ManagedBean;
import org.hibernate.resource.beans.spi.ManagedBeanRegistry;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.schema.extract.spi.ColumnTypeInformation;
import org.hibernate.type.BasicType;
import org.hibernate.type.CustomType;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.java.BasicJavaType;
import org.hibernate.type.descriptor.java.BasicPluralJavaType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.MutabilityPlan;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.hibernate.type.internal.BasicTypeImpl;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.type.spi.TypeConfigurationAware;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.UserType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.EnumType;
import jakarta.persistence.TemporalType;

import static org.hibernate.mapping.MappingHelper.injectParameters;

/**
 * @author Steve Ebersole
 */
public class BasicValue extends SimpleValue implements JdbcTypeIndicators, Resolvable {
	private static final CoreMessageLogger log = CoreLogging.messageLogger( BasicValue.class );

	private final TypeConfiguration typeConfiguration;

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// incoming "configuration" values

	private String explicitTypeName;
	private Map<String,String> explicitLocalTypeParams;

	private Function<TypeConfiguration, BasicJavaType> explicitJavaTypeAccess;
	private Function<TypeConfiguration, JdbcType> explicitJdbcTypeAccess;
	private Function<TypeConfiguration, MutabilityPlan> explicitMutabilityPlanAccess;
	private Function<TypeConfiguration, java.lang.reflect.Type> implicitJavaTypeAccess;

	private EnumType enumerationStyle;
	private TemporalType temporalPrecision;
	private TimeZoneStorageType timeZoneStorageType;

	private java.lang.reflect.Type resolvedJavaType;

	private String ownerName;
	private String propertyName;

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Resolved state - available after `#resolve`
	private Resolution<?> resolution;


	public BasicValue(MetadataBuildingContext buildingContext) {
		this( buildingContext, null );
	}

	public BasicValue(MetadataBuildingContext buildingContext, Table table) {
		super( buildingContext, table );

		this.typeConfiguration = buildingContext.getBootstrapContext().getTypeConfiguration();

		buildingContext.getMetadataCollector().registerValueMappingResolver( this::resolve );
	}

	public BasicValue(BasicValue original) {
		super( original );
		this.typeConfiguration = original.typeConfiguration;
		this.explicitTypeName = original.explicitTypeName;
		this.explicitLocalTypeParams = original.explicitLocalTypeParams == null
				? null
				: new HashMap<>(original.explicitLocalTypeParams);
		this.explicitJavaTypeAccess = original.explicitJavaTypeAccess;
		this.explicitJdbcTypeAccess = original.explicitJdbcTypeAccess;
		this.explicitMutabilityPlanAccess = original.explicitMutabilityPlanAccess;
		this.implicitJavaTypeAccess = original.implicitJavaTypeAccess;
		this.enumerationStyle = original.enumerationStyle;
		this.temporalPrecision = original.temporalPrecision;
		this.timeZoneStorageType = original.timeZoneStorageType;
		this.resolvedJavaType = original.resolvedJavaType;
		this.ownerName = original.ownerName;
		this.propertyName = original.propertyName;
	}

	@Override
	public BasicValue copy() {
		return new BasicValue( this );
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Setters - in preparation of resolution

	@Override
	public void setTypeUsingReflection(String className, String propertyName) throws MappingException {
		if ( resolution != null ) {
			throw new IllegalStateException( "BasicValue already resolved" );
		}

		this.ownerName = className;
		this.propertyName = propertyName;

		super.setTypeUsingReflection( className, propertyName );
	}

	public void setEnumerationStyle(EnumType enumerationStyle) {
		this.enumerationStyle = enumerationStyle;
	}

	public EnumType getEnumerationStyle() {
		return enumerationStyle;
	}

	public TimeZoneStorageType getTimeZoneStorageType() {
		return timeZoneStorageType;
	}

	public void setTimeZoneStorageType(TimeZoneStorageType timeZoneStorageType) {
		this.timeZoneStorageType = timeZoneStorageType;
	}

	public void setJpaAttributeConverterDescriptor(ConverterDescriptor descriptor) {
		setAttributeConverterDescriptor( descriptor );

		super.setJpaAttributeConverterDescriptor( descriptor );
	}

	@SuppressWarnings("rawtypes")
	public void setExplicitJavaTypeAccess(Function<TypeConfiguration, BasicJavaType> explicitJavaTypeAccess) {
		this.explicitJavaTypeAccess = explicitJavaTypeAccess;
	}

	public void setExplicitJdbcTypeAccess(Function<TypeConfiguration, JdbcType> jdbcTypeAccess) {
		this.explicitJdbcTypeAccess = jdbcTypeAccess;
	}

	public void setExplicitMutabilityPlanAccess(Function<TypeConfiguration, MutabilityPlan> explicitMutabilityPlanAccess) {
		this.explicitMutabilityPlanAccess = explicitMutabilityPlanAccess;
	}

	public void setImplicitJavaTypeAccess(Function<TypeConfiguration, java.lang.reflect.Type> implicitJavaTypeAccess) {
		this.implicitJavaTypeAccess = implicitJavaTypeAccess;
	}

	public Selectable getColumn() {
		if ( getColumnSpan() == 0 ) {
			return null;
		}
		return getColumn( 0 );
	}

	public java.lang.reflect.Type getResolvedJavaType() {
		return resolvedJavaType;
	}

	@Override
	public long getColumnLength() {
		final Selectable column = getColumn();
		if ( column instanceof Column ) {
			final Long length = ( (Column) column ).getLength();
			return length == null ? NO_COLUMN_LENGTH : length;
		}
		else {
			return NO_COLUMN_LENGTH;
		}
	}

	@Override
	public int getColumnPrecision() {
		final Selectable column = getColumn();
		if ( column instanceof Column ) {
			final Integer length = ( (Column) column ).getPrecision();
			return length == null ? NO_COLUMN_PRECISION : length;
		}
		else {
			return NO_COLUMN_PRECISION;
		}
	}

	@Override
	public int getColumnScale() {
		final Selectable column = getColumn();
		if ( column instanceof Column ) {
			final Integer length = ( (Column) column ).getScale();
			return length == null ? NO_COLUMN_SCALE : length;
		}
		else {
			return NO_COLUMN_SCALE;
		}
	}

	@Override
	public void addColumn(Column incomingColumn) {
		super.addColumn( incomingColumn );

		checkSelectable( incomingColumn );
	}

	@Override
	public void copyTypeFrom(SimpleValue sourceValue) {
		super.copyTypeFrom( sourceValue );
		if ( sourceValue instanceof BasicValue ) {
			final BasicValue basicValue = (BasicValue) sourceValue;
			this.resolution = basicValue.resolution;
			this.implicitJavaTypeAccess = (typeConfiguration) -> basicValue.implicitJavaTypeAccess.apply( typeConfiguration );
		}
	}

	private void checkSelectable(Selectable incomingColumn) {
		if ( incomingColumn == null ) {
			throw new IllegalArgumentException( "Incoming column was null" );
		}

		final Selectable column = getColumn();
		if ( column == incomingColumn || column.getText().equals( incomingColumn.getText() ) ) {
			log.debugf( "Skipping column re-registration: %s.%s", getTable().getName(), column.getText() );
			return;
		}

//		throw new IllegalStateException(
//				"BasicValue [" + ownerName + "." + propertyName +
//						"] already had column associated: `" + column.getText() +
//						"` -> `" + incomingColumn.getText() + "`"
//		);
	}

	@Override
	public void addColumn(Column incomingColumn, boolean isInsertable, boolean isUpdatable) {
		super.addColumn( incomingColumn, isInsertable, isUpdatable );

		checkSelectable( incomingColumn );
	}

	@Override
	public void addFormula(Formula formula) {
		super.addFormula( formula );

		checkSelectable( formula );
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Resolution

	@Override
	public Type getType() throws MappingException {
		resolve();
		assert getResolution() != null;

		return getResolution().getLegacyResolvedBasicType();
	}

	public Resolution<?> getResolution() {
		return resolution;
	}

	@Override
	public boolean resolve(MetadataBuildingContext buildingContext) {
		resolve();
		return true;
	}

	@Override
	public Resolution<?> resolve() {
		if ( resolution != null ) {
			return resolution;
		}

		resolution = buildResolution();

		if ( resolution == null ) {
			throw new IllegalStateException( "Unable to resolve BasicValue : " + this );
		}

		final Selectable column = getColumn();
		if ( column instanceof Column && resolution.getValueConverter() == null ) {
			final Column physicalColumn = (Column) column;
			if ( physicalColumn.getSqlTypeCode() == null ) {
				physicalColumn.setSqlTypeCode( resolution.getJdbcType().getDefaultSqlTypeCode() );
			}

			final BasicType<?> basicType = resolution.getLegacyResolvedBasicType();
			final Dialect dialect = getServiceRegistry().getService( JdbcServices.class ).getDialect();
			final String checkConstraint = physicalColumn.getCheckConstraint();
			if ( checkConstraint == null && dialect.supportsColumnCheck() ) {
				physicalColumn.setCheckConstraint(
						basicType.getJavaTypeDescriptor().getCheckCondition(
								physicalColumn.getQuotedName( dialect ),
								basicType.getJdbcType(),
								dialect
						)
				);
			}
		}

		return resolution;
	}

	protected Resolution<?> buildResolution() {
		Properties typeParameters = getTypeParameters();
		if ( typeParameters != null
				&& Boolean.parseBoolean( typeParameters.getProperty( DynamicParameterizedType.IS_DYNAMIC ) )
				&& typeParameters.get( DynamicParameterizedType.PARAMETER_TYPE ) == null ) {
			createParameterImpl();
		}
		if ( explicitTypeName != null ) {
			return interpretExplicitlyNamedType(
					explicitTypeName,
					enumerationStyle,
					implicitJavaTypeAccess,
					explicitJavaTypeAccess,
					explicitJdbcTypeAccess,
					explicitMutabilityPlanAccess,
					getAttributeConverterDescriptor(),
					typeParameters,
					this::setTypeParameters,
					this,
					typeConfiguration,
					getBuildingContext()
			);
		}


		if ( isVersion() ) {
			return VersionResolution.from(
					implicitJavaTypeAccess,
					explicitJavaTypeAccess,
					explicitJdbcTypeAccess,
					timeZoneStorageType,
					typeConfiguration,
					getBuildingContext()
			);
		}

		JavaType<?> jtd = null;

		// determine JavaType if we can

		final BasicJavaType explicitJtd;
		if ( explicitJavaTypeAccess != null ) {
			explicitJtd = explicitJavaTypeAccess.apply( typeConfiguration );
			if ( explicitJtd != null ) {
				jtd = explicitJtd;
			}
		}
		else {
			explicitJtd = null;
		}

		if ( jtd == null ) {
			if ( implicitJavaTypeAccess != null ) {
				final java.lang.reflect.Type implicitJtd = implicitJavaTypeAccess.apply( typeConfiguration );
				if ( implicitJtd != null ) {
					jtd = typeConfiguration.getJavaTypeRegistry().getDescriptor( implicitJtd );
				}
			}
		}

		if ( jtd == null ) {
			final JavaType<?> reflectedJtd = determineReflectedJavaType();
			if ( reflectedJtd != null ) {
				jtd = reflectedJtd;
			}
		}

		final ConverterDescriptor attributeConverterDescriptor = getAttributeConverterDescriptor();
		if ( attributeConverterDescriptor != null ) {
			final ManagedBeanRegistry managedBeanRegistry = getBuildingContext().getBootstrapContext()
					.getServiceRegistry()
					.getService( ManagedBeanRegistry.class );

			final JpaAttributeConverterCreationContext converterCreationContext = new JpaAttributeConverterCreationContext() {
				@Override
				public ManagedBeanRegistry getManagedBeanRegistry() {
					return managedBeanRegistry;
				}

				@Override
				public TypeConfiguration getTypeConfiguration() {
					return typeConfiguration;
				}
			};

			final NamedConverterResolution<Object> converterResolution = NamedConverterResolution.from(
					attributeConverterDescriptor,
					explicitJavaTypeAccess,
					explicitJdbcTypeAccess,
					explicitMutabilityPlanAccess,
					this,
					converterCreationContext,
					getBuildingContext()
			);

			if ( jtd instanceof BasicPluralJavaType<?>
					&& !attributeConverterDescriptor.getDomainValueResolvedType()
					.getErasedType()
					.isAssignableFrom( jtd.getJavaTypeClass() ) ) {
				// In this case, the converter applies to the element of a BasicPluralJavaType
				final BasicPluralJavaType<?> containerJtd = (BasicPluralJavaType<?>) jtd;
				final BasicType registeredElementType = converterResolution.getLegacyResolvedBasicType();
				final ColumnTypeInformation columnTypeInformation;
				if ( getColumn() instanceof ColumnTypeInformation ) {
					columnTypeInformation = (ColumnTypeInformation) getColumn();
				}
				else {
					columnTypeInformation = null;
				}
				final BasicType<?> registeredType = registeredElementType == null ? null : containerJtd.resolveType(
						typeConfiguration,
						getMetadata().getDatabase().getDialect(),
						registeredElementType,
						columnTypeInformation
				);
				if ( registeredType != null ) {
					typeConfiguration.getBasicTypeRegistry().register( registeredType );

					return new InferredBasicValueResolution(
							registeredType,
							registeredType.getJavaTypeDescriptor(),
							registeredType.getJavaTypeDescriptor(),
							registeredType.getJdbcType(),
							registeredType,
							null
					);
				}
			}

			return converterResolution;
		}

		final JdbcType jdbcType;
		if ( explicitJdbcTypeAccess != null ) {
			jdbcType = explicitJdbcTypeAccess.apply( typeConfiguration );
		}
		else {
			jdbcType = null;
		}

		if ( jtd == null ) {
			if ( jdbcType != null ) {
				jtd = jdbcType.getJdbcRecommendedJavaTypeMapping( null, null, typeConfiguration );
			}
		}

		if ( jtd == null ) {
			throw new MappingException( "Unable to determine JavaType to use : " + this );
		}

		final TypeDefinitionRegistry typeDefinitionRegistry = getBuildingContext().getTypeDefinitionRegistry();
		final TypeDefinition autoAppliedTypeDef = typeDefinitionRegistry.resolveAutoApplied( (BasicJavaType<?>) jtd );
		if ( autoAppliedTypeDef != null && ( !jtd.getJavaTypeClass().isEnum() || enumerationStyle == null ) ) {
			log.debug( "BasicValue resolution matched auto-applied type-definition" );
			return autoAppliedTypeDef.resolve(
					typeParameters,
					null,
					getBuildingContext(),
					this
			);
		}

		return InferredBasicValueResolver.from(
				explicitJtd,
				jdbcType,
				resolvedJavaType,
				this::determineReflectedJavaType,
				this,
				getTable(),
				getColumn(),
				ownerName,
				propertyName,
				getMetadata().getDatabase().getDialect(),
				typeConfiguration
		);

	}

	private JavaType<?> determineReflectedJavaType() {
		final java.lang.reflect.Type impliedJavaType;

		if ( resolvedJavaType != null ) {
			impliedJavaType = resolvedJavaType;
		}
		else if ( implicitJavaTypeAccess != null ) {
			impliedJavaType = implicitJavaTypeAccess.apply( typeConfiguration );
		}
		else if ( ownerName != null && propertyName != null ) {
			final ServiceRegistry serviceRegistry = typeConfiguration.getServiceRegistry();
			final ClassLoaderService classLoaderService = serviceRegistry.getService( ClassLoaderService.class );

			impliedJavaType = ReflectHelper.reflectedPropertyType(
					ownerName,
					propertyName,
					classLoaderService
			);
		}
		else {
			return null;
		}

		resolvedJavaType = impliedJavaType;

		if ( impliedJavaType == null ) {
			return null;
		}

		return typeConfiguration.getJavaTypeRegistry().resolveDescriptor( impliedJavaType );
	}


	private static Resolution<?> interpretExplicitlyNamedType(
			String name,
			EnumType enumerationStyle,
			Function<TypeConfiguration, java.lang.reflect.Type> implicitJavaTypeAccess,
			Function<TypeConfiguration, BasicJavaType> explicitJtdAccess,
			Function<TypeConfiguration, JdbcType> explicitStdAccess,
			Function<TypeConfiguration, MutabilityPlan> explicitMutabilityPlanAccess,
			ConverterDescriptor converterDescriptor,
			Map<Object,Object> localTypeParams,
			Consumer<Properties> combinedParameterConsumer,
			JdbcTypeIndicators stdIndicators,
			TypeConfiguration typeConfiguration,
			MetadataBuildingContext context) {

		final StandardServiceRegistry serviceRegistry = context.getBootstrapContext().getServiceRegistry();
		final ManagedBeanRegistry managedBeanRegistry = serviceRegistry.getService( ManagedBeanRegistry.class );

		final JpaAttributeConverterCreationContext converterCreationContext = new JpaAttributeConverterCreationContext() {
			@Override
			public ManagedBeanRegistry getManagedBeanRegistry() {
				return managedBeanRegistry;
			}

			@Override
			public TypeConfiguration getTypeConfiguration() {
				return typeConfiguration;
			}
		};


		// Name could refer to:
		//		1) a named converter - HBM support for JPA's AttributeConverter via its `type="..."` XML attribute
		//		2) a "named composed" mapping - like (1), this is mainly to support envers since it tells
		//			Hibernate the mappings via DOM.  See `org.hibernate.type.internal.BasicTypeImpl`
		//		3) basic type "resolution key"
		//		4) UserType or BasicType class name - directly, or through a TypeDefinition

		if ( name.startsWith( ConverterDescriptor.TYPE_NAME_PREFIX  ) ) {
			return NamedConverterResolution.from(
					name,
					explicitJtdAccess,
					explicitStdAccess,
					explicitMutabilityPlanAccess,
					stdIndicators,
					converterCreationContext,
					context
			);
		}

		if ( name.startsWith( BasicTypeImpl.EXTERNALIZED_PREFIX ) ) {
			final BasicTypeImpl<Object> basicType = context.getBootstrapContext().resolveAdHocBasicType( name );

			return new NamedBasicTypeResolution<>(
					basicType.getJavaTypeDescriptor(),
					basicType,
					null,
					explicitMutabilityPlanAccess,
					context
			);
		}

		// see if it is a named basic type
		final BasicType<?> basicTypeByName = typeConfiguration.getBasicTypeRegistry().getRegisteredType( name );
		if ( basicTypeByName != null ) {
			final BasicValueConverter<?,?> valueConverter;
			final JavaType<?> domainJtd;
			if ( converterDescriptor != null ) {
				valueConverter = converterDescriptor.createJpaAttributeConverter( converterCreationContext );
				domainJtd = valueConverter.getDomainJavaType();
			}
			else {
				valueConverter = basicTypeByName.getValueConverter();
				domainJtd = basicTypeByName.getJavaTypeDescriptor();
			}

			return new NamedBasicTypeResolution<>(
					domainJtd,
					basicTypeByName,
					valueConverter,
					explicitMutabilityPlanAccess,
					context
			);
		}

		// see if it is a named TypeDefinition
		final TypeDefinition typeDefinition = context.getTypeDefinitionRegistry().resolve( name );
		if ( typeDefinition != null ) {
			final Resolution<?> resolution = typeDefinition.resolve(
					localTypeParams,
					explicitMutabilityPlanAccess != null
							? explicitMutabilityPlanAccess.apply( typeConfiguration )
							: null,
					context,
					stdIndicators
			);
			combinedParameterConsumer.accept( resolution.getCombinedTypeParameters() );
			return resolution;
		}


		// see if the name is a UserType or BasicType implementor class name
		final ClassLoaderService cls = typeConfiguration.getServiceRegistry().getService( ClassLoaderService.class );
		try {
			final Class<?> typeNamedClass = cls.classForName( name );

			// if there are no local config params, register an implicit TypeDefinition for this custom type .
			//  later uses may find it and re-use its cacheable reference...
			if ( CollectionHelper.isEmpty( localTypeParams ) ) {
				final TypeDefinition implicitDefinition = new TypeDefinition(
						name,
						typeNamedClass,
						null,
						null
				);
				context.getTypeDefinitionRegistry().register( implicitDefinition );
				return implicitDefinition.resolve(
						localTypeParams,
						explicitMutabilityPlanAccess != null
								? explicitMutabilityPlanAccess.apply( typeConfiguration )
								: null,
						context,
						stdIndicators
				);
			}

			return TypeDefinition.createLocalResolution(
					name,
					typeNamedClass,
					explicitMutabilityPlanAccess != null
							? explicitMutabilityPlanAccess.apply( typeConfiguration )
							: null,
					localTypeParams,
					context
			);
		}
		catch (ClassLoadingException e) {
			// allow the exception below to trigger
			log.debugf( "Could not resolve type-name [%s] as Java type : %s", name, e );
		}

		throw new MappingException( "Could not resolve named type : " + name );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SqlTypeDescriptorIndicators

	@Override
	public EnumType getEnumeratedType() {
		return getEnumerationStyle();
	}


	@Override
	public int getPreferredSqlTypeCodeForBoolean() {
		return getBuildingContext().getPreferredSqlTypeCodeForBoolean();
	}

	@Override
	public int getPreferredSqlTypeCodeForDuration() {
		return getBuildingContext().getPreferredSqlTypeCodeForDuration();
	}

	@Override
	public int getPreferredSqlTypeCodeForUuid() {
		return getBuildingContext().getPreferredSqlTypeCodeForUuid();
	}

	@Override
	public int getPreferredSqlTypeCodeForInstant() {
		return getBuildingContext().getPreferredSqlTypeCodeForInstant();
	}

	@Override
	public int getPreferredSqlTypeCodeForArray() {
		return getBuildingContext().getPreferredSqlTypeCodeForArray();
	}

	@Override
	public TimeZoneStorageStrategy getDefaultTimeZoneStorageStrategy() {
		if ( timeZoneStorageType != null ) {
			switch ( timeZoneStorageType ) {
				case COLUMN:
					return TimeZoneStorageStrategy.COLUMN;
				case NATIVE:
					return TimeZoneStorageStrategy.NATIVE;
				case NORMALIZE:
					return TimeZoneStorageStrategy.NORMALIZE;
				case NORMALIZE_UTC:
					return TimeZoneStorageStrategy.NORMALIZE_UTC;
			}
		}
		return getBuildingContext().getBuildingOptions().getDefaultTimeZoneStorage();
	}

	@Override
	public TypeConfiguration getTypeConfiguration() {
		return typeConfiguration;
	}

	public void setExplicitTypeParams(Map<String,String> explicitLocalTypeParams) {
		this.explicitLocalTypeParams = explicitLocalTypeParams;
	}

	public void setExplicitTypeName(String typeName) {
		this.explicitTypeName = typeName;
	}

	public void setTypeName(String typeName) {
		if ( StringHelper.isNotEmpty( typeName ) ) {
			if ( typeName.startsWith( ConverterDescriptor.TYPE_NAME_PREFIX ) ) {
				final String converterClassName = typeName.substring( ConverterDescriptor.TYPE_NAME_PREFIX.length() );
				final ClassLoaderService cls = getBuildingContext()
						.getMetadataCollector()
						.getMetadataBuildingOptions()
						.getServiceRegistry()
						.getService( ClassLoaderService.class );
				try {
					final Class<AttributeConverter<?,?>> converterClass = cls.classForName( converterClassName );
					setAttributeConverterDescriptor( new ClassBasedConverterDescriptor(
							converterClass,
							false,
							getBuildingContext().getBootstrapContext().getClassmateContext()
					) );
					return;
				}
				catch (Exception e) {
					log.logBadHbmAttributeConverterType( typeName, e.getMessage() );
				}
			}
			else {
				setExplicitTypeName( typeName );
			}
		}

		super.setTypeName( typeName );
	}

	private static int COUNTER;

	public <T extends UserType<?>> void setExplicitCustomType(Class<T> explicitCustomType) {
		if ( explicitCustomType != null ) {
			if ( resolution != null ) {
				throw new UnsupportedOperationException( "Unsupported attempt to set an explicit-custom-type when value is already resolved" );
			}

			final BootstrapContext bootstrapContext = getBuildingContext().getBootstrapContext();
			final BeanInstanceProducer instanceProducer = bootstrapContext.getBeanInstanceProducer();

			final Properties properties = new Properties();
			if ( CollectionHelper.isNotEmpty( getTypeParameters() ) ) {
				properties.putAll( getTypeParameters() );
			}
			if ( CollectionHelper.isNotEmpty( explicitLocalTypeParams ) ) {
				properties.putAll( explicitLocalTypeParams );
			}

			final ManagedBean<T> typeBean;
			if ( properties.isEmpty() ) {
				typeBean = bootstrapContext
						.getServiceRegistry()
						.getService( ManagedBeanRegistry.class )
						.getBean( explicitCustomType, instanceProducer );
			}
			else {
				final String name = explicitCustomType.getName() + COUNTER++;
				typeBean = bootstrapContext
						.getServiceRegistry()
						.getService( ManagedBeanRegistry.class )
						.getBean( name, explicitCustomType, instanceProducer );
			}

			final T typeInstance = typeBean.getBeanInstance();

			if ( typeInstance instanceof TypeConfigurationAware ) {
				( (TypeConfigurationAware) typeInstance ).setTypeConfiguration( typeConfiguration );
			}

			if ( typeInstance instanceof DynamicParameterizedType ) {
				if ( Boolean.parseBoolean( properties.getProperty( DynamicParameterizedType.IS_DYNAMIC ) ) ) {
					if ( properties.get( DynamicParameterizedType.PARAMETER_TYPE ) == null ) {
						final DynamicParameterizedType.ParameterType parameterType = makeParameterImpl();
						properties.put( DynamicParameterizedType.PARAMETER_TYPE, parameterType );
					}
				}
			}

			injectParameters( typeInstance, properties );
			// envers - grr
			setTypeParameters( properties );

			this.resolution = new UserTypeResolution(
					new CustomType<>( (UserType<Object>) typeInstance, typeConfiguration ),
					null,
					properties
			);
		}
	}

	public void setTemporalPrecision(TemporalType temporalPrecision) {
		this.temporalPrecision = temporalPrecision;
	}

	@Override
	public TemporalType getTemporalPrecision() {
		return temporalPrecision;
	}

	@Override
	public Object accept(ValueVisitor visitor) {
		return visitor.accept(this);
	}

	/**
	 * Resolved form of {@link BasicValue} as part of interpreting the
	 * boot-time model into the run-time model
	 */
	public interface Resolution<J> {
		/**
		 * The BasicType resolved using the pre-6.0 rules.  This is temporarily
		 * needed because of the split in extracting / binding
		 */
		BasicType<J> getLegacyResolvedBasicType();

		/**
		 * Get the collection of type-parameters collected both locally as well
		 * as from the applied type-def, if one
		 */
		default Properties getCombinedTypeParameters() {
			return null;
		}

		JdbcMapping getJdbcMapping();

		/**
		 * The JavaType for the value as part of the domain model
		 */
		JavaType<J> getDomainJavaType();

		/**
		 * The JavaType for the relational value as part of
		 * the relational model (its JDBC representation)
		 */
		JavaType<?> getRelationalJavaType();

		/**
		 * The JavaType for the relational value as part of
		 * the relational model (its JDBC representation)
		 */
		JdbcType getJdbcType();

		/**
		 * Converter, if any, to convert values between the
		 * domain and relational JavaType representations
		 */
		BasicValueConverter<J,?> getValueConverter();

		/**
		 * The resolved MutabilityPlan
		 */
		MutabilityPlan<J> getMutabilityPlan();
	}
}
