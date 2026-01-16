/*
 * Copyright (C) 2024 SchemaSpy project
 *
 * This file is part of SchemaSpy.
 *
 * SchemaSpy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SchemaSpy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with SchemaSpy. If not, see <http://www.gnu.org/licenses/>.
 */
package org.schemaspy.output.markdown;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.schemaspy.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MermaidMarkdownProducer}.
 */
class MermaidMarkdownProducerTest {

    @TempDir
    Path tempDir;

    private MermaidMarkdownProducer producer;
    private Database database;

    @BeforeEach
    void setUp() {
        producer = new MermaidMarkdownProducer(true, true);
        database = mock(Database.class);
        when(database.getName()).thenReturn("testdb");
        when(database.getTables()).thenReturn(Collections.emptyList());
        when(database.getViews()).thenReturn(Collections.emptyList());
    }

    @Test
    void generatesThreeMarkdownFiles() throws IOException {
        File outputDir = tempDir.toFile();
        Collection<Table> tables = Collections.emptyList();

        producer.generate(database, tables, outputDir);

        File markdownDir = new File(outputDir, "markdown");
        assertThat(markdownDir).exists();
        assertThat(new File(markdownDir, "overview.md")).exists();
        assertThat(new File(markdownDir, "er-diagram.md")).exists();
        assertThat(new File(markdownDir, "tables.md")).exists();
    }

    @Test
    void overviewContainsHeader() throws IOException {
        File outputDir = tempDir.toFile();
        Collection<Table> tables = Collections.emptyList();

        producer.generate(database, tables, outputDir);

        String content = readFile(outputDir, "overview.md");
        assertThat(content).contains("# Database Schema: testdb");
        assertThat(content).contains("## Overview");
        assertThat(content).contains("## Related Files");
        assertThat(content).contains("[ER Diagram](er-diagram.md)");
        assertThat(content).contains("[Table Details](tables.md)");
    }

    @Test
    void erDiagramContainsMermaidWithNoTables() throws IOException {
        File outputDir = tempDir.toFile();
        Collection<Table> tables = Collections.emptyList();

        producer.generate(database, tables, outputDir);

        String content = readFile(outputDir, "er-diagram.md");
        assertThat(content).contains("# Entity Relationship Diagram");
        assertThat(content).contains("*No tables found.*");
    }

    @Test
    void erDiagramContainsMermaidWithTables() throws IOException {
        File outputDir = tempDir.toFile();

        Table table = new LogicalTable(database, "catalog", "schema", "users", "User table");
        TableColumn idColumn = createColumn(table, "id", "int");
        idColumn.setId(1);
        table.setPrimaryColumn(idColumn);

        Collection<Table> tables = Collections.singletonList(table);

        producer.generate(database, tables, outputDir);

        String content = readFile(outputDir, "er-diagram.md");
        assertThat(content).contains("# Entity Relationship Diagram");
        assertThat(content).contains("```mermaid");
        assertThat(content).contains("erDiagram");
    }

    @Test
    void tablesContainsTableDetails() throws IOException {
        File outputDir = tempDir.toFile();

        Table table = new LogicalTable(database, "catalog", "schema", "users", "User table");
        TableColumn idColumn = createColumn(table, "id", "int");
        idColumn.setId(1);
        table.setPrimaryColumn(idColumn);

        TableColumn nameColumn = createColumn(table, "name", "varchar");
        nameColumn.setId(2);
        nameColumn.setNullable(true);

        Collection<Table> tables = Collections.singletonList(table);

        producer.generate(database, tables, outputDir);

        String content = readFile(outputDir, "tables.md");
        assertThat(content).contains("# Table Details");
        assertThat(content).contains("## Table of Contents");
        assertThat(content).contains("## users");
        assertThat(content).contains("| Column | Type | Size | Nullable | Key | Default | Comment |");
        assertThat(content).contains("| id | int |");
        assertThat(content).contains("| PK |");
    }

    @Test
    void tablesContainsForeignKeySection() throws IOException {
        File outputDir = tempDir.toFile();

        Table usersTable = new LogicalTable(database, "catalog", "schema", "users", "User table");
        TableColumn userId = createColumn(usersTable, "id", "int");
        userId.setId(1);
        usersTable.setPrimaryColumn(userId);

        Table postsTable = new LogicalTable(database, "catalog", "schema", "posts", "Posts table");
        TableColumn postId = createColumn(postsTable, "id", "int");
        postId.setId(1);
        postsTable.setPrimaryColumn(postId);

        TableColumn authorId = createColumn(postsTable, "author_id", "int");
        authorId.setId(2);

        ForeignKeyConstraint fk = new ForeignKeyConstraint(userId, authorId);
        postsTable.getForeignKeysMap().put("fk_posts_users", fk);

        Collection<Table> tables = Arrays.asList(usersTable, postsTable);

        producer.generate(database, tables, outputDir);

        String content = readFile(outputDir, "tables.md");
        assertThat(content).contains("## Foreign Key Constraints");
        assertThat(content).contains("| Constraint | Child Table | Child Column(s) | Parent Table | Parent Column(s) | On Delete |");
    }

    @Test
    void filesContainNavigationLinks() throws IOException {
        File outputDir = tempDir.toFile();
        Collection<Table> tables = Collections.emptyList();

        producer.generate(database, tables, outputDir);

        String erContent = readFile(outputDir, "er-diagram.md");
        assertThat(erContent).contains("[Back to Overview](overview.md)");
        assertThat(erContent).contains("[Table Details](tables.md)");

        String tablesContent = readFile(outputDir, "tables.md");
        assertThat(tablesContent).contains("[Back to Overview](overview.md)");
        assertThat(tablesContent).contains("[ER Diagram](er-diagram.md)");
    }

    private String readFile(File outputDir, String filename) throws IOException {
        File markdownDir = new File(outputDir, "markdown");
        return Files.readString(new File(markdownDir, filename).toPath(), StandardCharsets.UTF_8);
    }

    private static TableColumn createColumn(Table table, String name, String type) {
        TableColumn column = new TableColumn(table);
        column.setName(name);
        column.setTypeName(type);
        column.setShortType(type);
        column.setDetailedSize("16");
        table.getColumnsMap().put(name, column);
        return column;
    }
}
