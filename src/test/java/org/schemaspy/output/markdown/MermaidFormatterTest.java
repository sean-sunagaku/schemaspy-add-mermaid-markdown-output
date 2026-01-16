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
import org.schemaspy.model.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link MermaidFormatter}.
 */
class MermaidFormatterTest {

    private static final Database database = mock(Database.class);
    private MermaidFormatter formatter;
    private StringWriter stringWriter;
    private PrintWriter writer;

    @BeforeEach
    void setUp() {
        formatter = new MermaidFormatter(true);
        stringWriter = new StringWriter();
        writer = new PrintWriter(stringWriter);
    }

    @Test
    void emptyTablesProducesValidMermaidBlock() {
        Collection<Table> tables = Collections.emptyList();

        formatter.writeErDiagram(tables, false, writer);

        String output = stringWriter.toString();
        assertThat(output).contains("```mermaid");
        assertThat(output).contains("erDiagram");
        assertThat(output).contains("```");
    }

    @Test
    void singleTableWithColumnsProducesValidOutput() {
        Table table = new LogicalTable(database, "catalog", "schema", "users", "User table");
        TableColumn idColumn = createColumn(table, "id", "int");
        idColumn.setId(1);
        table.setPrimaryColumn(idColumn);

        TableColumn nameColumn = createColumn(table, "name", "varchar");
        nameColumn.setId(2);

        formatter.writeErDiagram(Collections.singletonList(table), false, writer);

        String output = stringWriter.toString();
        assertThat(output).contains("```mermaid");
        assertThat(output).contains("erDiagram");
        assertThat(output).contains("users {");
        assertThat(output).contains("int id PK");
        assertThat(output).contains("varchar name");
        assertThat(output).contains("}");
        assertThat(output).contains("```");
    }

    @Test
    void tableWithForeignKeyRelationship() {
        // Create parent table
        Table usersTable = new LogicalTable(database, "catalog", "schema", "users", "User table");
        TableColumn userId = createColumn(usersTable, "id", "int");
        userId.setId(1);
        usersTable.setPrimaryColumn(userId);

        // Create child table
        Table postsTable = new LogicalTable(database, "catalog", "schema", "posts", "Posts table");
        TableColumn postId = createColumn(postsTable, "id", "int");
        postId.setId(1);
        postsTable.setPrimaryColumn(postId);

        TableColumn authorId = createColumn(postsTable, "author_id", "int");
        authorId.setId(2);

        // Create foreign key constraint
        ForeignKeyConstraint fk = new ForeignKeyConstraint(userId, authorId);
        postsTable.getForeignKeysMap().put("fk_posts_users", fk);

        Collection<Table> tables = Arrays.asList(usersTable, postsTable);
        formatter.writeErDiagram(tables, false, writer);

        String output = stringWriter.toString();
        assertThat(output).contains("users {");
        assertThat(output).contains("posts {");
        assertThat(output).contains("users ||--o{ posts");
    }

    @Test
    void specialCharactersAreSanitized() {
        Table table = new LogicalTable(database, "catalog", "schema", "user-data", "Table with special chars");
        TableColumn column = createColumn(table, "user-name", "varchar(255)");
        column.setId(1);

        formatter.writeErDiagram(Collections.singletonList(table), false, writer);

        String output = stringWriter.toString();
        // Special characters should be replaced with underscores
        assertThat(output).contains("user_data {");
        assertThat(output).contains("varchar_255_ user_name");
    }

    @Test
    void orphanTableExcludedWhenFlagIsFalse() {
        MermaidFormatter formatterNoOrphans = new MermaidFormatter(false);

        // Create orphan table (no relationships)
        Table orphanTable = new LogicalTable(database, "catalog", "schema", "orphan", "Orphan table");
        TableColumn col = createColumn(orphanTable, "col", "int");
        col.setId(1);

        formatterNoOrphans.writeErDiagram(Collections.singletonList(orphanTable), false, writer);

        String output = stringWriter.toString();
        // Orphan table should not appear in output
        assertThat(output).doesNotContain("orphan {");
    }

    @Test
    void orphanTableIncludedWhenFlagIsTrue() {
        // Create orphan table (no relationships)
        Table orphanTable = new LogicalTable(database, "catalog", "schema", "orphan", "Orphan table");
        TableColumn col = createColumn(orphanTable, "col", "int");
        col.setId(1);

        formatter.writeErDiagram(Collections.singletonList(orphanTable), false, writer);

        String output = stringWriter.toString();
        // Orphan table should appear in output
        assertThat(output).contains("orphan {");
    }

    @Test
    void columnWithUniqueConstraint() {
        Table table = new LogicalTable(database, "catalog", "schema", "users", "User table");

        TableColumn idColumn = createColumn(table, "id", "int");
        idColumn.setId(1);
        table.setPrimaryColumn(idColumn);

        TableColumn emailColumn = createColumn(table, "email", "varchar");
        emailColumn.setId(2);

        // Create unique index on email
        TableIndex uniqueIndex = new TableIndex("idx_email_unique", true);
        uniqueIndex.addColumn(emailColumn, "asc");
        table.getIndexesMap().put("idx_email_unique", uniqueIndex);

        formatter.writeErDiagram(Collections.singletonList(table), false, writer);

        String output = stringWriter.toString();
        assertThat(output).contains("int id PK");
        assertThat(output).contains("varchar email UK");
    }

    @Test
    void impliedRelationshipsIncludedWhenFlagIsTrue() {
        // Create parent table
        Table usersTable = new LogicalTable(database, "catalog", "schema", "users", "User table");
        TableColumn userId = createColumn(usersTable, "id", "int");
        userId.setId(1);
        usersTable.setPrimaryColumn(userId);

        // Create child table
        Table postsTable = new LogicalTable(database, "catalog", "schema", "posts", "Posts table");
        TableColumn postId = createColumn(postsTable, "id", "int");
        postId.setId(1);
        postsTable.setPrimaryColumn(postId);

        TableColumn authorId = createColumn(postsTable, "user_id", "int");
        authorId.setId(2);

        // Create implied foreign key constraint
        new ImpliedForeignKeyConstraint(userId, authorId);

        Collection<Table> tables = Arrays.asList(usersTable, postsTable);
        formatter.writeErDiagram(tables, true, writer);

        String output = stringWriter.toString();
        assertThat(output).contains("users ||--o{ posts");
        assertThat(output).contains("(implied)");
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
