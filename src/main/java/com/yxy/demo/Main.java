package com.yxy.demo;

import com.yxy.to.md.ToMdInterface;
import com.yxy.to.md.ToMdUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws IOException {
        // xmind
//        ToMdUtils.toMD(
//                "/Users/golden/Desktop/Go_memory_allocator.xmind",
//                i -> System.out.print(i.toString())
//        );

        // pos
        String filePath = "/Users/golden/Desktop/Go_memory_allocator.pos";
        StringBuilder builder = new StringBuilder();
        ToMdInterface toMd = ToMdUtils.toMD(filePath, i -> {
            System.out.print(i.toString());
            builder.append(i.toString());
        });

        Files.write( Paths.get("./demo.md"), builder.toString().getBytes());
    }
}
