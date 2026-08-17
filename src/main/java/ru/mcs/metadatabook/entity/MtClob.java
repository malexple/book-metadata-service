package ru.mcs.metadatabook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mt_clobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MtClob {

    @EmbeddedId
    @Builder.Default
    private MtClobId id = new MtClobId();

    @Column(name = "data_guid", nullable = false)
    private UUID dataGuid;

    @Column(name = "field_num", nullable = false)
    private Integer fieldNum;

    @Column(name = "clob_value", columnDefinition = "TEXT")
    private String clobValue;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "modified_at")
    @Builder.Default
    private OffsetDateTime modifiedAt = OffsetDateTime.now();

    public Integer getOrgId() {
        return id != null ? id.getOrgId() : null;
    }

    public UUID getClobId() {
        return id != null ? id.getClobId() : null;
    }

    public void setOrgId(Integer orgId) {
        if (id == null) id = new MtClobId();
        id.setOrgId(orgId);
    }

    public void setClobId(UUID clobId) {
        if (id == null) id = new MtClobId();
        id.setClobId(clobId);
    }
}
