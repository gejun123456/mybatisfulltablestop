package com.example.demo.mapper;

import com.example.demo.entity.User;
import com.github.mybatisfulltable.annotation.IgnoreFullTable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    /**
     * Accidental full table query without WHERE. Will be blocked.
     */
    List<User> findAll();

    /**
     * Query by primary key with WHERE condition. Allowed.
     */
    User findById(@Param("id") Long id);

    /**
     * Intentional full table query with @IgnoreFullTable exemption. Allowed.
     */
    @IgnoreFullTable(reason = "Export all users for report generation")
    List<User> findAllAllowed();

    /**
     * Intentional full table delete with @IgnoreFullTable exemption. Allowed.
     */
    @IgnoreFullTable(reason = "Clean up test data")
    int deleteAll();

    /**
     * Intentional full table update with @IgnoreFullTable exemption. Allowed.
     */
    @IgnoreFullTable(reason = "Reset all user statuses")
    int updateAllStatus();
}
