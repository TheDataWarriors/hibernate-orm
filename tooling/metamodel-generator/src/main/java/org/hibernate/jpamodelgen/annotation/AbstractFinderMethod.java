/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpamodelgen.annotation;

import org.hibernate.internal.util.StringHelper;
import org.hibernate.jpamodelgen.model.Metamodel;
import org.hibernate.jpamodelgen.util.Constants;

import java.util.List;
import java.util.Locale;

import static org.hibernate.jpamodelgen.util.StringUtil.getUpperUnderscoreCaseFromLowerCamelCase;

/**
 * @author Gavin King
 */
public abstract class AbstractFinderMethod extends AbstractQueryMethod  {
	final String entity;
	final List<String> fetchProfiles;

	public AbstractFinderMethod(
			Metamodel annotationMetaEntity,
			String methodName,
			String entity,
			boolean belongsToDao,
			String sessionType,
			String sessionName,
			List<String> fetchProfiles,
			List<String> paramNames,
			List<String> paramTypes,
			boolean addNonnullAnnotation) {
		super( annotationMetaEntity, methodName, paramNames, paramTypes, sessionType, sessionName, belongsToDao, addNonnullAnnotation );
		this.entity = entity;
		this.fetchProfiles = fetchProfiles;
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
	public String getTypeDeclaration() {
		return entity;
	}

	abstract boolean isId();

	@Override
	public String getAttributeNameDeclarationString() {
		return new StringBuilder()
				.append("public static final String ")
				.append(constantName())
				.append(" = \"!")
				.append(annotationMetaEntity.getQualifiedName())
				.append('.')
				.append(methodName)
				.append("(")
				.append(parameterList())
				.append(")")
				.append("\";")
				.toString();
	}

	String constantName() {
		return getUpperUnderscoreCaseFromLowerCamelCase(methodName) + "_BY_"
				+ paramNames.stream()
				.map(StringHelper::unqualify)
				.map(name -> name.toUpperCase(Locale.ROOT))
				.reduce((x,y) -> x + "_AND_" + y)
				.orElse("");
	}

	void comment(StringBuilder declaration) {
		declaration
				.append("\n/**")
				.append("\n * Find ")
				.append("{@link ")
				.append(annotationMetaEntity.importType(entity))
				.append("} by ");
		int paramCount = paramNames.size();
		for (int i = 0; i < paramCount; i++) {
			String param = paramNames.get(i);
			if ( i>0 ) {
				if ( i + 1 == paramCount) {
					declaration
							.append(paramCount>2 ? ", and " : " and "); //Oxford comma
				}
				else {
					declaration
							.append(", ");
				}
			}
			declaration
					.append("{@link ")
					.append(annotationMetaEntity.importType(entity))
					.append('#')
					.append(param)
					.append(' ')
					.append(param)
					.append("}");
		}
		declaration
				.append('.')
				.append("\n *")
				.append("\n * @see ")
				.append(annotationMetaEntity.getQualifiedName())
				.append("#")
				.append(methodName)
				.append("(")
				.append(parameterList())
				.append(")");
//		declaration
//				.append("\n *");
//		for (String param : paramNames) {
//			declaration
//					.append("\n * @see ")
//					.append(annotationMetaEntity.importType(entity))
//					.append('#')
//					.append(param);
//		}
		declaration
				.append("\n **/\n");
	}

	void unwrapSession(StringBuilder declaration) {
		if ( usingEntityManager ) {
			declaration
					.append(".unwrap(")
					.append(annotationMetaEntity.importType(Constants.HIB_SESSION))
					.append(".class)\n\t\t\t");
		}
	}

	void enableFetchProfile(StringBuilder declaration) {
//		if ( !usingEntityManager ) {
//			declaration
//					.append("\n\t\t\t.enableFetchProfile(")
//					.append(constantName())
//					.append(")");
//		}
		for ( String profile : fetchProfiles ) {
			declaration
					.append("\n\t\t\t.enableFetchProfile(")
					.append(profile)
					.append(")");
		}
	}

	void preamble(StringBuilder declaration) {
		modifiers( declaration );
		entityType( declaration );
		declaration
				.append(" ")
				.append(methodName);
		parameters( declaration) ;
		declaration
				.append(" {")
				.append("\n\treturn ")
				.append(sessionName);
	}

	private void entityType(StringBuilder declaration) {
		if ( reactive ) {
			declaration
					.append(annotationMetaEntity.importType(Constants.UNI))
					.append('<');
		}
		declaration
				.append(annotationMetaEntity.importType(entity));
		if ( reactive ) {
			declaration
					.append('>');
		}
	}

	void modifiers(StringBuilder declaration) {
		declaration
				.append(belongsToDao ? "@Override\npublic " : "public static ");
	}

	void parameters(StringBuilder declaration) {
		declaration
				.append("(");
		sessionParameter( declaration );
		for ( int i = 0; i < paramNames.size(); i ++ ) {
			if ( !belongsToDao || i > 0 ) {
				declaration
						.append(", ");
			}
			if ( isId() ) {
				notNull( declaration );
			}
			declaration
					.append(annotationMetaEntity.importType(paramTypes.get(i)))
					.append(" ")
					.append(paramNames.get(i));
		}
		declaration
				.append(')');
	}
}
