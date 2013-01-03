/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.internal.source.annotations.attribute;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

import javax.persistence.FetchType;

import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.LazyCollectionOption;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SortType;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.metamodel.internal.source.annotations.entity.EntityBindingContext;
import org.hibernate.metamodel.internal.source.annotations.util.AnnotationParserHelper;
import org.hibernate.metamodel.internal.source.annotations.util.HibernateDotNames;
import org.hibernate.metamodel.internal.source.annotations.util.JPADotNames;
import org.hibernate.metamodel.internal.source.annotations.util.JandexHelper;
import org.hibernate.metamodel.spi.binding.Caching;
import org.hibernate.metamodel.spi.binding.CustomSQL;
import org.hibernate.metamodel.spi.source.MappingException;
import org.hibernate.metamodel.spi.source.PluralAttributeSource;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

/**
 * Represents an collection (collection, list, set, map) association attribute.
 *
 * @author Hardy Ferentschik
 * @author Strong Liu
 * @author Brett Meyer
 */
public class PluralAssociationAttribute extends AssociationAttribute {
	private final String whereClause;
	private final String orderBy;
	private final boolean sorted;
	private final String comparatorName;
	private final Caching caching;
	private final String customPersister;
	private final String customLoaderName;
	private final CustomSQL customInsert;
	private final CustomSQL customUpdate;
	private final CustomSQL customDelete;
	private final CustomSQL customDeleteAll;
	private final ClassInfo entityClassInfo;
	private final boolean isExtraLazy;
	private final OnDeleteAction onDeleteAction;
	private final boolean isIndexed;
	// Used for the non-owning side of a ManyToMany relationship
	private final String inverseForeignKeyName;
	private final String explicitForeignKeyName;

	private final PluralAttributeSource.Nature pluralAttributeNature;
	private static final EnumSet<PluralAttributeSource.Nature> SHOULD_NOT_HAS_COLLECTION_ID = EnumSet.of( PluralAttributeSource.Nature.SET,
			PluralAttributeSource.Nature.MAP, PluralAttributeSource.Nature.LIST, PluralAttributeSource.Nature.ARRAY );

	private LazyCollectionOption lazyOption;
	private final boolean isCollectionIdPresent;
	
	private final String referencedKeyType;


