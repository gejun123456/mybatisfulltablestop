package com.github.mybatisfulltable.parser;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;

import java.util.List;
import java.util.Objects;

/**
 * AST-based SQL safety checker using JSqlParser.
 * <p>
 * Parses SQL statements into an AST (Abstract Syntax Tree) to accurately detect
 * whether SELECT, UPDATE, and DELETE operations contain effective WHERE conditions.
 */
public class SqlSafetyChecker {

    public static class CheckResult {
        private final boolean safe;
        private final String operationType;
        private final String reason;
        private final boolean count;

        private CheckResult(boolean safe, String operationType, String reason, boolean count) {
            this.safe = safe;
            this.operationType = operationType;
            this.reason = reason;
            this.count = count;
        }

        public static CheckResult safe(String operationType) {
            return new CheckResult(true, operationType, null, false);
        }

        public static CheckResult unsafe(String operationType, String reason) {
            return new CheckResult(false, operationType, reason, false);
        }

        public static CheckResult unsafe(String operationType, String reason, boolean count) {
            return new CheckResult(false, operationType, reason, count);
        }

        public boolean isSafe() {
            return safe;
        }

        public String getOperationType() {
            return operationType;
        }

        public String getReason() {
            return reason;
        }

        public boolean isCount() {
            return count;
        }

        @Override
        public String toString() {
            return (safe ? "ALLOW" : "BLOCK") + (reason != null ? " (" + reason + ")" : "");
        }
    }

