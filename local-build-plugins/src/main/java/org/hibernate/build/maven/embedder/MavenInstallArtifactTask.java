package org.hibernate.build.maven.embedder;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.ArrayList;

public abstract class MavenInstallArtifactTask extends DefaultTask {

	@ServiceReference
	abstract Property<MavenEmbedderService> getMavenEmbedderService();

	String artifactId;
	String pomFilePath;

	@InputFile
	abstract RegularFileProperty getArtifact();

	@TaskAction
	public void installArtifact() {
		getMavenEmbedderService().get().execute( constructTaskAndArgs() );
	}

	private String[] constructTaskAndArgs() {
		ArrayList<String> taskAndArgs = new ArrayList<String>();
		taskAndArgs.add("install:install-file");
		taskAndArgs.add("-DgroupId=" + getGroupId());
		taskAndArgs.add("-Dversion=" + getProjectVersion());
		taskAndArgs.add("-Dpackaging=jar");
		taskAndArgs.add("-DlocalRepositoryPath=" + getPathToLocalRepository());
		taskAndArgs.add("-Dfile=" + getPathToArtifact());
		taskAndArgs.add("-DartifactId=" + artifactId);
		if (pomFilePath != null) {
			taskAndArgs.add( "-DpomFile=" + pomFilePath );
		}
		return taskAndArgs.toArray(new String[0]);
	}

	private String getPathToArtifact() {
		return getArtifact().get().getAsFile().getAbsolutePath();
	}

	private String getPathToLocalRepository() {
		return getMavenEmbedderService()
				.get()
				.getParameters()
				.getMavenLocalDirectory()
				.getAsFile()
				.get()
				.getAbsolutePath();
	}

	private String getProjectVersion() {
		return getMavenEmbedderService().get().getParameters().getProjectVersion().get();
	}

	private String getGroupId() {
		return getProject().getGroup().toString();
	}

}

