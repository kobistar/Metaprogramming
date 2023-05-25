package sk.tuke.meta.example;

import sk.tuke.meta.example.GeneratedPersistenceManager;

import java.sql.Connection;
import java.sql.DriverManager;

public class Main {
    public static final String DB_PATH = "test.db";

    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);

        GeneratedPersistenceManager manager = new GeneratedPersistenceManager(conn);
        manager.createTables();

        Department development = new Department("Development", "DVLP");
        Department marketing = new Department("Marketing", "MARK");
        Department operations = new Department("Operations", "OPRS");

        Person hrasko = new Person("Janko", "Hrasko", 30);
        hrasko.setDepart(development);

        Person mrkvicka = new Person("Jozko", "Mrkvicka", 25);
        mrkvicka.setDepart(development);
        Person novak = new Person("Jan", "Novak", 45);
        novak.setDepart(marketing);

        manager.save(hrasko);
        hrasko.setAge(10);
        manager.save(hrasko);

        manager.save(mrkvicka);
        manager.save(novak);
        manager.save(operations);

       /* Optional<Person> DepartmentOptional = manager.get(Person.class,1L);
        if (DepartmentOptional.isPresent()) {
            Person department = DepartmentOptional.get();
            System.out.println("Found Department: " + department.getDepartment());

        } else System.out.println("Department not found.");
        // Get persons with surname "Hrasko"
        List<Person> persons = manager.getBy(Person.class, "age", "10");
        for (Person person : persons) {
            System.out.println(person);
            System.out.println("  " + person.getDepartment());
        }

        List<Person> persons2 = manager.getAll(Person.class);
        for (Person person : persons2) {
            System.out.println(person);
            System.out.println("  " + person.getDepartment());
        }

        manager.delete(hrasko);
        manager.delete(mrkvicka);
        manager.delete(development);
        manager.delete(novak);
        manager.delete(marketing);
*/
        conn.close();
    }
}
