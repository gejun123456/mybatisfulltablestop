package com.github.mybatisfulltable.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for MyBatis Full Table Interceptor (JSqlParser version).
 */
@ConfigurationProperties(prefix = "mybatis.full-table")
public class FullTableProperties {

    public enum Mode {
        STRICT,
        WARN
    }

    /**
     * Whether to enable the full table interceptor. Default is true.
     */
    private boolean enabled = true;

    /**
     * Whether to block full table SELECT queries. Default is true.
     */
    private boolean blockSelect = true;

    /**
     * Whether to block full table SELECT COUNT(...) queries. Default is false.
     */
    private boolean blockCount = false;

    /**
     * Whether to block full table UPDATE statements. Default is true.
     */
    private boolean blockUpdate = true;

    /**
     * Whether to block full table DELETE statements. Default is true.
     */
    private boolean blockDelete = true;

    /**
     * Interception mode: STRICT (throws FullTableOperationException) or WARN (logs warning). Default is STRICT.
     */
    private Mode mode = Mode.STRICT;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isBlockSelect() {
        return blockSelect;
    }

    public void setBlockSelect(boolean blockSelect) {
        this.blockSelect = blockSelect;
    }

    public boolean isBlockCount() {
        return blockCount;
    }

    public void setBlockCount(boolean blockCount) {
        this.blockCount = blockCount;
    }

    public boolean isBlockUpdate() {
        return blockUpdate;
    }

    public void setBlockUpdate(boolean blockUpdate) {
        this.blockUpdate = blockUpdate;
    }

    public boolean isBlockDelete() {
        return blockDelete;
    }

    public void setBlockDelete(boolean blockDelete) {
        this.blockDelete = blockDelete;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }
}