	public PluralAssociationAttribute(
			final ClassInfo entityClassInfo,
			final String name,
			final Class<?> attributeType,
			final Class<?> referencedKeyType,
			final Class<?> referencedAttributeType,
			final Nature associationType,
			final String accessType,
			final Map<DotName, List<AnnotationInstance>> annotations,
			final EntityBindingContext context) {
		super( entityClassInfo, name, attributeType, referencedAttributeType, associationType, accessType, annotations, context );
		this.entityClassInfo = entityClassInfo;
		this.whereClause = determineWereClause();
		this.orderBy = determineOrderBy();

		AnnotationInstance foreignKey = JandexHelper.getSingleAnnotation(
				annotations(),
				HibernateDotNames.FOREIGN_KEY
		);
		if ( foreignKey != null ) {
			explicitForeignKeyName = JandexHelper.getValue( foreignKey, "name", String.class );
			String temp = JandexHelper.getValue( foreignKey, "inverseName", String.class );
			inverseForeignKeyName = StringHelper.isNotEmpty( temp ) ? temp : null;
		}
		else {
			explicitForeignKeyName = null;
			inverseForeignKeyName = null;
		}

		this.caching = determineCachingSettings();
		this.isExtraLazy = lazyOption == LazyCollectionOption.EXTRA;
		this.customPersister = determineCustomPersister();
		this.customLoaderName = determineCustomLoaderName();
		this.customInsert = AnnotationParserHelper.processCustomSqlAnnotation(
				HibernateDotNames.SQL_INSERT, annotations()
		);
		this.customUpdate = AnnotationParserHelper.processCustomSqlAnnotation(
				HibernateDotNames.SQL_UPDATE, annotations()
		);
		this.customDelete = AnnotationParserHelper.processCustomSqlAnnotation(
				HibernateDotNames.SQL_DELETE, annotations()
		);
		this.customDeleteAll = AnnotationParserHelper.processCustomSqlAnnotation(
				HibernateDotNames.SQL_DELETE_ALL, annotations()
		);
		this.onDeleteAction = determineOnDeleteAction();
		this.isCollectionIdPresent = JandexHelper.getSingleAnnotation(
				annotations,
				HibernateDotNames.COLLECTION_ID
		) != null;
		final AnnotationInstance sortAnnotation =  JandexHelper.getSingleAnnotation( annotations, HibernateDotNames.SORT );
		if ( sortAnnotation == null ) {
			this.sorted = false;
			this.comparatorName = null;
		}
		else {
			final SortType sortType = JandexHelper.getEnumValue( sortAnnotation, "type", SortType.class );
			this.sorted = sortType != SortType.UNSORTED;
			if ( this.sorted && sortType == SortType.COMPARATOR ) {
				String comparatorName = JandexHelper.getValue( sortAnnotation, "comparator", String.class );
				if ( StringHelper.isEmpty( comparatorName ) ) {
					throw new MappingException(
							"Comparator class must be provided when using SortType.COMPARATOR on property: "+ getRole(),
							getContext().getOrigin()
					);
				}
				this.comparatorName = comparatorName;
			}
			else {
				this.comparatorName = null;
			}
		}

		AnnotationInstance orderColumnAnnotation =  JandexHelper.getSingleAnnotation( annotations, JPADotNames.ORDER_COLUMN );
		AnnotationInstance indexColumnAnnotation = JandexHelper.getSingleAnnotation( annotations, HibernateDotNames.INDEX_COLUMN );
		if ( orderColumnAnnotation != null && indexColumnAnnotation != null ) {
			throw new MappingException(
					"@OrderColumn and @IndexColumn can't be used together on property: " + getRole(),
					getContext().getOrigin()
			);
		}
		this.isIndexed = orderColumnAnnotation != null
				|| indexColumnAnnotation != null
				|| Map.class.isAssignableFrom( getAttributeType() );
		this.pluralAttributeNature = resolvePluralAttributeNature();
		
		this.referencedKeyType = determineReferencedKeyType( referencedKeyType );

		validateMapping();
	}

	public PluralAttributeSource.Nature getPluralAttributeNature() {
		return pluralAttributeNature;
	}

	public String getWhereClause() {
		return whereClause;
	}

	public String getOrderBy() {
		return orderBy;
	}

	public String getInverseForeignKeyName() {
		return inverseForeignKeyName;
	}
	public String getExplicitForeignKeyName(){
		return explicitForeignKeyName;
	}

	public Caching getCaching() {
		return caching;
	}

	public String getCustomPersister() {
		return customPersister;
	}

	public String getCustomLoaderName() {
		return customLoaderName;
	}

	public CustomSQL getCustomInsert() {
		return customInsert;
	}

	public CustomSQL getCustomUpdate() {
		return customUpdate;
	}

	public CustomSQL getCustomDelete() {
		return customDelete;
	}

	public CustomSQL getCustomDeleteAll() {
		return customDeleteAll;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append( "PluralAssociationAttribute" );
		sb.append( "{name='" ).append( getName() ).append( '\'' );
		sb.append( '}' );
		return sb.toString();
	}
	public OnDeleteAction getOnDeleteAction() {
		return onDeleteAction;
	}

	public String getComparatorName() {
		return comparatorName;
	}

	public boolean isSorted() {
		return sorted;
	}

	public boolean isIndexed() {
		return isIndexed;
	}

	public String getReferencedKeyType() {
		return referencedKeyType;
	}

	private void validateMapping() {
		checkSortedTypeIsSortable();
		checkIfCollectionIdIsWronglyPlaced();
	}

	private void checkIfCollectionIdIsWronglyPlaced() {
		if ( isCollectionIdPresent && SHOULD_NOT_HAS_COLLECTION_ID.contains( pluralAttributeNature ) ) {
			throw new MappingException(
					"The Collection type doesn't support @CollectionId annotation: " + getRole(),
					getContext().getOrigin()
			);
		}
	}

