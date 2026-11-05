package com.example.ecommerce.repository;

import com.example.ecommerce.model.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    /** Listing columns only, so the library never loads every file's bytes. */
    interface Meta {
        Long getId();
        String getName();
        String getContentType();
        MediaAsset.Kind getKind();
        long getSizeBytes();
        String getChecksum();
        LocalDateTime getCreatedAt();
    }

    List<Meta> findAllByOrderByIdDesc();

    /** True while another file still points at this R2 object (identical uploads share one copy). */
    boolean existsByStorageKey(String storageKey);

    /** Files still kept in the database, to move into R2. */
    @org.springframework.data.jpa.repository.Query("select m.id from MediaAsset m where m.storageKey is null order by m.id")
    List<Long> findIdsStoredInDatabase();
}
