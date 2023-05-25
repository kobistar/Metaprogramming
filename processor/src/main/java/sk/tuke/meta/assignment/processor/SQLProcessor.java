package sk.tuke.meta.assignment.processor;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;


import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.persistence.*;
import javax.tools.*;
import java.io.*;
import java.util.*;

@SupportedAnnotationTypes("javax.persistence.Entity")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SQLProcessor extends AbstractProcessor {

    private static final String TEMPLATE_PATH = "sk/tuke/meta/persistence/" ;
    private VelocityEngine velocity;
    private boolean onceRun = false;
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        velocity = new VelocityEngine();
        velocity.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocity.setProperty("classpath.resource.loader.class",
                ClasspathResourceLoader.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if (!onceRun) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Entity.class);
            List<Name> field = new ArrayList<>();
            for (Element element : elements) {
                field.add(element.getSimpleName());
                try {
                    generatedDAOClass(element, elements);
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR, e.getMessage());
                }
            }
            try {
                generatePersistenceManager(elements, field);
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR, e.getMessage());
            }
            onceRun = true;
        }
        return true;
    }

    private void generatedDAOClass(Element entityType, Set<? extends Element> entityTypes) throws IOException {
        var packageName = processingEnv.getElementUtils().getPackageOf(entityType).toString();
        var jfo = processingEnv.getFiler().createSourceFile(entityType.toString() + "DAO");

        try(Writer writer = jfo.openWriter()) {
            var template = velocity.getTemplate(TEMPLATE_PATH + "DAO.java.vm");
            var context = new VelocityContext();
            context.put("package", packageName);
            context.put("entityType", entityType.getSimpleName().toString());
            context.put("newline", "\n");
            context.put("createTableString", processGenerateEntity(entityType, entityTypes));
            context.put("insertTableString", generateInsertString(entityType));
            context.put("updateTableString", generateUpdateString(entityType));
            context.put("tableName", findTableName(entityType));
            context.put("variables", generateInsertString(entityType));
            context.put("idColumnName", findIdColumnName(entityType));
            context.put("getString", generateGetString(entityType));
            context.put("variables", findVariables(entityType));
            context.put("ManyToOne", findManyToOne(entityType));
            context.put("ManyToOneClass", findManyToOneClass(entityType));
            context.put("ForeignKey", findForeignKey(entityType));
            context.put("ManyToOneName", findManyToOneName(entityType));
            context.put("getAllString", generateGetAllString(entityType));
            context.put("columnList", columnList(entityType));
            context.put("variableList", variableList(entityType));
            context.put("deleteString", generateDeleteString(entityType));
            context.put("idGetVariable", getIdGetter(entityType));
            template.merge(context, writer);
        }
    }

    private String getIdGetter(Element entityType) {
        List<? extends Element> enclosedElements = entityType.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();
        for (Element element : enclosedElements)
            if (element.getAnnotation(Transient.class) == null && element.getAnnotation(Id.class) != null) return element.getSimpleName().toString().substring(0, 1).toUpperCase() + element.getSimpleName().toString().substring(1);;

        return null;
    }

    private String generateDeleteString(Element entityType) {
        return "DELETE FROM `" + findTableName(entityType) + "` WHERE `" + findIdColumnName(entityType) + "` = ?";
    }

    private String variableList(Element entityType){
        List<String> list = new ArrayList<>();
        List<? extends Element> enclosedElements = entityType.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();
        for (Element element : enclosedElements)
            list.add(element.getSimpleName().toString());

        return list.toString();
    }
    private String columnList(Element entityType){
        List<String> list = new ArrayList<>();
        List<? extends Element> enclosedElements = entityType.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();
        for (Element element : enclosedElements){
            if(element.getAnnotation(Column.class) != null){
                if (element.getAnnotation(Column.class).name() != null && !(element.getAnnotation(Column.class).name().equals("") || element.getAnnotation(Column.class).name().equals(" "))){
                    list.add(element.getAnnotation(Column.class).name());
                }
            }
        }
        return list.toString();
    }
    private String generateGetAllString(Element entityType) {
        String tableName = findTableName(entityType);
        if(tableName == null) return null;
        return "SELECT * FROM `" + tableName.toUpperCase() + "`";
    }
    private String findManyToOneName(Element entityType) {
        List<? extends Element> enclosedElements = entityType.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();
        for (Element element : enclosedElements) {
            if (element.getAnnotation(ManyToOne.class) != null){
                String manyToOneName = element.getSimpleName().toString();
                return "set" + manyToOneName.substring(0, 1).toUpperCase() + manyToOneName.substring(1);
            }
        }
        return "null";
    }

    private String findForeignKey(Element entityType){
        List<? extends Element> enclosedElements = entityType.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();
        for (Element element : enclosedElements) {
            if (element.getAnnotation(ManyToOne.class) != null && element.getAnnotation(Column.class) != null) {
                if (element.getAnnotation(Column.class).name() != null && !(element.getAnnotation(Column.class).name().equals("") || element.getAnnotation(Column.class).name().equals(" ")))
                    return element.getAnnotation(Column.class).name().toUpperCase();
            }
            else if(element.getAnnotation(ManyToOne.class) != null) return element.getSimpleName().toString().toUpperCase();
        }
        return "null";
    }
    private String findManyToOneClass(Element entityType) {
        List<? extends Element> enclosedElements = entityType.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();
        for (Element element : enclosedElements){
            if(element.getAnnotation(ManyToOne.class) != null)
                return processingEnv.getElementUtils().getTypeElement(element.asType().toString()).getSimpleName().toString();
        }
        return entityType.getSimpleName().toString();
    }

    private boolean findManyToOne(Element entityType) {
        List<? extends Element> enclosedElements = entityType.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();
        for (Element element : enclosedElements) if(element.getAnnotation(ManyToOne.class) != null) return true;
        return false;
    }

    private void generatePersistenceManager(Set<? extends Element> entityTypes, List<Name> field) throws IOException{
        var packageName = processingEnv.getElementUtils().getPackageOf(entityTypes.iterator().next()).toString();
        var jfo = processingEnv.getFiler().createSourceFile("GeneratedPersistenceManager");

        try(Writer writer = jfo.openWriter()) {
            var template = velocity.getTemplate(TEMPLATE_PATH + "GeneratedPersistenceManager.java.vm");
            var context = new VelocityContext();
            context.put("package", packageName);
            context.put("entities", entityTypes);
            context.put("names", field);
            template.merge(context, writer);
        }
    }

    private String processGenerateEntity(Element entityType, Set<? extends Element> entityTypes){
        String entityName = entityType.getSimpleName().toString();
        Table table_name = entityType.getAnnotation(Table.class);
        if(table_name != null && !(table_name.name().equals("") || table_name.name().equals(" "))) entityName = table_name.name();

        return String.format("CREATE TABLE IF NOT EXISTS `" + entityName.toUpperCase() + "` (" + String.join(",", getColumnNames(entityType, entityTypes)) + ");");
    }

    private List<String> getColumnNames(Element entityType, Set<? extends Element> entityTypes) {
        String columnType;
        List<String> columnDefinitions = new ArrayList<>();
        Table table_name;

        List<? extends Element> enclosedElements = entityType.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();

        for (Element element : enclosedElements) {

            if (element.getAnnotation(Transient.class) != null) continue;

            else if (element.getAnnotation(Id.class) != null) columnType = "INTEGER PRIMARY KEY AUTOINCREMENT";

            else if (element.getAnnotation(ManyToOne.class) != null) {
                String table = processingEnv.getElementUtils().getTypeElement(element.asType().toString()).getSimpleName().toString().toUpperCase();
                table_name = processingEnv.getTypeUtils().asElement(element.asType()).getAnnotation(Table.class);
                if (table_name != null && !(Objects.equals(table_name.name(), " ") || Objects.equals(table_name.name(), "")))
                    table = table_name.name().toUpperCase();

                columnType = "INTEGER REFERENCES `" + table + "`(`" + getNameForeigner(entityTypes, element.asType().toString()) + "`)";
                if (element.getAnnotation(Column.class) != null && element.getAnnotation(Column.class).unique())
                    columnType = "INTEGER UNIQUE REFERENCES `" + table + "`(`" + getNameForeigner(entityTypes, element.asType().toString()) + "`)";
            } else {
                columnType = getColumnType(element.asType());
                if (element.getAnnotation(Column.class) != null && element.getAnnotation(Column.class).unique())
                    columnType = getColumnType(element.asType()) + " UNIQUE";
                if (element.getAnnotation(Column.class) != null && !element.getAnnotation(Column.class).nullable())
                    columnType = getColumnType(element.asType()) + " NOT NULL";
                if (element.getAnnotation(Column.class) != null && !element.getAnnotation(Column.class).nullable() && element.getAnnotation(Column.class).unique())
                    columnType = getColumnType(element.asType()) + " NOT NULL UNIQUE";
            }

            String columnName = element.getSimpleName().toString().toUpperCase();
            if (element.getAnnotation(Column.class) != null && !(element.getAnnotation(Column.class).name().equals("") || element.getAnnotation(Column.class).name().equals(" ")))
                columnName = element.getAnnotation(Column.class).name().toUpperCase();
            columnDefinitions.add("`" + columnName + "` " + columnType);
        }
        return columnDefinitions;
    }

    private String getColumnType(TypeMirror type) {
        if (type.toString().equals("java.lang.String") || type.toString().equals("java.util.Date") || type.toString().equals("java.time.LocalDateTime")) return "TEXT";
        else if (type.toString().equals("java.lang.Integer") || type.toString().equals("int") || type.toString().equals("java.lang.Long") || type.toString().equals("long")) return "INTEGER";
        else if (type.toString().equals("java.lang.Double") || type.toString().equals("double") || type.toString().equals("java.lang.Float") || type.toString().equals("float")) return "REAL";
        else if (type.toString().equals("java.lang.Boolean") || type.toString().equals("boolean")) return "NUMERIC";
        else return "BLOB";
    }


    private String getNameForeigner(Set<? extends Element> elements, String ForeignKeyTable) {
        TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(ForeignKeyTable);
        if(typeElement == null) return "ID";
        String className = typeElement.getSimpleName().toString();

        for (Element type : elements) {
            if (type.getAnnotation(Entity.class) == null) continue; // ak trieda nie je anotovaná ako @Entity, tak ju preskočím
            if (type.getSimpleName().toString().equals(className)) {
                List<? extends Element> enclosedElements = type.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();

                for (Element element : enclosedElements) {
                    if (element.getAnnotation(Id.class) != null) {
                        if (element.getAnnotation(Column.class) != null){
                            if(element.getAnnotation(Column.class).name() != null && !(element.getAnnotation(Column.class).name().equals("") || element.getAnnotation(Column.class).name().equals(" ")))
                                return element.getAnnotation(Column.class).name().toUpperCase();
                        }
                        return element.getSimpleName().toString().toUpperCase();
                    }
                }
            }
        }
        return "ID";
    }


    private String generateInsertString(Element entityType){
        List<? extends Element> enclosedElements = entityType.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();
        List<String> columnNames = new ArrayList<>();

        for (Element element : enclosedElements) {
            if(element.getAnnotation(Transient.class) == null && element.getAnnotation(Id.class) == null && isColumnName(element)) columnNames.add("`" + element.getAnnotation(Column.class).name().toUpperCase() + "`");
            else if(element.getAnnotation(Transient.class) == null && element.getAnnotation(Id.class) == null) columnNames.add("`" + element.getSimpleName().toString().toUpperCase() + "`");
        }
        return "INSERT INTO `" + findTableName(entityType) + "` (" + String.join(",", columnNames) + ") VALUES (";
        // return columnNames.toString();
    }

    private String generateUpdateString(Element entityType) {
        List<? extends Element> enclosedElements = entityType.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();
        List<String> columnNames = new ArrayList<>();
        for (Element element : enclosedElements) {
            if(element.getAnnotation(Transient.class) == null && element.getAnnotation(Id.class) == null && isColumnName(element)) columnNames.add("`" + element.getAnnotation(Column.class).name().toUpperCase() + "` ");
            else if(element.getAnnotation(Transient.class) == null && element.getAnnotation(Id.class) == null && !isColumnName(element)) columnNames.add("`" + element.getSimpleName().toString().toUpperCase() + "` ");
        }

        return "UPDATE `" + findTableName(entityType) + "` SET " + String.join("= ?,", columnNames) + "= ? ";
    }

    private boolean isColumnName(Element element) {
        if(element.getAnnotation(Column.class) != null) {
            return element.getAnnotation(Column.class).name() != null && !(element.getAnnotation(Column.class).name().equals("") || element.getAnnotation(Column.class).name().equals(" "));
        }
        return false;
    }

    private String findTableName(Element entityType) {

        if (entityType.getAnnotation(Table.class) != null) {
            if (entityType.getAnnotation(Table.class).name() != null && !(entityType.getAnnotation(Table.class).name().equals("") || entityType.getAnnotation(Table.class).name().equals(" ")))
                return entityType.getAnnotation(Table.class).name().toUpperCase();
        }
        else return entityType.getSimpleName().toString();

        return null;
    }

    private String findIdColumnName(Element entityType){
        List<? extends Element> enclosedElements = entityType.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();
        String idColumnName = null;
        for (Element element : enclosedElements)
            if (element.getAnnotation(Transient.class) == null && element.getAnnotation(Id.class) != null) idColumnName = isColumnName(element) ? element.getAnnotation(Column.class).name().toUpperCase() : element.getSimpleName().toString();

        return idColumnName;
    }
    private String generateGetString(Element entityType){
        String tableName = findTableName(entityType);
        if(tableName == null) return null;
        return "SELECT * FROM `" + tableName.toUpperCase() + "` WHERE `" + findIdColumnName(entityType) + "` = ?";
    }

    private List<String> findVariables(Element entityType){
        List<? extends Element> enclosedElements = entityType.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.FIELD).toList();
        List <String> nameVariables = new ArrayList<>();
        String variable;
        String columnType = null;
        for(Element element : enclosedElements) {

            if (element.getAnnotation(Transient.class) != null /*|| element.getAnnotation(Id.class) != null*/ || element.getAnnotation(ManyToOne.class) != null) continue;
            else variable = element.getSimpleName().toString();

            //assert variable != null;
            String columnName = element.getSimpleName().toString().toUpperCase();
            if (element.getAnnotation(Column.class) != null && !(element.getAnnotation(Column.class).name().equals("") || element.getAnnotation(Column.class).name().equals(" ")) /*&& element.getAnnotation(Id.class) == null*/){
                columnName = "\"" + element.getAnnotation(Column.class).name().toUpperCase() + "\"";
                columnType = element.asType().toString();
            }
            //+ columnType.substring(0,1).toUpperCase() + columnType.substring(1).toLowerCase() + "("
            if (element.asType().toString().equals("java.lang.String") || element.asType().toString().equals("string")) columnType = "String";
            else if (element.asType().toString().equals("java.lang.Integer") || element.asType().toString().equals("int")) columnType = "Int";
            else if (element.asType().toString().equals("java.lang.Float") || element.asType().toString().equals("float")) columnType = "Float";
            else if (element.asType().toString().equals("java.lang.Long") || element.asType().toString().equals("long")) columnType = "Long";
            else if (element.asType().toString().equals("java.lang.Double") || element.asType().toString().equals("double")) columnType = "Double";
            else if (element.asType().toString().equals("java.lang.Boolean") || element.asType().toString().equals("boolean")) columnType = "Boolean";

            nameVariables.add(".set" + variable.substring(0, 1).toUpperCase() + variable.substring(1) + "(rs.get" + columnType + "(" + columnName + "));");
        }


        return nameVariables;
    }
}

