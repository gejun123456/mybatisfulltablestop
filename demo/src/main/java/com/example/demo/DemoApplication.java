package com.example.demo;

import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.github.mybatisfulltable.exception.FullTableOperationException;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
@MapperScan("com.example.demo.mapper")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner runner(UserMapper userMapper) {
        return args -> {
            System.out.println("========== Running FullTableInterceptor Demo ==========");

            // 1. Accidental full table query (blocked)
            System.out.println("1. Executing userMapper.findAll() [EXPECTED TO BE BLOCKED]...");
            try {
                userMapper.findAll();
                System.err.println("ERROR: Expected FullTableOperationException, but execution succeeded!");
            } catch (Exception e) {
                System.out.println(">> Intercepted successfully! Exception: " + e.getMessage());
            }

            // 2. Query with valid condition (allowed)
            System.out.println("\n2. Executing userMapper.findById(1) [EXPECTED TO BE ALLOWED]...");
            User user = userMapper.findById(1L);
            System.out.println(">> Success: " + user);

            // 3. Query with @IgnoreFullTable exemption (allowed)
            System.out.println("\n3. Executing userMapper.findAllAllowed() with @IgnoreFullTable [EXPECTED TO BE ALLOWED]...");
            List<User> users = userMapper.findAllAllowed();
            System.out.println(">> Success: retrieved " + users.size() + " users.");

            System.out.println("========== Demo Finished ==========");
        };
    }
}
