/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.tool.schema.internal;

import java.io.FileWriter;
import java.io.IOException;

import org.hibernate.engine.jdbc.internal.Formatter;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.hibernate.tool.schema.spi.Target;

/**
 * @author Steve Ebersole
 */
public class TargetFileImpl implements Target {
	private FileWriter fileWriter;
	private Formatter formatter;
	private String delimiter;

	public TargetFileImpl(String outputFile, Formatter formatter, String delimiter) {
		try {
			this.fileWriter = new FileWriter( outputFile );
			this.formatter = formatter;
			this.delimiter = delimiter;
		}
		catch (IOException e) {
			throw new SchemaManagementException( "Unable to open FileWriter [" + outputFile + "]", e );
		}
	}

	@Override
	public boolean acceptsImportScriptActions() {
		return true;
	}

	@Override
	public void prepare() {
	}

	@Override
	public void accept(String action) {
		try {
			action = formatter.format(action);
			if (delimiter != null) {
				action += delimiter;
			}
			fileWriter.write( action);
			fileWriter.write( "\n" );
		}
		catch (IOException e) {
			throw new SchemaManagementException( "Unable to write to FileWriter", e );
		}
	}

	@Override
	public void release() {
		if ( fileWriter != null ) {
			try {
				fileWriter.close();
			}
			catch (IOException ignore) {
			}
		}
	}
}
