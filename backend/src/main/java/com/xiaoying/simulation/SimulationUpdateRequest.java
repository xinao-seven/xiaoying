package com.xiaoying.simulation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

/**
 * 开发接口的局部更新请求。
 *
 * <p>用 {@code failStagePresent} 区分“字段缺省”和“显式传 null”：
 * 缺省表示保持原值，显式 null 表示清空注入计划。
 */
public class SimulationUpdateRequest {

    private String failStage;
    private boolean failStagePresent;
    private Integer failNext;
    private Integer delayMaterialCheckMs;
    private Integer delayGeneratingMs;
    private Integer delayAssemblingMs;

    public String getFailStage() {
        return failStage;
    }

    @JsonSetter("failStage")
    public void setFailStage(String failStage) {
        this.failStage = failStage;
        this.failStagePresent = true;
    }

    @JsonIgnore
    public boolean isFailStagePresent() {
        return failStagePresent;
    }

    public Integer getFailNext() {
        return failNext;
    }

    public void setFailNext(Integer failNext) {
        this.failNext = failNext;
    }

    public Integer getDelayMaterialCheckMs() {
        return delayMaterialCheckMs;
    }

    public void setDelayMaterialCheckMs(Integer delayMaterialCheckMs) {
        this.delayMaterialCheckMs = delayMaterialCheckMs;
    }

    public Integer getDelayGeneratingMs() {
        return delayGeneratingMs;
    }

    public void setDelayGeneratingMs(Integer delayGeneratingMs) {
        this.delayGeneratingMs = delayGeneratingMs;
    }

    public Integer getDelayAssemblingMs() {
        return delayAssemblingMs;
    }

    public void setDelayAssemblingMs(Integer delayAssemblingMs) {
        this.delayAssemblingMs = delayAssemblingMs;
    }
}