/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.hibernate.AnnotationException;
import org.hibernate.AssertionFailure;
import org.hibernate.FetchMode;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.TimeZoneStorageStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.CollectionTypeRegistration;
import org.hibernate.annotations.CollectionTypeRegistrations;
import org.hibernate.annotations.Columns;
import org.hibernate.annotations.CompositeType;
import org.hibernate.annotations.CompositeTypeRegistration;
import org.hibernate.annotations.CompositeTypeRegistrations;
import org.hibernate.annotations.ConverterRegistration;
import org.hibernate.annotations.ConverterRegistrations;
import org.hibernate.annotations.EmbeddableInstantiatorRegistration;
import org.hibernate.annotations.EmbeddableInstantiatorRegistrations;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchProfile;
import org.hibernate.annotations.FetchProfiles;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.FilterDefs;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.GenericGenerators;
import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.annotations.Index;
import org.hibernate.annotations.JavaTypeRegistration;
import org.hibernate.annotations.JavaTypeRegistrations;
import org.hibernate.annotations.JdbcTypeRegistration;
import org.hibernate.annotations.JdbcTypeRegistrations;
import org.hibernate.annotations.LazyGroup;
import org.hibernate.annotations.LazyToOne;
import org.hibernate.annotations.LazyToOneOption;
import org.hibernate.annotations.ManyToAny;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Parent;
import org.hibernate.annotations.Source;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.common.reflection.ReflectionManager;
import org.hibernate.annotations.common.reflection.XAnnotatedElement;
import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XMethod;
import org.hibernate.annotations.common.reflection.XPackage;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.boot.model.IdGeneratorStrategyInterpreter;
import org.hibernate.boot.model.IdentifierGeneratorDefinition;
import org.hibernate.boot.model.convert.spi.ConverterDescriptor;
import org.hibernate.boot.model.convert.spi.RegisteredConversion;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.annotations.CollectionBinder;
import org.hibernate.cfg.annotations.EntityBinder;
import org.hibernate.cfg.annotations.HCANNHelper;
import org.hibernate.cfg.annotations.Nullability;
import org.hibernate.cfg.annotations.PropertyBinder;
import org.hibernate.cfg.annotations.QueryBinder;
import org.hibernate.engine.OptimisticLockStyle;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.GenericsHelper;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.mapping.Any;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.Constraint;
import org.hibernate.mapping.IdentifierGeneratorCreator;
import org.hibernate.mapping.Join;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.ToOne;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.model.convert.internal.JpaAttributeConverterImpl;
import org.hibernate.metamodel.spi.EmbeddableInstantiator;
import org.hibernate.property.access.internal.PropertyAccessStrategyCompositeUserTypeImpl;
import org.hibernate.property.access.internal.PropertyAccessStrategyMixedImpl;
import org.hibernate.property.access.spi.PropertyAccessStrategy;
import org.hibernate.resource.beans.spi.ManagedBean;
import org.hibernate.resource.beans.spi.ManagedBeanRegistry;
import org.hibernate.type.CustomType;
import org.hibernate.type.descriptor.java.BasicJavaType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.JavaTypeRegistry;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.hibernate.type.internal.ConvertedBasicTypeImpl;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.CompositeUserType;
import org.hibernate.usertype.UserType;
import org.hibernate.usertype.internal.OffsetDateTimeCompositeUserType;
import org.hibernate.usertype.internal.ZonedDateTimeCompositeUserType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.MapsId;
import jakarta.persistence.NamedNativeQueries;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.NamedStoredProcedureQueries;
import jakarta.persistence.NamedStoredProcedureQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.PrimaryKeyJoinColumns;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.SequenceGenerators;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.persistence.SqlResultSetMappings;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.TableGenerators;
import jakarta.persistence.Version;

import static org.hibernate.cfg.BinderHelper.getMappedSuperclassOrNull;
import static org.hibernate.cfg.BinderHelper.getOverridableAnnotation;
import static org.hibernate.cfg.BinderHelper.getPath;
import static org.hibernate.cfg.BinderHelper.getPropertyOverriddenByMapperOrMapsId;
import static org.hibernate.cfg.BinderHelper.hasToOneAnnotation;
import static org.hibernate.cfg.BinderHelper.isEmptyAnnotationValue;
import static org.hibernate.cfg.BinderHelper.makeIdGenerator;
import static org.hibernate.cfg.InheritanceState.getInheritanceStateOfSuperEntity;
import static org.hibernate.cfg.InheritanceState.getSuperclassInheritanceState;
import static org.hibernate.cfg.PropertyHolderBuilder.buildPropertyHolder;
import static org.hibernate.internal.CoreLogging.messageLogger;
import static org.hibernate.internal.util.StringHelper.nullIfEmpty;
import static org.hibernate.mapping.SimpleValue.DEFAULT_ID_GEN_STRATEGY;

/**
 * JSR 175 annotation binder which reads the annotations from classes, applies the
 * principles of the EJB3 spec and produces the Hibernate configuration-time metamodel
 * (the classes in the {@link org.hibernate.mapping} package)
 * <p/>
 * Some design description
 * I tried to remove any link to annotation except from the 2 first level of
 * method call.
 * It'll enable to:
 *   - facilitate annotation overriding
 *   - mutualize one day xml and annotation binder (probably a dream though)
 *   - split this huge class in smaller mapping oriented classes
 *
 * bindSomething usually create the mapping container and is accessed by one of the 2 first level method
 * makeSomething usually create the mapping container and is accessed by bindSomething[else]
 * fillSomething take the container into parameter and fill it.
 *
 * @author Emmanuel Bernard
 * @author Hardy Ferentschik
 */
@SuppressWarnings("deprecation")
public final class AnnotationBinder {
	private static final CoreMessageLogger LOG = messageLogger( AnnotationBinder.class );

	private AnnotationBinder() {
	}

	@SuppressWarnings("unchecked")
	public static void bindDefaults(MetadataBuildingContext context) {
		Map<?,?> defaults = context.getBootstrapContext().getReflectionManager().getDefaults();

		// id generators ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

		{
			List<SequenceGenerator> anns = ( List<SequenceGenerator> ) defaults.get( SequenceGenerator.class );
			if ( anns != null ) {
				for ( SequenceGenerator ann : anns ) {
					IdentifierGeneratorDefinition idGen = buildIdGenerator( ann, context );
					if ( idGen != null ) {
						context.getMetadataCollector().addDefaultIdentifierGenerator( idGen );
					}
				}
			}
		}
		{
			List<TableGenerator> anns = ( List<TableGenerator> ) defaults.get( TableGenerator.class );
			if ( anns != null ) {
				for ( TableGenerator ann : anns ) {
					IdentifierGeneratorDefinition idGen = buildIdGenerator( ann, context );
					if ( idGen != null ) {
						context.getMetadataCollector().addDefaultIdentifierGenerator( idGen );
					}
				}
			}
		}

		{
			List<TableGenerators> anns = (List<TableGenerators>) defaults.get( TableGenerators.class );
			if ( anns != null ) {
				anns.forEach( tableGenerators -> {
					for ( TableGenerator tableGenerator : tableGenerators.value() ) {
						IdentifierGeneratorDefinition idGen = buildIdGenerator( tableGenerator, context );
						if ( idGen != null ) {
							context.getMetadataCollector().addDefaultIdentifierGenerator( idGen );
						}
					}
				} );
			}
		}

		{
			List<SequenceGenerators> anns = (List<SequenceGenerators>) defaults.get( SequenceGenerators.class );
			if ( anns != null ) {
				anns.forEach( sequenceGenerators -> {
					for ( SequenceGenerator ann : sequenceGenerators.value() ) {
						IdentifierGeneratorDefinition idGen = buildIdGenerator( ann, context );
						if ( idGen != null ) {
							context.getMetadataCollector().addDefaultIdentifierGenerator( idGen );
						}
					}
				} );
			}
		}

		// queries ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

		{
			List<NamedQuery> anns = ( List<NamedQuery> ) defaults.get( NamedQuery.class );
			if ( anns != null ) {
				for ( NamedQuery ann : anns ) {
					QueryBinder.bindQuery( ann, context, true );
				}
			}
		}
		{
			List<NamedNativeQuery> anns = ( List<NamedNativeQuery> ) defaults.get( NamedNativeQuery.class );
			if ( anns != null ) {
				for ( NamedNativeQuery ann : anns ) {
					QueryBinder.bindNativeQuery( ann, context, true );
				}
			}
		}

		// result-set-mappings ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

		{
			List<SqlResultSetMapping> anns = ( List<SqlResultSetMapping> ) defaults.get( SqlResultSetMapping.class );
			if ( anns != null ) {
				for ( SqlResultSetMapping ann : anns ) {
					QueryBinder.bindSqlResultSetMapping( ann, context, true );
				}
			}
		}

		// stored procs ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

		{
			final List<NamedStoredProcedureQuery> annotations =
					(List<NamedStoredProcedureQuery>) defaults.get( NamedStoredProcedureQuery.class );
			if ( annotations != null ) {
				for ( NamedStoredProcedureQuery annotation : annotations ) {
					bindNamedStoredProcedureQuery( annotation, context, true );
				}
			}
		}
		{
			final List<NamedStoredProcedureQueries> annotations =
					(List<NamedStoredProcedureQueries>) defaults.get( NamedStoredProcedureQueries.class );
			if ( annotations != null ) {
				for ( NamedStoredProcedureQueries annotation : annotations ) {
					bindNamedStoredProcedureQueries( annotation, context, true );
				}
			}
		}
	}

	public static void bindPackage(ClassLoaderService cls, String packageName, MetadataBuildingContext context) {
		final Package packaze = cls.packageForNameOrNull( packageName );
		if ( packaze == null ) {
			return;
		}
		final XPackage pckg = context.getBootstrapContext().getReflectionManager().toXPackage( packaze );

		if ( pckg.isAnnotationPresent( SequenceGenerator.class ) ) {
			SequenceGenerator ann = pckg.getAnnotation( SequenceGenerator.class );
			IdentifierGeneratorDefinition idGen = buildIdGenerator( ann, context );
			context.getMetadataCollector().addIdentifierGenerator( idGen );
			if ( LOG.isTraceEnabled() ) {
				LOG.tracev( "Add sequence generator with name: {0}", idGen.getName() );
			}
		}
		if ( pckg.isAnnotationPresent( SequenceGenerators.class ) ) {
			SequenceGenerators ann = pckg.getAnnotation( SequenceGenerators.class );
			for ( SequenceGenerator tableGenerator : ann.value() ) {
				context.getMetadataCollector().addIdentifierGenerator( buildIdGenerator( tableGenerator, context ) );
			}
		}

		if ( pckg.isAnnotationPresent( TableGenerator.class ) ) {
			TableGenerator ann = pckg.getAnnotation( TableGenerator.class );
			IdentifierGeneratorDefinition idGen = buildIdGenerator( ann, context );
			context.getMetadataCollector().addIdentifierGenerator( idGen );
		}
		if ( pckg.isAnnotationPresent( TableGenerators.class ) ) {
			TableGenerators ann = pckg.getAnnotation( TableGenerators.class );
			for ( TableGenerator tableGenerator : ann.value() ) {
				context.getMetadataCollector().addIdentifierGenerator( buildIdGenerator( tableGenerator, context ) );
			}
		}

		handleTypeDescriptorRegistrations( pckg, context );
		bindEmbeddableInstantiatorRegistrations( pckg, context );
		bindCompositeUserTypeRegistrations( pckg, context );
		handleConverterRegistrations( pckg, context );

		bindGenericGenerators( pckg, context );
		bindQueries( pckg, context );
		bindFilterDefs( pckg, context );
	}

	private static void bindGenericGenerators(XAnnotatedElement annotatedElement, MetadataBuildingContext context) {
		GenericGenerator defAnn = annotatedElement.getAnnotation( GenericGenerator.class );
		GenericGenerators defsAnn = annotatedElement.getAnnotation( GenericGenerators.class );
		if ( defAnn != null ) {
			bindGenericGenerator( defAnn, context );
		}
		if ( defsAnn != null ) {
			for ( GenericGenerator def : defsAnn.value() ) {
				bindGenericGenerator( def, context );
			}
		}
	}

	private static void bindGenericGenerator(GenericGenerator def, MetadataBuildingContext context) {
		context.getMetadataCollector().addIdentifierGenerator( buildIdGenerator( def, context ) );
	}

	private static void bindNamedJpaQueries(XAnnotatedElement annotatedElement, MetadataBuildingContext context) {
		QueryBinder.bindSqlResultSetMapping(
				annotatedElement.getAnnotation( SqlResultSetMapping.class ),
				context,
				false
		);

		final SqlResultSetMappings ann = annotatedElement.getAnnotation( SqlResultSetMappings.class );
		if ( ann != null ) {
			for ( SqlResultSetMapping current : ann.value() ) {
				QueryBinder.bindSqlResultSetMapping( current, context, false );
			}
		}

		QueryBinder.bindQuery(
				annotatedElement.getAnnotation( NamedQuery.class ),
				context,
				false
		);

		QueryBinder.bindQueries(
				annotatedElement.getAnnotation( NamedQueries.class ),
				context,
				false
		);

		QueryBinder.bindNativeQuery(
				annotatedElement.getAnnotation( NamedNativeQuery.class ),
				context,
				false
		);

		QueryBinder.bindNativeQueries(
				annotatedElement.getAnnotation( NamedNativeQueries.class ),
				context,
				false
		);
	}

