package com.li.apicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class APictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(APictureBackendApplication.class, args);
    }

}
