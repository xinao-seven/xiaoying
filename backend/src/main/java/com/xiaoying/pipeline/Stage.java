package com.xiaoying.pipeline;

import java.util.List;

/** 任务阶段；顺序即执行顺序。DONE 只作为终态标记，不作为执行阶段。 */
public enum Stage {
    MATERIAL_CHECK,
    GENERATING,
    ASSEMBLING,
    DONE;

    public static final List<Stage> EXECUTION_ORDER = List.of(MATERIAL_CHECK, GENERATING, ASSEMBLING);

    public static Stage from(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}