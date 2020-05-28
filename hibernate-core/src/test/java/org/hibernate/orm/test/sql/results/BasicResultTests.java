/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.sql.results;

import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.results.graph.DomainResultGraphPrinter;

import org.hibernate.testing.orm.domain.StandardDomainModel;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

/**
 * @author Steve Ebersole
 */
@SuppressWarnings("WeakerAccess")
@DomainModel( standardModels = StandardDomainModel.GAMBIT )
@SessionFactory( exportSchema = false )
public class BasicResultTests extends AbstractResultTests {
	@Test
	public void simpleTest(SessionFactoryScope scope) {
		final SelectStatement sqlAst = interpret(
				"select s.id, s.someString, s.someLong from SimpleEntity s",
				scope.getSessionFactory()
		);
		DomainResultGraphPrinter.logDomainResultGraph( sqlAst.getDomainResultDescriptors() );

	}
}
