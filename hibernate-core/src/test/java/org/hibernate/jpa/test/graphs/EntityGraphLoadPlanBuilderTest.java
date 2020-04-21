/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpa.test.graphs;

import java.util.Iterator;
import java.util.Set;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;


import org.junit.Test;

import static org.junit.Assert.*;

import org.hibernate.LockMode;
import org.hibernate.engine.spi.EffectiveEntityGraph;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.graph.GraphSemantic;
import org.hibernate.graph.spi.RootGraphImplementor;
import org.hibernate.jpa.test.BaseEntityManagerFunctionalTestCase;
import org.hibernate.loader.plan.build.internal.FetchGraphLoadPlanBuildingStrategy;
import org.hibernate.loader.plan.build.internal.LoadGraphLoadPlanBuildingStrategy;
import org.hibernate.loader.plan.build.internal.AbstractLoadPlanBuildingAssociationVisitationStrategy;
import org.hibernate.loader.plan.build.spi.LoadPlanTreePrinter;
import org.hibernate.loader.plan.build.spi.MetamodelDrivenLoadPlanBuilder;
import org.hibernate.loader.plan.exec.internal.AliasResolutionContextImpl;
import org.hibernate.loader.plan.spi.Join;
import org.hibernate.loader.plan.spi.LoadPlan;
import org.hibernate.loader.plan.spi.QuerySpace;
import org.hibernate.persister.entity.EntityPersister;

/**
 * @author <a href="mailto:stliu@hibernate.org">Strong Liu</a>
 */