	private void checkSortedTypeIsSortable() {
		//shortcut, a little performance improvement of avoiding the class type check
		if ( pluralAttributeNature == PluralAttributeSource.Nature.MAP
				|| pluralAttributeNature == PluralAttributeSource.Nature.SET ) {
			if ( SortedMap.class.isAssignableFrom( getAttributeType() )
					|| SortedSet.class.isAssignableFrom( getAttributeType() ) ) {
				if ( !isSorted() ) {
					throw new MappingException(
							"A sorted collection has to define @Sort: " + getRole(),
							getContext().getOrigin()
					);
				}
			}
		}

	}


	//TODO org.hibernate.cfg.annotations.CollectionBinder#hasToBeSorted
	private PluralAttributeSource.Nature resolvePluralAttributeNature() {

		if ( Map.class.isAssignableFrom( getAttributeType() ) ) {
			return PluralAttributeSource.Nature.MAP;
		}
		else if ( List.class.isAssignableFrom( getAttributeType() ) ) {
			if ( isIndexed() ) {
				return PluralAttributeSource.Nature.LIST;
			}
			else if ( isCollectionIdPresent ) {
				return PluralAttributeSource.Nature.ID_BAG;
			}
			else {
				return PluralAttributeSource.Nature.BAG;
			}
		}
		else if ( Set.class.isAssignableFrom( getAttributeType() ) ) {
			return PluralAttributeSource.Nature.SET;
		}
		else if ( getAttributeType().isArray() ) {
			return PluralAttributeSource.Nature.ARRAY;
		}
		else if ( Collection.class.isAssignableFrom( getAttributeType() ) ) {
			return isCollectionIdPresent ? PluralAttributeSource.Nature.ID_BAG : PluralAttributeSource.Nature.BAG;
		}
		else {
			return PluralAttributeSource.Nature.BAG;
		}
	}


	private OnDeleteAction determineOnDeleteAction() {
		final AnnotationInstance onDeleteAnnotation = JandexHelper.getSingleAnnotation(
				annotations(),
				HibernateDotNames.ON_DELETE
		);
		if ( onDeleteAnnotation != null ) {
			return JandexHelper.getEnumValue( onDeleteAnnotation, "action", OnDeleteAction.class );
		}
		return null;
	}

	@Override
	public boolean isOptimisticLockable() {
		return hasOptimisticLockAnnotation() ? super.isOptimisticLockable() : StringHelper.isEmpty( getMappedBy() );
	}

	private String determineCustomLoaderName() {
		String loader = null;
		final AnnotationInstance customLoaderAnnotation = JandexHelper.getSingleAnnotation(
				annotations(), HibernateDotNames.LOADER
		);
		if ( customLoaderAnnotation != null ) {
			loader = JandexHelper.getValue( customLoaderAnnotation, "namedQuery", String.class );
		}
		return loader;
	}

	private String determineCustomPersister() {
		String entityPersisterClass = null;
		final AnnotationInstance persisterAnnotation = JandexHelper.getSingleAnnotation(
				annotations(), HibernateDotNames.PERSISTER
		);
		if ( persisterAnnotation != null ) {
			entityPersisterClass = JandexHelper.getValue( persisterAnnotation, "impl", String.class );
		}
		return entityPersisterClass;
	}

	@Override
	protected boolean determineIsLazy(AnnotationInstance associationAnnotation) {
		FetchType fetchType = JandexHelper.getEnumValue( associationAnnotation, "fetch", FetchType.class );
		boolean lazy = fetchType == FetchType.LAZY;
		final AnnotationInstance lazyCollectionAnnotationInstance = JandexHelper.getSingleAnnotation(
				annotations(),
				HibernateDotNames.LAZY_COLLECTION
		);
		if ( lazyCollectionAnnotationInstance != null ) {
			lazyOption = JandexHelper.getEnumValue(
					lazyCollectionAnnotationInstance,
					"value",
					LazyCollectionOption.class
			);
			lazy = !( lazyOption == LazyCollectionOption.FALSE );

		}
		final AnnotationInstance fetchAnnotation = JandexHelper.getSingleAnnotation(
				annotations(),
				HibernateDotNames.FETCH
		);
		if ( fetchAnnotation != null && fetchAnnotation.value() != null ) {
			FetchMode fetchMode = FetchMode.valueOf( fetchAnnotation.value( ).asEnum().toUpperCase() );
			if ( fetchMode == FetchMode.JOIN ) {
				lazy = false;
			}
		}
		return lazy;
	}

