package com.example.experiment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row of a saved template. */
@Entity
@Table(name = "resource_template_row")
public class ResourceTemplateRow {

    public ResourceTemplateRow() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The template this row belongs to (foreign key template_id).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ResourceTemplate template;

    // Row number inside the template: 0, 1, 2, ...
    @Column(nullable = false)
    private Integer rowIndex;

    // One Excel row as {column name: value}. Hibernate converts it to JSON text (jsonb) and back.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> rowData;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ResourceTemplate getTemplate() {
        return template;
    }

    public void setTemplate(ResourceTemplate template) {
        this.template = template;
    }

    public Integer getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(Integer rowIndex) {
        this.rowIndex = rowIndex;
    }

    public Map<String, Object> getRowData() {
        return rowData;
    }

    public void setRowData(Map<String, Object> rowData) {
        this.rowData = rowData;
    }
}