package com.github.mybatisfulltable.interceptor;

import com.github.mybatisfulltable.annotation.IgnoreFullTable;
import com.github.mybatisfulltable.config.FullTableProperties;
import com.github.mybatisfulltable.exception.FullTableOperationException;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
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

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IgnoreFullTableTest {

    private static JdbcDataSource dataSource;

    public interface UserMapper {
        @Select("SELECT * FROM user")
        List<Map<String, Object>> findAllBlocked();

        @IgnoreFullTable(reason = "Export all users for admin")
        @Select("SELECT * FROM user")
        List<Map<String, Object>> findAllAllowed();

        @Select("SELECT count(*) FROM user")
        int countAll();

        @Update("UPDATE user SET status = 0")
        int updateAllBlocked();

        @IgnoreFullTable
        @Update("UPDATE user SET status = 0")
        int updateAllAllowed();

        @Delete("DELETE FROM user")
        int deleteAllBlocked();

        @IgnoreFullTable
        @Delete("DELETE FROM user")
        int deleteAllAllowed();
    }

    @IgnoreFullTable(reason = "Maintenance mapper")
    public interface WhitelistedMapper {
        @Select("SELECT * FROM user")
        List<Map<String, Object>> findAll();

        @Delete("DELETE FROM user")
        int deleteAll();
    }

    @BeforeAll
    static void setUpDb() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ignoretest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE user (id INT PRIMARY KEY, name VARCHAR(50), status INT)");
            stmt.execute("INSERT INTO user VALUES (1, 'Alice', 1), (2, 'Bob', 1)");
        }
    }

    private SqlSessionFactory createSessionFactory(FullTableInterceptor interceptor, Class<?>... mappers) {
        TransactionFactory transactionFactory = new JdbcTransactionFactory();
        Environment environment = new Environment("development", transactionFactory, dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.addInterceptor(interceptor);
        for (Class<?> mapper : mappers) {
            configuration.addMapper(mapper);
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    @DisplayName("Test @IgnoreFullTable on methods: whitelisted allowed, others blocked")
    void testMethodLevelAnnotation() {
        FullTableInterceptor interceptor = new FullTableInterceptor();
        SqlSessionFactory factory = createSessionFactory(interceptor, UserMapper.class);

        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);

            // Blocked without annotation
            assertThatThrownBy(mapper::findAllBlocked)
                    .hasRootCauseInstanceOf(FullTableOperationException.class)
                    .hasMessageContaining("SELECT");

            // Allowed with @IgnoreFullTable
            List<Map<String, Object>> all = mapper.findAllAllowed();
            assertThat(all).hasSize(2);

            // UPDATE: blocked without annotation
            assertThatThrownBy(mapper::updateAllBlocked)
                    .hasRootCauseInstanceOf(FullTableOperationException.class)
                    .hasMessageContaining("UPDATE");

            // UPDATE: allowed with annotation
            int updated = mapper.updateAllAllowed();
            assertThat(updated).isEqualTo(2);

            // DELETE: blocked without annotation
            assertThatThrownBy(mapper::deleteAllBlocked)
                    .hasRootCauseInstanceOf(FullTableOperationException.class)
                    .hasMessageContaining("DELETE");

            // DELETE: allowed with annotation
            int deleted = mapper.deleteAllAllowed();
            assertThat(deleted).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("Test @IgnoreFullTable on interface: all methods are exempt")
    void testInterfaceLevelAnnotation() {
        FullTableInterceptor interceptor = new FullTableInterceptor();
        SqlSessionFactory factory = createSessionFactory(interceptor, WhitelistedMapper.class);

        try (SqlSession session = factory.openSession()) {
            WhitelistedMapper mapper = session.getMapper(WhitelistedMapper.class);

            // Allowed because interface has @IgnoreFullTable
            List<Map<String, Object>> all = mapper.findAll();
            assertThat(all).isNotNull();

            int deleted = mapper.deleteAll();
            assertThat(deleted).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("Test annotation cache lookup")
    void testAnnotationCache() {
        FullTableInterceptor interceptor = new FullTableInterceptor();
        String allowedId = UserMapper.class.getName() + ".findAllAllowed";
        String blockedId = UserMapper.class.getName() + ".findAllBlocked";

        assertThat(interceptor.isIgnored(allowedId)).isTrue();
        assertThat(interceptor.isIgnored(blockedId)).isFalse();

        // Second call should hit the cache
        assertThat(interceptor.isIgnored(allowedId)).isTrue();
        assertThat(interceptor.isIgnored(blockedId)).isFalse();
    }

    @Test
    @DisplayName("Test configuration: blockCount=false (default) allows COUNT queries without WHERE")
    void testBlockCountDefaultFalse() {
        FullTableProperties properties = new FullTableProperties();
        assertThat(properties.isBlockCount()).isFalse();
        FullTableInterceptor interceptor = new FullTableInterceptor(properties);

        SqlSessionFactory factory = createSessionFactory(interceptor, UserMapper.class);
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            // countAll is allowed because blockCount=false by default
            int count = mapper.countAll();
            assertThat(count).isEqualTo(2);

            // But full table SELECT is still blocked because blockSelect=true
            assertThatThrownBy(mapper::findAllBlocked)
                    .hasRootCauseInstanceOf(FullTableOperationException.class);
        }
    }

    @Test
    @DisplayName("Test configuration: blockCount=true blocks COUNT queries without WHERE")
    void testBlockCountTrue() {
        FullTableProperties properties = new FullTableProperties();
        properties.setBlockCount(true);
        FullTableInterceptor interceptor = new FullTableInterceptor(properties);

        SqlSessionFactory factory = createSessionFactory(interceptor, UserMapper.class);
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            // countAll is blocked because blockCount=true
            assertThatThrownBy(mapper::countAll)
                    .hasRootCauseInstanceOf(FullTableOperationException.class)
                    .hasMessageContaining("SELECT")
                    .hasMessageContaining("count(*)");
        }
    }

    @Test
    @DisplayName("Test configuration: enabled=false allows all full-table operations")
    void testDisabledConfig() {
        FullTableProperties properties = new FullTableProperties();
        properties.setEnabled(false);
        FullTableInterceptor interceptor = new FullTableInterceptor(properties);

        SqlSessionFactory factory = createSessionFactory(interceptor, UserMapper.class);
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            List<Map<String, Object>> users = mapper.findAllBlocked();
            assertThat(users).isNotNull();
        }
    }

    @Test
    @DisplayName("Test configuration: blockSelect=false allows SELECT but blocks UPDATE")
    void testBlockSelectFalse() {
        FullTableProperties properties = new FullTableProperties();
        properties.setBlockSelect(false);
        FullTableInterceptor interceptor = new FullTableInterceptor(properties);

        SqlSessionFactory factory = createSessionFactory(interceptor, UserMapper.class);
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            List<Map<String, Object>> users = mapper.findAllBlocked();
            assertThat(users).isNotNull();

            assertThatThrownBy(mapper::updateAllBlocked)
                    .hasRootCauseInstanceOf(FullTableOperationException.class);
        }
    }

    @Test
    @DisplayName("Test configuration: mode=WARN logs warning without throwing exception")
    void testModeWarn() {
        FullTableProperties properties = new FullTableProperties();
        properties.setMode(FullTableProperties.Mode.WARN);
        FullTableInterceptor interceptor = new FullTableInterceptor(properties);

        SqlSessionFactory factory = createSessionFactory(interceptor, UserMapper.class);
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            List<Map<String, Object>> users = mapper.findAllBlocked();
            assertThat(users).isNotNull();
        }
    }
}