	public boolean isExtraLazy() {
		return isExtraLazy;
	}

	private String determineWereClause() {
		String where = null;

		AnnotationInstance whereAnnotation = JandexHelper.getSingleAnnotation( annotations(), HibernateDotNames.WHERE );
		if ( whereAnnotation != null ) {
			where = JandexHelper.getValue( whereAnnotation, "clause", String.class );
		}

		return where;
	}

	private String determineOrderBy() {
		String orderBy = null;

		AnnotationInstance hibernateWhereAnnotation = JandexHelper.getSingleAnnotation(
				annotations(),
				HibernateDotNames.ORDER_BY
		);

		AnnotationInstance jpaWhereAnnotation = JandexHelper.getSingleAnnotation(
				annotations(),
				JPADotNames.ORDER_BY
		);

		if ( jpaWhereAnnotation != null && hibernateWhereAnnotation != null ) {
			throw new MappingException(
					"Cannot use sql order by clause (@org.hibernate.annotations.OrderBy) " +
							"in conjunction with JPA order by clause (@java.persistence.OrderBy) on  " + getRole(),
					getContext().getOrigin()
			);
		}

		if ( hibernateWhereAnnotation != null ) {
			orderBy = JandexHelper.getValue( hibernateWhereAnnotation, "clause", String.class );
		}

		if ( jpaWhereAnnotation != null ) {
			// this could be an empty string according to JPA spec 11.1.38 -
			// If the ordering element is not specified for an entity association, ordering by the primary key of the
			// associated entity is assumed
			// The binder will need to take this into account and generate the right property names
			orderBy = JandexHelper.getValue( jpaWhereAnnotation, "value", String.class );
			if ( orderBy == null ) {
				orderBy = isBasicCollection() ?  "$element$ asc" :"id asc" ;
			}
			if ( orderBy.equalsIgnoreCase( "desc" ) ) {
				orderBy = isBasicCollection() ? "$element$ desc" :"id desc";
			}
		}

		return orderBy;
	}

	private boolean isBasicCollection(){
		return getNature() == Nature.ELEMENT_COLLECTION_BASIC || getNature() == Nature.ELEMENT_COLLECTION_EMBEDDABLE;
	}

	private Caching determineCachingSettings() {
		Caching caching = null;
		final AnnotationInstance hibernateCacheAnnotation = JandexHelper.getSingleAnnotation(
				annotations(),
				HibernateDotNames.CACHE
		);
		if ( hibernateCacheAnnotation != null ) {
			org.hibernate.cache.spi.access.AccessType accessType;
			if ( hibernateCacheAnnotation.value( "usage" ) == null ) {
				accessType = getContext().getMappingDefaults().getCacheAccessType();
			}
			else {
				accessType = CacheConcurrencyStrategy.parse( hibernateCacheAnnotation.value( "usage" ).asEnum() )
						.toAccessType();
			}

			return new Caching(
					hibernateCacheAnnotation.value( "region" ) == null
							? StringHelper.qualify( entityClassInfo.name().toString(), getName() )
							: hibernateCacheAnnotation.value( "region" ).asString(),
					accessType,
					hibernateCacheAnnotation.value( "include" ) != null
							&& "all".equals( hibernateCacheAnnotation.value( "include" ).asString() )
			);
		}
		return caching;
	}
	
	// TODO: For Maps only -- should this be here?
	private String determineReferencedKeyType(
			Class<?> referencedKeyType ) {
		String typeName = null;

		// @MapKeyClass
		AnnotationInstance mapKeyClassAnnotation
				= JandexHelper.getSingleAnnotation(
						annotations(), JPADotNames.MAP_KEY_CLASS );
		if ( mapKeyClassAnnotation != null ) {
			typeName = mapKeyClassAnnotation.value().asClass().name().toString();
		}
		else {
			if ( referencedKeyType != null )  {
				typeName = referencedKeyType.getName();
			}
		}

		return typeName;
	}
}


