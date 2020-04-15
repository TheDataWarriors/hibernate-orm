/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.boot.model.process.internal;

import java.util.function.Function;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.model.convert.spi.BasicValueConverter;
import org.hibernate.type.BasicType;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.java.MutabilityPlan;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * @author Steve Ebersole
 */
public class NamedBasicTypeResolution<J> implements BasicValue.Resolution<J> {
	private final JavaTypeDescriptor<J> domainJtd;

	private final BasicType basicType;

	private final BasicValueConverter valueConverter;
	private final MutabilityPlan<J> mutabilityPlan;

	public NamedBasicTypeResolution(
			JavaTypeDescriptor<J> domainJtd,
			BasicType basicType,
			BasicValueConverter valueConverter,
			Function<TypeConfiguration, MutabilityPlan> explicitMutabilityPlanAccess,
			MetadataBuildingContext context) {
		this.domainJtd = domainJtd;

		this.basicType = basicType;

		// named type cannot have converter applied
		this.valueConverter = null;
		// todo (6.0) : does it even make sense to allow a combo of explicit Type and a converter?
//		this.valueConverter = valueConverter;

		final MutabilityPlan explicitPlan = explicitMutabilityPlanAccess != null
				? explicitMutabilityPlanAccess.apply( context.getBootstrapContext().getTypeConfiguration() )
				: null;
		this.mutabilityPlan = explicitPlan != null
				? explicitPlan
				: domainJtd.getMutabilityPlan();
	}

	@Override
	public JdbcMapping getJdbcMapping() {
		return basicType;
	}

	@Override
	public BasicType getLegacyResolvedBasicType() {
		return basicType;
	}

	@Override
	public JavaTypeDescriptor<J> getDomainJavaDescriptor() {
		return domainJtd;
	}

	@Override
	public JavaTypeDescriptor<?> getRelationalJavaDescriptor() {
		return basicType.getJavaTypeDescriptor();
	}

	@Override
	public SqlTypeDescriptor getRelationalSqlTypeDescriptor() {
		return basicType.getSqlTypeDescriptor();
	}

	@Override
	public BasicValueConverter getValueConverter() {
		return valueConverter;
	}

	@Override
	public MutabilityPlan<J> getMutabilityPlan() {
		return mutabilityPlan;
	}
}
