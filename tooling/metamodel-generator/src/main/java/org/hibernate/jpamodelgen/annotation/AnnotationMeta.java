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
import org.hibernate.jpamodelgen.validation.ProcessorSessionFactory;
import org.hibernate.jpamodelgen.validation.Validation;

import javax.lang.model.element.AnnotationMirror;
import java.util.List;

import static java.util.Collections.emptySet;
import static org.hibernate.jpamodelgen.util.TypeUtils.containsAnnotation;
import static org.hibernate.jpamodelgen.util.TypeUtils.getAnnotationMirror;

public abstract class AnnotationMeta implements Metamodel {

	void addAuxiliaryMembers() {
		addAuxiliaryMembersForAnnotation( Constants.NAMED_QUERY, "QUERY_" );
		addAuxiliaryMembersForRepeatableAnnotation( Constants.NAMED_QUERIES, "QUERY_" );
		addAuxiliaryMembersForAnnotation( Constants.NAMED_NATIVE_QUERY, "QUERY_" );
		addAuxiliaryMembersForRepeatableAnnotation( Constants.NAMED_NATIVE_QUERIES, "QUERY_" );
		addAuxiliaryMembersForAnnotation( Constants.SQL_RESULT_SET_MAPPING, "MAPPING_" );
		addAuxiliaryMembersForRepeatableAnnotation( Constants.SQL_RESULT_SET_MAPPINGS, "MAPPING_" );
		addAuxiliaryMembersForAnnotation( Constants.NAMED_ENTITY_GRAPH, "GRAPH_" );
		addAuxiliaryMembersForRepeatableAnnotation( Constants.NAMED_ENTITY_GRAPHS, "GRAPH_" );

		addAuxiliaryMembersForAnnotation( Constants.HIB_NAMED_QUERY, "QUERY_" );
		addAuxiliaryMembersForRepeatableAnnotation( Constants.HIB_NAMED_QUERIES, "QUERY_" );
		addAuxiliaryMembersForAnnotation( Constants.HIB_NAMED_NATIVE_QUERY, "QUERY_" );
		addAuxiliaryMembersForRepeatableAnnotation( Constants.HIB_NAMED_NATIVE_QUERIES, "QUERY_" );
		addAuxiliaryMembersForAnnotation( Constants.HIB_FETCH_PROFILE, "PROFILE_" );
		addAuxiliaryMembersForRepeatableAnnotation( Constants.HIB_FETCH_PROFILES, "PROFILE_" );
		addAuxiliaryMembersForAnnotation( Constants.HIB_FILTER_DEF, "FILTER_" );
		addAuxiliaryMembersForRepeatableAnnotation( Constants.HIB_FILTER_DEFS, "FILTER_" );
	}

	void checkNamedQueries() {
		boolean checkHql = containsAnnotation( getElement(), Constants.CHECK_HQL )
						|| containsAnnotation( getElement().getEnclosingElement(), Constants.CHECK_HQL );
		checkNamedQueriesForAnnotation( Constants.NAMED_QUERY, checkHql );
		checkNamedQueriesForRepeatableAnnotation( Constants.NAMED_QUERIES, checkHql );
		checkNamedQueriesForAnnotation( Constants.HIB_NAMED_QUERY, checkHql );
		checkNamedQueriesForRepeatableAnnotation( Constants.HIB_NAMED_QUERIES, checkHql );
	}

	private void checkNamedQueriesForAnnotation(String annotationName, boolean checkHql) {
		final AnnotationMirror mirror = getAnnotationMirror( getElement(), annotationName );
		if ( mirror != null ) {
			checkNamedQueriesForMirror( mirror, checkHql );
		}
	}

	private void checkNamedQueriesForRepeatableAnnotation(String annotationName, boolean checkHql) {
		final AnnotationMirror mirror = getAnnotationMirror( getElement(), annotationName );
		if ( mirror != null ) {
			mirror.getElementValues().forEach((key, value) -> {
				if ( key.getSimpleName().contentEquals("value") ) {
					List<? extends AnnotationMirror> values =
							(List<? extends AnnotationMirror>) value.getValue();
					for ( AnnotationMirror annotationMirror : values ) {
						checkNamedQueriesForMirror( annotationMirror, checkHql );
					}
				}
			});
		}
	}

	private void checkNamedQueriesForMirror(AnnotationMirror mirror, boolean checkHql) {
		mirror.getElementValues().forEach((key, value) -> {
			if ( key.getSimpleName().contentEquals("query") ) {
				final String hql = value.getValue().toString();
				Validation.validate(
						hql,
						false, checkHql,
						emptySet(), emptySet(),
						new ErrorHandler( getElement(), mirror, hql, getContext() ),
						ProcessorSessionFactory.create( getContext().getProcessingEnvironment() )
				);
			}
		});
	}

	private void addAuxiliaryMembersForRepeatableAnnotation(String annotationName, String prefix) {
		final AnnotationMirror mirror = getAnnotationMirror( getElement(), annotationName );
		if ( mirror != null ) {
			mirror.getElementValues().forEach((key, value) -> {
				if ( key.getSimpleName().contentEquals("value") ) {
					List<? extends AnnotationMirror> values =
							(List<? extends AnnotationMirror>) value.getValue();
					for ( AnnotationMirror annotationMirror : values ) {
						addAuxiliaryMembersForMirror( annotationMirror, prefix );
					}
				}
			});
		}
	}

	private void addAuxiliaryMembersForAnnotation(String annotationName, String prefix) {
		final AnnotationMirror mirror = getAnnotationMirror( getElement(), annotationName );
		if ( mirror != null ) {
			addAuxiliaryMembersForMirror( mirror, prefix );
		}
	}

	private void addAuxiliaryMembersForMirror(AnnotationMirror mirror, String prefix) {
		mirror.getElementValues().forEach((key, value) -> {
			if ( key.getSimpleName().contentEquals("name") ) {
				final String name = value.getValue().toString();
				putMember( prefix + name,
						new NameMetaAttribute( this, name, prefix ) );
			}
		});
	}

	abstract void putMember(String name, MetaAttribute nameMetaAttribute);
}
