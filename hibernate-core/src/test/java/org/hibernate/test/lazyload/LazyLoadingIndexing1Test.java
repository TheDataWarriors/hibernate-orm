package org.hibernate.test.lazyload;

import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.hibernate.jpa.test.BaseEntityManagerFunctionalTestCase;
import org.hibernate.testing.TestForIssue;
import org.junit.Test;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToMany;
import javax.persistence.Version;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.testing.transaction.TransactionUtil.doInJPA;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@TestForIssue(jiraKey = "HHH-14839")
public class LazyLoadingIndexing1Test extends BaseEntityManagerFunctionalTestCase {

    protected Class<?>[] getAnnotatedClasses() {
        return new Class<?>[]{
                BusinessEntity.class,
                SalesOrder.class,
                SalesOrderDetail.class,
                SalesOrderDetailText.class,
                Item.class,
                ItemVendorInfo.class,
                SerialNumber.class,
                Vendor.class,
                Manufacturer.class,
                ItemText.class,
                PurchaseOrder.class,
                PurchaseOrderDetail.class
        };
    }

    @Test
    public void testLazyLoadingAfterDetachedPersistOrMerge() {

        // add vendor, manufacturer, and item
        doInJPA(this::entityManagerFactory, entityManager -> {

            Vendor vendor = new Vendor(1L, "Distributor");
            entityManager.persist(vendor);

            Manufacturer manufacturer = new Manufacturer(1L, "Manufacturer");
            entityManager.persist(manufacturer);

            Item item = new Item(1L, "New Item");
            item.setManufacturer(manufacturer);
            entityManager.persist(item);
        });

        // add item vendor info with all ToOne references detached
        doInJPA(this::entityManagerFactory, entityManager -> {

            Manufacturer manufacturer = new Manufacturer(1L);
            Vendor vendor = new Vendor(1L);
            Item item = new Item(1L);
            item.setManufacturer(manufacturer);

            ItemVendorInfo itemVendorInfo = new ItemVendorInfo(1L, item, vendor, new BigDecimal("2000"));
            entityManager.persist(itemVendorInfo);
            entityManager.flush();

            assertThat(itemVendorInfo.getItem().getManufacturer().getName()).matches("Manufacturer");
            assertThat(itemVendorInfo.getItem().getManufacturer().getItems()).hasSize(1);

            verifyReachableByIndexing(itemVendorInfo.getItem(), itemVendorInfo.getVendor(), 1, 1);

        });

        // update detached item
        {
            Item detachedItem;

            detachedItem = doInJPA(this::entityManagerFactory, entityManager -> {
                return entityManager.find(Item.class, 1L);
            });

            assertProxyState(detachedItem);

            doInJPA(this::entityManagerFactory, entityManager -> {

                Manufacturer manufacturer = new Manufacturer(1L);  // simulate detached manufacturer

                Item i = new Item(1L);
                i.setManufacturer(manufacturer);
                i.setName("Item 1 New Name");
                i.setVersion(detachedItem.getVersion());

                int version = i.getVersion();
                i = entityManager.merge(i);
                entityManager.flush();

                assertThat(i.getVendorInfos()).hasSize(1);

                manufacturer = i.getManufacturer();
                assertThat(manufacturer.getName()).matches("Manufacturer");
                assertThat(manufacturer.getItems()).hasSize(1);

                assertThat(i.getVersion()).isEqualTo(version + 1);

                entityManager.refresh(i);

                assertThat(i.getName()).matches("Item 1 New Name");
                assertThat(i.getVersion()).isEqualTo(version + 1);

            });

        }

        // add another item with vendor info, and make sure information from previous transaction is still there
        doInJPA(this::entityManagerFactory, entityManager -> {

            Manufacturer manufacturer1 = entityManager.find(Manufacturer.class, 1L);
            Item item2 = new Item(2L, "New Item 2");
            item2.setManufacturer(manufacturer1);
            item2.setVersion(0);

            entityManager.persist(item2);

            Vendor vendor1 = entityManager.find(Vendor.class, 1L);
            ItemVendorInfo itemVendorInfo2 = new ItemVendorInfo(2L, item2, vendor1, new BigDecimal("2000"));
            entityManager.persist(itemVendorInfo2);
            entityManager.flush();

            verifyReachableByIndexing(item2, itemVendorInfo2.getVendor(), 1, 2);
        });


        // also check manufacturer --> items is accumulating
        doInJPA(this::entityManagerFactory, entityManager -> {

            Manufacturer manufacturer1 = new Manufacturer(1L);
            Item item3 = new Item(3L, "New Item 3");
            item3.setManufacturer(manufacturer1);
            item3.setVersion(0);

            entityManager.persist(item3);

            Vendor vendor1 = new Vendor(1L);
            ItemVendorInfo itemVendorInfo3 = new ItemVendorInfo(3L, item3, vendor1, new BigDecimal("2000"));
            entityManager.persist(itemVendorInfo3);
            entityManager.flush();

            Set<ItemVendorInfo> vi = item3.getVendorInfos();
            assertThat(vi).hasSize(1);

            assertThat(item3.getManufacturer().getName()).matches("Manufacturer");
            assertThat(item3.getManufacturer().getItems()).hasSize(3);

            verifyReachableByIndexing(item3, itemVendorInfo3.getVendor(), 1, 3);

        });

        // test ToMany on the other side of a ToOne
        doInJPA(this::entityManagerFactory, entityManager -> {

            SalesOrder salesOrder = new SalesOrder(1L);
            entityManager.persist(salesOrder);

            Item Item1 = new Item(1L);
            Item1.setVersion(1);

            SalesOrderDetail salesOrderDetail = new SalesOrderDetail(1L, salesOrder, Item1);
            entityManager.persist(salesOrderDetail);
            entityManager.flush();

            assertThat(salesOrderDetail.getItem().getSalesOrderDetails()).hasSize(1);

        });

        // test ToMany --> ToOne from entity persisted
        doInJPA(this::entityManagerFactory, entityManager -> {

            SalesOrderDetail salesOrderDetail = new SalesOrderDetail(1L);

            SerialNumber serialNumber = new SerialNumber(1L, "1", salesOrderDetail);
            entityManager.persist(serialNumber);
            entityManager.flush();

            assertNotNull(serialNumber.getSalesOrderDetail().getSalesOrder());
        });


        // test ToMany --> ToOne from entity removed
        doInJPA(this::entityManagerFactory, entityManager -> {

            SerialNumber serialNumber = entityManager.find(SerialNumber.class, 1L);
            entityManager.remove(serialNumber);
            assertNotNull(serialNumber.getSalesOrderDetail().getSalesOrder());
        });


        // test ToOne --> ToMany from entity persisted
        doInJPA(this::entityManagerFactory, entityManager -> {

            Item Item1 = new Item(1L);
            Item1.setVersion(1);

            Vendor vendor1 = new Vendor(1L);

            ItemVendorInfo itemVendorInfo4 = new ItemVendorInfo(4L, Item1, vendor1, new BigDecimal("2000"));
            entityManager.persist(itemVendorInfo4);
            entityManager.flush();

            Set<ItemVendorInfo> vi = itemVendorInfo4.getItem().getVendorInfos();
            assertThat(vi).hasSize(2);

        });

        // add an entity to remove in the next test, check reachability
        doInJPA(this::entityManagerFactory, entityManager -> {

            Item item10 = new Item(10L);
            entityManager.persist(item10);
            entityManager.flush();

            Vendor vendor1 = new Vendor(1L);

            ItemVendorInfo itemVendorInfo5 = new ItemVendorInfo(5L, item10, vendor1, new BigDecimal("2000"));
            entityManager.persist(itemVendorInfo5);
            entityManager.flush();

            verifyReachableByIndexing(itemVendorInfo5.getItem(), itemVendorInfo5.getVendor(), 1, 5);

        });

        // remove entity and check reachability
        doInJPA(this::entityManagerFactory, entityManager -> {
            ItemVendorInfo itemVendorInfo5 = entityManager.find(ItemVendorInfo.class, 5L);
            entityManager.remove(itemVendorInfo5);
            entityManager.flush();

            verifyReachableByIndexing(itemVendorInfo5.getItem(), itemVendorInfo5.getVendor(), 0, 4);
        });


        // add an item to merge in the next test
        doInJPA(this::entityManagerFactory, entityManager -> {
            Item i = new Item(11L);
            entityManager.persist(i);
        });

        // check merged values after a refresh
        doInJPA(this::entityManagerFactory, entityManager -> {

            Item i = new Item(11L);

            Manufacturer manufacturer = new Manufacturer(1L);
            i.setManufacturer(manufacturer); // simulate detached manufacturer
            i.setName("Item 11 Test update with lazy init collection");
            i = entityManager.merge(i);
            entityManager.flush();

            entityManager.refresh(i);
            assertThat(i.getName()).isEqualTo("Item 11 Test update with lazy init collection");
        });

        // test that Cascade DETACH does not inhibit indexing for serial number in purchase order entity
        doInJPA(this::entityManagerFactory, entityManager -> {
            PurchaseOrder po = new PurchaseOrder(1L);
            entityManager.persist(po);

            PurchaseOrderDetail purchaseOrderDetail = new PurchaseOrderDetail(1L, po, new Item(1L));
            entityManager.persist(purchaseOrderDetail);
        });

        // make sure serial number remains reachable from po after persist/flush
        doInJPA(this::entityManagerFactory, entityManager -> {
            PurchaseOrderDetail purchaseOrderDetail = new PurchaseOrderDetail(1L);
            purchaseOrderDetail.setPo(new PurchaseOrder(1L));
            SerialNumber serialNumber = new SerialNumber(1L);
            serialNumber.setPurchaseOrderDetail(purchaseOrderDetail);
            serialNumber.setSerialNumber("ABCDEFG");
            entityManager.persist(serialNumber);
            entityManager.flush();

            assertThat(serialNumber.getPurchaseOrderDetail().getPo().getPoDetails()).hasSize(1);
        });

        // make sure serial number remains reachable from po after merge/flush
        doInJPA(this::entityManagerFactory, entityManager -> {
            PurchaseOrder po = entityManager.find(PurchaseOrder.class, 1L);
            po.setPoDescription("New Description");
            entityManager.merge(po);
            entityManager.flush();

            assertThat(po.getPoDetails()).hasSize(1);

            for (PurchaseOrderDetail pod : po.getPoDetails()) {
                assertThat(pod.getSerialNumbers()).hasSize(1);
            }
        });

        // set up nested sales order detail for update test
        doInJPA(this::entityManagerFactory, entityManager -> {

            SalesOrder salesOrder = new SalesOrder(2L);
            entityManager.persist(salesOrder);

            Item item1 = new Item(1L);
            item1.setVersion(1);

            SalesOrderDetail salesOrderDetail = new SalesOrderDetail(2L, salesOrder, item1);
            entityManager.persist(salesOrderDetail);

            SalesOrderDetail salesOrderDetailChild = new SalesOrderDetail(3L, salesOrder, item1);
            salesOrderDetailChild.setParent(salesOrderDetail);

        });

        // test update with nested init
        doInJPA(this::entityManagerFactory, entityManager -> {

            SalesOrderDetail sodInDb = entityManager.find(SalesOrderDetail.class, 2L);

            SalesOrder salesOrder = new SalesOrder(2L);
            Item item1 = new Item(1L);
            item1.setVersion(1);

            SalesOrderDetail salesOrderDetail = new SalesOrderDetail(2L, salesOrder, item1);
            salesOrderDetail.setQuantity(BigDecimal.ONE);
            salesOrderDetail = entityManager.merge(salesOrderDetail);

            entityManager.flush();

            entityManager.detach(salesOrderDetail);

            salesOrderDetail = entityManager.merge(salesOrderDetail);

            Hibernate.initialize(salesOrderDetail.getSalesOrderDetailTexts());

            for(SalesOrderDetail sod : salesOrderDetail.children) {
                Hibernate.initialize(sod.getSalesOrderDetailTexts());
            }

        });
    }

