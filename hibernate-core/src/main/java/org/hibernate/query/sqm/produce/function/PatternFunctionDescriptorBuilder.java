/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.produce.function;

import org.hibernate.query.sqm.function.PatternBasedSqmFunctionDescriptor;
import org.hibernate.query.sqm.function.SqmFunctionDescriptor;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.query.sqm.produce.function.internal.PatternRenderer;
import org.hibernate.type.BasicType;

/**
 * @author Steve Ebersole
 */
public class PatternFunctionDescriptorBuilder {
	private final SqmFunctionRegistry registry;
	private final String registrationKey;
	private final String pattern;
	private String argumentListSignature;

	private ArgumentsValidator argumentsValidator;
	private FunctionReturnTypeResolver returnTypeResolver;

	public PatternFunctionDescriptorBuilder(SqmFunctionRegistry registry, String registrationKey, String pattern) {
		this.registry = registry;
		this.registrationKey = registrationKey;
		this.pattern = pattern;
	}

	public PatternFunctionDescriptorBuilder setArgumentsValidator(ArgumentsValidator argumentsValidator) {
		this.argumentsValidator = argumentsValidator;
		return this;
	}

	public PatternFunctionDescriptorBuilder setExactArgumentCount(int exactArgumentCount) {
		return setArgumentsValidator( StandardArgumentsValidators.exactly( exactArgumentCount ) );
	}

	public PatternFunctionDescriptorBuilder setReturnTypeResolver(FunctionReturnTypeResolver returnTypeResolver) {
		this.returnTypeResolver = returnTypeResolver;
		return this;
	}

	public PatternFunctionDescriptorBuilder setInvariantType(BasicType invariantType) {
		setReturnTypeResolver( StandardFunctionReturnTypeResolvers.invariant( invariantType ) );
		return this;
	}

	public PatternFunctionDescriptorBuilder setArgumentListSignature(String argumentListSignature) {
		this.argumentListSignature = argumentListSignature;
		return this;
	}

	public SqmFunctionDescriptor register() {
		return registry.register( registrationKey, descriptor() );
	}

	public SqmFunctionDescriptor descriptor() {
		return new PatternBasedSqmFunctionDescriptor(
				new PatternRenderer( pattern ),
				argumentsValidator,
				returnTypeResolver,
				registrationKey,
				argumentListSignature
		);
	}
}
