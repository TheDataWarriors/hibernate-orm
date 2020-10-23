import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import java.util.Set;

@Entity
public class TheEntity {
	@Id
	private Integer id;
	private String name;

	@Embedded
	private TheEmbeddable theEmbeddable;

	@ManyToOne
	@JoinColumn
	private TheEntity theManyToOne;

	@OneToMany( mappedBy = "theManyToOne" )
	private Set<TheEntity> theOneToMany;

	@OneToMany
	@JoinColumn( name = "owner_id" )
	private Set<TheEmbeddable> theEmbeddableCollection;


	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public TheEmbeddable getTheEmbeddable() {
		return theEmbeddable;
	}

	public void setTheEmbeddable(TheEmbeddable theEmbeddable) {
		this.theEmbeddable = theEmbeddable;
	}

	public TheEntity getTheManyToOne() {
		return theManyToOne;
	}

	public void setTheManyToOne(TheEntity theManyToOne) {
		this.theManyToOne = theManyToOne;
	}

	public Set<TheEntity> getTheOneToMany() {
		return theOneToMany;
	}

	public void setTheOneToMany(Set<TheEntity> theOneToMany) {
		this.theOneToMany = theOneToMany;
	}

	public Set<TheEmbeddable> getTheEmbeddableCollection() {
		return theEmbeddableCollection;
	}

	public void setTheEmbeddableCollection(Set<TheEmbeddable> theEmbeddableCollection) {
		this.theEmbeddableCollection = theEmbeddableCollection;
	}
}