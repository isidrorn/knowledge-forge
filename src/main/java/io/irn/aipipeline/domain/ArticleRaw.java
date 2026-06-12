package io.irn.aipipeline.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.descriptor.jdbc.SmallIntJdbcType;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


@Entity
@Getter
@Setter
public class ArticleRaw {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(columnDefinition = "text")
    private String url;

    @Column(length = 64)
    private String contentChecksum;

    @Column(nullable = false, columnDefinition = "text")
    private String rawContent;

    @Column(nullable = false, length = 20)
    private String status;

    @JdbcType(SmallIntJdbcType.class)
    @Column(nullable = false)
    private Integer retryCount;

    @Column(nullable = false)
    private OffsetDateTime receivedAt;

    @Column
    private OffsetDateTime processedAt;

    @OneToMany(mappedBy = "articleRaw")
    private Set<ArticleProcessed> articleRawArticleProcesseds = new HashSet<>();

    @OneToMany(mappedBy = "articleRaw")
    private Set<PipelineStatusLog> articleRawPipelineStatusLogs = new HashSet<>();

}
