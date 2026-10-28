package com.xiaoying.support;

import com.xiaoying.pipeline.ResultComposer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class FakeResultComposer implements ResultComposer {

    public RuntimeException composeFailure;
    public IOException storeFailure;
    public boolean stored;

    @Override
    public BufferedImage compose(List<Path> materialPaths, String caption) {
        if (composeFailure != null) {
            throw composeFailure;
        }
        return new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
    }

    @Override
    public StoredResult store(BufferedImage image, String taskId) throws IOException {
        if (storeFailure != null) {
            throw storeFailure;
        }
        stored = true;
        return new StoredResult("results/" + taskId + ".png",
                "/api/tasks/" + taskId + "/result", image.getWidth(), image.getHeight());
    }
}