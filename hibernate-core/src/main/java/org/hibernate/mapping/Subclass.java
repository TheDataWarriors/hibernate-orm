/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hibernate.AssertionFailure;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.OptimisticLockStyle;
import org.hibernate.internal.FilterConfiguration;
import org.hibernate.internal.util.collections.JoinedIterator;
import org.hibernate.internal.util.collections.JoinedList;
import org.hibernate.internal.util.collections.SingletonIterator;
import org.hibernate.metamodel.RepresentationMode;
import org.hibernate.persister.entity.EntityPersister;

/**
 * A subclass in a table-per-class-hierarchy mapping
 * @author Gavin King
 */
public class Subclass extends PersistentClass {
	private PersistentClass superclass;
	private Class<? extends EntityPersister> classPersisterClass;
	private final int subclassId;

	public Subclass(PersistentClass superclass, MetadataBuildingContext metadataBuildingContext) {
		super( metadataBuildingContext );
		this.superclass = superclass;
		this.subclassId = superclass.nextSubclassId();
	}

	@Override
	int nextSubclassId() {
		return getSuperclass().nextSubclassId();
	}

	@Override
	public int getSubclassId() {
		return subclassId;
	}

	@Override
	public String getNaturalIdCacheRegionName() {
		return getSuperclass().getNaturalIdCacheRegionName();
	}

	@Override
	public String getCacheConcurrencyStrategy() {
		return getRootClass().getCacheConcurrencyStrategy();
	}

	@Override
	public RootClass getRootClass() {
		return getSuperclass().getRootClass();
	}

	@Override
	public PersistentClass getSuperclass() {
		return superclass;
	}

	@Override
	public Property getIdentifierProperty() {
		return getSuperclass().getIdentifierProperty();
	}

	@Override
	public Property getDeclaredIdentifierProperty() {
		return null;
	}

	@Override
	public KeyValue getIdentifier() {
		return getSuperclass().getIdentifier();
	}

	@Override
	public boolean hasIdentifierProperty() {
		return getSuperclass().hasIdentifierProperty();
	}

	@Override
	public Value getDiscriminator() {
		return getSuperclass().getDiscriminator();
	}

	@Override
	public boolean isMutable() {
		return getSuperclass().isMutable();
	}

	@Override
	public boolean isInherited() {
		return true;
	}

	@Override
	public boolean isPolymorphic() {
		return true;
	}

	@Override
	public void addProperty(Property p) {
		super.addProperty(p);
		getSuperclass().addSubclassProperty(p);
	}

	@Override
	public void addMappedsuperclassProperty(Property p) {
		super.addMappedsuperclassProperty( p );
		getSuperclass().addSubclassProperty(p);
	}

	@Override
	public void addJoin(Join j) {
		super.addJoin(j);
		getSuperclass().addSubclassJoin(j);
	}

	@Override
	public List<Property> getPropertyClosure() {
		return new JoinedList<>( getSuperclass().getPropertyClosure(), getProperties() );
	}

	@Deprecated @Override
	public Iterator<Property> getPropertyClosureIterator() {
		return new JoinedIterator<>(
				getSuperclass().getPropertyClosureIterator(),
				getPropertyIterator()
			);
	}

	@Deprecated @Override
	public Iterator<Table> getTableClosureIterator() {
		return new JoinedIterator<>(
				getSuperclass().getTableClosureIterator(),
				new SingletonIterator<>( getTable() )
			);
	}

	@Override
	public List<Table> getTableClosure() {
		return new JoinedList<>(
				getSuperclass().getTableClosure(),
				List.of( getTable() )
		);
	}

	@Override @Deprecated
	public Iterator<KeyValue> getKeyClosureIterator() {
		return new JoinedIterator<>(
				getSuperclass().getKeyClosureIterator(),
				new SingletonIterator<>( getKey() )
			);
	}

	@Override
	public List<KeyValue> getKeyClosure() {
		return new JoinedList<>(
				getSuperclass().getKeyClosure(),
				List.of( getKey() )
		);
	}

	@Override
	protected void addSubclassProperty(Property p) {
		super.addSubclassProperty(p);
		getSuperclass().addSubclassProperty(p);
	}

	@Override
	protected void addSubclassJoin(Join j) {
		super.addSubclassJoin(j);
		getSuperclass().addSubclassJoin(j);
	}

	@Override
	protected void addSubclassTable(Table table) {
		super.addSubclassTable(table);
		getSuperclass().addSubclassTable(table);
	}

	@Override
	public boolean isVersioned() {
		return getSuperclass().isVersioned();
	}

	@Override
	public Property getVersion() {
		return getSuperclass().getVersion();
	}

