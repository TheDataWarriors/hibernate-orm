package org.hibernate.serialization.entity;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

@Entity
public class BuildRecord {

	@EmbeddedId
	private BuildRecordId id;

	private String name;

	public BuildRecordId getId() {
		return id;
	}

	public void setId(BuildRecordId id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
