package com.github.mybatisfulltable.boot.mapper;

import com.github.mybatisfulltable.annotation.IgnoreFullTable;
import com.github.mybatisfulltable.boot.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper {

    List<User> findAll();

    User findById(@Param("id") Long id);

    @Select("SELECT count(*) FROM t_user")
    int countAll();

    @IgnoreFullTable(reason = "Allowed full table scan for reporting")
    List<User> findAllAllowed();

    int updateStatusAll();

    @IgnoreFullTable
    int updateStatusAllAllowed();

    int deleteAll();

    @IgnoreFullTable
    int deleteAllAllowed();
}
