package com.golden.demo;

import com.golden.to.md.ToMdUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws IOException {
        StringBuilder posBuilder = new StringBuilder();
        ToMdUtils.toMD("./demo/go_memory_allocator.pos", i -> {
            System.out.print(i.toString());
            posBuilder.append(i.toString());
        });
        Files.write(Paths.get("./demo/go_memory_allocator_pos.md"), posBuilder.toString().getBytes());
    }
}
