package com.psh.diet_planner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DietPlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DietPlannerApplication.class, args);
    }

}
