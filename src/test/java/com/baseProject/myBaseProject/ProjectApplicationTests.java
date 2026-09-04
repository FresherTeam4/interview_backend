package com.baseProject.myBaseProject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.interview.enabled=false")
class ProjectApplicationTests {

    @Test
    void contextLoads() {
    }

}
