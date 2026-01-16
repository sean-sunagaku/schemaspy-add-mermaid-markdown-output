/*
 * Copyright (C) 2024 SchemaSpy project
 *
 * This file is a part of the SchemaSpy project (http://schemaspy.org).
 *
 * SchemaSpy is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * SchemaSpy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.schemaspy.output.markdown;

import org.schemaspy.model.ForeignKeyConstraint;
import org.schemaspy.model.Table;
import org.schemaspy.model.TableColumn;

import java.io.PrintWriter;
import java.util.*;

/**
 * Formats database schema information into Mermaid ER diagram syntax.
 */
public class MermaidFormatter {

    private final boolean includeOrphans;

    public MermaidFormatter(boolean includeOrphans) {
        this.includeOrphans = includeOrphans;
    }

    /**
     * Writes a complete Mermaid ER diagram for the given tables.
     *
     * @param tables Collection of tables to include in the diagram
     * @param includeImplied whether to include implied relationships
     * @param writer output writer
     */
    public void writeErDiagram(Collection<Table> tables, boolean includeImplied, PrintWriter writer) {
        writer.println("```mermaid");
        writer.println("erDiagram");

        // Collect all tables to be included
        Set<Table> includedTables = new LinkedHashSet<>();
        for (Table table : tables) {
            if (includeOrphans || !table.isOrphan(includeImplied)) {
                includedTables.add(table);
            }
        }

        // Write table definitions
        for (Table table : includedTables) {
            writeTableDefinition(table, writer);
        }

        // Collect and write relationships
        Set<Relationship> relationships = collectRelationships(includedTables, includeImplied);
        for (Relationship rel : relationships) {
            writeRelationship(rel, writer);
        }

        writer.println("```");
        writer.flush();
    }

    /**
     * Writes a single table definition in Mermaid syntax.
     */
    private void writeTableDefinition(Table table, PrintWriter writer) {
        String tableName = sanitizeName(table.getName());
        writer.println("    " + tableName + " {");

        for (TableColumn column : table.getColumns()) {
            StringBuilder columnDef = new StringBuilder();
            columnDef.append("        ");
            columnDef.append(sanitizeTypeName(column.getTypeName()));
            columnDef.append(" ");
            columnDef.append(sanitizeName(column.getName()));

            // Add key indicators
            List<String> attributes = new ArrayList<>();
            if (column.isPrimary()) {
                attributes.add("PK");
            }
            if (column.isForeignKey()) {
                attributes.add("FK");
            }
            if (column.isUnique() && !column.isPrimary()) {
                attributes.add("UK");
            }

            if (!attributes.isEmpty()) {
                columnDef.append(" ");
                columnDef.append(String.join(",", attributes));
            }

            // Add comments if present (as quoted string)
            if (column.getComments() != null && !column.getComments().isEmpty()) {
                columnDef.append(" \"");
                columnDef.append(escapeQuotes(column.getComments()));
                columnDef.append("\"");
            }

            writer.println(columnDef.toString());
        }

        writer.println("    }");
    }

    /**
     * Collects all unique relationships from the tables.
     */
    private Set<Relationship> collectRelationships(Collection<Table> tables, boolean includeImplied) {
        Set<Relationship> relationships = new TreeSet<>();

        for (Table table : tables) {
            for (TableColumn column : table.getColumns()) {
                // Get parent relationships (this column is FK referencing parent PK)
                for (TableColumn parentColumn : column.getParents()) {
                    ForeignKeyConstraint constraint = column.getParentConstraint(parentColumn);
                    if (constraint != null && (includeImplied || !constraint.isImplied())) {
                        Table parentTable = parentColumn.getTable();
                        Table childTable = column.getTable();

                        // Only include if both tables are in our set
                        if (tables.contains(parentTable) && tables.contains(childTable)) {
                            relationships.add(new Relationship(
                                parentTable.getName(),
                                childTable.getName(),
                                constraint.getName(),
                                constraint.isImplied()
                            ));
                        }
                    }
                }
            }
        }

        return relationships;
    }

    /**
     * Writes a relationship in Mermaid ER diagram syntax.
     */
    private void writeRelationship(Relationship rel, PrintWriter writer) {
        StringBuilder sb = new StringBuilder();
        sb.append("    ");
        sb.append(sanitizeName(rel.parentTable));
        sb.append(" ||--o{ ");
        sb.append(sanitizeName(rel.childTable));
        sb.append(" : \"");
        if (rel.constraintName != null && !rel.constraintName.isEmpty()) {
            sb.append(escapeQuotes(rel.constraintName));
        } else {
            sb.append("FK");
        }
        if (rel.isImplied) {
            sb.append(" (implied)");
        }
        sb.append("\"");
        writer.println(sb.toString());
    }

    /**
     * Sanitizes a name for use in Mermaid diagrams.
     * Mermaid has restrictions on certain characters in identifiers.
     */
    private String sanitizeName(String name) {
        if (name == null) {
            return "unknown";
        }
        // Replace characters that Mermaid doesn't handle well
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * Sanitizes a type name for use in Mermaid diagrams.
     */
    private String sanitizeTypeName(String typeName) {
        if (typeName == null) {
            return "unknown";
        }
        // Remove parentheses and other special characters
        return typeName.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * Escapes quotes in strings for Mermaid.
     */
    private String escapeQuotes(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\"", "'").replace("\n", " ").replace("\r", "");
    }

    /**
     * Represents a relationship between two tables.
     */
    private static class Relationship implements Comparable<Relationship> {
        final String parentTable;
        final String childTable;
        final String constraintName;
        final boolean isImplied;

        Relationship(String parentTable, String childTable, String constraintName, boolean isImplied) {
            this.parentTable = parentTable;
            this.childTable = childTable;
            this.constraintName = constraintName;
            this.isImplied = isImplied;
        }

        @Override
        public int compareTo(Relationship other) {
            int result = this.parentTable.compareToIgnoreCase(other.parentTable);
            if (result == 0) {
                result = this.childTable.compareToIgnoreCase(other.childTable);
            }
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Relationship that = (Relationship) o;
            return parentTable.equalsIgnoreCase(that.parentTable) &&
                   childTable.equalsIgnoreCase(that.childTable);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parentTable.toLowerCase(), childTable.toLowerCase());
        }
    }
}
