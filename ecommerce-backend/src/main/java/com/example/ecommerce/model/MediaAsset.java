package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A photo or video the owner uploaded. With R2 configured the file lives in R2 (storageKey) and the database keeps
 * only these details; otherwise the bytes are kept in the data column so the store works on hosts without a disk.
 */
@Entity
@Table(name = "media_assets")
public class MediaAsset {
    public enum Kind { IMAGE, VIDEO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** First 10 hex characters of the SHA-256 of the bytes; part of the public URL so cached copies can never go stale. */
    @Column(nullable = false, length = 16)
    private String checksum;

    @Lob
    @Column(columnDefinition = "LONGBLOB NULL")
    private byte[] data;

    /** Object key in R2, e.g. media/ab12cd34ef (the checksum); null while the bytes are still in data. */
    @Column(name = "storage_key", length = 200)
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
