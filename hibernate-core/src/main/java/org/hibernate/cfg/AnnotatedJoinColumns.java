package org.hibernate.cfg;

import org.hibernate.AnnotationException;
import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.boot.model.naming.EntityNaming;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.ImplicitJoinColumnNameSource;
import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.ImplicitPrimaryKeyJoinColumnNameSource;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.source.spi.AttributePath;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.MetadataBuildingOptions;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Join;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.SimpleValue;

import java.util.Locale;
import java.util.Map;

import static org.hibernate.cfg.BinderHelper.isEmptyOrNullAnnotationValue;
import static org.hibernate.cfg.PropertyHolderBuilder.buildPropertyHolder;
import static org.hibernate.internal.util.StringHelper.isQuoted;

/**
 * A list of {@link jakarta.persistence.JoinColumn}s that form a single join
 * condition, similar in concept to {@link jakarta.persistence.JoinColumns},
 * but not every instance of this class corresponds to an explicit annotation
 * in the Java code.
 *
 * @author Gavin King
 */
public class AnnotatedJoinColumns {

    private AnnotatedJoinColumn[] columns;
    private PropertyHolder propertyHolder;
    private String propertyName; // this is really a .-separated property path
    private MetadataBuildingContext buildingContext;

    //TODO: do we really need to hang so many strings off this class?
    private String mappedBy;
    private String mappedByPropertyName; //property name on the owning side if any
    private String mappedByTableName; //table name on the mapped by side if any
    private String mappedByEntityName;
    private boolean elementCollection;
    private String manyToManyOwnerSideEntityName;

    public AnnotatedJoinColumns() {
        mappedBy = null;
    }

    public AnnotatedJoinColumn[] getColumns() {
        return columns;
    }

    public void setColumns(AnnotatedJoinColumn[] columns) {
        this.columns = columns;
        if ( columns != null ) {
            for ( AnnotatedJoinColumn column : columns ) {
                column.setParent( this );
            }
        }
    }

    public String getMappedBy() {
        return mappedBy;
    }

    public void setMappedBy(String mappedBy) {
        this.mappedBy = mappedBy;
    }

    /**
     * @return true if the association mapping annotation did specify
     *         {@link jakarta.persistence.OneToMany#mappedBy() mappedBy},
     *         meaning that this {@code @JoinColumn} mapping belongs to an
     *         unowned many-valued association.
     */
    public boolean hasMappedBy() {
        return !isEmptyOrNullAnnotationValue( getMappedBy() );
    }

    public String getMappedByEntityName() {
        return mappedByEntityName;
    }

    public String getMappedByPropertyName() {
        return mappedByPropertyName;
    }

    public String getMappedByTableName() {
        return mappedByTableName;
    }

    public PropertyHolder getPropertyHolder() {
        return propertyHolder;
    }

    public void setPropertyHolder(PropertyHolder propertyHolder) {
        this.propertyHolder = propertyHolder;
    }

    /**
     * Override persistent class on oneToMany Cases for late settings
     * Must only be used on second level pass binding
     */
    public void setPersistentClass(
            PersistentClass persistentClass,
            Map<String, Join> joins,
            Map<XClass, InheritanceState> inheritanceStatePerClass) {
        // TODO shouldn't we deduce the class name from the persistentClass?
        propertyHolder = buildPropertyHolder(
                persistentClass,
                joins,
                buildingContext,
                inheritanceStatePerClass
        );
        for ( AnnotatedJoinColumn column : columns ) {
            column.setPropertyHolder( propertyHolder );
        }
    }

    public void setBuildingContext(MetadataBuildingContext buildingContext) {
        this.buildingContext = buildingContext;
    }

    public boolean isElementCollection() {
        return elementCollection;
    }

    public void setElementCollection(boolean elementCollection) {
        this.elementCollection = elementCollection;
    }

    public void setManyToManyOwnerSideEntityName(String entityName) {
        manyToManyOwnerSideEntityName = entityName;
    }

    public String getManyToManyOwnerSideEntityName() {
        return manyToManyOwnerSideEntityName;
    }

    public void setMappedBy(String entityName, String logicalTableName, String mappedByProperty) {
        mappedByEntityName = entityName;
        mappedByTableName = logicalTableName;
        mappedByPropertyName = mappedByProperty;
    }

