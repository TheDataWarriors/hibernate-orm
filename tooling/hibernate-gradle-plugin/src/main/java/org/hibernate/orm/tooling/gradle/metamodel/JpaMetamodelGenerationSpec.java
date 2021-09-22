/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.tooling.gradle.metamodel;

import java.util.Arrays;
import jakarta.inject.Inject;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;

import org.hibernate.orm.tooling.gradle.HibernateOrmSpec;

/**
 * @author Steve Ebersole
 */
public class JpaMetamodelGenerationSpec {
	public static final String JPA_METAMODEL = "jpaMetamodel";
	public static final String DSL_NAME = JPA_METAMODEL;

	private final Property<Boolean> applyGeneratedAnnotation;
	private final SetProperty<String> suppressions;
	private final DirectoryProperty generationOutputDirectory;
	private final DirectoryProperty compileOutputDirectory;

	private final Provider<JavaVersion> targetJavaVersionAccess;

	@Inject
	@SuppressWarnings( "UnstableApiUsage" )
	public JpaMetamodelGenerationSpec(HibernateOrmSpec ormDsl, Project project) {
		applyGeneratedAnnotation = project.getObjects().property( Boolean.class );
		applyGeneratedAnnotation.convention( true );

		suppressions = project.getObjects().setProperty( String.class );
		suppressions.convention( Arrays.asList( "raw", "deprecation" ) );

		generationOutputDirectory = project.getObjects().directoryProperty();
		generationOutputDirectory.convention(
				project.getLayout().getBuildDirectory().dir( "generated/sources/" + JPA_METAMODEL )
		);

		compileOutputDirectory = project.getObjects().directoryProperty();
		compileOutputDirectory.convention(
				project.getLayout().getBuildDirectory().dir( "classes/java/" + JPA_METAMODEL )
		);

		targetJavaVersionAccess = project.provider(
				() -> {
					final JavaPluginConvention javaPluginConvention = project.getConvention().findPlugin( JavaPluginConvention.class );
					assert javaPluginConvention != null;
					final SourceSet sourceSet = javaPluginConvention.getSourceSets().getByName( SourceSet.MAIN_SOURCE_SET_NAME );
					final String compileTaskName = sourceSet.getCompileJavaTaskName();
					final JavaCompile compileTask = (JavaCompile) project.getTasks().getByName( compileTaskName );
					return JavaVersion.toVersion( compileTask.getTargetCompatibility() );
				}
		);
	}

	public Provider<JavaVersion> getTargetJavaVersionAccess() {
		return targetJavaVersionAccess;
	}

	public Property<Boolean> getApplyGeneratedAnnotation() {
		return applyGeneratedAnnotation;
	}

	public SetProperty<String> getSuppressions() {
		return suppressions;
	}

	public DirectoryProperty getGenerationOutputDirectory() {
		return generationOutputDirectory;
	}

	public DirectoryProperty getCompileOutputDirectory() {
		return compileOutputDirectory;
	}
}
