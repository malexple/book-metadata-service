package ru.mcs.metadatabook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Составной ключ mt_data: PRIMARY KEY (org_id, guid) — совпадает с реальной DDL (V2),
 * т.к. таблица партиционирована по org_id (PARTITION BY LIST).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MtDataId implements Serializable {

    @Column(name = "org_id")
    private Integer orgId;

    @Column(name = "guid")
    private UUID guid;
}