/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.bootstrap.binding.annotations.embedded;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.hibernate.annotations.Parent;

/**
 * @author Emmanuel Bernard
 */
@Embeddable
public class Summary {
	private int size;
	private String text;
	private Book summarizedBook;

	@Column(name="summary_size")
	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	@Parent
	public Book getSummarizedBook() {
		return summarizedBook;
	}

	public void setSummarizedBook(Book summarizedBook) {
		this.summarizedBook = summarizedBook;
	}
}
