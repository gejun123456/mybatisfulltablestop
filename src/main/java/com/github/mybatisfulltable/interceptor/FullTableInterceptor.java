package com.github.mybatisfulltable.interceptor;

import com.github.mybatisfulltable.annotation.IgnoreFullTable;
import com.github.mybatisfulltable.config.FullTableProperties;
import com.github.mybatisfulltable.exception.FullTableOperationException;
import com.github.mybatisfulltable.parser.SqlSafetyChecker;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native MyBatis Interceptor using JSqlParser AST analysis to intercept SELECT, UPDATE,
 * and DELETE operations to prevent accidental full-table operations.
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class FullTableInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(FullTableInterceptor.class);

    private final FullTableProperties properties;

    /**
     * Cache for reflection lookup of @IgnoreFullTable by MappedStatement ID.
     */
    private final Map<String, Boolean> ignoreCache = new ConcurrentHashMap<>();

    /**
     * Re-entrancy guard to avoid checking twice when Executor.query(4-arg) invokes Executor.query(6-arg).
     */
    private final ThreadLocal<Boolean> reentrancyGuard = new ThreadLocal<>();

    public FullTableInterceptor() {
        this(new FullTableProperties());
    }

    public FullTableInterceptor(FullTableProperties properties) {
        this.properties = properties != null ? properties : new FullTableProperties();
    }

    public FullTableProperties getProperties() {
        return properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.isEnabled()) {
            return invocation.proceed();
        }

        // Prevent redundant check if already executing within this interceptor
        if (Boolean.TRUE.equals(reentrancyGuard.get())) {
            return invocation.proceed();
        }

        try {
            reentrancyGuard.set(Boolean.TRUE);

            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object parameter = args[1];

            // Check if @IgnoreFullTable is present on method or interface
            if (isIgnored(ms.getId())) {
                return invocation.proceed();
            }

            // Obtain BoundSql
            BoundSql boundSql;
            if (args.length >= 6 && args[5] instanceof BoundSql) {
                boundSql = (BoundSql) args[5];
            } else {
                boundSql = ms.getBoundSql(parameter);
            }

            String sql = boundSql.getSql();

            // Run SQL safety check
            SqlSafetyChecker.CheckResult checkResult = SqlSafetyChecker.check(sql);
            if (!checkResult.isSafe()) {
                String op = checkResult.getOperationType();
                boolean shouldBlock = shouldBlockOperation(checkResult);

                if (shouldBlock) {
                    if (properties.getMode() == FullTableProperties.Mode.WARN) {
                        log.warn("Full table operation intercepted: Statement: {}, Operation: {}, SQL: {}, Reason: {}",
                                ms.getId(), op, sql, checkResult.getReason());
                    } else {
                        throw new FullTableOperationException(ms.getId(), sql, op, checkResult.getReason());
                    }
                }
            }

            return invocation.proceed();
        } finally {
            reentrancyGuard.remove();
        }
    }

    private boolean shouldBlockOperation(SqlSafetyChecker.CheckResult checkResult) {
        String operationType = checkResult.getOperationType();
        if ("SELECT".equalsIgnoreCase(operationType)) {
            if (checkResult.isCount()) {
                return properties.isBlockCount();
            }
            return properties.isBlockSelect();
        } else if ("UPDATE".equalsIgnoreCase(operationType)) {
            return properties.isBlockUpdate();
        } else if ("DELETE".equalsIgnoreCase(operationType)) {
            return properties.isBlockDelete();
        }
        return false;
    }

    /**
     * Checks if the statement is annotated with @IgnoreFullTable, using cache.
     */
    public boolean isIgnored(String statementId) {
        if (statementId == null) {
            return false;
        }
        return ignoreCache.computeIfAbsent(statementId, this::resolveIgnoreAnnotation);
    }

    private boolean resolveIgnoreAnnotation(String statementId) {
        int lastDot = statementId.lastIndexOf('.');
        if (lastDot == -1) {
            return false;
        }

        String className = statementId.substring(0, lastDot);
        String methodName = statementId.substring(lastDot + 1);

        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException ex) {
                // Not a class-backed mapper or class cannot be resolved
                return false;
            }
        }

        // Check if the mapper interface/class has @IgnoreFullTable
        if (clazz.isAnnotationPresent(IgnoreFullTable.class)) {
            return true;
        }

        // Check all methods matching the name (handles overloads)
        if (hasMethodIgnoreAnnotation(clazz, methodName)) {
            return true;
        }

        // Handle pagination plugins (e.g. PageHelper appends "_COUNT" or "_count" to statement ID)
        if (methodName.endsWith("_COUNT") || methodName.endsWith("_count")) {
            String baseMethodName = methodName.substring(0, methodName.length() - 6);
            if (hasMethodIgnoreAnnotation(clazz, baseMethodName)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasMethodIgnoreAnnotation(Class<?> clazz, String methodName) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName) && method.isAnnotationPresent(IgnoreFullTable.class)) {
                return true;
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.isAnnotationPresent(IgnoreFullTable.class)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties props) {
        if (props == null) {
            return;
        }

        String enabledStr = props.getProperty("enabled");
        if (enabledStr != null) {
            properties.setEnabled(Boolean.parseBoolean(enabledStr.trim()));
        }

        String blockSelectStr = props.getProperty("blockSelect");
        if (blockSelectStr != null) {
            properties.setBlockSelect(Boolean.parseBoolean(blockSelectStr.trim()));
        }

        String blockCountStr = props.getProperty("blockCount");
        if (blockCountStr != null) {
            properties.setBlockCount(Boolean.parseBoolean(blockCountStr.trim()));
        }

        String blockUpdateStr = props.getProperty("blockUpdate");
        if (blockUpdateStr != null) {
            properties.setBlockUpdate(Boolean.parseBoolean(blockUpdateStr.trim()));
        }

        String blockDeleteStr = props.getProperty("blockDelete");
        if (blockDeleteStr != null) {
            properties.setBlockDelete(Boolean.parseBoolean(blockDeleteStr.trim()));
        }

        String modeStr = props.getProperty("mode");
        if (modeStr != null) {
            try {
                properties.setMode(FullTableProperties.Mode.valueOf(modeStr.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
