/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpamodelgen.annotation;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.jpamodelgen.model.Metamodel;
import org.hibernate.jpamodelgen.util.Constants;
import org.hibernate.query.Order;
import org.hibernate.query.Page;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.hibernate.jpamodelgen.util.StringUtil.getUpperUnderscoreCaseFromLowerCamelCase;

/**
 * @author Gavin King
 */
public class QueryMethod extends AbstractQueryMethod {
	private final String queryString;
	private final @Nullable String returnTypeName;
	private final @Nullable String containerTypeName;
	private final boolean isNative;

	public QueryMethod(
			Metamodel annotationMetaEntity,
			String methodName,
			String queryString,
			@Nullable
			String returnTypeName,
			@Nullable
			String containerTypeName,
			List<String> paramNames,
			List<String> paramTypes,
			boolean isNative,
			boolean belongsToDao,
			String sessionType,
			String sessionName,
			boolean addNonnullAnnotation) {
		super( annotationMetaEntity, methodName, paramNames, paramTypes, sessionType, sessionName, belongsToDao, addNonnullAnnotation );
		this.queryString = queryString;
		this.returnTypeName = returnTypeName;
		this.containerTypeName = containerTypeName;
		this.isNative = isNative;
	}

	@Override
	public boolean hasTypedAttribute() {
		return true;
	}

	@Override
	public boolean hasStringAttribute() {
		return true;
	}

	@Override
	public String getAttributeDeclarationString() {
		final List<String> paramTypes = parameterTypes();
		final StringBuilder returnType = returnType();
		final StringBuilder declaration = new StringBuilder();
		comment( declaration );
		modifiers( paramTypes, declaration );
		declaration
				.append(returnType)
				.append(" ")
				.append(methodName);
		parameters( paramTypes, declaration );
		declaration
				.append(" {")
				.append("\n\treturn ");
		if ( isNative && returnTypeName != null && containerTypeName == null
				&& usingEntityManager) {
			// EntityManager.createNativeQuery() does not return TypedQuery,
			// so we need to cast to the entity type
			declaration.append("(")
					.append(returnType)
					.append(") ");
		}
		declaration
				.append(sessionName)
				.append(isNative ? ".createNativeQuery" : ".createQuery")
				.append("(")
				.append(getConstantName());
		if ( returnTypeName != null ) {
			declaration
					.append(", ")
					.append(annotationMetaEntity.importType(returnTypeName))
					.append(".class");
		}
		declaration.append(")");
		boolean unwrapped = setParameters( paramTypes, declaration );
		if ( containerTypeName == null) {
			declaration
					.append("\n\t\t\t.getSingleResult()");
		}
		else if ( containerTypeName.equals(Constants.LIST) ) {
			declaration
					.append("\n\t\t\t.getResultList()");
		}
		else {
			if ( usingEntityManager && !unwrapped
					&& ( containerTypeName.startsWith("org.hibernate")
						|| isNative && returnTypeName != null ) ) {
				declaration
						.append("\n\t\t\t.unwrap(")
						.append(annotationMetaEntity.importType(containerTypeName))
						.append(".class)");

			}
		}
		declaration.append(";\n}");
		return declaration.toString();
	}

	private boolean setParameters(List<String> paramTypes, StringBuilder declaration) {
		boolean unwrapped = !usingEntityManager;
		for (int i = 1; i <= paramNames.size(); i++) {
			final String paramName = paramNames.get(i-1);
			final String paramType = paramTypes.get(i-1);
			if ( queryString.contains(":" + paramName) ) {
				setNamedParameter( declaration, paramName );
			}
			else if ( queryString.contains("?" + i) ) {
				setOrdinalParameter( declaration, i, paramName );
			}
			else if ( isPageParam(paramType) ) {
				setPage( declaration, paramName );
			}
			else if ( isOrderParam(paramType) ) {
				unwrapped = setOrder( declaration, unwrapped, paramName, paramType );
			}
		}
		return unwrapped;
	}

	private static void setOrdinalParameter(StringBuilder declaration, int i, String paramName) {
		declaration
				.append("\n\t\t\t.setParameter(")
				.append(i)
				.append(", ")
				.append(paramName)
				.append(")");
	}

	private static void setNamedParameter(StringBuilder declaration, String paramName) {
		declaration
				.append("\n\t\t\t.setParameter(\"")
				.append(paramName)
				.append("\", ")
				.append(paramName)
				.append(")");
	}

