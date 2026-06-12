package io.irn.aipipeline.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.SmallIntJdbcType;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@Entity
@Getter
@Setter
public class ArticleProcessed {

    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String tldr;

    @Column(columnDefinition = "text")
    private List<String> keyPoints;

    @Column(columnDefinition = "text")
    private List<String> tags;

    @Column
    @JdbcType(SmallIntJdbcType.class)
    private Integer difficulty;

    @Column(nullable = false, columnDefinition = "text")
    private String markdownContent;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column
    private float[] embedding;

    @Column(length = 100)
    private String modelUsed;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column
    private OffsetDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_raw_id", nullable = false)
    private ArticleRaw articleRaw;

    @OneToMany(mappedBy = "articleProcessed")
    private Set<OutboxEvents> articleProcessedOutboxEventses = new HashSet<>();

}
