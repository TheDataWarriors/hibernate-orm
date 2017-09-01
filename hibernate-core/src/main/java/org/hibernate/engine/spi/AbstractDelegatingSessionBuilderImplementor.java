/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.engine.spi;

/**
 * Base class for {@link SessionBuilderImplementor} implementations that wish to implement only parts of that contract
 * themselves while forwarding other method invocations to a delegate instance.
 *
 * @author Gunnar Morling
 */
public abstract class AbstractDelegatingSessionBuilderImplementor<T extends SessionBuilderImplementor>
		extends AbstractDelegatingSessionBuilder<T>
		implements SessionBuilderImplementor<T> {

	public AbstractDelegatingSessionBuilderImplementor(SessionBuilderImplementor delegate) {
		super( delegate );
	}

	protected SessionBuilderImplementor getDelegate() {
		return (SessionBuilderImplementor) super.getDelegate();
	}

	@SuppressWarnings({ "unchecked", "deprecation" })
	@Override
	public T owner(SessionOwner sessionOwner) {
		getDelegate().owner( sessionOwner );
		return (T) this;
	}
}
