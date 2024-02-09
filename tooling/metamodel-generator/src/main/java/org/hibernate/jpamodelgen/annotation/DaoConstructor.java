/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpamodelgen.annotation;

import org.hibernate.jpamodelgen.model.MetaAttribute;
import org.hibernate.jpamodelgen.model.Metamodel;
import org.hibernate.jpamodelgen.util.Constants;

/**
 * @author Gavin King
 */
public class DaoConstructor implements MetaAttribute {
	private final Metamodel annotationMetaEntity;
	private final String constructorName;
	private final String methodName;
	private final String sessionTypeName;
	private final String sessionVariableName;
	private final boolean addInjectAnnotation;
	private final boolean addNonnullAnnotation;

	public DaoConstructor(
			Metamodel annotationMetaEntity,
			String constructorName,
			String methodName,
			String sessionTypeName,
			String sessionVariableName,
			boolean addInjectAnnotation,
			boolean addNonnullAnnotation) {
		this.annotationMetaEntity = annotationMetaEntity;
		this.constructorName = constructorName;
		this.methodName = methodName;
		this.sessionTypeName = sessionTypeName;
		this.sessionVariableName = sessionVariableName;
		this.addInjectAnnotation = addInjectAnnotation;
		this.addNonnullAnnotation = addNonnullAnnotation;
	}

	@Override
	public boolean hasTypedAttribute() {
		return true;
	}

	@Override
	public boolean hasStringAttribute() {
		return false;
	}

	@Override
	public String getAttributeDeclarationString() {
		StringBuilder declaration = new StringBuilder();
		declaration
				.append("\nprivate final ");
		notNull( declaration );
		declaration
				.append(annotationMetaEntity.importType(sessionTypeName))
				.append(" ")
				.append(sessionVariableName)
				.append(";")
				.append("\n");
		inject( declaration );
		declaration
				.append("\npublic ")
				.append(constructorName)
				.append("(");
		notNull( declaration );
		declaration
				.append(annotationMetaEntity.importType(sessionTypeName))
				.append(" ")
				.append(sessionVariableName)
				.append(") {")
				.append("\n\tthis.")
				.append(sessionVariableName)
				.append(" = ")
				.append(sessionVariableName)
				.append(";")
				.append("\n}")
				.append("\n\n")
				.append("public ");
		notNull( declaration );
		declaration
				.append(annotationMetaEntity.importType(sessionTypeName))
				.append(" ")
				.append(methodName)
				.append("() {")
				.append("\n\treturn ")
				.append(sessionVariableName)
				.append(";")
				.append("\n}");
		return declaration.toString();
	}

	private void inject(StringBuilder declaration) {
		if ( addInjectAnnotation ) {
			declaration
					.append("\n@")
					.append(annotationMetaEntity.importType("jakarta.inject.Inject"));
		}
	}

	private void notNull(StringBuilder declaration) {
		if ( addNonnullAnnotation ) {
			declaration
					.append('@')
					.append(annotationMetaEntity.importType("jakarta.annotation.Nonnull"))
					.append(' ');
		}
	}

	@Override
	public String getAttributeNameDeclarationString() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getMetaType() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getPropertyName() {
		return methodName;
	}

	@Override
	public String getTypeDeclaration() {
		return Constants.ENTITY_MANAGER;
	}

	@Override
	public Metamodel getHostingEntity() {
		return annotationMetaEntity;
	}
}
