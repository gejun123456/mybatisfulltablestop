package com.github.mybatisfulltable.exception;

/**
 * Exception thrown when a full table SELECT, UPDATE, or DELETE operation is intercepted.
 */
public class FullTableOperationException extends RuntimeException {

    private final String statementId;
    private final String sql;
    private final String operationType;
    private final String reason;

    public FullTableOperationException(String statementId, String sql, String operationType, String reason) {
        super(buildMessage(statementId, sql, operationType, reason));
        this.statementId = statementId;
        this.sql = sql;
        this.operationType = operationType;
        this.reason = reason;
    }

    private static String buildMessage(String statementId, String sql, String operationType, String reason) {
        return "\nFull table operation blocked.\n\n" +
                "Statement:\n" + statementId + "\n\n" +
                "Operation:\n" + operationType + "\n\n" +
                "SQL:\n" + sql + "\n\n" +
                "Reason:\n" + reason + "\n\n" +
                "If this operation is intentional, use @IgnoreFullTable.\n";
    }

    public String getStatementId() {
        return statementId;
    }

    public String getSql() {
        return sql;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getReason() {
        return reason;
    }
}