    String buildDefaultColumnName(PersistentClass referencedEntity, String logicalReferencedColumn) {
        final MetadataBuildingOptions options = buildingContext.getBuildingOptions();
        final ImplicitNamingStrategy implicitNamingStrategy = options.getImplicitNamingStrategy();
        final PhysicalNamingStrategy physicalNamingStrategy = options.getPhysicalNamingStrategy();

        boolean mappedBySide = getMappedByTableName() != null || getMappedByPropertyName() != null;
        boolean ownerSide = getPropertyName() != null;
        boolean isRefColumnQuoted = isQuoted( logicalReferencedColumn );

        final InFlightMetadataCollector collector = buildingContext.getMetadataCollector();
        final Database database = collector.getDatabase();

        Identifier columnIdentifier;
        if ( mappedBySide ) {
            // NOTE : While it is completely misleading here to allow for the combination
            //		of a "JPA ElementCollection" to be mappedBy, the code that uses this
            // 		class relies on this behavior for handling the inverse side of
            // 		many-to-many mappings
            columnIdentifier = implicitNamingStrategy.determineJoinColumnName(
                    new UnownedImplicitJoinColumnNameSource( referencedEntity, logicalReferencedColumn )
            );

            //one element was quoted so we quote
            if ( isRefColumnQuoted || isQuoted( getMappedByTableName() ) ) {
                columnIdentifier = Identifier.quote( columnIdentifier );
            }
        }
        else if ( ownerSide ) {
            final String logicalTableName = collector.getLogicalTableName( referencedEntity.getTable() );

            columnIdentifier = implicitNamingStrategy.determineJoinColumnName(
                    new OwnedImplicitJoinColumnNameSource( referencedEntity, logicalTableName, logicalReferencedColumn )
            );

            // HHH-11826 magic. See Ejb3Column and the HHH-6005 comments
            if ( columnIdentifier.getText().contains( "_collection&&element_" ) ) {
                columnIdentifier = Identifier.toIdentifier(
                        columnIdentifier.getText().replace( "_collection&&element_", "_" ),
                        columnIdentifier.isQuoted()
                );
            }

            //one element was quoted so we quote
            if ( isRefColumnQuoted || isQuoted( logicalTableName ) ) {
                columnIdentifier = Identifier.quote( columnIdentifier );
            }
        }
        else {
            final Identifier logicalTableName = database.toIdentifier(
                    collector.getLogicalTableName( referencedEntity.getTable() )
            );

            // is an intra-entity hierarchy table join so copy the name by default
            columnIdentifier = implicitNamingStrategy.determinePrimaryKeyJoinColumnName(
                    new ImplicitPrimaryKeyJoinColumnNameSource() {
                        @Override
                        public MetadataBuildingContext getBuildingContext() {
                            return buildingContext;
                        }

                        @Override
                        public Identifier getReferencedTableName() {
                            return logicalTableName;
                        }

                        @Override
                        public Identifier getReferencedPrimaryKeyColumnName() {
                            return database.toIdentifier( logicalReferencedColumn );
                        }
                    }
            );

            if ( !columnIdentifier.isQuoted() && ( isRefColumnQuoted || logicalTableName.isQuoted() ) ) {
                columnIdentifier = Identifier.quote( columnIdentifier );
            }
        }

        final JdbcEnvironment jdbcEnvironment = database.getJdbcEnvironment();
        return physicalNamingStrategy.toPhysicalColumnName( columnIdentifier, jdbcEnvironment )
                .render( jdbcEnvironment.getDialect() );
    }

    /**
     * A property path relative to the {@link #getPropertyHolder() PropertyHolder}.
     */
    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    /**
     * @deprecated this is not a column-level setting, so it's better to
     *             do this work somewhere else
     */
    @Deprecated
    private ImplicitJoinColumnNameSource.Nature getImplicitNature() {
        if ( getPropertyHolder().isEntity() ) {
            return ImplicitJoinColumnNameSource.Nature.ENTITY;
        }
        else if ( isElementCollection() ) {
            return ImplicitJoinColumnNameSource.Nature.ELEMENT_COLLECTION;
        }
        else {
            return ImplicitJoinColumnNameSource.Nature.ENTITY_COLLECTION;
        }
    }

    private class UnownedImplicitJoinColumnNameSource implements ImplicitJoinColumnNameSource {
        final AttributePath attributePath;
        final Nature implicitNamingNature;

        private final EntityNaming entityNaming;

        private final Identifier referencedTableName;
        private final PersistentClass referencedEntity;
        private final String logicalReferencedColumn;

        final InFlightMetadataCollector collector = buildingContext.getMetadataCollector();
        final Database database = collector.getDatabase();

