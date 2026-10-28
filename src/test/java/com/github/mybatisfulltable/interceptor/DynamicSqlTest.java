package com.github.mybatisfulltable.interceptor;

import com.github.mybatisfulltable.exception.FullTableOperationException;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DynamicSqlTest {

    private static SqlSessionFactory sqlSessionFactory;

    public interface DynamicUserMapper {
        List<Map<String, Object>> findUsers(@Param("id") Integer id);

        int updateUsers(@Param("status") Integer status, @Param("id") Integer id);

        int deleteUsers(@Param("id") Integer id);
    }

    @BeforeAll
    static void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:dynamictest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE user (id INT PRIMARY KEY, name VARCHAR(50), status INT)");
            stmt.execute("INSERT INTO user VALUES (1, 'Alice', 1), (2, 'Bob', 1)");
        }

        TransactionFactory transactionFactory = new JdbcTransactionFactory();
        Environment environment = new Environment("development", transactionFactory, dataSource);
        Configuration configuration = new Configuration(environment);

        // Add FullTableInterceptor
        configuration.addInterceptor(new FullTableInterceptor());

        // Register mapper XML
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" +
                "<mapper namespace=\"com.github.mybatisfulltable.interceptor.DynamicSqlTest$DynamicUserMapper\">\n" +
                "    <select id=\"findUsers\" resultType=\"map\">\n" +
                "        SELECT * FROM user\n" +
                "        <where>\n" +
                "            <if test=\"id != null\">\n" +
                "                AND id = #{id}\n" +
                "            </if>\n" +
                "        </where>\n" +
                "    </select>\n" +
                "    <update id=\"updateUsers\">\n" +
                "        UPDATE user SET status = #{status}\n" +
                "        <where>\n" +
                "            <if test=\"id != null\">\n" +
                "                AND id = #{id}\n" +
                "            </if>\n" +
                "        </where>\n" +
                "    </update>\n" +
                "    <delete id=\"deleteUsers\">\n" +
                "        DELETE FROM user\n" +
                "        <where>\n" +
                "            <if test=\"id != null\">\n" +
                "                AND id = #{id}\n" +
                "            </if>\n" +
                "        </where>\n" +
                "    </delete>\n" +
                "</mapper>";

        org.apache.ibatis.builder.xml.XMLMapperBuilder mapperBuilder =
                new org.apache.ibatis.builder.xml.XMLMapperBuilder(
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                        configuration, "DynamicUserMapper.xml", configuration.getSqlFragments());
        mapperBuilder.parse();

        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    @DisplayName("Dynamic SQL SELECT: id != null is allowed, id == null is blocked")
    void testDynamicSelect() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            DynamicUserMapper mapper = session.getMapper(DynamicUserMapper.class);

            // When id is provided, WHERE id = ? is generated -> ALLOWED
            List<Map<String, Object>> users = mapper.findUsers(1);
            assertThat(users).hasSize(1);

            // When id is null, WHERE is omitted -> BLOCKED
            assertThatThrownBy(() -> mapper.findUsers(null))
                    .hasRootCauseInstanceOf(FullTableOperationException.class)
                    .hasMessageContaining("Full table operation blocked")
                    .hasMessageContaining("SELECT")
                    .hasMessageContaining("No effective WHERE condition found");
        }
    }

    @Test
    @DisplayName("Dynamic SQL UPDATE: id != null is allowed, id == null is blocked")
    void testDynamicUpdate() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            DynamicUserMapper mapper = session.getMapper(DynamicUserMapper.class);

            // When id is provided -> ALLOWED
            int updated = mapper.updateUsers(0, 1);
            assertThat(updated).isEqualTo(1);

            // When id is null -> BLOCKED
            assertThatThrownBy(() -> mapper.updateUsers(0, null))
                    .hasRootCauseInstanceOf(FullTableOperationException.class)
                    .hasMessageContaining("Full table operation blocked")
                    .hasMessageContaining("UPDATE");
        }
    }

    @Test
    @DisplayName("Dynamic SQL DELETE: id != null is allowed, id == null is blocked")
    void testDynamicDelete() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            DynamicUserMapper mapper = session.getMapper(DynamicUserMapper.class);

            // When id is null -> BLOCKED (verifying before deleting existing data)
            assertThatThrownBy(() -> mapper.deleteUsers(null))
                    .hasRootCauseInstanceOf(FullTableOperationException.class)
                    .hasMessageContaining("Full table operation blocked")
                    .hasMessageContaining("DELETE");

            // When id is provided -> ALLOWED
            int deleted = mapper.deleteUsers(2);
            assertThat(deleted).isEqualTo(1);
        }
    }
}
