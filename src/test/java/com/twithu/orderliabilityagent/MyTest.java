package com.twithu.orderliabilityagent;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@ActiveProfiles({"test","local"}) // 配合@Profile("!test")使用，在测试时，不注册对应的Bean
public @interface MyTest {
}
