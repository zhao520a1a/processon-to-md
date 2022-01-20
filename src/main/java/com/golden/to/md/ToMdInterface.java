package com.golden.to.md;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * 转换接口
 */
public interface ToMdInterface {

    /**
     * 转换为MD文件输出到 stringBuilderConsumer
     */
    void toMD(String filePath, Consumer<StringBuilder> stringBuilderConsumer) throws IOException;
}
