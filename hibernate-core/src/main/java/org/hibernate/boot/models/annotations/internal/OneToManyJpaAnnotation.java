/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.boot.models.annotations.internal;

import java.lang.annotation.Annotation;

import org.hibernate.boot.models.JpaAnnotations;
import org.hibernate.boot.models.annotations.spi.AttributeMarker;
import org.hibernate.models.spi.SourceModelBuildingContext;

import org.jboss.jandex.AnnotationInstance;

import jakarta.persistence.OneToMany;

import static org.hibernate.boot.models.internal.OrmAnnotationHelper.extractJandexValue;

@SuppressWarnings({ "ClassExplicitlyAnnotation", "unused" })
@jakarta.annotation.Generated("org.hibernate.orm.build.annotations.ClassGeneratorProcessor")
public class OneToManyJpaAnnotation implements OneToMany, AttributeMarker.Fetchable, AttributeMarker.Cascadeable {
	private java.lang.Class<?> targetEntity;
	private jakarta.persistence.CascadeType[] cascade;
	private jakarta.persistence.FetchType fetch;
	private String mappedBy;
	private boolean orphanRemoval;

	/**
	 * Used in creating dynamic annotation instances (e.g. from XML)
	 */
	public OneToManyJpaAnnotation(SourceModelBuildingContext modelContext) {
		this.targetEntity = void.class;
		this.cascade = new jakarta.persistence.CascadeType[0];
		this.fetch = jakarta.persistence.FetchType.LAZY;
		this.mappedBy = "";
		this.orphanRemoval = false;
	}

	/**
	 * Used in creating annotation instances from JDK variant
	 */
	public OneToManyJpaAnnotation(OneToMany annotation, SourceModelBuildingContext modelContext) {
		this.targetEntity = annotation.targetEntity();
		this.cascade = annotation.cascade();
		this.fetch = annotation.fetch();
		this.mappedBy = annotation.mappedBy();
		this.orphanRemoval = annotation.orphanRemoval();
	}

	/**
	 * Used in creating annotation instances from Jandex variant
	 */
	public OneToManyJpaAnnotation(AnnotationInstance annotation, SourceModelBuildingContext modelContext) {
		this.targetEntity = extractJandexValue( annotation, JpaAnnotations.ONE_TO_MANY, "targetEntity", modelContext );
		this.cascade = extractJandexValue( annotation, JpaAnnotations.ONE_TO_MANY, "cascade", modelContext );
		this.fetch = extractJandexValue( annotation, JpaAnnotations.ONE_TO_MANY, "fetch", modelContext );
		this.mappedBy = extractJandexValue( annotation, JpaAnnotations.ONE_TO_MANY, "mappedBy", modelContext );
		this.orphanRemoval = extractJandexValue(
				annotation,
				JpaAnnotations.ONE_TO_MANY,
				"orphanRemoval",
				modelContext
		);
	}

	@Override
	public Class<? extends Annotation> annotationType() {
		return OneToMany.class;
	}

	@Override
	public java.lang.Class<?> targetEntity() {
		return targetEntity;
	}

	public void targetEntity(java.lang.Class<?> value) {
		this.targetEntity = value;
	}


	@Override
	public jakarta.persistence.CascadeType[] cascade() {
		return cascade;
	}

	public void cascade(jakarta.persistence.CascadeType[] value) {
		this.cascade = value;
	}


	@Override
	public jakarta.persistence.FetchType fetch() {
		return fetch;
	}

	public void fetch(jakarta.persistence.FetchType value) {
		this.fetch = value;
	}


	@Override
	public String mappedBy() {
		return mappedBy;
	}

	public void mappedBy(String value) {
		this.mappedBy = value;
	}


	@Override
	public boolean orphanRemoval() {
		return orphanRemoval;
	}

	public void orphanRemoval(boolean value) {
		this.orphanRemoval = value;
	}


}