	@Override
	public Property getDeclaredVersion() {
		return null;
	}

	@Override
	public boolean hasEmbeddedIdentifier() {
		return getSuperclass().hasEmbeddedIdentifier();
	}

	@Override
	public Class<? extends EntityPersister> getEntityPersisterClass() {
		return classPersisterClass == null
				? getSuperclass().getEntityPersisterClass()
				: classPersisterClass;
	}

	@Override
	public Table getRootTable() {
		return getSuperclass().getRootTable();
	}

	@Override
	public KeyValue getKey() {
		return getSuperclass().getIdentifier();
	}

	@Override
	public boolean isExplicitPolymorphism() {
		return getSuperclass().isExplicitPolymorphism();
	}

	public void setSuperclass(PersistentClass superclass) {
		this.superclass = superclass;
	}

	@Override
	public String getWhere() {
		return getSuperclass().getWhere();
	}

	@Override
	public boolean isJoinedSubclass() {
		return getTable()!=getRootTable();
	}

	public void createForeignKey() {
		if ( !isJoinedSubclass() ) {
			throw new AssertionFailure( "not a joined-subclass" );
		}
		getKey().createForeignKeyOfEntity( getSuperclass().getEntityName() );
	}

	@Override
	public void setEntityPersisterClass(Class<? extends EntityPersister> classPersisterClass) {
		this.classPersisterClass = classPersisterClass;
	}


	@Override
	public int getJoinClosureSpan() {
		return getSuperclass().getJoinClosureSpan() + super.getJoinClosureSpan();
	}

	@Override
	public int getPropertyClosureSpan() {
		return getSuperclass().getPropertyClosureSpan() + super.getPropertyClosureSpan();
	}

	@Override
	public List<Join> getJoinClosure() {
		return new JoinedList<>( getSuperclass().getJoinClosure(), super.getJoinClosure() );
	}

	@Deprecated(since = "6.0")
	public Iterator<Join> getJoinClosureIterator() {
		return new JoinedIterator<>(
			getSuperclass().getJoinClosureIterator(),
			super.getJoinClosureIterator()
		);
	}


	@Override
	public boolean isClassOrSuperclassJoin(Join join) {
		return super.isClassOrSuperclassJoin(join) || getSuperclass().isClassOrSuperclassJoin(join);
	}

	@Override
	public boolean isClassOrSuperclassTable(Table table) {
		return super.isClassOrSuperclassTable(table) || getSuperclass().isClassOrSuperclassTable(table);
	}

	@Override
	public Table getTable() {
		return getSuperclass().getTable();
	}

	@Override
	public boolean isForceDiscriminator() {
		return getSuperclass().isForceDiscriminator();
	}

	@Override
	public boolean isDiscriminatorInsertable() {
		return getSuperclass().isDiscriminatorInsertable();
	}

	@Override
	public java.util.Set<String> getSynchronizedTables() {
		HashSet<String> result = new HashSet<>();
		result.addAll(synchronizedTables);
		result.addAll( getSuperclass().getSynchronizedTables() );
		return result;
	}

	@Override
	public Object accept(PersistentClassVisitor mv) {
		return mv.accept(this);
	}

	@Override
	public java.util.List<FilterConfiguration> getFilters() {
		java.util.List<FilterConfiguration> filters = new ArrayList<>(super.getFilters());
		filters.addAll(getSuperclass().getFilters());
		return filters;
	}

	@Override
	public boolean hasSubselectLoadableCollections() {
		return super.hasSubselectLoadableCollections() ||
			getSuperclass().hasSubselectLoadableCollections();
	}

	@Override
	public String getTuplizerImplClassName(RepresentationMode mode) {
		String impl = super.getTuplizerImplClassName( mode );
		if ( impl == null ) {
			impl = getSuperclass().getTuplizerImplClassName( mode );
		}
		return impl;
	}

	@Override
	public Map getTuplizerMap() {
		Map specificTuplizerDefs = super.getTuplizerMap();
		Map superclassTuplizerDefs = getSuperclass().getTuplizerMap();
		if ( specificTuplizerDefs == null && superclassTuplizerDefs == null ) {
			return null;
		}
		else {
			Map combined = new HashMap();
			if ( superclassTuplizerDefs != null ) {
				combined.putAll( superclassTuplizerDefs );
			}
			if ( specificTuplizerDefs != null ) {
				combined.putAll( specificTuplizerDefs );
			}
			return java.util.Collections.unmodifiableMap( combined );
		}
	}

	@Override
	public Component getIdentifierMapper() {
		return superclass.getIdentifierMapper();
	}

	@Override
	public OptimisticLockStyle getOptimisticLockStyle() {
		return superclass.getOptimisticLockStyle();
	}
}
