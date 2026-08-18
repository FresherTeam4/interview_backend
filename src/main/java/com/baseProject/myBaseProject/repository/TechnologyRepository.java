package com.baseProject.myBaseProject.repository;

import java.util.Collection;
import java.util.List;

import com.baseProject.myBaseProject.entity.Technology;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnologyRepository extends JpaRepository<Technology, Integer> {
    List<Technology> findAllByIdInAndActiveTrue(Collection<Integer> ids);

    List<Technology> findAllByOrderByTechnologyTypeAscNameEnAsc();

    List<Technology> findAllByActiveTrueOrderByTechnologyTypeAscNameEnAsc();
}
