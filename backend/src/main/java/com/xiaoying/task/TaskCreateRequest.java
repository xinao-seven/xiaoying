package com.xiaoying.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public class TaskCreateRequest {

    @NotBlank(message = "templateId 不能为空")
    private String templateId;

    @NotEmpty(message = "materialIds 不能为空")
    private List<@NotBlank(message = "素材 id 不能为空") String> materialIds;

    /** 制作参数；当前只接受 caption，未知字段会被拒绝。 */
    private Map<String, Object> params;

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public List<String> getMaterialIds() {
        return materialIds;
    }

    public void setMaterialIds(List<String> materialIds) {
        this.materialIds = materialIds;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}