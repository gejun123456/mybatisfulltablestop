package com.github.mybatisfulltable.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlSafetyCheckerTest {

    static Stream<Arguments> sqlProvider() {
        return Stream.of(
                // SELECT
                Arguments.of("SELECT * FROM user", false),
                Arguments.of("SELECT * FROM user WHERE id = ?", true),
                Arguments.of("SELECT * FROM user WHERE name = ?", true),
                Arguments.of("SELECT * FROM user WHERE 1 = 1", false),
                Arguments.of("SELECT * FROM user WHERE 1 = 1 AND id = ?", true),
                Arguments.of("SELECT * FROM user WHERE (1 = 1) AND (2 = 2)", false),
                Arguments.of("SELECT * FROM user WHERE id = ? OR 1 = 1", false),
                Arguments.of("SELECT * FROM user WHERE id = ? OR name = ?", true),
                Arguments.of("SELECT * FROM user WHERE 1 = '1'", false),
                Arguments.of("SELECT * FROM user WHERE 1", false),
                Arguments.of("SELECT * FROM user WHERE TRUE", false),
                Arguments.of("SELECT 1", true),
                Arguments.of("SELECT 1 FROM DUAL", true),
                Arguments.of("SELECT DISTINCT name FROM user", false),
                Arguments.of("SELECT COUNT(*) FROM user", false),
                Arguments.of("SELECT * FROM user u JOIN order o ON o.user_id = u.id WHERE u.id = ?", true),
                Arguments.of("SELECT * FROM user u JOIN order o ON o.user_id = u.id", false),
                Arguments.of("SELECT * FROM user WHERE id IN (SELECT user_id FROM order WHERE status = 1)", true),

                // UPDATE
                Arguments.of("UPDATE user SET name = ?", false),
                Arguments.of("UPDATE user SET name = ? WHERE id = ?", true),
                Arguments.of("UPDATE user SET status = 0", false),
                Arguments.of("UPDATE user SET status = 0 WHERE id = ?", true),
                Arguments.of("UPDATE user SET status = 0 WHERE 1 = 1", false),
                Arguments.of("UPDATE user SET status = 0 WHERE 1 = 1 AND id = ?", true),
                Arguments.of("UPDATE user SET name = (SELECT name FROM user_backup WHERE user_backup.id = user.id) WHERE id = ?", true),
                Arguments.of("UPDATE user SET name = (SELECT name FROM user_backup WHERE user_backup.id = user.id)", false),

                // DELETE
                Arguments.of("DELETE FROM user", false),
                Arguments.of("DELETE FROM user WHERE id = ?", true),
                Arguments.of("DELETE FROM user WHERE 1 = 1", false),
                Arguments.of("DELETE FROM user WHERE 1 = 1 AND id = ?", true),

                // Comments
                Arguments.of("-- comment\nSELECT * FROM user", false),
                Arguments.of("/* comment */ SELECT * FROM user", false),
                Arguments.of("-- comment\nSELECT * FROM user WHERE id = ?", true),
                Arguments.of("/* comment */ SELECT * FROM user WHERE id = ?", true),

                // Tautology variations
                Arguments.of("SELECT * FROM user WHERE user.id = user.id", false),
                Arguments.of("SELECT * FROM user WHERE USER.ID = user.id", false),
                Arguments.of("SELECT * FROM user WHERE ID = id", false),
                Arguments.of("SELECT * FROM user WHERE 'a' = 'a'", false),
                Arguments.of("SELECT * FROM user WHERE 1 != 0", false),

                // Lowercase and mixed case SQL
                Arguments.of("select * from user", false),
                Arguments.of("select * from user where id = ?", true),
                Arguments.of("update user set status = 0", false),
                Arguments.of("update user set status = 0 where id = ?", true),
                Arguments.of("delete from user", false),
                Arguments.of("delete from user where id = ?", true),

                // Semicolon terminated
                Arguments.of("SELECT * FROM user;", false),
                Arguments.of("SELECT * FROM user WHERE id = ?;", true),
                Arguments.of("UPDATE user SET status = 0;", false),
                Arguments.of("DELETE FROM user;", false),

                // Multiline formatting
                Arguments.of("SELECT *\nFROM user\nWHERE\n    1 = 1", false),
                Arguments.of("SELECT *\nFROM user\nWHERE\n    1 = 1 AND id = ?", true),

                // Non-target operations
                Arguments.of("INSERT INTO user (name) VALUES (?)", true)
        );
    }

    @ParameterizedTest(name = "{index} => sql: {0} => expectedSafe: {1}")
    @MethodSource("sqlProvider")
    @DisplayName("Parametric SQL safety verification using AST")
    void testSqlSafety(String sql, boolean expectedSafe) {
        SqlSafetyChecker.CheckResult result = SqlSafetyChecker.check(sql);
        assertThat(result.isSafe())
                .as("Checking SQL: %s", sql)
                .isEqualTo(expectedSafe);
    }

    @Test
    @DisplayName("Test UNION queries")
    void testUnionQueries() {
        // Both sides safe
        String sql1 = "SELECT * FROM user WHERE id = 1 UNION SELECT * FROM user WHERE id = 2";
        assertThat(SqlSafetyChecker.check(sql1).isSafe()).isTrue();

        // One side unsafe
        String sql2 = "SELECT * FROM user WHERE id = 1 UNION SELECT * FROM user";
        assertThat(SqlSafetyChecker.check(sql2).isSafe()).isFalse();

        // UNION ALL one side unsafe
        String sql3 = "SELECT * FROM user UNION ALL SELECT * FROM user WHERE id = 1";
        assertThat(SqlSafetyChecker.check(sql3).isSafe()).isFalse();
    }

    @Test
    @DisplayName("Test empty or blank SQL")
    void testEmptySql() {
        assertThat(SqlSafetyChecker.check(null).isSafe()).isTrue();
        assertThat(SqlSafetyChecker.check("").isSafe()).isTrue();
        assertThat(SqlSafetyChecker.check("   ").isSafe()).isTrue();
    }

    @Test
    @DisplayName("Test isCount detection on CheckResult")
    void testIsCount() {
        assertThat(SqlSafetyChecker.check("SELECT COUNT(*) FROM user").isCount()).isTrue();
        assertThat(SqlSafetyChecker.check("SELECT count(0) FROM user").isCount()).isTrue();
        assertThat(SqlSafetyChecker.check("SELECT count(1) as total FROM user").isCount()).isTrue();
        assertThat(SqlSafetyChecker.check("SELECT * FROM user").isCount()).isFalse();
        assertThat(SqlSafetyChecker.check("SELECT id, name FROM user").isCount()).isFalse();
    }
}
