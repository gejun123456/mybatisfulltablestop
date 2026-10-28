package com.github.mybatisfulltable.boot;

import com.github.mybatisfulltable.boot.entity.User;
import com.github.mybatisfulltable.boot.mapper.UserMapper;
import com.github.mybatisfulltable.exception.FullTableOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestApplication.class)
public class SpringBootIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDb() {
        jdbcTemplate.execute("DELETE FROM t_user");
        jdbcTemplate.execute("INSERT INTO t_user (id, name, status) VALUES (1, 'Alice', 1)");
        jdbcTemplate.execute("INSERT INTO t_user (id, name, status) VALUES (2, 'Bob', 1)");
    }

    @Test
    @DisplayName("userMapper.findAll() should throw FullTableOperationException")
    void testFindAllBlocked() {
        assertThatThrownBy(() -> userMapper.findAll())
                .hasRootCauseInstanceOf(FullTableOperationException.class)
                .hasMessageContaining("Full table operation blocked")
                .hasMessageContaining("Statement:\ncom.github.mybatisfulltable.boot.mapper.UserMapper.findAll")
                .hasMessageContaining("Operation:\nSELECT")
                .hasMessageContaining("SQL:\nSELECT id, name, status FROM t_user")
                .hasMessageContaining("Reason:\nNo effective WHERE condition found")
                .hasMessageContaining("If this operation is intentional, use @IgnoreFullTable");
    }

    @Test
    @DisplayName("userMapper.findById(1) should execute normally")
    void testFindByIdAllowed() {
        User user = userMapper.findById(1L);
        assertThat(user).isNotNull();
        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("userMapper.countAll() should execute normally (blockCount=false by default)")
    void testCountAllAllowed() {
        int count = userMapper.countAll();
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("userMapper.findAllAllowed() with @IgnoreFullTable should execute normally")
    void testFindAllAllowedWithAnnotation() {
        List<User> users = userMapper.findAllAllowed();
        assertThat(users).isNotNull();
        assertThat(users).hasSize(2);
    }

    @Test
    @DisplayName("userMapper.updateStatusAll() should throw FullTableOperationException")
    void testUpdateAllBlocked() {
        assertThatThrownBy(() -> userMapper.updateStatusAll())
                .hasRootCauseInstanceOf(FullTableOperationException.class)
                .hasMessageContaining("Operation:\nUPDATE");
    }

    @Test
    @DisplayName("userMapper.updateStatusAllAllowed() with @IgnoreFullTable should execute normally")
    void testUpdateAllAllowed() {
        int count = userMapper.updateStatusAllAllowed();
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("userMapper.deleteAll() should throw FullTableOperationException")
    void testDeleteAllBlocked() {
        assertThatThrownBy(() -> userMapper.deleteAll())
                .hasRootCauseInstanceOf(FullTableOperationException.class)
                .hasMessageContaining("Operation:\nDELETE");
    }

    @Test
    @DisplayName("userMapper.deleteAllAllowed() with @IgnoreFullTable should execute normally")
    void testDeleteAllAllowed() {
        int count = userMapper.deleteAllAllowed();
        assertThat(count).isEqualTo(2);
    }
}
