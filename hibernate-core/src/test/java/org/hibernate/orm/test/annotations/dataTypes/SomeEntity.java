/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.annotations.dataTypes;

import java.util.Date;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * @author Steve Ebersole
 */
@Entity
@Table(name = "SOMEENTITY")
@Access(AccessType.FIELD)
public class SomeEntity {
	@Id
	@Temporal(TemporalType.DATE)
	@Column(name = "ID")
	private Date id;
	@Column(name = "TIMEDATA")
	private java.sql.Time timeData;
	@Column(name = "TSDATA")
	private java.sql.Timestamp tsData;
	@Lob
	private Byte[] byteData;
	private Character[] charData;

	public SomeEntity() {
	}

	public SomeEntity(Date id) {
		this.id = id;
	}

	public Date getId() {
		return id;
	}

	public void setId(Date id) {
		this.id = id;
	}

	public Character[] getCharData() {
		return charData;
	}

	public void setCharData(Character[] charData) {
		this.charData = charData;
	}

	public java.sql.Time getTimeData() {
		return timeData;
	}

	public void setTimeData(java.sql.Time timeData) {
		this.timeData = timeData;
	}

	public java.sql.Timestamp getTsData() {
		return tsData;
	}

	public void setTsData(java.sql.Timestamp tsData) {
		this.tsData = tsData;
	}

	public Byte[] getByteData() {
		return byteData;
	}

	public void setByteData(Byte[] byteData) {
		this.byteData = byteData;
	}
}