	public static void bindQueries(XAnnotatedElement annotatedElement, MetadataBuildingContext context) {
		bindNamedJpaQueries( annotatedElement, context );

		QueryBinder.bindQuery(
				annotatedElement.getAnnotation( org.hibernate.annotations.NamedQuery.class ),
				context
		);

		QueryBinder.bindQueries(
				annotatedElement.getAnnotation( org.hibernate.annotations.NamedQueries.class ),
				context
		);

		QueryBinder.bindNativeQuery(
				annotatedElement.getAnnotation( org.hibernate.annotations.NamedNativeQuery.class ),
				context
		);

		QueryBinder.bindNativeQueries(
				annotatedElement.getAnnotation( org.hibernate.annotations.NamedNativeQueries.class ),
				context
		);

		// NamedStoredProcedureQuery handling ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		bindNamedStoredProcedureQuery(
				annotatedElement.getAnnotation( NamedStoredProcedureQuery.class ),
				context,
				false
		);

		// NamedStoredProcedureQueries handling ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		bindNamedStoredProcedureQueries(
				annotatedElement.getAnnotation( NamedStoredProcedureQueries.class ),
				context,
				false
		);
	}

	private static void bindNamedStoredProcedureQueries(
			NamedStoredProcedureQueries annotation,
			MetadataBuildingContext context,
			boolean isDefault) {
		if ( annotation != null ) {
			for ( NamedStoredProcedureQuery queryAnnotation : annotation.value() ) {
				bindNamedStoredProcedureQuery( queryAnnotation, context, isDefault );
			}
		}
	}

	private static void bindNamedStoredProcedureQuery(
			NamedStoredProcedureQuery annotation,
			MetadataBuildingContext context,
			boolean isDefault) {
		if ( annotation != null ) {
			QueryBinder.bindNamedStoredProcedureQuery( annotation, context, isDefault );
		}
	}

	private static IdentifierGeneratorDefinition buildIdGenerator(
			java.lang.annotation.Annotation generatorAnn,
			MetadataBuildingContext context) {
		if ( generatorAnn == null ) {
			return null;
		}

		IdentifierGeneratorDefinition.Builder definitionBuilder = new IdentifierGeneratorDefinition.Builder();

		if ( generatorAnn instanceof TableGenerator ) {
			context.getBuildingOptions().getIdGenerationTypeInterpreter().interpretTableGenerator(
					(TableGenerator) generatorAnn,
					definitionBuilder
			);
			if ( LOG.isTraceEnabled() ) {
				LOG.tracev( "Add table generator with name: {0}", definitionBuilder.getName() );
			}
		}
		else if ( generatorAnn instanceof SequenceGenerator ) {
			context.getBuildingOptions().getIdGenerationTypeInterpreter().interpretSequenceGenerator(
					(SequenceGenerator) generatorAnn,
					definitionBuilder
			);
			if ( LOG.isTraceEnabled() ) {
				LOG.tracev( "Add sequence generator with name: {0}", definitionBuilder.getName() );
			}
		}
		else if ( generatorAnn instanceof GenericGenerator ) {
			GenericGenerator genGen = ( GenericGenerator ) generatorAnn;
			definitionBuilder.setName( genGen.name() );
			definitionBuilder.setStrategy( genGen.strategy() );
			Parameter[] params = genGen.parameters();
			for ( Parameter parameter : params ) {
				definitionBuilder.addParam( parameter.name(), parameter.value() );
			}
			if ( LOG.isTraceEnabled() ) {
				LOG.tracev( "Add generic generator with name: {0}", definitionBuilder.getName() );
			}
		}
		else {
			throw new AssertionFailure( "Unknown Generator annotation: " + generatorAnn );
		}

		return definitionBuilder.build();
	}

	/**
	 * Bind a class having JSR175 annotations. Subclasses <b>have to</b> be bound after its parent class.
	 *
	 * @param clazzToProcess entity to bind as {@code XClass} instance
	 * @param inheritanceStatePerClass Metadata about the inheritance relationships for all mapped classes
	 *
	 * @throws MappingException in case there is a configuration error
	 */
	public static void bindClass(
			XClass clazzToProcess,
			Map<XClass, InheritanceState> inheritanceStatePerClass,
			MetadataBuildingContext context) throws MappingException {

		detectMappedSuperclassProblems( clazzToProcess );

		switch ( context.getMetadataCollector().getClassType( clazzToProcess ) ) {
			case MAPPED_SUPERCLASS:
				// Allow queries and filters to be declared by a @MappedSuperclass
				bindQueries( clazzToProcess, context );
				bindFilterDefs( clazzToProcess, context );
				//fall through:
			case EMBEDDABLE:
			case NONE:
				return;
		}

		// try to find class level generators
		HashMap<String, IdentifierGeneratorDefinition> classGenerators = AnnotationBinder.buildGenerators( clazzToProcess, context );
		handleTypeDescriptorRegistrations( clazzToProcess, context );
		bindEmbeddableInstantiatorRegistrations( clazzToProcess, context );
		bindCompositeUserTypeRegistrations( clazzToProcess, context );
		handleConverterRegistrations( clazzToProcess, context );

		bindQueries( clazzToProcess, context );
		bindFilterDefs( clazzToProcess, context );

		EntityBinder.bindEntityClass( clazzToProcess, inheritanceStatePerClass, classGenerators, context );
	}

	private static void detectMappedSuperclassProblems(XClass clazzToProcess) {
		//@Entity and @MappedSuperclass on the same class leads to a NPE down the road
		if ( clazzToProcess.isAnnotationPresent( Entity.class )
				&&  clazzToProcess.isAnnotationPresent( MappedSuperclass.class ) ) {
			throw new AnnotationException( "An entity cannot be annotated with both @Entity and @MappedSuperclass: "
					+ clazzToProcess.getName() );
		}

		if ( clazzToProcess.isAnnotationPresent( Inheritance.class )
				&&  clazzToProcess.isAnnotationPresent( MappedSuperclass.class ) ) {
			LOG.unsupportedMappedSuperclassWithEntityInheritance( clazzToProcess.getName() );
		}
	}

	private static void handleTypeDescriptorRegistrations(XAnnotatedElement annotatedElement, MetadataBuildingContext context) {
		final ManagedBeanRegistry managedBeanRegistry = context.getBootstrapContext()
				.getServiceRegistry()
				.getService( ManagedBeanRegistry.class );

		final JavaTypeRegistration javaTypeAnn = annotatedElement.getAnnotation( JavaTypeRegistration.class );
		if ( javaTypeAnn != null ) {
			handleJavaTypeRegistration( context, managedBeanRegistry, javaTypeAnn );
		}
		else {
			final JavaTypeRegistrations annotation = annotatedElement.getAnnotation( JavaTypeRegistrations.class );
			if ( annotation != null ) {
				final JavaTypeRegistration[] registrations = annotation.value();
				for (JavaTypeRegistration registration : registrations) {
					handleJavaTypeRegistration( context, managedBeanRegistry, registration );
				}
			}
		}

		final JdbcTypeRegistration jdbcTypeAnn = annotatedElement.getAnnotation( JdbcTypeRegistration.class );
		if ( jdbcTypeAnn != null ) {
			handleJdbcTypeRegistration( context, managedBeanRegistry, jdbcTypeAnn );
		}
		else {
			final JdbcTypeRegistrations jdbcTypesAnn = annotatedElement.getAnnotation( JdbcTypeRegistrations.class );
			if ( jdbcTypesAnn != null ) {
				final JdbcTypeRegistration[] registrations = jdbcTypesAnn.value();
				for ( JdbcTypeRegistration registration : registrations ) {
					handleJdbcTypeRegistration(context, managedBeanRegistry, registration);
				}
			}
		}

		final CollectionTypeRegistration singleRegistration = annotatedElement.getAnnotation( CollectionTypeRegistration.class );
		if ( singleRegistration != null ) {
			context.getMetadataCollector().addCollectionTypeRegistration( singleRegistration );
		}

		final CollectionTypeRegistrations multiRegistration = annotatedElement.getAnnotation( CollectionTypeRegistrations.class );
		if ( multiRegistration != null ) {
			for ( CollectionTypeRegistration registration : multiRegistration.value() ) {
				context.getMetadataCollector().addCollectionTypeRegistration( registration );
			}
		}
	}

	private static void handleJdbcTypeRegistration(
			MetadataBuildingContext context,
			ManagedBeanRegistry managedBeanRegistry,
			JdbcTypeRegistration annotation) {
		final Class<? extends JdbcType> jdbcTypeClass = annotation.value();
		final JdbcType jdbcType = managedBeanRegistry.getBean( jdbcTypeClass ).getBeanInstance();

		final int typeCode = annotation.registrationCode() == Integer.MIN_VALUE
				? jdbcType.getJdbcTypeCode()
				: annotation.registrationCode();
		context.getMetadataCollector().addJdbcTypeRegistration( typeCode, jdbcType );
	}

	private static void handleJavaTypeRegistration(
			MetadataBuildingContext context,
			ManagedBeanRegistry managedBeanRegistry,
			JavaTypeRegistration annotation) {
		final Class<? extends BasicJavaType<?>> jtdClass = annotation.descriptorClass();
		final BasicJavaType<?> jtd = managedBeanRegistry.getBean( jtdClass ).getBeanInstance();
		context.getMetadataCollector().addJavaTypeRegistration( annotation.javaType(), jtd );
	}

	private static void bindEmbeddableInstantiatorRegistrations(XAnnotatedElement annotatedElement, MetadataBuildingContext context) {
		final EmbeddableInstantiatorRegistration instantiatorReg =
				annotatedElement.getAnnotation( EmbeddableInstantiatorRegistration.class );
		if ( instantiatorReg != null ) {
			handleEmbeddableInstantiatorRegistration( context, instantiatorReg );
		}
		else {
			final EmbeddableInstantiatorRegistrations annotation = annotatedElement.getAnnotation( EmbeddableInstantiatorRegistrations.class );
			if ( annotation != null ) {
				final EmbeddableInstantiatorRegistration[] registrations = annotation.value();
				for ( EmbeddableInstantiatorRegistration registration : registrations ) {
					handleEmbeddableInstantiatorRegistration(context, registration);
				}
			}
		}
	}

	private static void handleEmbeddableInstantiatorRegistration(
			MetadataBuildingContext context,
			EmbeddableInstantiatorRegistration annotation) {
		context.getMetadataCollector().registerEmbeddableInstantiator(
				annotation.embeddableClass(),
				annotation.instantiator()
		);
	}

	private static void bindCompositeUserTypeRegistrations(XAnnotatedElement annotatedElement, MetadataBuildingContext context) {
		final CompositeTypeRegistration singleRegistration =
				annotatedElement.getAnnotation( CompositeTypeRegistration.class );
		if ( singleRegistration != null ) {
			handleCompositeUserTypeRegistration( context, singleRegistration );
		}
		else {
			final CompositeTypeRegistrations annotation = annotatedElement.getAnnotation( CompositeTypeRegistrations.class );
			if ( annotation != null ) {
				final CompositeTypeRegistration[] registrations = annotation.value();
				for ( CompositeTypeRegistration registration : registrations ) {
					handleCompositeUserTypeRegistration(context, registration);
				}
			}
		}
	}

	private static void handleCompositeUserTypeRegistration(
			MetadataBuildingContext context,
			CompositeTypeRegistration annotation) {
		context.getMetadataCollector().registerCompositeUserType(
				annotation.embeddableClass(),
				annotation.userType()
		);
	}

	private static void handleConverterRegistrations(XAnnotatedElement container, MetadataBuildingContext context) {
		final ConverterRegistration singular = container.getAnnotation( ConverterRegistration.class );
		if ( singular != null ) {
			handleConverterRegistration( singular, context );
			return;
		}

		final ConverterRegistrations plural = container.getAnnotation( ConverterRegistrations.class );
		if ( plural != null ) {
			final ConverterRegistration[] registrations = plural.value();
			for (ConverterRegistration registration : registrations) {
				handleConverterRegistration(registration, context);
			}
		}
	}

	private static void handleConverterRegistration(ConverterRegistration registration, MetadataBuildingContext context) {
		final InFlightMetadataCollector metadataCollector = context.getMetadataCollector();
		metadataCollector.addRegisteredConversion(
				new RegisteredConversion(
						registration.domainType(),
						registration.converter(),
						registration.autoApply(),
						context
				)
		);
	}

	public static void bindFilterDefs(XAnnotatedElement annotatedElement, MetadataBuildingContext context) {
		FilterDef defAnn = annotatedElement.getAnnotation( FilterDef.class );
		FilterDefs defsAnn = getOverridableAnnotation( annotatedElement, FilterDefs.class, context );
		if ( defAnn != null ) {
			bindFilterDef( defAnn, context );
		}
		if ( defsAnn != null ) {
			for ( FilterDef def : defsAnn.value() ) {
				bindFilterDef( def, context );
			}
		}
	}

	private static void bindFilterDef(FilterDef defAnn, MetadataBuildingContext context) {
		final Map<String, JdbcMapping> explicitParamJaMappings;
		if ( defAnn.parameters().length == 0 ) {
			explicitParamJaMappings = null;
		}
		else {
			explicitParamJaMappings = new HashMap<>();
		}

		for ( ParamDef param : defAnn.parameters() ) {
			final JdbcMapping jdbcMapping = resolveFilterParamType( param.type(), context );

			if ( jdbcMapping == null ) {
				throw new MappingException(
						String.format(
								Locale.ROOT,
								"Unable to resolve type specified for parameter (%s) defined for @FilterDef (%s)",
								param.name(),
								defAnn.name()
						)
				);
			}

			explicitParamJaMappings.put( param.name(), jdbcMapping );
		}

		final FilterDefinition def = new FilterDefinition( defAnn.name(), defAnn.defaultCondition(), explicitParamJaMappings );
		LOG.debugf( "Binding filter definition: %s", def.getFilterName() );
		context.getMetadataCollector().addFilterDefinition( def );
	}

	private static JdbcMapping resolveFilterParamType(Class<?> type, MetadataBuildingContext context) {
		if ( UserType.class.isAssignableFrom( type ) ) {
			//noinspection unchecked
			return resolveUserType( (Class<UserType<?>>) type, context );
		}

		if ( AttributeConverter.class.isAssignableFrom( type ) ) {
			//noinspection unchecked
			return resolveAttributeConverter( (Class<AttributeConverter<?,?>>) type, context );
		}

		if ( JavaType.class.isAssignableFrom( type ) ) {
			//noinspection unchecked
			return resolveJavaType( (Class<JavaType<?>>) type, context );
		}

		final TypeConfiguration typeConfiguration = context.getBootstrapContext().getTypeConfiguration();

		final JavaType<Object> jtd = typeConfiguration.getJavaTypeRegistry().findDescriptor( type );
		if ( jtd != null ) {
			final JdbcType jdbcType = jtd.getRecommendedJdbcType(
					new JdbcTypeIndicators() {
						@Override
						public TypeConfiguration getTypeConfiguration() {
							return typeConfiguration;
						}

						@Override
						public int getPreferredSqlTypeCodeForBoolean() {
							return context.getPreferredSqlTypeCodeForBoolean();
						}

						@Override
						public int getPreferredSqlTypeCodeForDuration() {
							return context.getPreferredSqlTypeCodeForDuration();
						}

						@Override
						public int getPreferredSqlTypeCodeForUuid() {
							return context.getPreferredSqlTypeCodeForUuid();
						}

						@Override
						public int getPreferredSqlTypeCodeForInstant() {
							return context.getPreferredSqlTypeCodeForInstant();
						}

						@Override
						public int getPreferredSqlTypeCodeForArray() {
							return context.getPreferredSqlTypeCodeForArray();
						}
					}
			);
			return typeConfiguration.getBasicTypeRegistry().resolve( jtd, jdbcType );
		}

		return null;
	}

	private static JdbcMapping resolveUserType(Class<UserType<?>> type, MetadataBuildingContext context) {
		final StandardServiceRegistry serviceRegistry = context.getBootstrapContext().getServiceRegistry();
		final ManagedBeanRegistry beanRegistry = serviceRegistry.getService( ManagedBeanRegistry.class );
		final ManagedBean<UserType<?>> bean = beanRegistry.getBean( type );
		final UserType<?> userType = bean.getBeanInstance();

		return new CustomType<>( userType, context.getBootstrapContext().getTypeConfiguration() );
	}

	private static JdbcMapping resolveAttributeConverter(Class<AttributeConverter<?, ?>> type, MetadataBuildingContext context) {
		final StandardServiceRegistry serviceRegistry = context.getBootstrapContext().getServiceRegistry();
		final ManagedBeanRegistry beanRegistry = serviceRegistry.getService( ManagedBeanRegistry.class );
		final ManagedBean<AttributeConverter<?, ?>> bean = beanRegistry.getBean( type );

		final TypeConfiguration typeConfiguration = context.getBootstrapContext().getTypeConfiguration();
		final JavaTypeRegistry jtdRegistry = typeConfiguration.getJavaTypeRegistry();
		final JavaType<? extends AttributeConverter<?,?>> converterJtd = jtdRegistry.resolveDescriptor( bean.getBeanClass() );

		final ParameterizedType converterParameterizedType = GenericsHelper.extractParameterizedType( bean.getBeanClass() );
		final Class<?> domainJavaClass = GenericsHelper.extractClass( converterParameterizedType.getActualTypeArguments()[0] );
		final Class<?> relationalJavaClass = GenericsHelper.extractClass( converterParameterizedType.getActualTypeArguments()[1] );

		final JavaType<?> domainJtd = jtdRegistry.resolveDescriptor( domainJavaClass );
		final JavaType<?> relationalJtd = jtdRegistry.resolveDescriptor( relationalJavaClass );

		@SuppressWarnings({"rawtypes", "unchecked"})
		final JpaAttributeConverterImpl<?,?> valueConverter = new JpaAttributeConverterImpl(
				bean,
				converterJtd,
				domainJtd,
				relationalJtd
		);
		return new ConvertedBasicTypeImpl<>(
				ConverterDescriptor.TYPE_NAME_PREFIX
						+ valueConverter.getConverterJavaType().getJavaType().getTypeName(),
				String.format(
						"BasicType adapter for AttributeConverter<%s,%s>",
						domainJtd.getJavaType().getTypeName(),
						relationalJtd.getJavaType().getTypeName()
				),
				relationalJtd.getRecommendedJdbcType( typeConfiguration.getCurrentBaseSqlTypeIndicators() ),
				valueConverter
		);
	}

	private static JdbcMapping resolveJavaType(Class<JavaType<?>> type, MetadataBuildingContext context) {
		final TypeConfiguration typeConfiguration = context.getBootstrapContext().getTypeConfiguration();
		final JavaTypeRegistry javaTypeRegistry = typeConfiguration.getJavaTypeRegistry();
		final JavaType<?> registeredJtd = javaTypeRegistry.findDescriptor( type );

		final JavaType<?> jtd;
		if ( registeredJtd != null ) {
			jtd = registeredJtd;
		}
		else {
			final StandardServiceRegistry serviceRegistry = context.getBootstrapContext().getServiceRegistry();
			final ManagedBeanRegistry beanRegistry = serviceRegistry.getService( ManagedBeanRegistry.class );
			final ManagedBean<JavaType<?>> bean = beanRegistry.getBean( type );
			jtd = bean.getBeanInstance();
		}

		final JdbcType jdbcType = jtd.getRecommendedJdbcType( typeConfiguration.getCurrentBaseSqlTypeIndicators() );
		return typeConfiguration.getBasicTypeRegistry().resolve( jtd, jdbcType );
	}

	public static void bindFetchProfilesForClass(XClass clazzToProcess, MetadataBuildingContext context) {
		bindFetchProfiles( clazzToProcess, context );
	}

	public static void bindFetchProfilesForPackage(ClassLoaderService cls, String packageName, MetadataBuildingContext context) {
		final Package packaze = cls.packageForNameOrNull( packageName );
		if ( packaze == null ) {
			return;
		}
		final ReflectionManager reflectionManager = context.getBootstrapContext().getReflectionManager();
		final XPackage pckg = reflectionManager.toXPackage( packaze );
		bindFetchProfiles( pckg, context );
	}

	private static void bindFetchProfiles(XAnnotatedElement annotatedElement, MetadataBuildingContext context) {
		FetchProfile fetchProfileAnnotation = annotatedElement.getAnnotation( FetchProfile.class );
		FetchProfiles fetchProfileAnnotations = annotatedElement.getAnnotation( FetchProfiles.class );
		if ( fetchProfileAnnotation != null ) {
			bindFetchProfile( fetchProfileAnnotation, context );
		}
		if ( fetchProfileAnnotations != null ) {
			for ( FetchProfile profile : fetchProfileAnnotations.value() ) {
				bindFetchProfile( profile, context );
			}
		}
	}

	private static void bindFetchProfile(FetchProfile fetchProfileAnnotation, MetadataBuildingContext context) {
		for ( FetchProfile.FetchOverride fetch : fetchProfileAnnotation.fetchOverrides() ) {
			org.hibernate.annotations.FetchMode mode = fetch.mode();
			if ( !mode.equals( org.hibernate.annotations.FetchMode.JOIN ) ) {
				throw new MappingException( "Only FetchMode.JOIN is currently supported" );
			}

			context.getMetadataCollector().addSecondPass(
					new VerifyFetchProfileReferenceSecondPass(
							fetchProfileAnnotation.name(),
							fetch,
							context
					)
			);
		}
	}

	/**
	 * @param elements List of {@code ProperyData} instances
	 * @param propertyContainer Metadata about a class and its properties
	 *
	 * @return the number of id properties found while iterating the elements of {@code annotatedClass} using
	 *         the determined access strategy, {@code false} otherwise.
	 */
	static int addElementsOfClass(
			List<PropertyData> elements,
			PropertyContainer propertyContainer,
			MetadataBuildingContext context) {
		int idPropertyCounter = 0;
		for ( XProperty p : propertyContainer.propertyIterator() ) {
			final int currentIdPropertyCounter = addProperty(
					propertyContainer,
					p,
					elements,
					context
			);
			idPropertyCounter += currentIdPropertyCounter;
		}
		return idPropertyCounter;
	}

	private static int addProperty(
			PropertyContainer propertyContainer,
			XProperty property,
			List<PropertyData> inFlightPropertyDataList,
			MetadataBuildingContext context) {
		// see if inFlightPropertyDataList already contains a PropertyData for this name,
		// and if so, skip it..
		for ( PropertyData propertyData : inFlightPropertyDataList ) {
			if ( propertyData.getPropertyName().equals( property.getName() ) ) {
				Id incomingIdProperty = property.getAnnotation( Id.class );
				Id existingIdProperty = propertyData.getProperty().getAnnotation( Id.class );
				if ( incomingIdProperty != null && existingIdProperty == null ) {
					throw new MappingException(
							String.format(
									"You cannot override the [%s] non-identifier property from the [%s] base class or @MappedSuperclass and make it an identifier in the [%s] subclass",
									propertyData.getProperty().getName(),
									propertyData.getProperty().getDeclaringClass().getName(),
									property.getDeclaringClass().getName()
							)
					);
				}
				// EARLY EXIT!!!
				return 0;
			}
		}

		final XClass declaringClass = propertyContainer.getDeclaringClass();
		final XClass entity = propertyContainer.getEntityAtStake();
		int idPropertyCounter = 0;
		final PropertyData propertyAnnotatedElement = new PropertyInferredData(
				declaringClass,
				property,
				propertyContainer.getClassLevelAccessType().getType(),
				context.getBootstrapContext().getReflectionManager()
		);

		// put element annotated by @Id in front, since it has to be parsed
		// before any association by Hibernate
		final XAnnotatedElement element = propertyAnnotatedElement.getProperty();
		if ( hasIdAnnotation(element) ) {
			inFlightPropertyDataList.add( 0, propertyAnnotatedElement );
			/*
			 * The property must be put in hibernate.properties as it's a system wide property. Fixable?
			 * TODO support true/false/default on the property instead of present / not present
			 * TODO is @Column mandatory?
			 * TODO add method support
			 */
			if ( context.getBuildingOptions().isSpecjProprietarySyntaxEnabled() ) {
				if ( element.isAnnotationPresent( Id.class ) && element.isAnnotationPresent( Column.class ) ) {
					String columnName = element.getAnnotation( Column.class ).name();
					for ( XProperty prop : declaringClass.getDeclaredProperties( AccessType.FIELD.getType() ) ) {
						if ( !prop.isAnnotationPresent( MapsId.class ) ) {
							/*
							 * The detection of a configured individual JoinColumn differs between Annotation
							 * and XML configuration processing.
							 */
							boolean isRequiredAnnotationPresent = false;
							JoinColumns groupAnnotation = prop.getAnnotation( JoinColumns.class );
							if ( (prop.isAnnotationPresent( JoinColumn.class )
									&& prop.getAnnotation( JoinColumn.class ).name().equals( columnName )) ) {
								isRequiredAnnotationPresent = true;
							}
							else if ( prop.isAnnotationPresent( JoinColumns.class ) ) {
								for ( JoinColumn columnAnnotation : groupAnnotation.value() ) {
									if ( columnName.equals( columnAnnotation.name() ) ) {
										isRequiredAnnotationPresent = true;
										break;
									}
								}
							}
							if ( isRequiredAnnotationPresent ) {
								//create a PropertyData for the specJ property holding the mapping
								PropertyData specJPropertyData = new PropertyInferredData(
										declaringClass,
										//same dec
										prop,
										// the actual @XToOne property
										propertyContainer.getClassLevelAccessType().getType(),
										//TODO we should get the right accessor but the same as id would do
										context.getBootstrapContext().getReflectionManager()
								);
								context.getMetadataCollector().addPropertyAnnotatedWithMapsIdSpecj(
										entity,
										specJPropertyData,
										element.toString()
								);
							}
						}
					}
				}
			}

			if ( hasToOneAnnotation(element) ) {
				context.getMetadataCollector().addToOneAndIdProperty( entity, propertyAnnotatedElement );
			}
			idPropertyCounter++;
		}
		else {
			inFlightPropertyDataList.add( propertyAnnotatedElement );
		}
		if ( element.isAnnotationPresent( MapsId.class ) ) {
			context.getMetadataCollector().addPropertyAnnotatedWithMapsId( entity, propertyAnnotatedElement );
		}

		return idPropertyCounter;
	}

	private static boolean hasIdAnnotation(XAnnotatedElement element) {
		return element.isAnnotationPresent(Id.class)
			|| element.isAnnotationPresent(EmbeddedId.class);
	}

	/*
	 * Process annotation of a particular property
	 */

	public static void processElementAnnotations(
			PropertyHolder propertyHolder,
			Nullability nullability,
			PropertyData inferredData,
			HashMap<String, IdentifierGeneratorDefinition> classGenerators,
			EntityBinder entityBinder,
			boolean isIdentifierMapper,
			boolean isComponentEmbedded,
			boolean inSecondPass,
			MetadataBuildingContext context,
			Map<XClass, InheritanceState> inheritanceStatePerClass) throws MappingException {

		if ( !propertyHolder.isComponent()
				&& entityBinder.isPropertyDefinedInSuperHierarchy( inferredData.getPropertyName() ) ) {
			LOG.debugf(
					"Skipping attribute [%s : %s] as it was already processed as part of super hierarchy",
					inferredData.getClassOrElementName(),
					inferredData.getPropertyName()
			);
		}
		else {
			// inSecondPass can only be used to apply right away the second pass of a composite-element
			// Because it's a value type, there is no bidirectional association, hence second pass
			// ordering does not matter

			if ( LOG.isTraceEnabled() ) {
				LOG.tracev( "Processing annotations of {0}.{1}" , propertyHolder.getEntityName(), inferredData.getPropertyName() );
			}

			final XProperty property = inferredData.getProperty();
			if ( property.isAnnotationPresent( Parent.class ) ) {
				if ( propertyHolder.isComponent() ) {
					propertyHolder.setParentProperty( property.getName() );
				}
				else {
					throw new AnnotationException(
							"@Parent cannot be applied outside an embeddable object: "
									+ getPath( propertyHolder, inferredData )
					);
				}
			}
			else {
				//prepare PropertyBinder
				buildProperty(
						propertyHolder,
						nullability,
						inferredData,
						classGenerators,
						entityBinder,
						isIdentifierMapper,
						isComponentEmbedded,
						inSecondPass,
						context,
						inheritanceStatePerClass,
						property,
						inferredData.getClassOrElement()
				);
			}
		}
	}

	private static void buildProperty(
			PropertyHolder propertyHolder,
			Nullability nullability,
			PropertyData inferredData,
			HashMap<String, IdentifierGeneratorDefinition> classGenerators,
			EntityBinder entityBinder,
			boolean isIdentifierMapper,
			boolean isComponentEmbedded,
			boolean inSecondPass,
			MetadataBuildingContext context,
			Map<XClass, InheritanceState> inheritanceStatePerClass,
			XProperty property,
			XClass returnedClass) {

		final ColumnsBuilder columnsBuilder = new ColumnsBuilder(
				propertyHolder,
				nullability,
				property,
				inferredData,
				entityBinder,
				context
		).extractMetadata();
		AnnotatedColumn[] columns = columnsBuilder.getColumns();
		AnnotatedJoinColumn[] joinColumns = columnsBuilder.getJoinColumns();

		final PropertyBinder propertyBinder = new PropertyBinder();
		propertyBinder.setName( inferredData.getPropertyName() );
		propertyBinder.setReturnedClassName( inferredData.getTypeName() );
		propertyBinder.setAccessType( inferredData.getDefaultAccess() );
		propertyBinder.setHolder(propertyHolder);
		propertyBinder.setProperty(property);
		propertyBinder.setReturnedClass( inferredData.getPropertyClass() );
		propertyBinder.setBuildingContext(context);
		if ( isIdentifierMapper ) {
			propertyBinder.setInsertable( false );
			propertyBinder.setUpdatable( false );
		}
		propertyBinder.setDeclaringClass( inferredData.getDeclaringClass() );
		propertyBinder.setEntityBinder( entityBinder );
		propertyBinder.setInheritanceStatePerClass( inheritanceStatePerClass );

		final boolean isId = !entityBinder.isIgnoreIdAnnotations() && hasIdAnnotation( property );
		propertyBinder.setId( isId );

		final LazyGroup lazyGroupAnnotation = property.getAnnotation( LazyGroup.class );
		if ( lazyGroupAnnotation != null ) {
			propertyBinder.setLazyGroup( lazyGroupAnnotation.value() );
		}

		if ( property.isAnnotationPresent( Version.class ) ) {
			bindVersionProperty(
					propertyHolder,
					inferredData,
					isIdentifierMapper,
					context,
					inheritanceStatePerClass,
					property,
					columns,
					propertyBinder
			);
		}
		else {
			if ( property.isAnnotationPresent( ManyToOne.class ) ) {
				bindManyToOne(
						propertyHolder,
						inferredData,
						isIdentifierMapper,
						inSecondPass,
						context,
						property,
						joinColumns,
						propertyBinder,
						isForcePersist(property)
				);
			}
			else if ( property.isAnnotationPresent( OneToOne.class ) ) {
				bindOneToOne(
						propertyHolder,
						inferredData,
						isIdentifierMapper,
						inSecondPass,
						context,
						property,
						joinColumns,
						propertyBinder,
						isForcePersist(property)
				);
			}
			else if ( property.isAnnotationPresent( org.hibernate.annotations.Any.class ) ) {
				bindAny(
						propertyHolder,
						nullability,
						inferredData,
						entityBinder,
						isIdentifierMapper,
						context,
						property,
						joinColumns,
						isForcePersist(property)
				);
			}
			else if ( property.isAnnotationPresent( OneToMany.class )
					|| property.isAnnotationPresent( ManyToMany.class )
					|| property.isAnnotationPresent( ElementCollection.class )
					|| property.isAnnotationPresent( ManyToAny.class ) ) {
				CollectionBinder.bindCollection(
						propertyHolder,
						nullability,
						inferredData,
						classGenerators,
						entityBinder,
						isIdentifierMapper,
						context,
						inheritanceStatePerClass,
						property,
						joinColumns
				);
			}
			//Either a regular property or a basic @Id or @EmbeddedId while not ignoring id annotations
			else if ( !isId || !entityBinder.isIgnoreIdAnnotations() ) {
				// returns overridden columns
				columns = bindBasic(
						propertyHolder,
						nullability,
						inferredData,
						classGenerators,
						entityBinder,
						isIdentifierMapper,
						isComponentEmbedded,
						context,
						inheritanceStatePerClass,
						property,
						columnsBuilder,
						columns,
						returnedClass,
						propertyBinder,
						isId
				);
			}
		}

		addIndexes( inSecondPass, property, columns, joinColumns );
		addNaturalIds( inSecondPass, property, columns, joinColumns );
	}

	private static boolean isForcePersist(XProperty property) {
		return property.isAnnotationPresent(MapsId.class)
			|| property.isAnnotationPresent(Id.class);
	}

	private static void bindVersionProperty(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			boolean isIdentifierMapper,
			MetadataBuildingContext context,
			Map<XClass, InheritanceState> inheritanceStatePerClass,
			XProperty property,
			AnnotatedColumn[] columns,
			PropertyBinder propertyBinder) {
		if (isIdentifierMapper) {
			throw new AnnotationException(
					"@IdClass class should not have @Version property"
			);
		}
		if ( !( propertyHolder.getPersistentClass() instanceof RootClass ) ) {
			throw new AnnotationException(
					"Unable to define/override @Version on a subclass: "
							+ propertyHolder.getEntityName()
			);
		}
		if ( !propertyHolder.isEntity() ) {
			throw new AnnotationException(
					"Unable to define @Version on an embedded class: "
							+ propertyHolder.getEntityName()
			);
		}
		if ( LOG.isTraceEnabled() ) {
			LOG.tracev( "{0} is a version property", inferredData.getPropertyName() );
		}
		RootClass rootClass = (RootClass) propertyHolder.getPersistentClass();
		propertyBinder.setColumns(columns);
		Property prop = propertyBinder.makePropertyValueAndBind();
		setVersionInformation(property, propertyBinder);
		rootClass.setVersion( prop );

		//If version is on a mapped superclass, update the mapping
		final org.hibernate.mapping.MappedSuperclass superclass = getMappedSuperclassOrNull(
				inferredData.getDeclaringClass(),
				inheritanceStatePerClass,
				context
		);
		if ( superclass != null ) {
			superclass.setDeclaredVersion( prop );
		}
		else {
			//we know the property is on the actual entity
			rootClass.setDeclaredVersion( prop );
		}

		( (SimpleValue) prop.getValue() ).setNullValue( "undefined" );
		rootClass.setOptimisticLockStyle( OptimisticLockStyle.VERSION );
		if ( LOG.isTraceEnabled() ) {
			LOG.tracev( "Version name: {0}, unsavedValue: {1}", rootClass.getVersion().getName(),
					( (SimpleValue) rootClass.getVersion().getValue() ).getNullValue() );
		}
	}

	private static AnnotatedColumn[] bindBasic(
			PropertyHolder propertyHolder,
			Nullability nullability,
			PropertyData inferredData,
			Map<String, IdentifierGeneratorDefinition> classGenerators,
			EntityBinder entityBinder,
			boolean isIdentifierMapper,
			boolean isComponentEmbedded,
			MetadataBuildingContext context,
			Map<XClass, InheritanceState> inheritanceStatePerClass,
			XProperty property,
			ColumnsBuilder columnsBuilder,
			AnnotatedColumn[] columns,
			XClass returnedClass,
			PropertyBinder propertyBinder,
			boolean isId) {
		//define whether the type is a component or not

		boolean isComponent = false;

		//Overrides from @MapsId if needed
		boolean isOverridden = false;
		if ( isId || propertyHolder.isOrWithinEmbeddedId() || propertyHolder.isInIdClass() ) {
			//the associated entity could be using an @IdClass making the overridden property a component
			PropertyData overridingProperty = getPropertyOverriddenByMapperOrMapsId(
					isId,
					propertyHolder,
					property.getName(),
					context
			);
			if ( overridingProperty != null ) {
				isOverridden = true;
				final InheritanceState state = inheritanceStatePerClass.get( overridingProperty.getClassOrElement() );
				if ( state != null ) {
					isComponent = state.hasIdClassOrEmbeddedId();
				}
				//Get the new column
				columns = columnsBuilder.overrideColumnFromMapperOrMapsIdProperty(isId);
			}
		}

		isComponent = isComponent || isEmbedded( property, returnedClass );
		final Class<? extends CompositeUserType<?>> compositeUserType = resolveCompositeUserType(
				inferredData.getProperty(),
				inferredData.getClassOrElement(),
				context
		);

		if ( isComponent || compositeUserType != null ) {
			String referencedEntityName = null;
			if ( isOverridden ) {
				PropertyData mapsIdProperty = getPropertyOverriddenByMapperOrMapsId(
						isId, propertyHolder, property.getName(), context
				);
				referencedEntityName = mapsIdProperty.getClassOrElementName();
			}

			propertyBinder = bindComponent(
					inferredData,
					propertyHolder,
					entityBinder.getPropertyAccessor(property),
					entityBinder,
					isIdentifierMapper,
					context,
					isComponentEmbedded,
					isId,
					inheritanceStatePerClass,
					referencedEntityName,
					determineCustomInstantiator( property, returnedClass, context ),
					compositeUserType,
					isOverridden ? (AnnotatedJoinColumn[]) columns : null
			);
		}
		else {
			//provide the basic property mapping
			boolean optional = true;
			boolean lazy = false;
			if ( property.isAnnotationPresent( Basic.class ) ) {
				Basic ann = property.getAnnotation( Basic.class );
				optional = ann.optional();
				lazy = ann.fetch() == FetchType.LAZY;
			}
			//implicit type will check basic types and Serializable classes
			if ( isId || !optional && nullability != Nullability.FORCED_NULL ) {
				//force columns to not null
				for ( AnnotatedColumn col : columns) {
					if ( isId && col.isFormula() ) {
						throw new CannotForceNonNullableException(
								String.format(
										Locale.ROOT,
										"Identifier property [%s] cannot contain formula mapping [%s]",
										HCANNHelper.annotatedElementSignature(property),
										col.getFormulaString()
								)
						);
					}
					col.forceNotNull();
				}
			}

			propertyBinder.setLazy( lazy );
			propertyBinder.setColumns(columns);
			if ( isOverridden ) {
				final PropertyData mapsIdProperty = getPropertyOverriddenByMapperOrMapsId(
						isId, propertyHolder, property.getName(), context
				);
				propertyBinder.setReferencedEntityName( mapsIdProperty.getClassOrElementName() );
			}

			propertyBinder.makePropertyValueAndBind();

		}
		if ( isOverridden ) {
			final PropertyData mapsIdProperty = getPropertyOverriddenByMapperOrMapsId(
					isId, propertyHolder, property.getName(), context
			);
			final IdentifierGeneratorDefinition.Builder foreignGeneratorBuilder =
					new IdentifierGeneratorDefinition.Builder();
			foreignGeneratorBuilder.setName( "Hibernate-local--foreign generator" );
			foreignGeneratorBuilder.setStrategy( "foreign" );
			foreignGeneratorBuilder.addParam( "property", mapsIdProperty.getPropertyName() );
			final IdentifierGeneratorDefinition foreignGenerator = foreignGeneratorBuilder.build();

			if ( isGlobalGeneratorNameGlobal( context ) ) {
				SecondPass secondPass = new IdGeneratorResolverSecondPass(
						(SimpleValue) propertyBinder.getValue(),
						property,
						foreignGenerator.getStrategy(),
						foreignGenerator.getName(),
						context,
						foreignGenerator
				);
				context.getMetadataCollector().addSecondPass( secondPass );
			}
			else {
				Map<String, IdentifierGeneratorDefinition> localGenerators = new HashMap<>(classGenerators);
				localGenerators.put( foreignGenerator.getName(), foreignGenerator );

				makeIdGenerator(
						(SimpleValue) propertyBinder.getValue(),
						property,
						foreignGenerator.getStrategy(),
						foreignGenerator.getName(),
						context,
						localGenerators
				);
			}
		}
		if (isId) {
			//components and regular basic types create SimpleValue objects
			if ( !isOverridden ) {
				processId(
						propertyHolder,
						inferredData,
						(SimpleValue) propertyBinder.getValue(),
						classGenerators,
						isIdentifierMapper,
						context
				);
			}
		}
		return columns;
	}

	private static boolean isEmbedded(XProperty property, XClass returnedClass) {
		return property.isAnnotationPresent( Embedded.class )
			|| property.isAnnotationPresent( EmbeddedId.class )
			|| returnedClass.isAnnotationPresent( Embeddable.class );
	}

	private static void bindAny(
			PropertyHolder propertyHolder,
			Nullability nullability,
			PropertyData inferredData,
			EntityBinder entityBinder,
			boolean isIdentifierMapper,
			MetadataBuildingContext context,
			XProperty property,
			AnnotatedJoinColumn[] joinColumns,
			boolean forcePersist) {

		//check validity
		if (  property.isAnnotationPresent( Columns.class ) ) {
			throw new AnnotationException(
					String.format(
							Locale.ROOT,
							"@Columns not allowed on a @Any property [%s]; @Column or @Formula is used to map the discriminator" +
									"and only one is allowed",
							getPath(propertyHolder, inferredData)
					)
			);
		}

		Cascade hibernateCascade = property.getAnnotation( Cascade.class );
		OnDelete onDeleteAnn = property.getAnnotation( OnDelete.class );
		JoinTable assocTable = propertyHolder.getJoinTable(property);
		if ( assocTable != null ) {
			Join join = propertyHolder.addJoin( assocTable, false );
			for ( AnnotatedJoinColumn joinColumn : joinColumns) {
				joinColumn.setExplicitTableName( join.getTable().getName() );
			}
		}
		bindAny(
				getCascadeStrategy( null, hibernateCascade, false, forcePersist),
				//@Any has not cascade attribute
				joinColumns,
				onDeleteAnn != null && OnDeleteAction.CASCADE == onDeleteAnn.action(),
				nullability,
				propertyHolder,
				inferredData,
				entityBinder,
				isIdentifierMapper,
				context
		);
	}

	private static void bindOneToOne(PropertyHolder propertyHolder, PropertyData inferredData, boolean isIdentifierMapper, boolean inSecondPass, MetadataBuildingContext context, XProperty property, AnnotatedJoinColumn[] joinColumns, PropertyBinder propertyBinder, boolean forcePersist) {
		OneToOne ann = property.getAnnotation( OneToOne.class );

		//check validity
		if ( property.isAnnotationPresent( Column.class )
				|| property.isAnnotationPresent( Columns.class ) ) {
			throw new AnnotationException(
					"@Column(s) not allowed on a @OneToOne property: "
							+ getPath(propertyHolder, inferredData)
			);
		}

		//FIXME support a proper PKJCs
		boolean trueOneToOne = property.isAnnotationPresent( PrimaryKeyJoinColumn.class )
				|| property.isAnnotationPresent( PrimaryKeyJoinColumns.class );
		Cascade hibernateCascade = property.getAnnotation( Cascade.class );
		NotFound notFound = property.getAnnotation( NotFound.class );
		NotFoundAction notFoundAction = notFound == null ? null : notFound.action();
		final boolean hasNotFoundAction = notFoundAction != null;

		// MapsId means the columns belong to the pk;
		// A @MapsId association (obviously) must be non-null when the entity is first persisted.
		// If a @MapsId association is not mapped with @NotFound(IGNORE), then the association
		// is mandatory (even if the association has optional=true).
		// If a @MapsId association has optional=true and is mapped with @NotFound(IGNORE) then
		// the association is optional.
		// @OneToOne(optional = true) with @PKJC makes the association optional.
		final boolean mandatory = !ann.optional()
				|| property.isAnnotationPresent( Id.class )
				|| property.isAnnotationPresent( MapsId.class ) && !hasNotFoundAction;
		matchIgnoreNotFoundWithFetchType( propertyHolder.getEntityName(), property.getName(), notFoundAction, ann.fetch() );
		OnDelete onDeleteAnn = property.getAnnotation( OnDelete.class );
		JoinTable assocTable = propertyHolder.getJoinTable(property);
		if ( assocTable != null ) {
			Join join = propertyHolder.addJoin( assocTable, false );
			if ( hasNotFoundAction ) {
				join.disableForeignKeyCreation();
			}
			for ( AnnotatedJoinColumn joinColumn : joinColumns) {
				joinColumn.setExplicitTableName( join.getTable().getName() );
			}
		}
		bindOneToOne(
				getCascadeStrategy( ann.cascade(), hibernateCascade, ann.orphanRemoval(), forcePersist),
				joinColumns,
				!mandatory,
				getFetchMode( ann.fetch() ),
				notFoundAction,
				onDeleteAnn != null && OnDeleteAction.CASCADE == onDeleteAnn.action(),
				ToOneBinder.getTargetEntity(inferredData, context),
				propertyHolder,
				inferredData,
				ann.mappedBy(),
				trueOneToOne,
				isIdentifierMapper,
				inSecondPass,
				propertyBinder,
				context
		);
	}

	private static void bindManyToOne(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			boolean isIdentifierMapper,
			boolean inSecondPass,
			MetadataBuildingContext context,
			XProperty property,
			AnnotatedJoinColumn[] joinColumns,
			PropertyBinder propertyBinder,
			boolean forcePersist) {
		ManyToOne ann = property.getAnnotation( ManyToOne.class );

		//check validity
		if ( property.isAnnotationPresent( Column.class )
				|| property.isAnnotationPresent( Columns.class ) ) {
			throw new AnnotationException(
					"@Column(s) not allowed on a @ManyToOne property: "
							+ getPath(propertyHolder, inferredData)
			);
		}

		Cascade hibernateCascade = property.getAnnotation( Cascade.class );
		NotFound notFound = property.getAnnotation( NotFound.class );
		NotFoundAction notFoundAction = notFound == null ? null : notFound.action();
		matchIgnoreNotFoundWithFetchType( propertyHolder.getEntityName(), property.getName(), notFoundAction, ann.fetch() );
		OnDelete onDeleteAnn = property.getAnnotation( OnDelete.class );
		JoinTable assocTable = propertyHolder.getJoinTable(property);
		if ( assocTable != null ) {
			Join join = propertyHolder.addJoin( assocTable, false );
			for ( AnnotatedJoinColumn joinColumn : joinColumns) {
				joinColumn.setExplicitTableName( join.getTable().getName() );
			}
		}
		// MapsId means the columns belong to the pk;
		// A @MapsId association (obviously) must be non-null when the entity is first persisted.
		// If a @MapsId association is not mapped with @NotFound(IGNORE), then the association
		// is mandatory (even if the association has optional=true).
		// If a @MapsId association has optional=true and is mapped with @NotFound(IGNORE) then
		// the association is optional.
		final boolean mandatory = !ann.optional()
				|| property.isAnnotationPresent( Id.class )
				|| property.isAnnotationPresent( MapsId.class ) && notFoundAction != null;
		bindManyToOne(
				getCascadeStrategy( ann.cascade(), hibernateCascade, false, forcePersist),
				joinColumns,
				!mandatory,
				notFoundAction,
				onDeleteAnn != null && OnDeleteAction.CASCADE == onDeleteAnn.action(),
				ToOneBinder.getTargetEntity(inferredData, context),
				propertyHolder,
				inferredData,
				false,
				isIdentifierMapper,
				inSecondPass,
				propertyBinder,
				context
		);
	}

	private static void addIndexes(
			boolean inSecondPass,
			XProperty property,
			AnnotatedColumn[] columns,
			AnnotatedJoinColumn[] joinColumns) {
		//process indexes after everything: in second pass, many to one has to be done before indexes
		Index index = property.getAnnotation( Index.class );
		if ( index != null ) {
			if ( joinColumns != null ) {

				for ( AnnotatedColumn column : joinColumns) {
					column.addIndex( index, inSecondPass);
				}
			}
			else {
				if ( columns != null ) {
					for ( AnnotatedColumn column : columns) {
						column.addIndex( index, inSecondPass);
					}
				}
			}
		}
	}

	private static void addNaturalIds(
			boolean inSecondPass,
			XProperty property,
			AnnotatedColumn[] columns,
			AnnotatedJoinColumn[] joinColumns) {
		// Natural ID columns must reside in one single UniqueKey within the Table.
		// For now, simply ensure consistent naming.
		// TODO: AFAIK, there really isn't a reason for these UKs to be created
		// on the secondPass.  This whole area should go away...
		NaturalId naturalIdAnn = property.getAnnotation( NaturalId.class );
		if ( naturalIdAnn != null ) {
			if ( joinColumns != null ) {
				for ( AnnotatedColumn column : joinColumns) {
					String keyName = "UK_" + Constraint.hashedName( column.getTable().getName() + "_NaturalID" );
					column.addUniqueKey( keyName, inSecondPass);
				}
			}
			else {
				for ( AnnotatedColumn column : columns) {
					String keyName = "UK_" + Constraint.hashedName( column.getTable().getName() + "_NaturalID" );
					column.addUniqueKey( keyName, inSecondPass);
				}
			}
		}
	}

	private static Class<? extends EmbeddableInstantiator> determineCustomInstantiator(
			XProperty property,
			XClass returnedClass,
			MetadataBuildingContext context) {
		if ( property.isAnnotationPresent( EmbeddedId.class ) ) {
			// we don't allow custom instantiators for composite ids
			return null;
		}

		final org.hibernate.annotations.EmbeddableInstantiator propertyAnnotation =
				property.getAnnotation( org.hibernate.annotations.EmbeddableInstantiator.class );
		if ( propertyAnnotation != null ) {
			return propertyAnnotation.value();
		}

		final org.hibernate.annotations.EmbeddableInstantiator classAnnotation =
				returnedClass.getAnnotation( org.hibernate.annotations.EmbeddableInstantiator.class );
		if ( classAnnotation != null ) {
			return classAnnotation.value();
		}

		final Class<?> embeddableClass = context.getBootstrapContext().getReflectionManager().toClass( returnedClass );
		if ( embeddableClass != null ) {
			return context.getMetadataCollector().findRegisteredEmbeddableInstantiator( embeddableClass );
		}

		return null;
	}

	private static Class<? extends CompositeUserType<?>> resolveCompositeUserType(
			XProperty property,
			XClass returnedClass,
			MetadataBuildingContext context) {
		if ( property != null ) {
			final CompositeType compositeType = property.getAnnotation( CompositeType.class );
			if ( compositeType != null ) {
				return compositeType.value();
			}
			Class<? extends CompositeUserType<?>> compositeUserType = resolveTimeZoneStorageCompositeUserType(
					property,
					returnedClass,
					context
			);
			if ( compositeUserType != null ) {
				return compositeUserType;
			}
		}

		if ( returnedClass != null ) {
			final Class<?> embeddableClass = context.getBootstrapContext()
					.getReflectionManager()
					.toClass( returnedClass );
			if ( embeddableClass != null ) {
				return context.getMetadataCollector().findRegisteredCompositeUserType( embeddableClass );
			}
		}

		return null;
	}

	static Class<? extends CompositeUserType<?>> resolveTimeZoneStorageCompositeUserType(
			XProperty property,
			XClass returnedClass,
			MetadataBuildingContext context) {
		if ( property != null ) {
			final TimeZoneStorage timeZoneStorage = property.getAnnotation( TimeZoneStorage.class );
			if ( timeZoneStorage != null ) {
				switch ( timeZoneStorage.value() ) {
					case AUTO:
						if ( context.getBuildingOptions().getDefaultTimeZoneStorage() != TimeZoneStorageStrategy.COLUMN ) {
							return null;
						}
					case COLUMN:
						switch ( returnedClass.getName() ) {
							case "java.time.OffsetDateTime":
								return OffsetDateTimeCompositeUserType.class;
							case "java.time.ZonedDateTime":
								return ZonedDateTimeCompositeUserType.class;
						}
				}
			}
		}
		return null;
	}

	private static boolean isGlobalGeneratorNameGlobal(MetadataBuildingContext context) {
		return context.getBootstrapContext().getJpaCompliance().isGlobalGeneratorScopeEnabled();
	}

	private static void setVersionInformation(XProperty property, PropertyBinder propertyBinder) {
		propertyBinder.getBasicValueBinder().setVersion( true );
		if ( property.isAnnotationPresent( Source.class ) ) {
			Source source = property.getAnnotation( Source.class );
			propertyBinder.getBasicValueBinder().setTimestampVersionType( source.value().typeName() );
		}
	}

	private static void processId(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			SimpleValue idValue,
			Map<String, IdentifierGeneratorDefinition> classGenerators,
			boolean isIdentifierMapper,
			MetadataBuildingContext buildingContext) {
		if ( isIdentifierMapper ) {
			throw new AnnotationException(
					"@IdClass class should not have @Id nor @EmbeddedId properties: "
							+ getPath( propertyHolder, inferredData )
			);
		}
		XClass entityXClass = inferredData.getClassOrElement();
		XProperty idXProperty = inferredData.getProperty();

		final Annotation generatorAnnotation =
				HCANNHelper.findContainingAnnotation( idXProperty, IdGeneratorType.class);
		if ( generatorAnnotation != null ) {
			idValue.setCustomIdGeneratorCreator( new CustomIdGeneratorCreator( generatorAnnotation, idXProperty ) );
		}
		else {
			//manage composite related metadata
			//guess if its a component and find id data access (property, field etc)
			final boolean isComponent = entityXClass.isAnnotationPresent( Embeddable.class )
					|| idXProperty.isAnnotationPresent( EmbeddedId.class );

			GeneratedValue generatedValue = idXProperty.getAnnotation( GeneratedValue.class );

			String generatorType = generatedValue != null
					? generatorType( generatedValue, buildingContext, entityXClass )
					: "assigned";
			String generatorName = generatedValue != null
					? generatedValue.generator()
					: "";
			if ( isComponent ) {
				//a component must not have any generator
				generatorType = "assigned";
			}

			if ( isGlobalGeneratorNameGlobal( buildingContext ) ) {
				buildGenerators( idXProperty, buildingContext );
				SecondPass secondPass = new IdGeneratorResolverSecondPass(
						idValue,
						idXProperty,
						generatorType,
						generatorName,
						buildingContext
				);
				buildingContext.getMetadataCollector().addSecondPass( secondPass );
			}
			else {
				//clone classGenerator and override with local values
				HashMap<String, IdentifierGeneratorDefinition> localGenerators = new HashMap<>( classGenerators );
				localGenerators.putAll( buildGenerators( idXProperty, buildingContext ) );
				makeIdGenerator(
						idValue,
						idXProperty,
						generatorType,
						generatorName,
						buildingContext,
						localGenerators
				);
			}

			if ( LOG.isTraceEnabled() ) {
				LOG.tracev( "Bind {0} on {1}", ( isComponent ? "@EmbeddedId" : "@Id" ), inferredData.getPropertyName() );
			}
		}
	}

	public static String generatorType(
			GeneratedValue generatedValueAnn,
			final MetadataBuildingContext buildingContext,
			final XClass javaTypeXClass) {
		return buildingContext.getBuildingOptions().getIdGenerationTypeInterpreter()
				.determineGeneratorName(
						generatedValueAnn.strategy(),
						new IdGeneratorStrategyInterpreter.GeneratorNameDeterminationContext() {
							Class<?> javaType = null;
							@Override
							public Class<?> getIdType() {
								if ( javaType == null ) {
									javaType = buildingContext
											.getBootstrapContext()
											.getReflectionManager()
											.toClass( javaTypeXClass );
								}
								return javaType;
							}

							@Override
							public String getGeneratedValueGeneratorName() {
								return generatedValueAnn.generator();
							}
						}
				);
	}

	private static PropertyBinder bindComponent(
			PropertyData inferredData,
			PropertyHolder propertyHolder,
			AccessType propertyAccessor,
			EntityBinder entityBinder,
			boolean isIdentifierMapper,
			MetadataBuildingContext buildingContext,
			boolean isComponentEmbedded,
			boolean isId, //is an identifier
			Map<XClass, InheritanceState> inheritanceStatePerClass,
			String referencedEntityName, //is a component who is overridden by a @MapsId
			Class<? extends EmbeddableInstantiator> customInstantiatorImpl,
			Class<? extends CompositeUserType<?>> compositeUserTypeClass,
			AnnotatedJoinColumn[] columns) {
		Component comp;
		if ( referencedEntityName != null ) {
			comp = createComponent(
					propertyHolder,
					inferredData,
					isComponentEmbedded,
					isIdentifierMapper,
					customInstantiatorImpl,
					buildingContext
			);
			SecondPass sp = new CopyIdentifierComponentSecondPass(
					comp,
					referencedEntityName,
					columns,
					buildingContext
			);
			buildingContext.getMetadataCollector().addSecondPass( sp );
		}
		else {
			comp = fillComponent(
					propertyHolder,
					inferredData,
					propertyAccessor,
					!isId,
					entityBinder,
					isComponentEmbedded,
					isIdentifierMapper,
					false,
					customInstantiatorImpl,
					compositeUserTypeClass,
					buildingContext,
					inheritanceStatePerClass
			);
		}
		if ( isId ) {
			comp.setKey( true );
			if ( propertyHolder.getPersistentClass().getIdentifier() != null ) {
				throw new AnnotationException(
						comp.getComponentClassName()
								+ " must not have @Id properties when used as an @EmbeddedId: "
								+ getPath( propertyHolder, inferredData )
				);
			}
			if ( referencedEntityName == null && comp.getPropertySpan() == 0 ) {
				throw new AnnotationException(
						comp.getComponentClassName()
								+ " has no persistent id property: "
								+ getPath( propertyHolder, inferredData )
				);
			}
		}
		PropertyBinder binder = new PropertyBinder();
		binder.setDeclaringClass(inferredData.getDeclaringClass());
		binder.setName( inferredData.getPropertyName() );
		binder.setValue( comp );
		binder.setProperty( inferredData.getProperty() );
		binder.setAccessType( inferredData.getDefaultAccess() );
		binder.setEmbedded( isComponentEmbedded );
		binder.setHolder( propertyHolder );
		binder.setId( isId );
		binder.setEntityBinder( entityBinder );
		binder.setInheritanceStatePerClass( inheritanceStatePerClass );
		binder.setBuildingContext( buildingContext );
		binder.makePropertyAndBind();
		return binder;
	}

	public static Component fillComponent(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			AccessType propertyAccessor,
			boolean isNullable,
			EntityBinder entityBinder,
			boolean isComponentEmbedded,
			boolean isIdentifierMapper,
			boolean inSecondPass,
			Class<? extends EmbeddableInstantiator> customInstantiatorImpl,
			Class<? extends CompositeUserType<?>> compositeUserTypeClass,
			MetadataBuildingContext buildingContext,
			Map<XClass, InheritanceState> inheritanceStatePerClass) {
		return fillComponent(
				propertyHolder,
				inferredData,
				null,
				propertyAccessor,
				isNullable,
				entityBinder,
				isComponentEmbedded,
				isIdentifierMapper,
				inSecondPass,
				customInstantiatorImpl,
				compositeUserTypeClass,
				buildingContext,
				inheritanceStatePerClass
		);
	}

	public static Component fillComponent(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			PropertyData baseInferredData, //base inferred data correspond to the entity reproducing inferredData's properties (ie IdClass)
			AccessType propertyAccessor,
			boolean isNullable,
			EntityBinder entityBinder,
			boolean isComponentEmbedded,
			boolean isIdentifierMapper,
			boolean inSecondPass,
			Class<? extends EmbeddableInstantiator> customInstantiatorImpl,
			Class<? extends CompositeUserType<?>> compositeUserTypeClass,
			MetadataBuildingContext buildingContext,
			Map<XClass, InheritanceState> inheritanceStatePerClass) {
		/*
		 * inSecondPass can only be used to apply right away the second pass of a composite-element
		 * Because it's a value type, there is no bidirectional association, hence second pass
		 * ordering does not matter
		 */
		Component comp = createComponent(
				propertyHolder,
				inferredData,
				isComponentEmbedded,
				isIdentifierMapper,
				customInstantiatorImpl,
				buildingContext
		);

		String subpath = getPath( propertyHolder, inferredData );
		LOG.tracev( "Binding component with path: {0}", subpath );
		PropertyHolder subHolder = buildPropertyHolder(
				comp,
				subpath,
				inferredData,
				propertyHolder,
				buildingContext
		);


		// propertyHolder here is the owner of the component property.  Tell it we are about to start the component...

		propertyHolder.startingProperty( inferredData.getProperty() );

		final XClass xClassProcessed = inferredData.getPropertyClass();
		List<PropertyData> classElements = new ArrayList<>();

		final CompositeUserType<?> compositeUserType;
		XClass returnedClassOrElement;
		if ( compositeUserTypeClass == null ) {
			compositeUserType = null;
			returnedClassOrElement = inferredData.getClassOrElement();
		}
		else {
			compositeUserType = buildingContext.getBootstrapContext()
					.getServiceRegistry()
					.getService( ManagedBeanRegistry.class )
					.getBean( compositeUserTypeClass )
					.getBeanInstance();
			comp.setTypeName( compositeUserTypeClass.getName() );
			returnedClassOrElement = buildingContext.getBootstrapContext().getReflectionManager().toXClass( compositeUserType.embeddable() );
		}

		List<PropertyData> baseClassElements = null;
		Map<String, PropertyData> orderedBaseClassElements = new HashMap<>();
		XClass baseReturnedClassOrElement;
		if ( baseInferredData != null ) {
			baseClassElements = new ArrayList<>();
			baseReturnedClassOrElement = baseInferredData.getClassOrElement();
			// iterate from base returned class up hierarchy to handle cases where the @Id attributes
			// might be spread across the subclasses and super classes.
			while ( !Object.class.getName().equals( baseReturnedClassOrElement.getName() ) ) {
				addElementsOfClass(
						baseClassElements,
						new PropertyContainer( baseReturnedClassOrElement, xClassProcessed, propertyAccessor ),
						buildingContext
				);
				for ( PropertyData element : baseClassElements ) {
					orderedBaseClassElements.put( element.getPropertyName(), element );
				}
				baseReturnedClassOrElement = baseReturnedClassOrElement.getSuperclass();
			}
		}

		//embeddable elements can have type defs
		PropertyContainer propContainer = new PropertyContainer( returnedClassOrElement, xClassProcessed, propertyAccessor );
		addElementsOfClass( classElements, propContainer, buildingContext );

		//add elements of the embeddable superclass
		XClass superClass = xClassProcessed.getSuperclass();
		while ( superClass != null && superClass.isAnnotationPresent( MappedSuperclass.class ) ) {
			//FIXME: proper support of type variables incl var resolved at upper levels
			propContainer = new PropertyContainer( superClass, xClassProcessed, propertyAccessor );
			addElementsOfClass( classElements, propContainer, buildingContext );
			superClass = superClass.getSuperclass();
		}
		if ( baseClassElements != null ) {
			//useful to avoid breaking pre JPA 2 mappings
			if ( !hasAnnotationsOnIdClass( xClassProcessed ) ) {
				for ( int i = 0; i < classElements.size(); i++ ) {
					final PropertyData idClassPropertyData = classElements.get( i );
					final PropertyData entityPropertyData =
							orderedBaseClassElements.get( idClassPropertyData.getPropertyName() );
					if ( propertyHolder.isInIdClass() ) {
						if ( entityPropertyData == null ) {
							throw new AnnotationException(
									"Property of @IdClass not found in entity "
											+ baseInferredData.getPropertyClass().getName() + ": "
											+ idClassPropertyData.getPropertyName()
							);
						}
						final boolean hasXToOneAnnotation = hasToOneAnnotation( entityPropertyData.getProperty() );
						final boolean isOfDifferentType = !entityPropertyData.getClassOrElement()
								.equals( idClassPropertyData.getClassOrElement() );
						if ( !hasXToOneAnnotation || !isOfDifferentType ) {
							classElements.set( i, entityPropertyData );  //this works since they are in the same order
						}
//						else {
							//don't replace here as we need to use the actual original return type
							//the annotation overriding will be dealt with by a mechanism similar to @MapsId
//						}
					}
					else {
						classElements.set( i, entityPropertyData );  //this works since they are in the same order
					}
				}
			}
		}
		for ( PropertyData propertyAnnotatedElement : classElements ) {
			processElementAnnotations(
					subHolder,
					isNullable
							? Nullability.NO_CONSTRAINT
							: Nullability.FORCED_NOT_NULL,
					propertyAnnotatedElement,
					new HashMap<>(),
					entityBinder,
					isIdentifierMapper,
					isComponentEmbedded,
					inSecondPass,
					buildingContext,
					inheritanceStatePerClass
			);

			XProperty property = propertyAnnotatedElement.getProperty();
			if ( property.isAnnotationPresent( GeneratedValue.class )
					&& property.isAnnotationPresent( Id.class ) ) {
				GeneratedValue generatedValue = property.getAnnotation( GeneratedValue.class );
				String generatorType = generatedValue != null
						? generatorType( generatedValue, buildingContext, property.getType() )
						: DEFAULT_ID_GEN_STRATEGY;
				String generator = generatedValue != null ? generatedValue.generator() : "";

				if ( isGlobalGeneratorNameGlobal( buildingContext ) ) {
					buildGenerators( property, buildingContext );
					SecondPass secondPass = new IdGeneratorResolverSecondPass(
							(SimpleValue) comp.getProperty( property.getName() ).getValue(),
							property,
							generatorType,
							generator,
							buildingContext
					);
					buildingContext.getMetadataCollector().addSecondPass( secondPass );

					handleTypeDescriptorRegistrations( property, buildingContext );
					bindEmbeddableInstantiatorRegistrations( property, buildingContext );
					bindCompositeUserTypeRegistrations( property, buildingContext );
					handleConverterRegistrations( property, buildingContext );
				}
				else {
					Map<String, IdentifierGeneratorDefinition> localGenerators =
							new HashMap<>( buildGenerators( property, buildingContext ) );
					makeIdGenerator(
							(SimpleValue) comp.getProperty( property.getName() ).getValue(),
							property,
							generatorType,
							generator,
							buildingContext,
							localGenerators
					);
				}
			}
		}

		if ( compositeUserType != null ) {
			comp.sortProperties();
			final List<String> sortedPropertyNames = new ArrayList<>( comp.getPropertySpan() );
			final List<Type> sortedPropertyTypes = new ArrayList<>( comp.getPropertySpan() );
			final PropertyAccessStrategy strategy = new PropertyAccessStrategyCompositeUserTypeImpl( compositeUserType, sortedPropertyNames, sortedPropertyTypes );
			for ( Property property : comp.getProperties() ) {
				sortedPropertyNames.add( property.getName() );
				sortedPropertyTypes.add(
						PropertyAccessStrategyMixedImpl.INSTANCE.buildPropertyAccess(
								compositeUserType.embeddable(),
								property.getName(),
								false
						).getGetter().getReturnType()
				);
				property.setPropertyAccessStrategy( strategy );
			}
		}
		return comp;
	}

	public static Component createComponent(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			boolean isComponentEmbedded,
			boolean isIdentifierMapper,
			Class<? extends EmbeddableInstantiator> customInstantiatorImpl,
			MetadataBuildingContext context) {
		Component comp = new Component( context, propertyHolder.getPersistentClass() );
		comp.setEmbedded( isComponentEmbedded );
		//yuk
		comp.setTable( propertyHolder.getTable() );
		//FIXME shouldn't identifier mapper use getClassOrElementName? Need to be checked.
		if ( isIdentifierMapper || ( isComponentEmbedded && inferredData.getPropertyName() == null ) ) {
			comp.setComponentClassName( comp.getOwner().getClassName() );
		}
		else {
			comp.setComponentClassName( inferredData.getClassOrElementName() );
		}
		comp.setCustomInstantiator( customInstantiatorImpl );
		return comp;
	}

	public static PropertyData getUniqueIdPropertyFromBaseClass(
			PropertyData inferredData,
			PropertyData baseInferredData,
			AccessType propertyAccessor,
			MetadataBuildingContext context) {
		final List<PropertyData> baseClassElements = new ArrayList<>();
		final PropertyContainer propContainer = new PropertyContainer(
				baseInferredData.getClassOrElement(),
				inferredData.getPropertyClass(),
				propertyAccessor
		);
		addElementsOfClass( baseClassElements, propContainer, context );
		//Id properties are on top and there is only one
		return baseClassElements.get( 0 );
	}

	private static void bindManyToOne(
			String cascadeStrategy,
			AnnotatedJoinColumn[] columns,
			boolean optional,
			NotFoundAction notFoundAction,
			boolean cascadeOnDelete,
			XClass targetEntity,
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			boolean unique,
			boolean isIdentifierMapper,
			boolean inSecondPass,
			PropertyBinder propertyBinder,
			MetadataBuildingContext context) {
		//All FK columns should be in the same table
		final org.hibernate.mapping.ManyToOne value = new org.hibernate.mapping.ManyToOne( context, columns[0].getTable() );
		// This is a @OneToOne mapped to a physical o.h.mapping.ManyToOne
		if ( unique ) {
			value.markAsLogicalOneToOne();
		}
		value.setReferencedEntityName( ToOneBinder.getReferenceEntityName( inferredData, targetEntity, context ) );
		final XProperty property = inferredData.getProperty();
		defineFetchingStrategy( value, property );
		//value.setFetchMode( fetchMode );
		value.setNotFoundAction( notFoundAction );
		value.setCascadeDeleteEnabled( cascadeOnDelete );
		//value.setLazy( fetchMode != FetchMode.JOIN );
		if ( !optional ) {
			for ( AnnotatedJoinColumn column : columns ) {
				column.setNullable( false );
			}
		}
		if ( property.isAnnotationPresent( MapsId.class ) ) {
			//read only
			for ( AnnotatedJoinColumn column : columns ) {
				column.setInsertable( false );
				column.setUpdatable( false );
			}
		}

		final JoinColumn joinColumn = property.getAnnotation( JoinColumn.class );
		final JoinColumns joinColumns = property.getAnnotation( JoinColumns.class );

		//Make sure that JPA1 key-many-to-one columns are read only too
		boolean hasSpecjManyToOne=false;
		if ( context.getBuildingOptions().isSpecjProprietarySyntaxEnabled() ) {
			String columnName = "";
			for ( XProperty prop : inferredData.getDeclaringClass()
					.getDeclaredProperties( AccessType.FIELD.getType() ) ) {
				if ( prop.isAnnotationPresent( Id.class ) && prop.isAnnotationPresent( Column.class ) ) {
					columnName = prop.getAnnotation( Column.class ).name();
				}

				if ( property.isAnnotationPresent( ManyToOne.class ) && joinColumn != null
						&& ! isEmptyAnnotationValue( joinColumn.name() )
						&& joinColumn.name().equals( columnName )
						&& !property.isAnnotationPresent( MapsId.class ) ) {
					hasSpecjManyToOne = true;
					for ( AnnotatedJoinColumn column : columns ) {
						column.setInsertable( false );
						column.setUpdatable( false );
					}
				}
			}

		}
		value.setTypeName( inferredData.getClassOrElementName() );
		final String propertyName = inferredData.getPropertyName();
		value.setTypeUsingReflection( propertyHolder.getClassName(), propertyName );

		bindForeignKeyNameAndDefinition(
				value,
				property,
				propertyHolder.getOverriddenForeignKey( StringHelper.qualify( propertyHolder.getPath(), propertyName ) ),
				joinColumn,
				joinColumns,
				context
		);

		final FkSecondPass secondPass = new ToOneFkSecondPass(
				value, columns,
				!optional && unique, //cannot have nullable and unique on certain DBs like Derby
				propertyHolder.getEntityOwnerClassName(),
				propertyHolder.getPath() + "." + propertyName,
				context
		);
		if ( inSecondPass ) {
			secondPass.doSecondPass( context.getMetadataCollector().getEntityBindingMap() );
		}
		else {
			context.getMetadataCollector().addSecondPass( secondPass );
		}
		AnnotatedColumn.checkPropertyConsistency( columns, propertyHolder.getEntityName() + "." + propertyName );
		//PropertyBinder binder = new PropertyBinder();
		propertyBinder.setName( propertyName );
		propertyBinder.setValue( value );
		//binder.setCascade(cascadeStrategy);
		if ( isIdentifierMapper ) {
			propertyBinder.setInsertable( false );
			propertyBinder.setUpdatable( false );
		}
		else if (hasSpecjManyToOne) {
			propertyBinder.setInsertable( false );
			propertyBinder.setUpdatable( false );
		}
		else {
			propertyBinder.setInsertable( columns[0].isInsertable() );
			propertyBinder.setUpdatable( columns[0].isUpdatable() );
		}
		propertyBinder.setColumns( columns );
		propertyBinder.setAccessType( inferredData.getDefaultAccess() );
		propertyBinder.setCascade( cascadeStrategy );
		propertyBinder.setProperty( property );
		propertyBinder.setXToMany( true );

		propertyBinder.makePropertyAndBind().setOptional( optional && isNullable( joinColumns, joinColumn ) );
	}

	private static boolean isNullable(JoinColumns joinColumns, JoinColumn joinColumn) {
		if ( joinColumn != null ) {
			return joinColumn.nullable();
		}
		else if ( joinColumns != null ) {
			final JoinColumn[] columns = joinColumns.value();
			for ( int i = 0; i < columns.length; i++ ) {
				if ( columns[i].nullable() ) {
					return true;
				}
			}
			return false;
		}
		else {
			return true;
		}
	}

	static void defineFetchingStrategy(ToOne toOne, XProperty property) {
		final FetchType fetchType = getJpaFetchType( property );

		LazyToOne lazy = property.getAnnotation( LazyToOne.class );
		NotFound notFound = property.getAnnotation( NotFound.class );
		if ( notFound != null ) {
			toOne.setLazy( false );
			toOne.setUnwrapProxy( true );
		}
		else if ( lazy != null ) {
			toOne.setLazy( lazy.value() != LazyToOneOption.FALSE );
			toOne.setUnwrapProxy( ( lazy.value() == LazyToOneOption.NO_PROXY ) );
		}
		else {
			toOne.setLazy( fetchType == FetchType.LAZY );
			toOne.setUnwrapProxy( fetchType != FetchType.LAZY );
			toOne.setUnwrapProxyImplicit( true );
		}

		Fetch fetch = property.getAnnotation( Fetch.class );
		if ( fetch != null ) {
			// Hibernate @Fetch annotation takes precedence
			if ( fetch.value() == org.hibernate.annotations.FetchMode.JOIN ) {
				toOne.setFetchMode( FetchMode.JOIN );
				toOne.setLazy( false );
				toOne.setUnwrapProxy( false );
			}
			else if ( fetch.value() == org.hibernate.annotations.FetchMode.SELECT ) {
				toOne.setFetchMode( FetchMode.SELECT );
			}
			else if ( fetch.value() == org.hibernate.annotations.FetchMode.SUBSELECT ) {
				throw new AnnotationException( "Use of FetchMode.SUBSELECT not allowed for to-one associations: "
						+ property.getName() );
			}
			else {
				throw new AssertionFailure( "Unknown FetchMode: " + fetch.value() );
			}
		}
		else {
			toOne.setFetchMode( getFetchMode( fetchType ) );
		}
	}

	private static FetchType getJpaFetchType(XProperty property) {
		ManyToOne manyToOne = property.getAnnotation( ManyToOne.class );
		OneToOne oneToOne = property.getAnnotation( OneToOne.class );
		if ( manyToOne != null ) {
			return manyToOne.fetch();
		}
		else if ( oneToOne != null ) {
			return oneToOne.fetch();
		}
		else {
			throw new AssertionFailure("Define fetch strategy on a property not annotated with @OneToMany nor @OneToOne");
		}
	}

	private static void bindOneToOne(
			String cascadeStrategy,
			AnnotatedJoinColumn[] joinColumns,
			boolean optional,
			FetchMode fetchMode,
			NotFoundAction notFoundAction,
			boolean cascadeOnDelete,
			XClass targetEntity,
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			String mappedBy,
			boolean trueOneToOne,
			boolean isIdentifierMapper,
			boolean inSecondPass,
			PropertyBinder propertyBinder,
			MetadataBuildingContext context) {
		//column.getTable() => persistentClass.getTable()
		final String propertyName = inferredData.getPropertyName();
		LOG.tracev( "Fetching {0} with {1}", propertyName, fetchMode );
		if ( isMapToPK( joinColumns, propertyHolder, trueOneToOne ) || !isEmptyAnnotationValue( mappedBy ) ) {
			//is a true one-to-one
			//FIXME referencedColumnName ignored => ordering may fail.
			OneToOneSecondPass secondPass = new OneToOneSecondPass(
					mappedBy,
					propertyHolder.getEntityName(),
					propertyName,
					propertyHolder,
					inferredData,
					targetEntity,
					notFoundAction,
					cascadeOnDelete,
					optional,
					cascadeStrategy,
					joinColumns,
					context
			);
			if ( inSecondPass ) {
				secondPass.doSecondPass( context.getMetadataCollector().getEntityBindingMap() );
			}
			else {
				context.getMetadataCollector().addSecondPass( secondPass, isEmptyAnnotationValue( mappedBy ) );
			}
		}
		else {
			//has a FK on the table
			bindManyToOne(
					cascadeStrategy, joinColumns, optional, notFoundAction, cascadeOnDelete,
					targetEntity,
					propertyHolder, inferredData, true, isIdentifierMapper, inSecondPass,
					propertyBinder, context
			);
		}
	}

	private static boolean isMapToPK(AnnotatedJoinColumn[] joinColumns, PropertyHolder propertyHolder, boolean trueOneToOne) {
		if ( trueOneToOne ) {
			return true;
		}
		else {
			//try to find a hidden true one to one (FK == PK columns)
			KeyValue identifier = propertyHolder.getIdentifier();
			if ( identifier == null ) {
				//this is a @OneToOne in an @EmbeddedId (the persistentClass.identifier is not set yet, it's being built)
				//by definition the PK cannot refer to itself so it cannot map to itself
				return false;
			}
			else {
				List<String> idColumnNames = new ArrayList<>();
				if ( identifier.getColumnSpan() != joinColumns.length ) {
					return false;
				}
				else {
					for ( org.hibernate.mapping.Column currentColumn: identifier.getColumns() ) {
						idColumnNames.add( currentColumn.getName() );
					}
					for ( AnnotatedJoinColumn col: joinColumns) {
						if ( !idColumnNames.contains( col.getMappingColumn().getName() ) ) {
							return false;
						}
					}
					return true;
				}
			}
		}
	}

	private static void bindAny(
			String cascadeStrategy,
			AnnotatedJoinColumn[] columns,
			boolean cascadeOnDelete,
			Nullability nullability,
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			EntityBinder entityBinder,
			boolean isIdentifierMapper,
			MetadataBuildingContext buildingContext) {
		final XProperty property = inferredData.getProperty();
		final org.hibernate.annotations.Any any = property.getAnnotation( org.hibernate.annotations.Any.class );
		if ( any == null ) {
			throw new AssertionFailure( "Missing @Any annotation: " + getPath( propertyHolder, inferredData ) );
		}

		final boolean lazy = any.fetch() == FetchType.LAZY;
		final Any value = BinderHelper.buildAnyValue(
				property.getAnnotation( Column.class ),
				getOverridableAnnotation( property, Formula.class, buildingContext ),
				columns,
				inferredData,
				cascadeOnDelete,
				lazy,
				nullability,
				propertyHolder,
				entityBinder,
				any.optional(),
				buildingContext
		);

		final PropertyBinder binder = new PropertyBinder();
		binder.setName( inferredData.getPropertyName() );
		binder.setValue( value );

		binder.setLazy( lazy );
		//binder.setCascade(cascadeStrategy);
		if ( isIdentifierMapper ) {
			binder.setInsertable( false );
			binder.setUpdatable( false );
		}
		else {
			binder.setInsertable( columns[0].isInsertable() );
			binder.setUpdatable( columns[0].isUpdatable() );
		}
		binder.setAccessType( inferredData.getDefaultAccess() );
		binder.setCascade( cascadeStrategy );
		Property prop = binder.makeProperty();
		//composite FK columns are in the same table so its OK
		propertyHolder.addProperty( prop, columns, inferredData.getDeclaringClass() );
	}

	private static EnumSet<CascadeType> convertToHibernateCascadeType(jakarta.persistence.CascadeType[] ejbCascades) {
		final EnumSet<CascadeType> cascadeTypes = EnumSet.noneOf( CascadeType.class );
		if ( ejbCascades != null && ejbCascades.length > 0 ) {
			for ( jakarta.persistence.CascadeType cascade: ejbCascades ) {
				cascadeTypes.add( convertCascadeType( cascade ) );
			}
		}
		return cascadeTypes;
	}

	private static CascadeType convertCascadeType(jakarta.persistence.CascadeType cascade) {
		switch (cascade) {
			case ALL:
				return CascadeType.ALL;
			case PERSIST:
				return CascadeType.PERSIST;
			case MERGE:
				return CascadeType.MERGE;
			case REMOVE:
				return CascadeType.REMOVE;
			case REFRESH:
				return CascadeType.REFRESH;
			case DETACH:
				return CascadeType.DETACH;
			default:
				throw new AssertionFailure("unknown cascade type: " + cascade);
		}
	}

	public static String getCascadeStrategy(
			jakarta.persistence.CascadeType[] ejbCascades,
			Cascade hibernateCascadeAnnotation,
			boolean orphanRemoval,
			boolean forcePersist) {
		EnumSet<CascadeType> cascadeTypes = convertToHibernateCascadeType( ejbCascades );
		CascadeType[] hibernateCascades = hibernateCascadeAnnotation == null ? null : hibernateCascadeAnnotation.value();
		if ( hibernateCascades != null && hibernateCascades.length > 0 ) {
			cascadeTypes.addAll( Arrays.asList( hibernateCascades ) );
		}
		if ( orphanRemoval ) {
			cascadeTypes.add( CascadeType.DELETE_ORPHAN );
			cascadeTypes.add( CascadeType.REMOVE );
		}
		if ( forcePersist ) {
			cascadeTypes.add( CascadeType.PERSIST );
		}
		return renderCascadeTypeList( cascadeTypes );
	}

	private static String renderCascadeTypeList(EnumSet<CascadeType> cascadeTypes) {
		StringBuilder cascade = new StringBuilder();
		for ( CascadeType cascadeType : cascadeTypes) {
			switch ( cascadeType ) {
				case ALL:
					cascade.append( "," ).append( "all" );
					break;
				case SAVE_UPDATE:
					cascade.append( "," ).append( "save-update" );
					break;
				case PERSIST:
					cascade.append( "," ).append( "persist" );
					break;
				case MERGE:
					cascade.append( "," ).append( "merge" );
					break;
				case LOCK:
					cascade.append( "," ).append( "lock" );
					break;
				case REFRESH:
					cascade.append( "," ).append( "refresh" );
					break;
				case REPLICATE:
					cascade.append( "," ).append( "replicate" );
					break;
				case DETACH:
					cascade.append( "," ).append( "evict" );
					break;
				case DELETE:
				case REMOVE:
					cascade.append( "," ).append( "delete" );
					break;
				case DELETE_ORPHAN:
					cascade.append( "," ).append( "delete-orphan" );
					break;
			}
		}
		return cascade.length() > 0 ? cascade.substring( 1 ) : "none";
	}

	public static FetchMode getFetchMode(FetchType fetch) {
		return fetch == FetchType.EAGER ? FetchMode.JOIN : FetchMode.SELECT;
	}

	public static void bindForeignKeyNameAndDefinition(
			SimpleValue value,
			XProperty property,
			jakarta.persistence.ForeignKey fkOverride,
			JoinColumn joinColumn,
			JoinColumns joinColumns,
			MetadataBuildingContext context) {
		if ( property.getAnnotation( NotFound.class ) != null ) {
			// supersedes all others
			value.disableForeignKey();
		}
		else {
			if ( joinColumn!=null && noConstraint( joinColumn.foreignKey(), context )
					|| joinColumns!=null && noConstraint( joinColumns.foreignKey(), context ) ) {
				value.disableForeignKey();
			}
			else {
				final ForeignKey fk = property.getAnnotation( ForeignKey.class );
				if ( fk != null && StringHelper.isNotEmpty( fk.name() ) ) {
					value.setForeignKeyName( fk.name() );
				}
				else {
					if ( noConstraint( fkOverride, context) ) {
						value.disableForeignKey();
					}
					else if ( fkOverride != null ) {
						value.setForeignKeyName( nullIfEmpty( fkOverride.name() ) );
						value.setForeignKeyDefinition( nullIfEmpty( fkOverride.foreignKeyDefinition() ) );
					}
					else if ( joinColumns != null ) {
						value.setForeignKeyName( nullIfEmpty( joinColumns.foreignKey().name() ) );
						value.setForeignKeyDefinition( nullIfEmpty( joinColumns.foreignKey().foreignKeyDefinition() ) );
					}
					else if ( joinColumn != null ) {
						value.setForeignKeyName( nullIfEmpty( joinColumn.foreignKey().name() ) );
						value.setForeignKeyDefinition( nullIfEmpty( joinColumn.foreignKey().foreignKeyDefinition() ) );
					}
				}
			}
		}
	}

	private static boolean noConstraint(jakarta.persistence.ForeignKey joinColumns, MetadataBuildingContext context) {
		return joinColumns != null
			&& ( joinColumns.value() == ConstraintMode.NO_CONSTRAINT
				|| joinColumns.value() == ConstraintMode.PROVIDER_DEFAULT
						&& context.getBuildingOptions().isNoConstraintByDefault() );
	}

	public static HashMap<String, IdentifierGeneratorDefinition> buildGenerators(
			XAnnotatedElement annElt,
			MetadataBuildingContext context) {

		final InFlightMetadataCollector metadataCollector = context.getMetadataCollector();
		final HashMap<String, IdentifierGeneratorDefinition> generators = new HashMap<>();

		TableGenerators tableGenerators = annElt.getAnnotation( TableGenerators.class );
		if ( tableGenerators != null ) {
			for ( TableGenerator tableGenerator : tableGenerators.value() ) {
				IdentifierGeneratorDefinition idGenerator = buildIdGenerator(
						tableGenerator,
						context
				);
				generators.put(
						idGenerator.getName(),
						idGenerator
				);
				metadataCollector.addIdentifierGenerator( idGenerator );
			}
		}

		SequenceGenerators sequenceGenerators = annElt.getAnnotation( SequenceGenerators.class );
		if ( sequenceGenerators != null ) {
			for ( SequenceGenerator sequenceGenerator : sequenceGenerators.value() ) {
				IdentifierGeneratorDefinition idGenerator = buildIdGenerator(
						sequenceGenerator,
						context
				);
				generators.put(
						idGenerator.getName(),
						idGenerator
				);
				metadataCollector.addIdentifierGenerator( idGenerator );
			}
		}

		TableGenerator tabGen = annElt.getAnnotation( TableGenerator.class );
		if ( tabGen != null ) {
			IdentifierGeneratorDefinition idGen = buildIdGenerator( tabGen, context );
			generators.put( idGen.getName(), idGen );
			metadataCollector.addIdentifierGenerator( idGen );

		}

		SequenceGenerator seqGen = annElt.getAnnotation( SequenceGenerator.class );
		if ( seqGen != null ) {
			IdentifierGeneratorDefinition idGen = buildIdGenerator( seqGen, context );
			generators.put( idGen.getName(), idGen );
			metadataCollector.addIdentifierGenerator( idGen );
		}

		GenericGenerator genGen = annElt.getAnnotation( GenericGenerator.class );
		if ( genGen != null ) {
			IdentifierGeneratorDefinition idGen = buildIdGenerator( genGen, context );
			generators.put( idGen.getName(), idGen );
			metadataCollector.addIdentifierGenerator( idGen );
		}

		return generators;
	}

	public static boolean isDefault(XClass clazz, MetadataBuildingContext context) {
		return context.getBootstrapContext().getReflectionManager().equals( clazz, void.class );
	}

	/**
	 * For the mapped entities build some temporary data-structure containing information about the
	 * inheritance status of a class.
	 *
	 * @param orderedClasses Order list of all annotated entities and their mapped superclasses
	 *
	 * @return A map of {@code InheritanceState}s keyed against their {@code XClass}.
	 */
	public static Map<XClass, InheritanceState> buildInheritanceStates(
			List<XClass> orderedClasses,
			MetadataBuildingContext buildingContext) {
		Map<XClass, InheritanceState> inheritanceStatePerClass = new HashMap<>( orderedClasses.size() );
		for ( XClass clazz : orderedClasses ) {
			final InheritanceState superclassState = getSuperclassInheritanceState( clazz, inheritanceStatePerClass );
			final InheritanceState state = new InheritanceState( clazz, inheritanceStatePerClass, buildingContext );
			if ( superclassState != null ) {
				//the classes are ordered thus preventing an NPE
				//FIXME if an entity has subclasses annotated @MappedSuperclass wo sub @Entity this is wrong
				superclassState.setHasSiblings( true );
				InheritanceState superEntityState = getInheritanceStateOfSuperEntity( clazz, inheritanceStatePerClass );
				state.setHasParents( superEntityState != null );
				logMixedInheritance( clazz, superclassState, state );
				if ( superclassState.getType() != null ) {
					state.setType( superclassState.getType() );
				}
			}
			inheritanceStatePerClass.put( clazz, state );
		}
		return inheritanceStatePerClass;
	}

	private static void logMixedInheritance(XClass clazz, InheritanceState superclassState, InheritanceState state) {
		if ( state.getType() != null && superclassState.getType() != null ) {
			final boolean nonDefault = InheritanceType.SINGLE_TABLE != state.getType();
			final boolean mixingStrategy = state.getType() != superclassState.getType();
			if ( nonDefault && mixingStrategy ) {
				LOG.invalidSubStrategy( clazz.getName() );
			}
		}
	}

	private static boolean hasAnnotationsOnIdClass(XClass idClass) {
		for ( XProperty property : idClass.getDeclaredProperties( XClass.ACCESS_FIELD ) ) {
			if ( hasTriggeringAnnotation(property) ) {
				return true;
			}
		}
		for ( XMethod method : idClass.getDeclaredMethods() ) {
			if ( hasTriggeringAnnotation(method) ) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasTriggeringAnnotation(XAnnotatedElement property) {
		return property.isAnnotationPresent(Column.class)
			|| property.isAnnotationPresent(OneToMany.class)
			|| property.isAnnotationPresent(ManyToOne.class)
			|| property.isAnnotationPresent(Id.class)
			|| property.isAnnotationPresent(GeneratedValue.class)
			|| property.isAnnotationPresent(OneToOne.class)
			|| property.isAnnotationPresent(ManyToMany.class);
	}

	private static void matchIgnoreNotFoundWithFetchType(
			String entity,
			String association,
			NotFoundAction notFoundAction,
			FetchType fetchType) {
		if ( notFoundAction != null && fetchType == FetchType.LAZY ) {
			LOG.ignoreNotFoundWithFetchTypeLazy( entity, association );
		}
	}

	private static class CustomIdGeneratorCreator implements IdentifierGeneratorCreator {
		private final Annotation generatorAnnotation;
		private final Member underlyingMember;

		public CustomIdGeneratorCreator(Annotation generatorAnnotation, XProperty idXProperty) {
			this.generatorAnnotation = generatorAnnotation;
			this.underlyingMember = HCANNHelper.getUnderlyingMember( idXProperty );
		}

		@Override
		public IdentifierGenerator createGenerator(CustomIdGeneratorCreationContext context) {
			final IdGeneratorType idGeneratorType =
					generatorAnnotation.annotationType().getAnnotation( IdGeneratorType.class );
			assert idGeneratorType != null;

			final Class<? extends IdentifierGenerator> generatorClass = idGeneratorType.value();
			try {
				return generatorClass
						.getConstructor( generatorAnnotation.annotationType(), Member.class, CustomIdGeneratorCreationContext.class )
						.newInstance( generatorAnnotation, underlyingMember, context );
			}
			catch (NoSuchMethodException e) {
				throw new HibernateException(
						"Unable to find appropriate constructor for @IdGeneratorType handling : " + generatorClass.getName(),
						e
				);
			}
			catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
				throw new HibernateException(
						"Unable to invoke constructor for @IdGeneratorType handling : " + generatorClass.getName(),
						e
				);
			}
		}
	}
}
