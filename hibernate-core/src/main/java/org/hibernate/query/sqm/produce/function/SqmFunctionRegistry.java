/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.produce.function;

import java.util.AbstractMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.hibernate.query.sqm.produce.function.spi.MultipatternSqmFunctionTemplate;
import org.hibernate.type.StandardBasicTypes;
import org.jboss.logging.Logger;

import static java.lang.String.CASE_INSENSITIVE_ORDER;

/**
 * Defines a registry for SQLFunction instances
 *
 * @author Steve Ebersole
 */
public class SqmFunctionRegistry {
	private static final Logger log = Logger.getLogger( SqmFunctionRegistry.class );

	private final Map<String,SqmFunctionTemplate> functionMap = new TreeMap<>( CASE_INSENSITIVE_ORDER );
	private final Map<String,String> alternateKeyMap = new TreeMap<>( CASE_INSENSITIVE_ORDER );

	public SqmFunctionRegistry() {
		log.tracef( "SqmFunctionRegistry created" );
	}

	public Stream<Map.Entry<String,SqmFunctionTemplate>> getFunctionsByName() {
		return Stream.concat(
				functionMap.entrySet().stream(),
				alternateKeyMap.entrySet().stream().map(
						entry -> new AbstractMap.SimpleEntry<>(
								entry.getKey(),
								functionMap.get( entry.getValue() )
						)
				)
		);
	}

	/**
	 * Find a SqmFunctionTemplate by name.  Returns {@code null} if
	 * no such function is found.
	 */
	public SqmFunctionTemplate findFunctionTemplate(String functionName) {
		SqmFunctionTemplate found = null;

		final String alternateKeyResolution = alternateKeyMap.get( functionName );
		if ( alternateKeyResolution != null ) {
			found = functionMap.get( alternateKeyResolution );
		}

		if ( found == null ) {
			found = functionMap.get( functionName );
		}

		return found;
	}

	/**
	 * Register a function template by name
	 */
	public SqmFunctionTemplate register(String registrationKey, SqmFunctionTemplate function) {
		final SqmFunctionTemplate priorRegistration = functionMap.put( registrationKey, function );
		log.debugf(
				"Registered SqmFunctionTemplate [%s] under %s; prior registration was %s",
				function,
				registrationKey,
				priorRegistration
		);

		return function;
	}

	/**
	 * Register a pattern-based template by name.  Shortcut for building the template
	 * via {@link #patternTemplateBuilder} accepting its defaults.
	 */
	public SqmFunctionTemplate registerPattern(String name, String pattern) {
		return patternTemplateBuilder( name, pattern ).register();
	}

	/**
	 * Register a pattern-based template by name and invariant return type.  Shortcut for building the template
	 * via {@link #patternTemplateBuilder} accepting its defaults.
	 */
	public SqmFunctionTemplate registerPattern(String name, String pattern, StandardBasicTypes.StandardBasicType<?> returnType) {
		return patternTemplateBuilder( name, pattern )
				.setInvariantType( returnType )
				.register();
	}

	/**
	 * Get a builder for creating and registering a pattern-based function template.
	 *
	 * @param registrationKey The name under which the template will get registered
	 * @param pattern The pattern defining the the underlying function call
	 *
	 * @return The builder
	 */
	public PatternFunctionTemplateBuilder patternTemplateBuilder(String registrationKey, String pattern) {
		return new PatternFunctionTemplateBuilder( this, registrationKey, pattern );
	}

	/**
	 * Register a named template by name.  Shortcut for building a template via
	 * {@link #namedTemplateBuilder} using the passed name as both the registration
	 * key and underlying SQL function name and accepting the builder's defaults.
	 *
	 * @param name The function name (and registration key)
	 */
	public SqmFunctionTemplate registerNamed(String name) {
		return namedTemplateBuilder( name ).register();
	}

	/**
	 * Register a named template by name and invariant return type.  Shortcut for building
	 * a template via {@link #namedTemplateBuilder} using the passed name as both the
	 * registration key and underlying SQL function name and accepting the builder's defaults.
	 *
	 * @param name The function name (and registration key)
	 */
	public SqmFunctionTemplate registerNamed(String name, StandardBasicTypes.StandardBasicType<?> returnType) {
		return namedTemplateBuilder( name ).setInvariantType( returnType ).register();
	}

	/**
	 * Get a builder for creating and registering a name-based function template
	 * using the passed name as both the registration key and underlying SQL
	 * function name
	 *
	 * @param name The function name (and registration key)
	 *
	 * @return The builder
	 */
	public NamedFunctionTemplateBuilder namedTemplateBuilder(String name) {
		return namedTemplateBuilder( name, name );
	}

	/**
	 * Get a builder for creating and registering a name-based function template.
	 *
	 * @param registrationKey The name under which the template will get registered
	 * @param name The underlying SQL function name to use
	 *
	 * @return The builder
	 */
	public NamedFunctionTemplateBuilder namedTemplateBuilder(String registrationKey, String name) {
		return new NamedFunctionTemplateBuilder( this, registrationKey, name );
	}

	public NamedFunctionTemplateBuilder noArgsBuilder(String name) {
		return noArgsBuilder( name, name );
	}

