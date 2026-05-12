package com.tenpo.challenge.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "api_call_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "called_at", nullable = false)
    private OffsetDateTime calledAt;

    @Column(name = "endpoint", nullable = false, length = 255)
    private String endpoint;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private String parameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response", columnDefinition = "jsonb")
    private String response;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "client_ip", length = 45)
    private String clientIp;
}
