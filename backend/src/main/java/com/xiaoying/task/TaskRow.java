package com.xiaoying.task;

import java.time.Instant;

/** tasks 表行；templateTitle 来自与 templates 的连接查询。 */
public class TaskRow {

    private String id;
    private String userId;
    private String templateId;
    private String templateTitle;
    private String status;
    private String stage;
    private String failedStage;
    private String errorCode;
    private String errorMessage;
    private String resultUrl;
    private Integer resultWidth;
    private Integer resultHeight;
    private String paramsJson;
    private String plannedFailStage;
    private Integer version;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getTemplateTitle() {
        return templateTitle;
    }

    public void setTemplateTitle(String templateTitle) {
        this.templateTitle = templateTitle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getFailedStage() {
        return failedStage;
    }

    public void setFailedStage(String failedStage) {
        this.failedStage = failedStage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getResultUrl() {
        return resultUrl;
    }

    public void setResultUrl(String resultUrl) {
        this.resultUrl = resultUrl;
    }

    public Integer getResultWidth() {
        return resultWidth;
    }

    public void setResultWidth(Integer resultWidth) {
        this.resultWidth = resultWidth;
    }

    public Integer getResultHeight() {
        return resultHeight;
    }

    public void setResultHeight(Integer resultHeight) {
        this.resultHeight = resultHeight;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public void setParamsJson(String paramsJson) {
        this.paramsJson = paramsJson;
    }

    public String getPlannedFailStage() {
        return plannedFailStage;
    }

    public void setPlannedFailStage(String plannedFailStage) {
        this.plannedFailStage = plannedFailStage;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}