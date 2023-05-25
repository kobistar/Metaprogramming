package sk.tuke.meta.assignment.persistence;

import javax.persistence.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.stream.Stream;


public class ReflectivePersistenceManager implements PersistenceManager {
    private final Connection connection;
    public ReflectivePersistenceManager(Connection connection) { this.connection = connection; }

    @Override
    public void createTables() {
        /*String fileName = "CreateTable.sql";
        Path start = Paths.get(""); // začnite hľadať od aktuálneho pracovného adresára
        try (Stream<Path> pathStream = Files.walk(start)){
            Optional<Path> cestaSuboru = pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst();

            if(cestaSuboru.isPresent()) {
                Scanner scanner = new Scanner(cestaSuboru.get());
                String[] commands;

                try { commands = scanner.useDelimiter("\\Z").next().split(";"); }
                catch (NoSuchElementException | IllegalFormatException | ArrayIndexOutOfBoundsException e) {
                    throw new RuntimeException("Invalid SQL file format", e); // výnimka pre zlý formát súboru
                }
                scanner.close();

                DatabaseMetaData metaData = connection.getMetaData();
                Statement statement = connection.createStatement();

                for (String command : commands) {
                    String tableName = getTableNameFromCreateStatement(command);
                    ResultSet tableResultSet = metaData.getTables(null, null, tableName, null);
                    if (tableResultSet.next()){
                        System.out.println("Table " + tableName + " already exists, skipping creation"); // tabuľka už existuje, takže nevytváram ju znovu
                        continue;
                    }
                    statement.execute(command);
                }
                statement.close();
            }
            else throw new NoSuchFileException(fileName); // výnimka pre chýbajúci súbor
        }
        catch (SQLException | IOException | RuntimeException e) { System.err.println("Failed to create database tables: " + e.getMessage()); }
*/
    }

    private String getTableNameFromCreateStatement(String createStatement) {
        String[] words;
        if(createStatement.contains("`")) {
            words = createStatement.split("`");
            return words[1];
        }
        words = createStatement.split(" ");
        return words[2];
    }

