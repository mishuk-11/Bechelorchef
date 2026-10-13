package com.bachelorchef.repository;

import com.bachelorchef.entity.UserGoal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGoalRepository extends JpaRepository<UserGoal, Long> {

    @EntityGraph(attributePaths = "tag")
    List<UserGoal> findByUserIdOrderByTargetMonthDescIdDesc(Long userId);

    @EntityGraph(attributePaths = "tag")
    List<UserGoal> findByUserIdAndTargetMonthIn(Long userId, Collection<LocalDate> months);
}
