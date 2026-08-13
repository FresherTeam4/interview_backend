package com.baseProject.myBaseProject.repository;

import java.util.List;
import java.util.Optional;

import com.baseProject.myBaseProject.entity.TechStack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechStackRepository extends JpaRepository<TechStack, Integer> {
    Optional<TechStack> findByIdAndActiveTrue(Integer id);

    List<TechStack> findAllByOrderByNameEnAsc();

    List<TechStack> findAllByActiveTrueOrderByNameEnAsc();
}
