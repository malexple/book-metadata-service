package ru.mcs.metadatabook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "mt_relationships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MtRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rel_id")
    private Long relId;

    @Column(name = "org_id", nullable = false)
    private Integer orgId;

    @Column(name = "relationship_id", nullable = false)
    private Integer relationshipId;

    @Column(name = "parent_obj_id", nullable = false)
    private Integer parentObjId;

    @Column(name = "parent_guid", nullable = false)
    private UUID parentGuid;

    @Column(name = "child_obj_id", nullable = false)
    private Integer childObjId;

    @Column(name = "child_guid", nullable = false)
    private UUID childGuid;
}