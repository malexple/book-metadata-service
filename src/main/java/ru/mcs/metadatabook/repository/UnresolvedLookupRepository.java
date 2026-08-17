package ru.mcs.metadatabook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mcs.metadatabook.entity.UnresolvedLookup;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnresolvedLookupRepository extends JpaRepository<UnresolvedLookup, Long> {
    List<UnresolvedLookup> findByOrgIdAndStatus(Integer orgId, String status);
    Optional<UnresolvedLookup> findByOrgIdAndIsbn(Integer orgId, String isbn);
}