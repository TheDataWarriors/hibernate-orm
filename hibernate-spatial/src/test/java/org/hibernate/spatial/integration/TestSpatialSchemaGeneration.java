/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.spatial.integration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.spatial.testing.domain.SpatialDomainModel;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.hibernate.tool.schema.TargetType;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DomainModel(modelDescriptorClasses = SpatialDomainModel.class)
@SessionFactory
public class TestSpatialSchemaGeneration {

	File output;

	@BeforeEach
	public void setup() throws IOException {
		output = File.createTempFile( "update_script", ".sql" );
		output.deleteOnExit();
	}

	@Test
	public void testCreatedSchemaHasGeometryField(SessionFactoryScope scope) throws IOException {

		MetadataImplementor metadata = scope.getMetadataImplementor();
		new SchemaExport()
				.setOverrideOutputFileContent()
				.setOutputFile( output.getAbsolutePath() )
				.setFormat( false )
				.execute( EnumSet.of( TargetType.SCRIPT ), SchemaExport.Action.BOTH, metadata );
		final List<String> sqlLines = Files.readAllLines( output.toPath(), Charset.defaultCharset() );
		String result = sqlLines.stream().collect( Collectors.joining( " " ) ).toLowerCase( Locale.ROOT );
		assertThat( result, stringContainsInOrder( List.of( "geometry", "geom" ) ) );
	}
}
