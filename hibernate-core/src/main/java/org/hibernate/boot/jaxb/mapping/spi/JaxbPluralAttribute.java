/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.boot.jaxb.mapping.spi;

import java.util.List;

import jakarta.persistence.EnumType;
import jakarta.persistence.TemporalType;

/**
 * JAXB binding interface for plural attributes
 *
 * @author Brett Meyer
 */
public interface JaxbPluralAttribute extends JaxbFetchableAttribute {
	JaxbPluralFetchModeImpl getFetchMode();

	void setFetchMode(JaxbPluralFetchModeImpl mode);

	String getOrderBy();

	void setOrderBy(String value);

	JaxbOrderColumnImpl getOrderColumn();

	void setOrderColumn(JaxbOrderColumnImpl value);

	String getSort();

	void setSort(String value);

	JaxbMapKeyImpl getMapKey();

	void setMapKey(JaxbMapKeyImpl value);

	JaxbMapKeyClassImpl getMapKeyClass();

	void setMapKeyClass(JaxbMapKeyClassImpl value);

	TemporalType getMapKeyTemporal();

	void setMapKeyTemporal(TemporalType value);

	EnumType getMapKeyEnumerated();

	void setMapKeyEnumerated(EnumType value);

	List<JaxbAttributeOverrideImpl> getMapKeyAttributeOverride();

	List<JaxbConvertImpl> getMapKeyConvert();

	JaxbMapKeyColumnImpl getMapKeyColumn();

	void setMapKeyColumn(JaxbMapKeyColumnImpl value);

	List<JaxbMapKeyJoinColumnImpl> getMapKeyJoinColumn();

	JaxbForeignKeyImpl getMapKeyForeignKey();

	void setMapKeyForeignKey(JaxbForeignKeyImpl value);
}