	private void setPage(StringBuilder declaration, String paramName) {
		if ( usingEntityManager ) {
			declaration
					.append("\n\t\t\t.setFirstResult(")
					.append(paramName)
					.append(".getFirstResult())")
					.append("\n\t\t\t.setMaxResults(")
					.append(paramName)
					.append(".getMaxResults())");
		}
		else {
			declaration
					.append("\n\t\t\t.setPage(")
					.append(paramName)
					.append(")");
		}
	}

	private boolean setOrder(StringBuilder declaration, boolean unwrapped, String paramName, String paramType) {
		unwrap( declaration, unwrapped );
		if ( paramType.endsWith("...") ) {
			declaration
					.append("\n\t\t\t.setOrder(")
					.append(annotationMetaEntity.importType(Constants.LIST))
					.append(".of(")
					.append(paramName)
					.append("))");
		}
		else {
			declaration
					.append("\n\t\t\t.setOrder(")
					.append(paramName)
					.append(")");
		}
		return true;
	}

	private void parameters(List<String> paramTypes, StringBuilder declaration) {
		declaration
				.append("(");
		sessionParameter( declaration );
		for ( int i = 0; i < paramNames.size(); i++ ) {
			if ( !belongsToDao || i > 0 ) {
				declaration
						.append(", ");
			}
			final String type = paramTypes.get(i);
			final String paramType = returnTypeName != null
					? type.replace(returnTypeName, annotationMetaEntity.importType(returnTypeName))
					: type;
			declaration
					.append(annotationMetaEntity.importType(paramType))
					.append(" ")
					.append(paramNames.get(i));
		}
		declaration
				.append(")");
	}

	private StringBuilder returnType() {
		StringBuilder type = new StringBuilder();
		boolean returnsUni = reactive
				&& (containerTypeName == null || Constants.LIST.equals(containerTypeName));
		if ( returnsUni ) {
			type.append(annotationMetaEntity.importType(Constants.UNI)).append('<');
		}
		if ( containerTypeName != null ) {
			type.append(annotationMetaEntity.importType(containerTypeName));
			if ( returnTypeName != null ) {
				type.append("<").append(annotationMetaEntity.importType(returnTypeName)).append(">");
			}
		}
		else if ( returnTypeName != null )  {
			type.append(annotationMetaEntity.importType(returnTypeName));
		}
		if ( returnsUni ) {
			type.append('>');
		}
		return type;
	}

	private List<String> parameterTypes() {
		return paramTypes.stream()
				.map(paramType -> isOrderParam(paramType) && paramType.endsWith("[]")
						? paramType.substring(0, paramType.length() - 2) + "..."
						: paramType)
				.collect(toList());
	}

	private void comment(StringBuilder declaration) {
		declaration
				.append("\n/**\n * @see ")
				.append(annotationMetaEntity.getQualifiedName())
				.append("#")
				.append(methodName)
				.append("(")
				.append(parameterList())
				.append(")")
				.append("\n **/\n");
	}

	private void modifiers(List<String> paramTypes, StringBuilder declaration) {
		boolean hasVarargs = paramTypes.stream().anyMatch(ptype -> ptype.endsWith("..."));
		if ( hasVarargs ) {
			declaration
					.append("@SafeVarargs\n");
		}
		if ( belongsToDao ) {
			declaration
					.append("@Override\npublic ");
			if ( hasVarargs ) {
				declaration
						.append("final ");
			}
		}
		else {
			declaration
					.append("public static ");
		}
	}

	static boolean isPageParam(String parameterType) {
		return Page.class.getName().equals(parameterType);
	}

	static boolean isOrderParam(String parameterType) {
		return parameterType.startsWith(Order.class.getName())
			|| parameterType.startsWith(List.class.getName() + "<" + Order.class.getName());
	}

	private void unwrap(StringBuilder declaration, boolean unwrapped) {
		if ( !unwrapped ) {
			declaration
					.append("\n\t\t\t.unwrap(")
					.append(annotationMetaEntity.importType(Constants.HIB_SELECTION_QUERY))
					.append(".class)");
		}
	}

	@Override
	public String getAttributeNameDeclarationString() {
		return new StringBuilder()
				.append("static final String ")
				.append(getConstantName())
				.append(" = \"")
				.append(queryString)
				.append("\";")
				.toString();
	}

	private String getConstantName() {
		String stem = getUpperUnderscoreCaseFromLowerCamelCase(methodName);
		if ( paramTypes.isEmpty() ) {
			return stem;
		}
		else {
			return stem + "_"
					+ paramTypes.stream()
							.filter(name -> !isPageParam(name) && !isOrderParam(name))
							.map(StringHelper::unqualify)
							.reduce((x,y) -> x + '_' + y)
							.orElse("");
		}
	}

	public String getTypeDeclaration() {
		return Constants.QUERY;
	}
}
