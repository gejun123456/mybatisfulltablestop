package com.github.mybatisfulltable.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to exempt a Mapper method or Mapper interface from full-table operation restrictions.
 * <p>
 * When applied, the marked method or all methods in the marked interface will be permitted
 * to execute full-table SELECT, UPDATE, or DELETE operations.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreFullTable {

    /**
     * Optional explanation or reason why full table operation is allowed.
     *
     * @return reason string
     */
    String reason() default "";
}