    private void verifyReachableByIndexing(Item item, Vendor vendor, int infoByItemSize, int infoByVendorSize) {
        assertThat(item.getVendorInfos()).hasSize(infoByItemSize);
        assertThat(vendor.getItemVendorInfos()).hasSize(infoByVendorSize);

        if (item.getVendorInfos().size() > 0) {
            ItemVendorInfo vendorInfo = (ItemVendorInfo) item.getVendorInfos().toArray()[0];
            assertThat(vendorInfo.getVendor().getId()).isEqualTo(vendor.getId());
        }
    }

    protected void assertProxyState(Item item) {
        try {
            item.getManufacturer().getName();
            fail("Should throw LazyInitializationException!");
        } catch (LazyInitializationException expected) {

        }

        try {
            item.getVendorInfos().size();
            fail("Should throw LazyInitializationException!");
        } catch (LazyInitializationException expected) {

        }
    }

    @MappedSuperclass
    public static class BusinessEntity {

        private Long id;
        private int version;

        public BusinessEntity() {
            version = 0;
        }

        public BusinessEntity(Long id) {
            this.id = id;
            version = 0;
        }

        @Id
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        @Version
        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }
    }

    @Entity
    public static class SalesOrder extends BusinessEntity {

        public SalesOrder() {
        }

        public SalesOrder(Long id) {
            super(id);
        }

        private Set<SalesOrderDetail> salesOrderDetails;

        @OneToMany(mappedBy = "salesOrder")
        public Set<SalesOrderDetail> getSalesOrderDetails() {
            return this.salesOrderDetails;
        }

        public void setSalesOrderDetails(Set<SalesOrderDetail> SalesOrderDetails) {
            this.salesOrderDetails = SalesOrderDetails;
        }

    }

    @Entity
    public static class SalesOrderDetail extends BusinessEntity {

        Item item;
        BigDecimal quantity;
        SalesOrder salesOrder;
        private SalesOrderDetail parent;
        Set<SalesOrderDetail> children;
        Set<SalesOrderDetailText> salesOrderDetailTexts;

        public SalesOrderDetail() {

        }

        public SalesOrderDetail(Long id) {
            super(id);
        }

        public SalesOrderDetail(Long id, SalesOrder salesOrder, Item item) {
            super(id);
            this.salesOrder = salesOrder;
            this.item = item;
        }

        private Set<SerialNumber> serialNumbers;

        @ManyToOne(fetch = FetchType.LAZY)
        public SalesOrder getSalesOrder() {
            return this.salesOrder;
        }

        public void setSalesOrder(SalesOrder salesOrder) {
            this.salesOrder = salesOrder;
        }


        @ManyToOne(fetch = FetchType.LAZY)
        public Item getItem() {
            return item;
        }

        public void setItem(Item item) {
            this.item = item;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        @OneToMany(mappedBy = "salesOrderDetail")
        public Set<SerialNumber> getSerialNumbers() {
            return serialNumbers;
        }

        public void setSerialNumbers(Set<SerialNumber> serialNumbers) {
            this.serialNumbers = serialNumbers;
        }

        @ManyToOne(fetch = FetchType.LAZY)
        //@IndexedEmbedded(includeDepth = 1)
        public SalesOrderDetail getParent() {
            return parent;
        }

        public void setParent(SalesOrderDetail parent) {
            this.parent = parent;
        }

        @OneToMany(mappedBy = "parent")
        public Set<SalesOrderDetail> getChildren() {
            return children;
        }

        public void setChildren(Set<SalesOrderDetail> salesOrderDetail) {
            this.children = salesOrderDetail;
        }

        @OneToMany(mappedBy = "salesOrderDetail")
        @LazyCollection(LazyCollectionOption.EXTRA)
        public Set<SalesOrderDetailText> getSalesOrderDetailTexts() {
            return this.salesOrderDetailTexts;
        }

        public void setSalesOrderDetailTexts(
                Set<SalesOrderDetailText> SalesOrderDetailTexts) {
            this.salesOrderDetailTexts = SalesOrderDetailTexts;
        }
    }

    @Entity
    public class SalesOrderDetailText extends BusinessEntity {

        private SalesOrderDetail salesOrderDetail;
        private String text;

        @ManyToOne(fetch = FetchType.LAZY)
        public SalesOrderDetail getSalesOrderDetail() {
            return this.salesOrderDetail;
        }

        public void setSalesOrderDetail(SalesOrderDetail salesOrderDetail) {
            this.salesOrderDetail = salesOrderDetail;
        }

        public String getText() {
            return this.text;
        }

        public void setText(String text) {
            this.text = text;
        }

    }

    @Entity
    public static class Item extends BusinessEntity {

        private String name;
        private Manufacturer manufacturer;
        private Set<ItemVendorInfo> vendorInfos;

        protected Item() {
        }

        public Item(Long id, String name) {
            super(id);
            this.name = name;
        }

        public Item(long id) {
            super(id);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @OneToMany(mappedBy = "item", targetEntity = ItemVendorInfo.class)
        public Set<ItemVendorInfo> getVendorInfos() {
            return this.vendorInfos;
        }

        public void setVendorInfos(Set<ItemVendorInfo> vendorInfo) {
            this.vendorInfos = vendorInfo;
        }

        @ManyToOne(fetch = FetchType.LAZY)
        public Manufacturer getManufacturer() {
            return manufacturer;
        }

        public void setManufacturer(Manufacturer manufacturer) {
            this.manufacturer = manufacturer;
        }

        private Set<SalesOrderDetail> salesOrderDetails;

        @OneToMany(mappedBy = "item")
        public Set<SalesOrderDetail> getSalesOrderDetails() {
            return salesOrderDetails;
        }

        private Set<ItemText> itemTexts;

        public void setSalesOrderDetails(Set<SalesOrderDetail> salesOrderDetails) {
            this.salesOrderDetails = salesOrderDetails;
        }

        @OneToMany(mappedBy = "item")
        public Set<ItemText> getItemTexts() {
            return this.itemTexts;
        }

        public void setItemTexts(Set<ItemText> itemTexts) {
            this.itemTexts = itemTexts;
        }
    }


    @Entity
    public static class ItemVendorInfo extends BusinessEntity {

        private Item item;
        private Vendor vendor;
        private BigDecimal cost;

        protected ItemVendorInfo() {
        }

        public ItemVendorInfo(Long id, Item item, Vendor vendor, BigDecimal cost) {
            super(id);
            this.item = item;
            this.vendor = vendor;
            this.cost = cost;
        }

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(nullable = false)
        public Item getItem() {
            return item;
        }

        public void setItem(Item item) {
            this.item = item;
        }

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(nullable = false)
        public Vendor getVendor() {
            return this.vendor;
        }

        public void setVendor(Vendor Vendor) {
            this.vendor = Vendor;
        }

        public BigDecimal getCost() {
            return cost;
        }

        public void setCost(BigDecimal cost) {
            this.cost = cost;
        }
    }

    @Entity
    public static class SerialNumber extends BusinessEntity {
        private Item item;
        private String serialNumber;
        private SalesOrderDetail salesOrderDetail;
        private PurchaseOrderDetail purchaseOrderDetail;

        public SerialNumber() {

        }

        public SerialNumber(Long id) {
            super(id);
        }

        public SerialNumber(Long id, String serialNumber, SalesOrderDetail salesOrderDetail) {
            super(id);
            this.serialNumber = serialNumber;
            this.salesOrderDetail = salesOrderDetail;
        }

        @ManyToOne(fetch = FetchType.LAZY)
        public Item getItem() {
            return item;
        }

        public void setItem(Item item) {
            this.item = item;
        }

        public String getSerialNumber() {
            return serialNumber;
        }

        public void setSerialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
        }

        @ManyToOne(fetch = FetchType.LAZY)
        public SalesOrderDetail getSalesOrderDetail() {
            return salesOrderDetail;
        }

        public void setSalesOrderDetail(SalesOrderDetail salesOrderDetail) {
            this.salesOrderDetail = salesOrderDetail;
        }

        @ManyToOne(fetch = FetchType.LAZY)
        public PurchaseOrderDetail getPurchaseOrderDetail() {
            return purchaseOrderDetail;
        }

        public void setPurchaseOrderDetail(PurchaseOrderDetail purchaseOrderDetail) {
            this.purchaseOrderDetail = purchaseOrderDetail;
        }
    }

    @Entity
    public static class Vendor extends BusinessEntity {

        private String name;
        private Set<ItemVendorInfo> itemVendorInfos;

        protected Vendor() {
        }

        public Vendor(Long id, String name) {
            super(id);
            this.name = name;
        }

        public Vendor(long id) {
            super(id);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @OneToMany(mappedBy = "vendor")
        public Set<ItemVendorInfo> getItemVendorInfos() {
            return itemVendorInfos;
        }

        public void setItemVendorInfos(Set<ItemVendorInfo> itemVendorInfos) {
            this.itemVendorInfos = itemVendorInfos;
        }

    }

    @Entity
    public static class Manufacturer extends BusinessEntity {

        private String name;

        public Manufacturer() {

        }

        public Manufacturer(Long id, String name) {
            super(id);
            this.name = name;
        }

        public Manufacturer(long id) {
            super(id);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        private Collection<Item> items = new ArrayList<>();

        @OneToMany(mappedBy = "manufacturer")
        public Collection<Item> getItems() {
            return items;
        }

        public void setItems(Collection<Item> items) {
            this.items = items;
        }
    }

    @Entity
    public static class ItemText extends BusinessEntity {
        private Item item;

        public ItemText() {
        }

        public ItemText(long id) {
            super(id);
        }

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "PARENT_ID")
        public Item getItem() {
            return this.item;
        }

        public void setItem(Item item) {
            this.item = item;
        }

    }

    @Entity
    public static class PurchaseOrder extends BusinessEntity {
        public PurchaseOrder() {

        }

        public PurchaseOrder(long id) {
            super(id);
        }

        private String poDescription;
        private Set<PurchaseOrderDetail> poDetails;

        public String getPoDescription() {
            return poDescription;
        }

        public void setPoDescription(String poDescription) {
            this.poDescription = poDescription;
        }

        @OneToMany(mappedBy = "po")
        @Cascade({org.hibernate.annotations.CascadeType.DETACH})
        public Set<PurchaseOrderDetail> getPoDetails() {
            return poDetails;
        }

        public void setPoDetails(Set<PurchaseOrderDetail> poDetails) {
            this.poDetails = poDetails;
        }
    }

    @Entity
    public static class PurchaseOrderDetail extends BusinessEntity {
        private PurchaseOrder po;
        private Item item;
        private String vendorRmaNumber;
        private Set<SerialNumber> serialNumbers;

        public PurchaseOrderDetail() {

        }

        public PurchaseOrderDetail(long id) {
            super(id);
        }

        public PurchaseOrderDetail(Long id, PurchaseOrder po, Item item) {
            super(id);
            this.po = po;
            this.item = item;
        }

        @ManyToOne(fetch = FetchType.LAZY)
        public Item getItem() {
            return item;
        }

        public void setItem(Item item) {
            this.item = item;
        }

        @ManyToOne(fetch = FetchType.LAZY)
        public PurchaseOrder getPo() {
            return this.po;
        }

        public void setPo(PurchaseOrder Po) {
            this.po = Po;
        }


        @OneToMany(mappedBy = "purchaseOrderDetail")
        public Set<SerialNumber> getSerialNumbers() {
            return serialNumbers;
        }

        public void setSerialNumbers(Set<SerialNumber> serialNumbers) {
            this.serialNumbers = serialNumbers;
        }

        public String getVendorRmaNumber() {
            return vendorRmaNumber;
        }

        public void setVendorRmaNumber(String vendorRmaNumber) {
            this.vendorRmaNumber = vendorRmaNumber;
        }
    }
}
