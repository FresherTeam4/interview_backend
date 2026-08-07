package com.baseProject.myBaseProject.security.authorization;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE}) // nơi đặt
@Retention(RetentionPolicy.RUNTIME) // lifecycle
@Documented
@PreAuthorize("hasRole('EVENT_ADMIN')")
public @interface IsEventAdmin {
}

