package com.xiaoying.support;

import com.xiaoying.pipeline.Sleeper;
import java.util.ArrayList;
import java.util.List;

/** 记录调用顺序，不真正等待；可通过 onSleep 在“等待中”读取 repository。 */
public class FakeSleeper implements Sleeper {

    public final List<Long> sleeps = new ArrayList<>();
    public Runnable onSleep;

    @Override
    public void sleep(long millis) {
        sleeps.add(millis);
        if (onSleep != null) {
            onSleep.run();
        }
    }
}