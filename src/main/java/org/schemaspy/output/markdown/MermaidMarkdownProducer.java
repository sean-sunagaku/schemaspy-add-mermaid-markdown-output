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

import org.schemaspy.model.*;
import org.schemaspy.output.OutputException;
import org.schemaspy.util.DefaultPrintWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

/**
 * Produces Markdown documentation with Mermaid ER diagrams.
 * Outputs 3 separate files:
 * - overview.md: Database metadata and overview
 * - er-diagram.md: Mermaid ER diagram
 * - tables.md: Table details and foreign key constraints
 */
public class MermaidMarkdownProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String MARKDOWN_DIR = "markdown";
    private static final String OVERVIEW_FILE = "overview.md";
    private static final String ER_DIAGRAM_FILE = "er-diagram.md";
    private static final String TABLES_FILE = "tables.md";

    private final MermaidFormatter mermaidFormatter;
    private final boolean includeImplied;

    public MermaidMarkdownProducer(boolean includeOrphans, boolean includeImplied) {
        this.mermaidFormatter = new MermaidFormatter(includeOrphans);
        this.includeImplied = includeImplied;
    }

    /**
     * Generates Markdown documentation for the database schema.
     * Creates 3 separate files in markdown/ directory: overview.md, er-diagram.md, tables.md
     *
     * @param database the analyzed database
     * @param tables the collection of tables to document
     * @param outputDir the output directory
     * @throws OutputException if an error occurs during generation
     */
    public void generate(Database database, Collection<Table> tables, File outputDir) throws OutputException {
        File markdownDir = new File(outputDir, MARKDOWN_DIR);
        if (!markdownDir.exists()) {
            markdownDir.mkdirs();
        }

        LOGGER.info("Generating Markdown documentation in: {}", markdownDir.getAbsolutePath());

        try {
            generateOverview(database, markdownDir);
            generateErDiagram(database, tables, markdownDir);
            generateTables(database, tables, markdownDir);

            LOGGER.info("Markdown documentation generated successfully (3 files)");
        } catch (IOException e) {
            throw new OutputException("Failed to generate Markdown documentation", e);
        }
    }

    /**
     * Generates overview.md with database metadata.
     */
    private void generateOverview(Database database, File outputDir) throws IOException {
        File overviewFile = new File(outputDir, OVERVIEW_FILE);
        LOGGER.info("  - {}", OVERVIEW_FILE);

        try (PrintWriter writer = new DefaultPrintWriter(overviewFile)) {
            writer.println("# Database Schema: " + database.getName());
            writer.println();
            writer.println("## Overview");
            writer.println();
            writer.println("| Property | Value |");
            writer.println("|----------|-------|");
            writer.println("| Database Name | " + escapeMarkdown(database.getName()) + " |");
            if (database.getSchema() != null) {
                writer.println("| Schema | " + escapeMarkdown(database.getSchema().getName()) + " |");
            }
            if (database.getCatalog() != null) {
                writer.println("| Catalog | " + escapeMarkdown(database.getCatalog().getName()) + " |");
            }
            writer.println("| Tables | " + database.getTables().size() + " |");
            writer.println("| Views | " + database.getViews().size() + " |");
            writer.println("| Generated | " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " |");
            writer.println();
            writer.println("## Related Files");
            writer.println();
            writer.println("- [ER Diagram](" + ER_DIAGRAM_FILE + ")");
            writer.println("- [Table Details](" + TABLES_FILE + ")");
        }
    }

    /**
     * Generates er-diagram.md with Mermaid ER diagram.
     */
    private void generateErDiagram(Database database, Collection<Table> tables, File outputDir) throws IOException {
        File erFile = new File(outputDir, ER_DIAGRAM_FILE);
        LOGGER.info("  - {}", ER_DIAGRAM_FILE);

        try (PrintWriter writer = new DefaultPrintWriter(erFile)) {
            writer.println("# Entity Relationship Diagram");
            writer.println();
            writer.println("Database: **" + escapeMarkdown(database.getName()) + "**");
            writer.println();

            if (tables.isEmpty()) {
                writer.println("*No tables found.*");
                writer.println();
            } else {
                mermaidFormatter.writeErDiagram(tables, includeImplied, writer);
                writer.println();
            }

            writer.println("---");
            writer.println();
            writer.println("[Back to Overview](" + OVERVIEW_FILE + ") | [Table Details](" + TABLES_FILE + ")");
        }
    }

    /**
     * Generates tables.md with table details and foreign key constraints.
     */
    private void generateTables(Database database, Collection<Table> tables, File outputDir) throws IOException {
        File tablesFile = new File(outputDir, TABLES_FILE);
        LOGGER.info("  - {}", TABLES_FILE);

        try (PrintWriter writer = new DefaultPrintWriter(tablesFile)) {
            writer.println("# Table Details");
            writer.println();
            writer.println("Database: **" + escapeMarkdown(database.getName()) + "**");
            writer.println();

            if (tables.isEmpty()) {
                writer.println("*No tables found.*");
                writer.println();
            } else {
                // Table of contents
                writer.println("## Table of Contents");
                writer.println();
                for (Table table : tables) {
                    String anchor = table.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");
                    writer.println("- [" + escapeMarkdown(table.getName()) + "](#" + anchor + ")");
                }
                writer.println();

                // Table details
                for (Table table : tables) {
                    writeTableDetail(table, writer);
                }
            }

            // Foreign key constraints
            writeForeignKeyConstraints(tables, writer);

            writer.println("---");
            writer.println();
            writer.println("[Back to Overview](" + OVERVIEW_FILE + ") | [ER Diagram](" + ER_DIAGRAM_FILE + ")");
        }
    }

    /**
     * Writes details for a single table.
     */
    private void writeTableDetail(Table table, PrintWriter writer) {
        writer.println("## " + escapeMarkdown(table.getName()));
        writer.println();

        // Table comment
        if (table.getComments() != null && !table.getComments().isEmpty()) {
            writer.println("> " + escapeMarkdown(table.getComments()));
            writer.println();
        }

        // Table type
        writer.println("**Type:** " + table.getType());
        if (table.getNumRows() >= 0) {
            writer.println(" | **Rows:** " + table.getNumRows());
        }
        writer.println();

        // Columns table
        List<TableColumn> columns = table.getColumns();
        if (!columns.isEmpty()) {
            writer.println("### Columns");
            writer.println();
            writer.println("| Column | Type | Size | Nullable | Key | Default | Comment |");
            writer.println("|--------|------|------|----------|-----|---------|---------|");

            for (TableColumn column : columns) {
                writeColumnRow(column, writer);
            }
            writer.println();
        }

        // Indexes
        if (!table.getIndexes().isEmpty()) {
            writer.println("### Indexes");
            writer.println();
            writer.println("| Name | Columns | Unique |");
            writer.println("|------|---------|--------|");

            for (TableIndex index : table.getIndexes()) {
                writeIndexRow(index, writer);
            }
            writer.println();
        }

        // Check constraints
        if (!table.getCheckConstraints().isEmpty()) {
            writer.println("### Check Constraints");
            writer.println();
            writer.println("| Name | Definition |");
            writer.println("|------|------------|");

            for (var entry : table.getCheckConstraints().entrySet()) {
                writer.println("| " + escapeMarkdown(entry.getKey()) + " | " + escapeMarkdown(entry.getValue()) + " |");
            }
            writer.println();
        }

        // View definition
        if (table.isView() && table.getViewDefinition() != null) {
            writer.println("### View Definition");
            writer.println();
            writer.println("```sql");
            writer.println(table.getViewDefinition());
            writer.println("```");
            writer.println();
        }

        writer.println("---");
        writer.println();
    }

    /**
     * Writes a single column row in the columns table.
     */
    private void writeColumnRow(TableColumn column, PrintWriter writer) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ");
        sb.append(escapeMarkdown(column.getName()));
        sb.append(" | ");
        sb.append(escapeMarkdown(column.getTypeName()));
        sb.append(" | ");
        sb.append(column.getDetailedSize() != null ? column.getDetailedSize() : "-");
        sb.append(" | ");
        sb.append(column.isNullable() ? "YES" : "NO");
        sb.append(" | ");

        // Key indicators
        StringBuilder keys = new StringBuilder();
        if (column.isPrimary()) {
            keys.append("PK");
        }
        if (column.isForeignKey()) {
            if (keys.length() > 0) keys.append(", ");
            keys.append("FK");
        }
        if (column.isUnique() && !column.isPrimary()) {
            if (keys.length() > 0) keys.append(", ");
            keys.append("UK");
        }
        sb.append(keys.length() > 0 ? keys.toString() : "-");
        sb.append(" | ");

        // Default value
        Object defaultValue = column.getDefaultValue();
        sb.append(defaultValue != null ? escapeMarkdown(defaultValue.toString()) : "-");
        sb.append(" | ");

        // Comment
        String comments = column.getComments();
        sb.append(comments != null && !comments.isEmpty() ? escapeMarkdown(comments) : "-");
        sb.append(" |");

        writer.println(sb.toString());
    }

    /**
     * Writes a single index row in the indexes table.
     */
    private void writeIndexRow(TableIndex index, PrintWriter writer) {
        StringBuilder columns = new StringBuilder();
        for (TableColumn col : index.getColumns()) {
            if (columns.length() > 0) {
                columns.append(", ");
            }
            columns.append(col.getName());
        }

        writer.println("| " + escapeMarkdown(index.getName()) + " | " +
            escapeMarkdown(columns.toString()) + " | " +
            (index.isUnique() ? "YES" : "NO") + " |");
    }

    /**
     * Writes the foreign key constraints section.
     */
    private void writeForeignKeyConstraints(Collection<Table> tables, PrintWriter writer) {
        writer.println("## Foreign Key Constraints");
        writer.println();
        writer.println("| Constraint | Child Table | Child Column(s) | Parent Table | Parent Column(s) | On Delete |");
        writer.println("|------------|-------------|-----------------|--------------|------------------|-----------|");

        boolean hasConstraints = false;
        for (Table table : tables) {
            for (ForeignKeyConstraint fk : table.getForeignKeys()) {
                hasConstraints = true;
                writeForeignKeyRow(fk, writer);
            }
        }

        if (!hasConstraints) {
            writer.println("| *No foreign key constraints* | | | | | |");
        }

        writer.println();
    }

    /**
     * Writes a single foreign key constraint row.
     */
    private void writeForeignKeyRow(ForeignKeyConstraint fk, PrintWriter writer) {
        StringBuilder childCols = new StringBuilder();
        for (TableColumn col : fk.getChildColumns()) {
            if (childCols.length() > 0) childCols.append(", ");
            childCols.append(col.getName());
        }

        StringBuilder parentCols = new StringBuilder();
        for (TableColumn col : fk.getParentColumns()) {
            if (parentCols.length() > 0) parentCols.append(", ");
            parentCols.append(col.getName());
        }

        String constraintName = fk.getName() != null ? fk.getName() : "(implied)";

        writer.println("| " + escapeMarkdown(constraintName) +
            " | " + escapeMarkdown(fk.getChildTable().getName()) +
            " | " + escapeMarkdown(childCols.toString()) +
            " | " + escapeMarkdown(fk.getParentTable().getName()) +
            " | " + escapeMarkdown(parentCols.toString()) +
            " | " + fk.getDeleteRuleName() + " |");
    }

    /**
     * Escapes special Markdown characters.
     */
    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("|", "\\|")
            .replace("\n", " ")
            .replace("\r", "");
    }
}