    @Override
    public <T> Optional<T> get(Class<T> type, long id) {
        if(type == null) return Optional.empty();
        if(!type.isAnnotationPresent(Entity.class)) return Optional.empty();
        String tableName = type.getSimpleName();
        if(type.isAnnotationPresent(Table.class) && type.getAnnotation(Table.class).name() != null) tableName = type.getAnnotation(Table.class).name();

        String idColumnName = null;
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)){
                idColumnName = field.getName();
                if(field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).name() != null) idColumnName = field.getAnnotation(Column.class).name();
            }
        }
        tableName = "`" + tableName + "`";
        idColumnName = "`" + idColumnName + "`";
        String selectQuery = "SELECT * FROM " + tableName + " WHERE " + idColumnName + " = ?";

        try (PreparedStatement stmt = connection.prepareStatement(selectQuery)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return Optional.of(createObjectFromResultSet(type, rs));

        } catch (SQLException | IllegalArgumentException ex){ ex.printStackTrace(); }
        return Optional.empty();
    }
    @Override
    public <T> List<T> getAll(Class<T> type) {
        List<T> entities = new ArrayList<>();
        if(type == null) return entities;

        String tableName = type.getSimpleName();
        if(type.getAnnotation(Table.class) != null && type.getAnnotation(Table.class).name() != null) tableName = type.getAnnotation(Table.class).name();

        tableName = "`" + tableName + "`";
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM " + tableName)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) entities.add(createObjectFromResultSet(type, rs));

        } catch (SQLException | IllegalArgumentException ex) { ex.printStackTrace(); }
        return entities;
    }

    @Override
    public <T> List<T> getBy(Class<T> type, String fieldName, Object value) {
        if(type == null || fieldName == null || value == null) return new ArrayList<>();
        List<T> result = new ArrayList<>();
        String tableName = type.getSimpleName();

        if(type.isAnnotationPresent(Table.class) && type.getAnnotation(Table.class).name() != null) tableName = type.getAnnotation(Table.class).name();
        for(Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).name() != null && field.getName().equals(fieldName))
                fieldName = field.getAnnotation(Column.class).name();
        }
        try {
            tableName = "`" + tableName + "`";
            fieldName = "`" + fieldName + "`";

            String sql = "SELECT * FROM " + tableName + " WHERE " + fieldName + "=?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setObject(1, value);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) result.add(createObjectFromResultSet(type, rs));

        } catch (SQLException ex) { throw new PersistenceException(ex.getMessage(), ex); }

        return result;
    }

    private <T> T createObjectFromResultSet(Class<T> type, ResultSet rs) throws SQLException {
        try {
            type.getDeclaredConstructor().setAccessible(true);
            T obj = type.getDeclaredConstructor().newInstance();

            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                String columnName = field.getName();
                if(field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).name() != null) columnName = field.getAnnotation(Column.class).name();

                if (field.isAnnotationPresent(ManyToOne.class)) {
                    Class<?> foreignKeyType = field.getType();
                    Optional<?> foreignKeyEntity = get(foreignKeyType, rs.getLong(columnName));
                    if (foreignKeyEntity.isPresent()) field.set(obj, foreignKeyEntity.get());

                } else field.set(obj, rs.getObject(columnName));
            }
            return obj;

        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new PersistenceException(ex.getMessage(), ex);
        }
    }

    @Override
    public long save(Object entity) {
        try {
            Class<?> entityType = entity.getClass();
            String tableName = entityType.getSimpleName().toUpperCase(); // získaj názov tabuľky pre daný typ entity
            Field[] fields = entityType.getDeclaredFields(); // získaj z triedy zoznam jej premenných
            List<String> columnNames = new ArrayList<>();
            List<String> columnValues = new ArrayList<>();
            List<String> columnUpdate = new ArrayList<>();

            String insertSql;
            String IdColumnName = null; //meno stĺpca v databáze
            String IdFieldName = null; //špecificky sa zameriava na meno premennej s @Id
            Long IdColumnValue = null;

            if(entityType.isAnnotationPresent(Table.class) && entityType.getAnnotation(Table.class).name() != null) tableName = entityType.getAnnotation(Table.class).name().toUpperCase();
            for (Field field : fields) {
                field.setAccessible(true);

                if(field.isAnnotationPresent(Column.class) && !field.getAnnotation(Column.class).nullable() && field.get(entity) == null)
                    throw new RuntimeException("Value cannot be null for column: " + field.getName());

                Object columnValue = field.get(entity); //získa hodnotu stĺpca

                // preskočím premennú, ktorá je anotovaná ako @Transient a identifikačný stĺpec, keďže ten sa generuje automaticky
                if (field.isAnnotationPresent(Transient.class)) continue;

                if (field.isAnnotationPresent(Id.class)) {
                    IdFieldName = field.getName();
                    IdColumnName = field.getName();
                    if(field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).name() != null) IdColumnName = field.getAnnotation(Column.class).name();
                    IdColumnValue = (long) columnValue;
                    continue;
                }
                if (field.isAnnotationPresent(ManyToOne.class)) columnValue = save(columnValue);

                columnValues.add( getColumnValue(columnValue) );
                String columnName = field.getName().toUpperCase();// získaj názov stĺpca a hodnotu pre danú premennú
                if(field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).name() != null) columnName = field.getAnnotation(Column.class).name().toUpperCase();
                columnNames.add("`" + columnName + "`");
                columnUpdate.add("`" + columnName + "` = " + getColumnValue(columnValue)); //toto sa pouziva len pre UPDATE

                if(field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).unique()) {
                    String checkSql = String.format("SELECT COUNT(*) FROM `%s` WHERE `%s` = %s", tableName, columnName, getColumnValue(field.get(entity)));
                    if(field.isAnnotationPresent(ManyToOne.class)) checkSql = String.format("SELECT COUNT(*) FROM `%s` WHERE `%s` = %s", tableName, columnName, columnValue);
                    try (PreparedStatement checkStatement = connection.prepareStatement(checkSql)) {
                        ResultSet checkResult = checkStatement.executeQuery();
                        if (checkResult.next() && checkResult.getInt(1) > 0) throw new RuntimeException("Value already exists in database for column: " + columnName);
                    }
                }
            }
            String columnUpdateSql = String.join(", ", columnUpdate); //toto sa pouziva len pre UPDATE
            String columnNamesSql = String.join(",", columnNames);
            String columnValuesSql = String.join(",", columnValues);

            tableName = "`" + tableName + "`";
            insertSql = String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columnNamesSql, columnValuesSql);
           // System.out.println("insertSql-> " + insertSql);
            if (IdColumnValue != null && IdColumnValue != 0){
                IdColumnName = "`" + IdColumnName + "`";
                insertSql = "UPDATE " + tableName + " SET " + columnUpdateSql + " WHERE " + IdColumnName + " = " + getColumnValue(IdColumnValue);
                //System.out.println("UpdatetSql-> " + insertSql);
            }

            if(!entityType.isAnnotationPresent(Entity.class)) return 0;
            try (PreparedStatement statement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                statement.executeUpdate();

                ResultSet generatedKeys = statement.getGeneratedKeys(); //zoberiem najnovsie id a urobim set objektu, ktory mi prisiel na save
                if (generatedKeys.next() && (IdColumnValue == null || IdColumnValue == 0)) { // ak sa správne vygeneruje kluc tak funkcia vrati true
                    Field idField = null;
                    if (IdFieldName != null) idField = entityType.getDeclaredField(IdFieldName);

                    long id = generatedKeys.getLong(1);
                    if (idField != null) {
                        idField.setAccessible(true);
                        idField.set(entity, id);
                    }
                    return id; // vráti id vloženého záznamu
                }
                else if(IdColumnValue != null) return IdColumnValue;
                else return 0;
            }
        } catch (SQLException | IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException("Failed to save entity " + entity.getClass().getSimpleName(), e);
        }
    }

    private String getColumnValue(Object value) { // pomocná metóda pre získanie SQL hodnoty pre danú premennú
        if (value == null) return "NULL";
        else if (value instanceof String || value instanceof Date || value instanceof java.time.LocalDateTime) return String.format("'%s'", value);
        else return value.toString();
    }

    @Override
    public void delete(Object entity) {
        Class<?> type = entity.getClass();

        try {
            Field idField = type.getDeclaredField(getIdFieldName(type)); // Get the annotated identifier field
            idField.setAccessible(true);

            Object id = idField.get(entity); // Get the identifier value of the entity
            String idColumnName = idField.getName();
            if(idField.isAnnotationPresent(Column.class) && idField.getAnnotation(Column.class).name() != null) idColumnName = idField.getAnnotation(Column.class).name();

            String tableName = type.getSimpleName().toUpperCase(); // Delete the record from the table with the given identifier
            if(type.isAnnotationPresent(Table.class) && idField.getAnnotation(Table.class).name() != null) tableName = type.getAnnotation(Table.class).name();

            tableName = "`" + tableName + "`";
            idColumnName = "`" + idColumnName + "`";
            String sql = "DELETE FROM " + tableName + " WHERE " + idColumnName + " = ?";
            PreparedStatement stmt = connection.prepareStatement(sql);
            stmt.setObject(1, id);
            stmt.executeUpdate();

        } catch (Exception e) {
            throw new PersistenceException(e);
        }
    }
    private String getIdFieldName(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) if (field.isAnnotationPresent(Id.class)) return field.getName();
        throw new IllegalArgumentException("Class " + clazz.getSimpleName() + " does not have a field annotated with @Id");
    }
}
