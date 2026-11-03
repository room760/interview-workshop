
package com.cqr.interviewworkshop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 讯智后管应用
 */
@SpringBootApplication
@MapperScan("com.cqr.interviewworkshop.**.dao.mapper")
@EnableScheduling
public class InterviewWorkshopAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(InterviewWorkshopAdminApplication.class, args);
    }
}
