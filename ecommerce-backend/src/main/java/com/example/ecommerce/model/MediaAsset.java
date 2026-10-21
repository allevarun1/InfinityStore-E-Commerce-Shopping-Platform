package com.example.ecommerce.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A photo or video the owner uploaded. The bytes live in the database so the store works on hosts without a disk. */
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
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

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
    public LocalDateTime getCreatedAt() { return createdAt; }
}
