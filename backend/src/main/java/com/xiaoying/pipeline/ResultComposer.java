package com.xiaoying.pipeline;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 结果生成与落盘。GENERATING 调用 {@link #compose}，ASSEMBLING 调用 {@link #store}；
 * 这样“生成”和“写结果文件”分属两个阶段，失败阶段可区分。
 */
public interface ResultComposer {

    BufferedImage compose(List<Path> materialPaths, String caption);

    StoredResult store(BufferedImage image, String taskId) throws IOException;

    record StoredResult(String storageKey, String url, int width, int height) {
    }
}