        public UnownedImplicitJoinColumnNameSource(PersistentClass referencedEntity, String logicalReferencedColumn) {
            this.referencedEntity = referencedEntity;
            this.logicalReferencedColumn = logicalReferencedColumn;
            attributePath = AttributePath.parse(getMappedByPropertyName());
            implicitNamingNature = getImplicitNature();
            entityNaming = new EntityNaming() {
                @Override
                public String getClassName() {
                    return referencedEntity.getClassName();
                }

                @Override
                public String getEntityName() {
                    return referencedEntity.getEntityName();
                }

                @Override
                public String getJpaEntityName() {
                    return referencedEntity.getJpaEntityName();
                }
            };
            referencedTableName = database.toIdentifier(getMappedByTableName());
        }

        @Override
        public Nature getNature() {
            return implicitNamingNature;
        }

        @Override
        public EntityNaming getEntityNaming() {
            return entityNaming;
        }

        @Override
        public AttributePath getAttributePath() {
            return attributePath;
        }

        @Override
        public Identifier getReferencedTableName() {
            return referencedTableName;
        }

        @Override
        public Identifier getReferencedColumnName() {
            if ( logicalReferencedColumn != null ) {
                return database.toIdentifier(logicalReferencedColumn);
            }

            if ( getMappedByEntityName() == null || getMappedByPropertyName() == null ) {
                return null;
            }

            final Property mappedByProperty = collector.getEntityBinding( getMappedByEntityName() )
                    .getProperty( getMappedByPropertyName() );
            final SimpleValue value = (SimpleValue) mappedByProperty.getValue();
            if ( value.getSelectables().isEmpty() ) {
                throw new AnnotationException(
                        String.format(
                                Locale.ENGLISH,
                                "Association '%s' is 'mappedBy' a property '%s' of entity '%s' with no columns",
                                propertyHolder.getPath(),
                                getMappedByPropertyName(),
                                getMappedByEntityName()
                        )
                );
            }
            final Selectable selectable = value.getSelectables().get(0);
            if ( !(selectable instanceof Column) ) {
                throw new AnnotationException(
                        String.format(
                                Locale.ENGLISH,
                                "Association '%s' is 'mappedBy' a property '%s' of entity '%s' which maps to a formula",
                                propertyHolder.getPath(),
                                getMappedByPropertyName(),
                                propertyHolder.getPath()
                        )
                );
            }
            if ( value.getSelectables().size()>1 ) {
                throw new AnnotationException(
                        String.format(
                                Locale.ENGLISH,
                                "Association '%s' is 'mappedBy' a property '%s' of entity '%s' with multiple columns",
                                propertyHolder.getPath(),
                                getMappedByPropertyName(),
                                propertyHolder.getPath()
                        )
                );
            }
            return database.toIdentifier( ( (Column) selectable ).getQuotedName() );
        }

        @Override
        public MetadataBuildingContext getBuildingContext() {
            return buildingContext;
        }
    }

    private class OwnedImplicitJoinColumnNameSource implements ImplicitJoinColumnNameSource {
        final Nature implicitNamingNature;

        private final EntityNaming entityNaming;

        private final AttributePath attributePath;
        private final Identifier referencedTableName;
        private final Identifier referencedColumnName;
        private final PersistentClass referencedEntity;
        private final String logicalTableName;
        private final String logicalReferencedColumn;

        final InFlightMetadataCollector collector = buildingContext.getMetadataCollector();
        final Database database = collector.getDatabase();

        public OwnedImplicitJoinColumnNameSource(PersistentClass referencedEntity, String logicalTableName, String logicalReferencedColumn) {
            this.referencedEntity = referencedEntity;
            this.logicalTableName = logicalTableName;
            this.logicalReferencedColumn = logicalReferencedColumn;
            implicitNamingNature = getImplicitNature();
            entityNaming = new EntityNaming() {
                @Override
                public String getClassName() {
                    return referencedEntity.getClassName();
                }

                @Override
                public String getEntityName() {
                    return referencedEntity.getEntityName();
                }

                @Override
                public String getJpaEntityName() {
                    return referencedEntity.getJpaEntityName();
                }
            };
            attributePath = AttributePath.parse(getPropertyName());
            referencedTableName = database.toIdentifier(logicalTableName);
            referencedColumnName = database.toIdentifier(logicalReferencedColumn);
        }

        @Override
        public Nature getNature() {
            return implicitNamingNature;
        }

        @Override
        public EntityNaming getEntityNaming() {
            return entityNaming;
        }

        @Override
        public AttributePath getAttributePath() {
            return attributePath;
        }

        @Override
        public Identifier getReferencedTableName() {
            return referencedTableName;
        }

        @Override
        public Identifier getReferencedColumnName() {
            return referencedColumnName;
        }

        @Override
        public MetadataBuildingContext getBuildingContext() {
            return buildingContext;
        }
    }
}
