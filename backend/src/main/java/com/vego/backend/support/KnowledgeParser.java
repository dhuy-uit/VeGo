package com.vego.backend.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Đọc tài liệu markdown trong resources/knowledge: dòng "# Tiêu đề", dòng "source: Theo ...",
 * mỗi mục "## ..." là một đoạn.
 */
public final class KnowledgeParser {

    private KnowledgeParser() { }

    public static List<Chunk> parse(String name, String markdown) {
        String title = name;
        String source = null;
        String heading = null;
        StringBuilder body = new StringBuilder();
        List<Chunk> out = new ArrayList<>();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("## ")) {
                add(out, name, source, title, heading, body);
                heading = line.substring(3).trim();
            } else if (line.startsWith("# ")) {
                title = line.substring(2).trim();
            } else if (heading == null && line.startsWith("source:")) {
                source = line.substring("source:".length()).trim();
            } else if (heading != null) {
                body.append(line).append('\n');
            }
        }
        add(out, name, source, title, heading, body);
        return out;
    }

    private static void add(List<Chunk> out, String name, String source, String title, String heading,
                            StringBuilder body) {
        String text = body.toString().strip();
        body.setLength(0);
        if (heading == null || text.isEmpty()) return;
        out.add(new Chunk(name + "-" + (out.size() + 1), source == null ? "Theo " + title : source,
                title, heading, text));
    }
}
