package com.baseProject.myBaseProject.repository;

import java.util.Collection;
import java.util.List;

import com.baseProject.myBaseProject.entity.TechStack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechStackRepository extends JpaRepository<TechStack, Integer> {
    List<TechStack> findAllByIdInAndActiveTrue(Collection<Integer> ids);

    List<TechStack> findAllByCodeInAndActiveTrue(Collection<String> codes);

    List<TechStack> findAllByOrderByNameEnAsc();

    List<TechStack> findAllByActiveTrueOrderByNameEnAsc();
}
