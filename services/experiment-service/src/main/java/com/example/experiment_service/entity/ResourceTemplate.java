package com.example.experiment_service.entity;

import com.example.experiment_service.enums.TemplateType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** A saved Excel table (infeed, zone or sort plan) that a user can load again later. */
@Entity
@Table(name = "resource_template")
public class ResourceTemplate {

    public ResourceTemplate() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateType templateType;

    @Column(nullable = false)
    private String templateName;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private Integer rowCount;

    // Old column, kept so old data copies in. The real rows are in resource_template_row.
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Owner id (user lives in auth_db) and a copy of the name.
    @Column(nullable = false)
    private Long userId;

    private String ownerUsername;

    // One template has many rows.
    // cascade = ALL: saving or deleting the template also saves or deletes its rows.
    // orphanRemoval = true: a row taken out of this list is deleted from the database.
    // LAZY: the rows are loaded only when we use them, so use it inside a @Transactional method.
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rowIndex ASC")
    private List<ResourceTemplateRow> rows = new ArrayList<>();

    @PrePersist
    void beforeFirstSave() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
        if (payloadJson == null) {
            payloadJson = "[]";
        }
        if (rowCount == null) {
            rowCount = rows.size();
        }
    }

    /** Adds a row and sets BOTH sides of the link (list and row.template). */
    public void addRow(ResourceTemplateRow row) {
        row.setTemplate(this);
        rows.add(row);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TemplateType getTemplateType() {
        return templateType;
    }

    public void setTemplateType(TemplateType templateType) {
        this.templateType = templateType;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public List<ResourceTemplateRow> getRows() {
        return rows;
    }
}