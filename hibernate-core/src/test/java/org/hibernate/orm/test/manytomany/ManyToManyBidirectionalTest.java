/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.manytomany;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.hamcrest.core.IsCollectionContaining;
import org.hamcrest.core.IsSame;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Nathan Xu
 */
@DomainModel(
		annotatedClasses = {
				ManyToManyBidirectionalTest.Book.class,
				ManyToManyBidirectionalTest.Author.class
		}
)
@SessionFactory(generateStatistics = true)
@ServiceRegistry
@SuppressWarnings( "unused" )
public class ManyToManyBidirectionalTest {

	@BeforeEach
	public void setUp(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final Author author1 = new Author( 1 );
			final Author author2 = new Author( 2 );

			final Book bookByAuthor1 = new Book( 1 );
			bookByAuthor1.addAuthor( author1 );

			final Book bookByAuthor2 = new Book( 2 );
			bookByAuthor2.addAuthor( author2 );

			final Book bookByAuthor1AndAuthor2 = new Book( 3 );
			bookByAuthor1AndAuthor2.addAuthor( author1 );
			bookByAuthor1AndAuthor2.addAuthor( author2 );

			session.save( author1 );
			session.save( author2 );
			session.save( bookByAuthor1 );
			session.save( bookByAuthor2 );
			session.save( bookByAuthor1AndAuthor2 );

		} );
	}

	@AfterEach
	public void tearDown(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			session.createQuery( "delete from Author" ).executeUpdate();
			session.createQuery( "delete from Book" ).executeUpdate();
		} );
	}

	@Test
	public void testCircularReferenceDetection(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					final List<Book> books = session.createQuery( "from Book b join fetch b.authors" , Book.class ).list();
					books.forEach( book ->
						book.authors.forEach( author ->
							assertThat( author.books, IsCollectionContaining.hasItem( IsSame.sameInstance( book ) ) )
						)
					);

					final List<Author> authors = session.createQuery( "from Author a join fetch a.books" , Author.class ).list();
					authors.forEach( author ->
						author.books.forEach( book ->
							assertThat( book.authors, IsCollectionContaining.hasItem( IsSame.sameInstance( author ) ) )
						)
					);
				}
		);
	}

	@Entity(name = "Book")
	public static class Book {

		@Id
		private int id;

		public Book() {
		}

		public Book(int id) {
			this.id = id;
		}

		@ManyToMany
		@JoinTable(name = "book_author",
				joinColumns = { @JoinColumn(name = "fk_book") },
				inverseJoinColumns = { @JoinColumn(name = "fk_author") })
		private Set<Author> authors = new HashSet<>();

		public void addAuthor(Author author) {
			authors.add( author );
			author.books.add( this );
		}
	}

	@Entity(name = "Author")
	public static class Author {

		@Id
		private int id;

		public Author() {
		}

		public Author(int id) {
			this.id = id;
		}

		@ManyToMany(mappedBy = "authors")
		private Set<Book> books = new HashSet<>();

		public void addBook(Book book) {
			books.add( book );
			book.authors.add( this );
		}
	}

}
