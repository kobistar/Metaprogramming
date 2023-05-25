package sk.tuke.meta.example;

import javax.persistence.*;

@Entity
@Table(name = "Osoba")
public class Person {

    @Id
    @Column(name = "idecko"/*, unique = true, nullable = false*/)
    private long id;
    @Column(name = "meno"/*, unique = true, nullable = false*/)
    private String surname;
    @Column(name = "priezvisko"/*, unique = false, nullable = false*/)
    private String name;
    @Column(name = "select"/*, unique = true, nullable = true*/)
    private int age;
    @ManyToOne
    @Column(name = "odd"/*, unique = true, nullable = false*/)
    private Department depart;

    public Person(String surname, String name, int age) {
        this.surname = surname;
        this.name = name;
        this.age = age;
    }

    public Person() {
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Department getDepart() {
        return depart;
    }

    public void setDepart(Department department) {
        this.depart = department;
    }

    @Override
    public String toString() {
        return String.format("Person %d: %s %s (%d)", id, surname, name, age);
    }
}
