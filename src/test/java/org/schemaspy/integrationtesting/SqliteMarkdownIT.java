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
package org.schemaspy.integrationtesting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.schemaspy.testing.SQLScriptsRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.schemaspy.testing.SchemaSpyRunnerFixture.schemaSpyRunner;

/**
 * Integration test for Markdown output with Mermaid ER diagrams.
 */
class SqliteMarkdownIT {

    private static final Path OUTPUT_DIR = Path.of("target/integrationtesting/sqlite-markdown");

    @BeforeAll
    static void generateMarkdown() throws SQLException, IOException {
        String[] args = {
            "-t", "sqlite-xerial",
            "-db", "src/test/resources/integrationTesting/sqlite/database/chinook.db",
            "-s", "chinook",
            "-cat", "chinook",
            "-o", OUTPUT_DIR.toString(),
            "-markdown",
            "-nohtml",
            "-sso"
        };
        schemaSpyRunner(args).run();
    }

    @Test
    void markdownFileIsGenerated() {
        File markdownFile = OUTPUT_DIR.resolve("schema.md").toFile();
        assertThat(markdownFile).exists();
    }

    @Test
    void markdownContainsMermaidDiagram() throws IOException {
        String content = Files.readString(OUTPUT_DIR.resolve("schema.md"), StandardCharsets.UTF_8);
        assertThat(content).contains("```mermaid");
        assertThat(content).contains("erDiagram");
        assertThat(content).contains("```");
    }

    @Test
    void markdownContainsDatabaseName() throws IOException {
        String content = Files.readString(OUTPUT_DIR.resolve("schema.md"), StandardCharsets.UTF_8);
        // SQLite uses the file path as database name
        assertThat(content).contains("# Database Schema:");
        assertThat(content).contains("chinook.db");
    }

    @Test
    void markdownContainsTableDetails() throws IOException {
        String content = Files.readString(OUTPUT_DIR.resolve("schema.md"), StandardCharsets.UTF_8);
        assertThat(content).contains("## Table Details");
        // Chinook database has tables like albums, artists, tracks, etc.
        assertThat(content).contains("### albums");
        assertThat(content).contains("### artists");
        assertThat(content).contains("### tracks");
    }

    @Test
    void markdownContainsForeignKeyRelationships() throws IOException {
        String content = Files.readString(OUTPUT_DIR.resolve("schema.md"), StandardCharsets.UTF_8);
        assertThat(content).contains("## Foreign Key Constraints");
        // The chinook database has foreign key relationships
        assertThat(content).contains("| Constraint | Child Table | Child Column(s) | Parent Table | Parent Column(s) |");
    }

    @Test
    void mermaidDiagramContainsRelationships() throws IOException {
        String content = Files.readString(OUTPUT_DIR.resolve("schema.md"), StandardCharsets.UTF_8);
        // Check that relationships are shown in the Mermaid diagram
        // The chinook database has albums -> artists relationship
        assertThat(content).contains("||--o{");
    }
}