    /**
     * Checks if the given SQL is safe from accidental full-table operations using AST analysis.
     *
     * @param sql the raw or bound SQL statement
     * @return CheckResult with safety status, operation type, and failure reason if unsafe
     */
    public static CheckResult check(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return CheckResult.safe("EMPTY");
        }

        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            // If parser fails, extract leading keyword
            String trimmed = removeLeadingComments(sql.trim());
            String firstWord = getFirstWord(trimmed).toUpperCase();
            if ("SELECT".equals(firstWord) || "UPDATE".equals(firstWord) || "DELETE".equals(firstWord)) {
                return CheckResult.unsafe(firstWord, "Unable to parse SQL statement: " + e.getMessage());
            }
            return CheckResult.safe("OTHER");
        }

        if (statement instanceof Select) {
            return checkSelect((Select) statement);
        } else if (statement instanceof Update) {
            return checkUpdate((Update) statement);
        } else if (statement instanceof Delete) {
            return checkDelete((Delete) statement);
        }

        return CheckResult.safe("OTHER");
    }

    private static CheckResult checkSelect(Select select) {
        if (select instanceof PlainSelect) {
            return checkPlainSelect((PlainSelect) select);
        } else if (select instanceof SetOperationList) {
            SetOperationList setOperationList = (SetOperationList) select;
            if (setOperationList.getSelects() != null) {
                for (Select sub : setOperationList.getSelects()) {
                    CheckResult res = checkSelect(sub);
                    if (!res.isSafe()) {
                        return res;
                    }
                }
            }
            return CheckResult.safe("SELECT");
        }
        return CheckResult.safe("SELECT");
    }

    private static CheckResult checkPlainSelect(PlainSelect ps) {
        FromItem fromItem = ps.getFromItem();
        // SELECT without FROM (e.g. SELECT 1, SELECT NOW()) is safe
        if (fromItem == null) {
            return CheckResult.safe("SELECT");
        }

        boolean isCount = isCountSelect(ps);

        // Dummy table DUAL in Oracle/MySQL (SELECT 1 FROM DUAL) is safe
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            if ("DUAL".equalsIgnoreCase(table.getName())) {
                return CheckResult.safe("SELECT");
            }
        }

        Expression where = ps.getWhere();
        if (where == null) {
            return CheckResult.unsafe("SELECT", "No effective WHERE condition found.", isCount);
        }

        if (isTautology(where)) {
            return CheckResult.unsafe("SELECT", "No effective WHERE condition found.", isCount);
        }

        return CheckResult.safe("SELECT");
    }

    private static boolean isCountSelect(PlainSelect ps) {
        List<SelectItem<?>> items = ps.getSelectItems();
        if (items != null && items.size() == 1) {
            SelectItem<?> item = items.get(0);
            Expression expr = item.getExpression();
            if (expr instanceof Function) {
                Function func = (Function) expr;
                return "COUNT".equalsIgnoreCase(func.getName());
            }
        }
        return false;
    }

    private static CheckResult checkUpdate(Update update) {
        Expression where = update.getWhere();
        if (where == null) {
            return CheckResult.unsafe("UPDATE", "No effective WHERE condition found.");
        }

        if (isTautology(where)) {
            return CheckResult.unsafe("UPDATE", "No effective WHERE condition found.");
        }

        return CheckResult.safe("UPDATE");
    }

    private static CheckResult checkDelete(Delete delete) {
        Expression where = delete.getWhere();
        if (where == null) {
            return CheckResult.unsafe("DELETE", "No effective WHERE condition found.");
        }

        if (isTautology(where)) {
            return CheckResult.unsafe("DELETE", "No effective WHERE condition found.");
        }

        return CheckResult.safe("DELETE");
    }

    /**
     * Checks if the AST expression is a tautology (e.g. 1=1, 'a'='a', TRUE, 1=1 AND 2=2).
     */
    public static boolean isTautology(Expression expr) {
        if (expr == null) {
            return true;
        }

        if (expr instanceof Parenthesis) {
            return isTautology(((Parenthesis) expr).getExpression());
        }

        if (expr instanceof AndExpression) {
            AndExpression and = (AndExpression) expr;
            // Both sides must be tautologies for the AND chain to be a tautology
            return isTautology(and.getLeftExpression()) && isTautology(and.getRightExpression());
        }

        if (expr instanceof OrExpression) {
            OrExpression or = (OrExpression) expr;
            // If either side of OR is a tautology, condition matches all rows
            return isTautology(or.getLeftExpression()) || isTautology(or.getRightExpression());
        }


        // Single TRUE column/identifier: WHERE TRUE
        if (expr instanceof Column) {
            Column col = (Column) expr;
            return col.getTable() == null && "TRUE".equalsIgnoreCase(col.getColumnName());
        }

        // Single number: WHERE 1
        if (expr instanceof LongValue) {
            return ((LongValue) expr).getValue() != 0;
        }

        // EqualsTo: left = right
        if (expr instanceof EqualsTo) {
            EqualsTo eq = (EqualsTo) expr;
            Expression left = eq.getLeftExpression();
            Expression right = eq.getRightExpression();

            // Identical columns: id = id, user.id = user.id
            if (isSameColumn(left, right)) {
                return true;
            }

            // Literal comparisons: 1 = 1, 'a' = 'a', 1 = '1'
            if (isLiteral(left) && isLiteral(right)) {
                return compareLiterals(left, right);
            }

            return false;
        }

        // NotEqualsTo: left != right or left <> right
        if (expr instanceof NotEqualsTo) {
            NotEqualsTo ne = (NotEqualsTo) expr;
            Expression left = ne.getLeftExpression();
            Expression right = ne.getRightExpression();

            if (isLiteral(left) && isLiteral(right)) {
                return !compareLiterals(left, right);
            }

            return false;
        }

        // Other expressions (Column, InExpression, LikeExpression, Between, IsNull, SubSelect, etc.)
        // are real filtering conditions.
        return false;
    }

    private static boolean isSameColumn(Expression left, Expression right) {
        if (left instanceof Column && right instanceof Column) {
            Column c1 = (Column) left;
            Column c2 = (Column) right;
            boolean tableSame;
            if (c1.getTable() == null && c2.getTable() == null) {
                tableSame = true;
            } else if (c1.getTable() != null && c2.getTable() != null) {
                tableSame = c1.getTable().getName().equalsIgnoreCase(c2.getTable().getName());
            } else {
                tableSame = false;
            }
            return tableSame && c1.getColumnName().equalsIgnoreCase(c2.getColumnName());
        }
        return false;
    }

    private static boolean isLiteral(Expression expr) {
        return expr instanceof LongValue || expr instanceof DoubleValue ||
                expr instanceof StringValue || expr instanceof DateValue ||
                expr instanceof TimeValue || expr instanceof TimestampValue ||
                expr instanceof HexValue || expr instanceof NullValue;
    }

    private static boolean compareLiterals(Expression left, Expression right) {
        if (left instanceof LongValue && right instanceof LongValue) {
            return ((LongValue) left).getValue() == ((LongValue) right).getValue();
        }
        if (left instanceof StringValue && right instanceof StringValue) {
            return Objects.equals(((StringValue) left).getValue(), ((StringValue) right).getValue());
        }
        if (left instanceof LongValue && right instanceof StringValue) {
            try {
                return ((LongValue) left).getValue() == Long.parseLong(((StringValue) right).getValue());
            } catch (Exception ignored) {
                return false;
            }
        }
        if (left instanceof StringValue && right instanceof LongValue) {
            try {
                return Long.parseLong(((StringValue) left).getValue()) == ((LongValue) right).getValue();
            } catch (Exception ignored) {
                return false;
            }
        }
        return Objects.equals(left.toString(), right.toString());
    }

    private static String removeLeadingComments(String sql) {
        String s = sql.trim();
        while (s.startsWith("--") || s.startsWith("/*") || s.startsWith("#")) {
            if (s.startsWith("--") || s.startsWith("#")) {
                int idx = s.indexOf('\n');
                if (idx != -1) {
                    s = s.substring(idx + 1).trim();
                } else {
                    return "";
                }
            } else if (s.startsWith("/*")) {
                int idx = s.indexOf("*/");
                if (idx != -1) {
                    s = s.substring(idx + 2).trim();
                } else {
                    return "";
                }
            }
        }
        return s;
    }

    private static String getFirstWord(String s) {
        int i = 0;
        while (i < s.length() && !Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(0, i);
    }
}
