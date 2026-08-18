package ru.mcs.metadatabook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mcs.metadatabook.entity.LlmSuggestion;

import java.util.List;

@Repository
public interface LlmSuggestionRepository extends JpaRepository<LlmSuggestion, Long> {
    List<LlmSuggestion> findByOrgIdAndStatusOrderByCreatedAtDesc(Integer orgId, String status);
}