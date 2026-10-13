package com.bachelorchef.repository;

import com.bachelorchef.entity.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByNormalizedName(String normalizedName);

    List<Tag> findByNormalizedNameIn(Collection<String> normalizedNames);

    List<Tag> findAllByOrderByNameAsc();
}
