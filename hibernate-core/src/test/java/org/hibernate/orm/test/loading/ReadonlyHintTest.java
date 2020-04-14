package org.hibernate.orm.test.loading;

import java.util.Collections;
import javax.persistence.Entity;
import javax.persistence.Id;

import org.hibernate.jpa.QueryHints;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@DomainModel(
		annotatedClasses = {
				ReadonlyHintTest.SimpleEntity.class
		}
)
@SessionFactory
@TestForIssue( jiraKey = "HHH-11958" )
public class ReadonlyHintTest {

	private static final String ORIGINAL_NAME = "original";
	private static final String CHANGED_NAME = "changed";

	@BeforeEach
	void setUp(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			SimpleEntity entity = new SimpleEntity();
			entity.id = 1L;
			entity.name = ORIGINAL_NAME;
			session.persist( entity );
		} );
	}

	@Test
	void testWithReadOnlyHint(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			SimpleEntity fetchedEntity = session.find( SimpleEntity.class, 1L, Collections.singletonMap( QueryHints.HINT_READONLY, true ) );
			fetchedEntity.name = CHANGED_NAME;
		} );

		scope.inTransaction( session -> {
			SimpleEntity fetchedEntity = session.find( SimpleEntity.class, 1L );
			assertThat(fetchedEntity.name, is( ORIGINAL_NAME ) );
		} );
	}

	@Test
	void testWithoutReadOnlyHint(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			SimpleEntity fetchedEntity = session.find( SimpleEntity.class, 1L );
			fetchedEntity.name = CHANGED_NAME;
		} );

		scope.inTransaction( session -> {
			SimpleEntity fetchedEntity = session.find( SimpleEntity.class, 1L );
			assertThat(fetchedEntity.name, is( CHANGED_NAME ) );
		} );
	}

	@AfterEach
	void tearDown(SessionFactoryScope scope) {
		scope.inTransaction( session -> session.createQuery( "delete from SimpleEntity" ).executeUpdate() );
	}

	@Entity(name = "SimpleEntity")
	public static class SimpleEntity {
		@Id
		private Long id;

		private String name;
	}
}
