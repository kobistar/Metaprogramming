package sk.tuke.meta.example;

import javax.persistence.*;

@Entity
@Table(name = "Oddelenie")
public class Department {

    @Id
    @Column(name = "idecko2"/*, unique = true, nullable = false*/)
    private long pk;
    @Column(name = "meno"/*, unique = false, nullable = true*/)
    private String name;
    @Column(name = "oznacenie"/*, unique = true, nullable = false*/)
    private String code;

    public Department() {
    }

    public Department(String name, String code) {
        this.name = name;
        this.code = code;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }

    public long getPk(){ return pk; }
    public void setPk(long pk){ this.pk = pk; }
    public String toString() {
        return String.format("Department %d: %s (%s)", pk, name, code);
    }

}