public class EntityGraphLoadPlanBuilderTest extends BaseEntityManagerFunctionalTestCase {
	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class[] { Cat.class, Person.class, Country.class, Dog.class, ExpressCompany.class };
	}

	@Entity
	public static class Dog {
		@Id
		String name;
		@ElementCollection
		Set<String> favorites;
	}

	@Entity
	public static class Cat {
		@Id
		String name;
		@ManyToOne(fetch = FetchType.LAZY)
		Person owner;

	}

	@Entity
	public static class Person {
		@Id
		String name;
		@OneToMany(mappedBy = "owner")
		Set<Cat> pets;
		@Embedded
		Address homeAddress;
	}

	@Embeddable
	public static class Address {
		@ManyToOne
		Country country;
	}

	@Entity
	public static class ExpressCompany {
		@Id
		String name;
		@ElementCollection
		Set<Address> shipAddresses;
	}

	@Entity
	public static class Country {
		@Id
		String name;
	}

	/**
	 * EntityGraph(1):
	 *
	 * Cat
	 *
	 * LoadPlan:
	 *
	 * Cat
	 *
	 * ---------------------
	 *
	 * EntityGraph(2):
	 *
	 * Cat
	 * owner -- Person
	 *
	 * LoadPlan:
	 *
	 * Cat
	 * owner -- Person
	 * address --- Address
	 */
	@Test
	public void testBasicFetchLoadPlanBuilding() {
		EntityManager em = getOrCreateEntityManager();
		EntityGraph eg = em.createEntityGraph( Cat.class );
		LoadPlan plan = buildLoadPlan( eg, GraphSemantic.FETCH, Cat.class );
		LoadPlanTreePrinter.INSTANCE.logTree( plan, new AliasResolutionContextImpl( sfi() ) );
		QuerySpace rootQuerySpace = plan.getQuerySpaces().getRootQuerySpaces().get( 0 );
		assertFalse(
				"With fetchgraph property and an empty EntityGraph, there should be no join at all",
				rootQuerySpace.getJoins().iterator().hasNext()
		);
		// -------------------------------------------------- another a little more complicated case
		eg = em.createEntityGraph( Cat.class );
		eg.addSubgraph( "owner", Person.class );
		plan = buildLoadPlan( eg, GraphSemantic.FETCH, Cat.class );
		LoadPlanTreePrinter.INSTANCE.logTree( plan, new AliasResolutionContextImpl( sfi() ) );
		rootQuerySpace = plan.getQuerySpaces().getRootQuerySpaces().get( 0 );
		Iterator<Join> iterator = rootQuerySpace.getJoins().iterator();
		assertTrue(
				"With fetchgraph property and an empty EntityGraph, there should be no join at all", iterator.hasNext()
		);
		Join personJoin = iterator.next();
		assertNotNull( personJoin );
		QuerySpace.Disposition disposition = personJoin.getRightHandSide().getDisposition();
		assertEquals(
				"This should be an entity join which fetches Person", QuerySpace.Disposition.ENTITY, disposition
		);

		iterator = personJoin.getRightHandSide().getJoins().iterator();
		assertTrue( "The composite address should be fetched", iterator.hasNext() );
		Join addressJoin = iterator.next();
		assertNotNull( addressJoin );
		disposition = addressJoin.getRightHandSide().getDisposition();
		assertEquals( QuerySpace.Disposition.COMPOSITE, disposition );
		assertFalse( iterator.hasNext() );
		assertFalse(
				"The ManyToOne attribute in composite should not be fetched",
				addressJoin.getRightHandSide().getJoins().iterator().hasNext()
		);
		em.close();
	}

	/**
	 * EntityGraph(1):
	 *
	 * Cat
	 *
	 * LoadPlan:
	 *
	 * Cat
	 *
	 * ---------------------
	 *
	 * EntityGraph(2):
	 *
	 * Cat
	 * owner -- Person
	 *
	 * LoadPlan:
	 *
	 * Cat
	 * owner -- Person
	 * address --- Address
	 * country -- Country
	 */
	@Test
	public void testBasicLoadLoadPlanBuilding() {
		EntityManager em = getOrCreateEntityManager();
		EntityGraph eg = em.createEntityGraph( Cat.class );
		LoadPlan plan = buildLoadPlan( eg, GraphSemantic.LOAD, Cat.class );
		LoadPlanTreePrinter.INSTANCE.logTree( plan, new AliasResolutionContextImpl( sfi() ) );
		QuerySpace rootQuerySpace = plan.getQuerySpaces().getRootQuerySpaces().get( 0 );
		assertFalse(
				"With fetchgraph property and an empty EntityGraph, there should be no join at all",
				rootQuerySpace.getJoins().iterator().hasNext()
		);
		// -------------------------------------------------- another a little more complicated case
		eg = em.createEntityGraph( Cat.class );
		eg.addSubgraph( "owner", Person.class );
		plan = buildLoadPlan( eg, GraphSemantic.LOAD, Cat.class );
		LoadPlanTreePrinter.INSTANCE.logTree( plan, new AliasResolutionContextImpl( sfi() ) );
		rootQuerySpace = plan.getQuerySpaces().getRootQuerySpaces().get( 0 );
		Iterator<Join> iterator = rootQuerySpace.getJoins().iterator();
		assertTrue(
				"With fetchgraph property and an empty EntityGraph, there should be no join at all", iterator.hasNext()
		);
		Join personJoin = iterator.next();
		assertNotNull( personJoin );
		QuerySpace.Disposition disposition = personJoin.getRightHandSide().getDisposition();
		assertEquals(
				"This should be an entity join which fetches Person", QuerySpace.Disposition.ENTITY, disposition
		);

		iterator = personJoin.getRightHandSide().getJoins().iterator();
		assertTrue( "The composite address should be fetched", iterator.hasNext() );
		Join addressJoin = iterator.next();
		assertNotNull( addressJoin );
		disposition = addressJoin.getRightHandSide().getDisposition();
		assertEquals( QuerySpace.Disposition.COMPOSITE, disposition );
		iterator = addressJoin.getRightHandSide().getJoins().iterator();
		assertTrue( iterator.hasNext() );
		Join countryJoin = iterator.next();
		assertNotNull( countryJoin );
		disposition = countryJoin.getRightHandSide().getDisposition();
		assertEquals( QuerySpace.Disposition.ENTITY, disposition );
		assertFalse(
				"The ManyToOne attribute in composite should not be fetched",
				countryJoin.getRightHandSide().getJoins().iterator().hasNext()
		);
		em.close();
	}


	@Test
	public void testBasicElementCollections() {
		EntityManager em = getOrCreateEntityManager();
		EntityGraph eg = em.createEntityGraph( Dog.class );
		eg.addAttributeNodes( "favorites" );
		LoadPlan loadLoadPlan = buildLoadPlan( eg, GraphSemantic.LOAD, Dog.class ); //WTF name!!!
		LoadPlanTreePrinter.INSTANCE.logTree( loadLoadPlan, new AliasResolutionContextImpl( sfi() ) );
		QuerySpace querySpace = loadLoadPlan.getQuerySpaces().getRootQuerySpaces().iterator().next();
		Iterator<Join> iterator = querySpace.getJoins().iterator();
		assertTrue( iterator.hasNext() );
		Join collectionJoin = iterator.next();
		assertEquals( QuerySpace.Disposition.COLLECTION, collectionJoin.getRightHandSide().getDisposition() );
		assertFalse( iterator.hasNext() );
		//----------------------------------------------------------------
		LoadPlan fetchLoadPlan = buildLoadPlan( eg, GraphSemantic.FETCH, Dog.class );
		LoadPlanTreePrinter.INSTANCE.logTree( fetchLoadPlan, new AliasResolutionContextImpl( sfi() ) );
		querySpace = fetchLoadPlan.getQuerySpaces().getRootQuerySpaces().iterator().next();
		iterator = querySpace.getJoins().iterator();
		assertTrue( iterator.hasNext() );
		collectionJoin = iterator.next();
		assertEquals( QuerySpace.Disposition.COLLECTION, collectionJoin.getRightHandSide().getDisposition() );
		assertFalse( iterator.hasNext() );
		em.close();
	}


	@Test
	public void testEmbeddedCollection() {
		EntityManager em = getOrCreateEntityManager();
		EntityGraph eg = em.createEntityGraph( ExpressCompany.class );
		eg.addAttributeNodes( "shipAddresses" );

		LoadPlan loadLoadPlan = buildLoadPlan( eg, GraphSemantic.LOAD, ExpressCompany.class ); //WTF name!!!
		LoadPlanTreePrinter.INSTANCE.logTree( loadLoadPlan, new AliasResolutionContextImpl( sfi() ) );

		QuerySpace querySpace = loadLoadPlan.getQuerySpaces().getRootQuerySpaces().iterator().next();
		Iterator<Join> iterator = querySpace.getJoins().iterator();
		assertTrue( iterator.hasNext() );
		Join collectionJoin = iterator.next();
		assertEquals( QuerySpace.Disposition.COLLECTION, collectionJoin.getRightHandSide().getDisposition() );
		assertFalse( iterator.hasNext() );

		iterator = collectionJoin.getRightHandSide().getJoins().iterator();
		assertTrue( iterator.hasNext() );
		Join collectionElementJoin = iterator.next();
		assertFalse( iterator.hasNext() );
		assertEquals( QuerySpace.Disposition.COMPOSITE, collectionElementJoin.getRightHandSide().getDisposition() );

		iterator = collectionElementJoin.getRightHandSide().getJoins().iterator();
		assertTrue( iterator.hasNext() );
		Join countryJoin = iterator.next();
		assertFalse( iterator.hasNext() );
		assertEquals( QuerySpace.Disposition.ENTITY, countryJoin.getRightHandSide().getDisposition() );

		//----------------------------------------------------------------
		LoadPlan fetchLoadPlan = buildLoadPlan( eg, GraphSemantic.FETCH, ExpressCompany.class );
		LoadPlanTreePrinter.INSTANCE.logTree( fetchLoadPlan, new AliasResolutionContextImpl( sfi() ) );


		querySpace = fetchLoadPlan.getQuerySpaces().getRootQuerySpaces().iterator().next();
		iterator = querySpace.getJoins().iterator();
		assertTrue( iterator.hasNext() );
		collectionJoin = iterator.next();
		assertEquals( QuerySpace.Disposition.COLLECTION, collectionJoin.getRightHandSide().getDisposition() );
		assertFalse( iterator.hasNext() );

		iterator = collectionJoin.getRightHandSide().getJoins().iterator();
		assertTrue( iterator.hasNext() );
		collectionElementJoin = iterator.next();
		assertFalse( iterator.hasNext() );
		assertEquals( QuerySpace.Disposition.COMPOSITE, collectionElementJoin.getRightHandSide().getDisposition() );

		iterator = collectionElementJoin.getRightHandSide().getJoins().iterator();
		assertFalse( iterator.hasNext() );
		//----------------------------------------------------------------
		em.close();
	}


	private SessionFactoryImplementor sfi() {
		return entityManagerFactory().unwrap( SessionFactoryImplementor.class );
	}

	private LoadPlan buildLoadPlan(EntityGraph entityGraph, GraphSemantic mode, Class clazz) {
		final LoadQueryInfluencers loadQueryInfluencers = new LoadQueryInfluencers( sfi() );
		final EffectiveEntityGraph effectiveEntityGraph = loadQueryInfluencers.getEffectiveEntityGraph();
		effectiveEntityGraph.applyGraph( ( RootGraphImplementor) entityGraph, mode );

		final EntityPersister ep = (EntityPersister) sfi().getClassMetadata( clazz );
		AbstractLoadPlanBuildingAssociationVisitationStrategy strategy = GraphSemantic.FETCH == mode
				? new FetchGraphLoadPlanBuildingStrategy(
						sfi(),
						effectiveEntityGraph.getGraph(),
						loadQueryInfluencers,
						LockMode.NONE
		) : new LoadGraphLoadPlanBuildingStrategy( sfi(), loadQueryInfluencers, LockMode.NONE );
		return MetamodelDrivenLoadPlanBuilder.buildRootEntityLoadPlan( strategy, ep );
	}

}
