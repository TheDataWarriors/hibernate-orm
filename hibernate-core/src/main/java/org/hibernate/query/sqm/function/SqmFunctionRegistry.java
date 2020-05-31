/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.function;

import java.util.AbstractMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.hibernate.query.sqm.produce.function.NamedFunctionDescriptorBuilder;
import org.hibernate.query.sqm.produce.function.PatternFunctionDescriptorBuilder;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.type.BasicType;

import org.jboss.logging.Logger;

import static java.lang.String.CASE_INSENSITIVE_ORDER;

/**
 * Defines a registry for {@link SqmFunctionDescriptor} instances
 *
 * @author Steve Ebersole
 */
public class SqmFunctionRegistry {
	private static final Logger log = Logger.getLogger( SqmFunctionRegistry.class );

	private final Map<String, SqmFunctionDescriptor> functionMap = new TreeMap<>( CASE_INSENSITIVE_ORDER );
	private final Map<String,String> alternateKeyMap = new TreeMap<>( CASE_INSENSITIVE_ORDER );

	public SqmFunctionRegistry() {
		log.trace( "SqmFunctionRegistry created" );
	}

	public Map<String, SqmFunctionDescriptor> getFunctions() {
		return functionMap;
	}

	public Stream<Map.Entry<String, SqmFunctionDescriptor>> getFunctionsByName() {
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
	public SqmFunctionDescriptor findFunctionDescriptor(String functionName) {
		SqmFunctionDescriptor found = null;

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
	 * Register a function descriptor by name
	 */
	public SqmFunctionDescriptor register(String registrationKey, SqmFunctionDescriptor function) {
		final SqmFunctionDescriptor priorRegistration = functionMap.put( registrationKey, function );
		log.debugf(
				"Registered SqmFunctionTemplate [%s] under %s; prior registration was %s",
				function,
				registrationKey,
				priorRegistration
		);
		alternateKeyMap.remove( registrationKey );
		return function;
	}

	/**
	 * Register a pattern-based descriptor by name.  Shortcut for building the descriptor
	 * via {@link #patternDescriptorBuilder} accepting its defaults.
	 */
	public SqmFunctionDescriptor registerPattern(String name, String pattern) {
		return patternDescriptorBuilder( name, pattern ).register();
	}

	/**
	 * Register a pattern-based descriptor by name and invariant return type.  Shortcut for building the descriptor
	 * via {@link #patternDescriptorBuilder} accepting its defaults.
	 */
	public SqmFunctionDescriptor registerPattern(String name, String pattern, BasicType returnType) {
		return patternDescriptorBuilder( name, pattern )
				.setInvariantType( returnType )
				.register();
	}

	/**
	 * Get a builder for creating and registering a pattern-based function descriptor.
	 *
	 * @param registrationKey The name under which the descriptor will get registered
	 * @param pattern The pattern defining the the underlying function call
	 *
	 * @return The builder
	 */
	public PatternFunctionDescriptorBuilder patternDescriptorBuilder(String registrationKey, String pattern) {
		return new PatternFunctionDescriptorBuilder( this, registrationKey, pattern );
	}

	/**
	 * Register a named descriptor by name.  Shortcut for building a descriptor via
	 * {@link #namedDescriptorBuilder} using the passed name as both the registration
	 * key and underlying SQL function name and accepting the builder's defaults.
	 *
	 * @param name The function name (and registration key)
	 */
	public SqmFunctionDescriptor registerNamed(String name) {
		return namedDescriptorBuilder( name ).register();
	}

	/**
	 * Register a named descriptor by name and invariant return type.  Shortcut for building
	 * a descriptor via {@link #namedDescriptorBuilder} using the passed name as both the
	 * registration key and underlying SQL function name and accepting the builder's defaults.
	 *
	 * @param name The function name (and registration key)
	 */
	public SqmFunctionDescriptor registerNamed(String name, BasicType returnType) {
		return namedDescriptorBuilder( name, name ).setInvariantType( returnType ).register();
	}

	/**
	 * Get a builder for creating and registering a name-based function descriptor
	 * using the passed name as both the registration key and underlying SQL
	 * function name
	 *
	 * @param name The function name (and registration key)
	 *
	 * @return The builder
	 */
	public NamedFunctionDescriptorBuilder namedDescriptorBuilder(String name) {
		return namedDescriptorBuilder( name, name );
	}

	/**
	 * Get a builder for creating and registering a name-based function descriptor.
	 *
	 * @param registrationKey The name under which the descriptor will get registered
	 * @param name The underlying SQL function name to use
	 *
	 * @return The builder
	 */
	public NamedFunctionDescriptorBuilder namedDescriptorBuilder(String registrationKey, String name) {
		return new NamedFunctionDescriptorBuilder( this, registrationKey, name );
	}

	public NamedFunctionDescriptorBuilder noArgsBuilder(String name) {
		return noArgsBuilder( name, name );
	}

	public NamedFunctionDescriptorBuilder noArgsBuilder(String registrationKey, String name) {
		return namedDescriptorBuilder( registrationKey, name )
				.setExactArgumentCount( 0 );
	}

	/**
	 * Specialized registration method for registering a named descriptor for functions
	 * expecting zero arguments.  Short-cut for building a named descriptor via
	 * {@link #namedDescriptorBuilder} specifying zero arguments and accepting the
	 * rest of the builder's defaults.
	 *
	 * @param name The function name (and registration key)
	 */
	public SqmFunctionDescriptor registerNoArgs(String name) {
		return registerNoArgs( name, name );
	}

	public SqmFunctionDescriptor registerNoArgs(String registrationKey, String name) {
		return noArgsBuilder( registrationKey, name ).register();
	}

	public SqmFunctionDescriptor registerNoArgs(String name, BasicType returnType) {
		return registerNoArgs( name, name, returnType );
	}

	public SqmFunctionDescriptor registerNoArgs(String registrationKey, String name, BasicType returnType) {
		return noArgsBuilder( registrationKey, name )
				.setInvariantType( returnType )
				.register();
	}

	public SqmFunctionDescriptor wrapInJdbcEscape(String name, SqmFunctionDescriptor wrapped) {
		final JdbcEscapeFunctionDescriptor wrapperTemplate = new JdbcEscapeFunctionDescriptor( name, wrapped );
		register( name, wrapperTemplate );
		return wrapperTemplate;
	}

	public void registerAlternateKey(String alternateKey, String mappedKey) {
		log.debugf( "Registering alternate key : %s -> %s", alternateKey, mappedKey );
		alternateKeyMap.put( alternateKey, mappedKey );
	}

	/**
	 * Register a nullary/unary function.
	 *
	 * i.e. a function which accepts 0-1 arguments.
	 */
	public MultipatternSqmFunctionDescriptor registerNullaryUnaryPattern(
			String name,
			BasicType type,
			String pattern0,
			String pattern1) {
		return registerPatterns( name, type, pattern0, pattern1 );
	}

	/**
	 * Register a unary/binary function.
	 *
	 * i.e. a function which accepts 1-2 arguments.
	 */
	public MultipatternSqmFunctionDescriptor registerUnaryBinaryPattern(
			String name,
			BasicType type,
			String pattern1,
			String pattern2) {
		return registerPatterns( name, type, null, pattern1, pattern2 );
	}

	/**
	 * Register a binary/ternary function.
	 *
	 * i.e. a function which accepts 2-3 arguments.
	 */
	public MultipatternSqmFunctionDescriptor registerBinaryTernaryPattern(
			String name,
			BasicType type,
			String pattern2,
			String pattern3) {
		return registerPatterns( name, type, null, null, pattern2, pattern3 );
	}

	/**
	 * Register a ternary/quaternary function.
	 *
	 * i.e. a function which accepts 3-4 arguments.
	 */
	public MultipatternSqmFunctionDescriptor registerTernaryQuaternaryPattern(
			String name,
			BasicType type,
			String pattern3,
			String pattern4) {
		return registerPatterns( name, type, null, null, null, pattern3, pattern4 );
	}

	private MultipatternSqmFunctionDescriptor registerPatterns(
			String name,
			BasicType type,
			String... patterns) {
		SqmFunctionDescriptor[] descriptors =
				new SqmFunctionDescriptor[patterns.length];
		for ( int i = 0; i < patterns.length; i++ ) {
			String pattern = patterns[i];
			if ( pattern != null ) {
				descriptors[i] =
						patternDescriptorBuilder( name, pattern )
								.setExactArgumentCount( i )
								.setInvariantType( type )
								.descriptor();
			}
		}

		MultipatternSqmFunctionDescriptor function = new MultipatternSqmFunctionDescriptor(
				name, descriptors,
				StandardFunctionReturnTypeResolvers.invariant( type )
		);
		register( name, function );
		return function;
	}

	/**
	 * Overlay the functions registered here on top of the
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
}