	public NamedFunctionTemplateBuilder noArgsBuilder(String registrationKey, String name) {
		return namedTemplateBuilder( registrationKey, name )
				.setExactArgumentCount( 0 );
	}

	public VarArgsFunctionTemplateBuilder varArgsBuilder(String registrationKey, String begin, String sep, String end) {
		return new VarArgsFunctionTemplateBuilder( this, registrationKey, begin, sep, end );
	}

	/**
	 * Specialized registration method for registering a named template for functions
	 * expecting zero arguments.  Short-cut for building a named template via
	 * {@link #namedTemplateBuilder} specifying zero arguments and accepting the
	 * rest of the builder's defaults.
	 *
	 * @param name The function name (and registration key)
	 */
	public SqmFunctionTemplate registerNoArgs(String name) {
		return registerNoArgs( name, name );
	}

	public SqmFunctionTemplate registerNoArgs(String registrationKey, String name) {
		return noArgsBuilder( registrationKey, name ).register();
	}

	public SqmFunctionTemplate registerNoArgs(String name, StandardBasicTypes.StandardBasicType<?> returnType) {
		return registerNoArgs( name, name, returnType );
	}

	public SqmFunctionTemplate registerNoArgs(String registrationKey, String name, StandardBasicTypes.StandardBasicType<?> returnType) {
		return noArgsBuilder( registrationKey, name )
				.setInvariantType( returnType )
				.register();
	}

	public SqmFunctionTemplate registerVarArgs(String registrationKey, StandardBasicTypes.StandardBasicType<?> returnType, String begin, String sep, String end) {
		return varArgsBuilder( registrationKey, begin, sep, end )
				.setInvariantType( returnType )
				.register();
	}

	public SqmFunctionTemplate wrapInJdbcEscape(String registrationKey, SqmFunctionTemplate wrapped) {
		final JdbcFunctionEscapeWrapperTemplate wrapperTemplate = new JdbcFunctionEscapeWrapperTemplate( wrapped );
		register( registrationKey, wrapperTemplate );
		return wrapperTemplate;
	}

	public void registerAlternateKey(String alternateKey, String mappedKey) {
		log.debugf( "Registering alternate key : %s -> %s", alternateKey, mappedKey );
		alternateKeyMap.put( alternateKey, mappedKey );
	}

	/**
	 * Overlay (put on top) the functions registered here on top of the
	 * incoming registry, potentially overriding its registrations
	 */
	public void overlay(SqmFunctionRegistry registryToOverly) {
		// NOTE : done in this "direction" as it is easier to access the
		//		functionMap directly in performing this operation
		functionMap.forEach( registryToOverly::register );
		alternateKeyMap.forEach( registryToOverly::registerAlternateKey );
	}

	public void close() {
		functionMap.clear();
		alternateKeyMap.clear();
	}

	/**
	 * Register a nullary/unary function.
	 *
	 * i.e. a function which accepts 0-1 arguments.
	 */
	public MultipatternSqmFunctionTemplate registerNullaryUnaryPattern(
			String name,
			StandardBasicTypes.StandardBasicType<?> type,
			String pattern0,
			String pattern1) {
		return registerPatterns( name, type, pattern0, pattern1 );
	}

	/**
	 * Register a unary/binary function.
	 *
	 * i.e. a function which accepts 1-2 arguments.
	 */
	public MultipatternSqmFunctionTemplate registerUnaryBinaryPattern(
			String name,
			StandardBasicTypes.StandardBasicType<?> type,
			String pattern1,
			String pattern2) {
		return registerPatterns( name, type, null, pattern1, pattern2 );
	}

	/**
	 * Register a binary/ternary function.
	 *
	 * i.e. a function which accepts 2-3 arguments.
	 */
	public MultipatternSqmFunctionTemplate registerBinaryTernaryPattern(
			String name,
			StandardBasicTypes.StandardBasicType<?> type,
			String pattern2,
			String pattern3) {
		return registerPatterns( name, type, null, null, pattern2, pattern3 );
	}

	/**
	 * Register a ternary/quaternary function.
	 *
	 * i.e. a function which accepts 3-4 arguments.
	 */
	public MultipatternSqmFunctionTemplate registerTernaryQuaternaryPattern(
			String name,
			StandardBasicTypes.StandardBasicType<?> type,
			String pattern3,
			String pattern4) {
		return registerPatterns( name, type, null, null, null, pattern3, pattern4 );
	}

	private MultipatternSqmFunctionTemplate registerPatterns(
			String name,
			StandardBasicTypes.StandardBasicType<?> type,
			String... patterns) {
		SqmFunctionTemplate[] templates =
				new SqmFunctionTemplate[patterns.length];
		for ( int i = 0; i < patterns.length; i++ ) {
			String pattern = patterns[i];
			if ( pattern != null ) {
				templates[i] =
						patternTemplateBuilder(name, pattern)
								.setExactArgumentCount(i)
								.setInvariantType(type)
								.template();
			}
		}

		MultipatternSqmFunctionTemplate function = new MultipatternSqmFunctionTemplate(templates);
		register( name, function);
		return function;
	}

}
