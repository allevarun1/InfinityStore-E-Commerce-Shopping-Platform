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
}
