/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.mapping;

import jakarta.persistence.FetchType;
import org.hibernate.annotations.FetchMode;

import java.util.LinkedHashSet;
import java.util.Locale;

import static jakarta.persistence.FetchType.EAGER;

/**
 * A mapping model object representing a {@link org.hibernate.annotations.FetchProfile}.
 *
 * @author Steve Ebersole
 *
 * @see org.hibernate.engine.profile.FetchProfile
 */
public class FetchProfile {
	private final String name;
	private final MetadataSource source;
	private final LinkedHashSet<Fetch> fetches = new LinkedHashSet<>();

	/**
	 * Create a fetch profile representation.
	 *
	 * @param name The name of the fetch profile.
	 * @param source The source of the fetch profile (where was it defined).
	 */
	public FetchProfile(String name, MetadataSource source) {
		this.name = name;
		this.source = source;
	}

	/**
	 * Retrieve the name of the fetch profile.
	 *
	 * @return The profile name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Retrieve the fetch profile source.
	 *
	 * @return The profile source.
	 */
	public MetadataSource getSource() {
		return source;
	}

	/**
	 * Retrieve the fetches associated with this profile
	 *
	 * @return The fetches associated with this profile.
	 */
	public LinkedHashSet<Fetch> getFetches() {
		return fetches;
	}

	/**
	 * Adds a fetch to this profile.
	 *
	 * @param entity The entity which contains the association to be fetched
	 * @param association The association to fetch
	 * @param style The style of fetch to apply
	 *
	 * @deprecated use {@link #addFetch(Fetch)}
	 */
	@Deprecated(forRemoval = true)
	public void addFetch(String entity, String association, String style) {
		addFetch( new Fetch( entity, association, style ) );
	}

	/**
	 * Adds a fetch to this profile.
	 */
	public void addFetch(Fetch fetch) {
		fetches.add( fetch );
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}

		FetchProfile that = ( FetchProfile ) o;

		return name.equals( that.name );
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}


	/**
	 * An individual association fetch within the given profile.
	 */
	public static class Fetch {
		private final String entity;
		private final String association;
		private final FetchMode method;
		private final FetchType type;

		public Fetch(String entity, String association, FetchMode method, FetchType type) {
			this.entity = entity;
			this.association = association;
			this.method = method;
			this.type = type;
		}

		/**
		 * @deprecated use {@link FetchProfile.Fetch#Fetch(String,String,FetchMode,FetchType)}
		 */
		@Deprecated(forRemoval = true)
		public Fetch(String entity, String association, String style) {
			this.entity = entity;
			this.association = association;
			this.method = fetchMode( style );
			this.type = EAGER;
		}

		private FetchMode fetchMode(String style) {
			for ( FetchMode mode: FetchMode.values() ) {
				if ( mode.name().equalsIgnoreCase( style ) ) {
					return mode;
				}
			}
			throw new IllegalArgumentException( "Unknown FetchMode: " + style );
		}

		public String getEntity() {
			return entity;
		}

		public String getAssociation() {
			return association;
		}

		/**
		 * @deprecated use {@link #getMethod()}
		 */
		@Deprecated(forRemoval = true)
		public String getStyle() {
			return method.toString().toLowerCase(Locale.ROOT);
		}

		public FetchMode getMethod() {
			return method;
		}

		public FetchType getType() {
			return type;
		}
	}
}
