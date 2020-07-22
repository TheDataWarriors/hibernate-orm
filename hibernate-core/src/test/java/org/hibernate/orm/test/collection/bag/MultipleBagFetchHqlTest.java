/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.collection.bag;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.loader.MultipleBagFetchException;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DomainModel(
		annotatedClasses = {
				MultipleBagFetchHqlTest.Post.class,
				MultipleBagFetchHqlTest.PostComment.class,
				MultipleBagFetchHqlTest.Tag.class
		}
)
@SessionFactory
public class MultipleBagFetchHqlTest {

	@BeforeEach
	public void setUp(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					Post post = new Post();
					post.setId( 1L );
					post.setTitle( String.format( "Post nr. %d", 1 ) );
					PostComment comment = new PostComment();
					comment.setId( 1L );
					comment.setReview( "Excellent!" );
					session.persist( post );
					session.persist( comment );
					post.comments.add( comment );
				}
		);
	}

	@AfterEach
	public void tearDown(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery( "delete from Post" ).executeUpdate();
					session.createQuery( "delete from Tag" ).executeUpdate();
					session.createQuery( "delete from PostComment" ).executeUpdate();
				}
		);
	}

	@Test
	public void testMultipleBagFetchHql(SessionFactoryScope scope) {
		scope.inSession(
				session -> {
					try {
						session.createQuery(
								"select p " +
										"from Post p " +
										"join fetch p.tags " +
										"join fetch p.comments " +
										"where p.id = :id"
						).setParameter( "id", 1L ).uniqueResult();
						fail( "Should throw org.hibernate.loader.MultipleBagFetchException: cannot simultaneously fetch multiple bags" );
					}
					catch (IllegalArgumentException expected) {
						session.getTransaction().rollback();
						// MultipleBagFetchException was converted to IllegalArgumentException
						assertTrue( MultipleBagFetchException.class.isInstance( expected.getCause() ) );
					}
				}
		);
	}

	@Test
	public void testSingleBagFetchHql(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createQuery(
							"select p " +
									"from Post p " +
									"join fetch p.tags " +
									"join p.comments " +
									"where p.id = :id"
					).setParameter( "id", 1L ).uniqueResult();
				}
		);
	}

	@Entity(name = "Post")
	@Table(name = "post")
	public static class Post {

		@Id
		private Long id;

		private String title;

		@OneToMany(fetch = FetchType.LAZY)
		private List<PostComment> comments = new ArrayList<PostComment>();

		@ManyToMany(fetch = FetchType.LAZY)
		@JoinTable(name = "post_tag",
				joinColumns = @JoinColumn(name = "post_id"),
				inverseJoinColumns = @JoinColumn(name = "tag_id")
		)
		private List<Tag> tags = new ArrayList<>();

		public Post() {
		}

		public Post(Long id) {
			this.id = id;
		}

		public Post(String title) {
			this.title = title;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public List<Tag> getTags() {
			return tags;
		}
	}

	@Entity(name = "PostComment")
	@Table(name = "post_comment")
	public static class PostComment {

		@Id
		private Long id;

		private String review;

		public PostComment() {
		}

		public PostComment(String review) {
			this.review = review;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getReview() {
			return review;
		}

		public void setReview(String review) {
			this.review = review;
		}
	}

	@Entity(name = "Tag")
	@Table(name = "tag")
	public static class Tag {

		@Id
		private Long id;

		private String name;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
