package com.vego.backend.support;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tìm kiếm trên kho tài liệu: BM25 theo từ khóa (bỏ dấu tiếng Việt, tính cả cặp từ vì từ tiếng Việt
 * thường gồm 2 âm tiết) và cosine giữa các vector embedding.
 */
public final class TextSearch {

    private static final double K1 = 1.2;
    private static final double B = 0.75;
    /** Từ hỏi / từ đệm không mang nội dung. */
    private static final Set<String> STOP = Set.of(("a ah ak ad anh chi em ban minh toi to la co khong ko k duoc dc "
            + "bi the thi ma va hay hoac cua cho voi khi neu nao gi sao dau bao nhieu may nhe nha vay u o ra vao "
            + "len di den tu ve xe can muon phai se da dang roi cung nhu hoi xin giup lam on shop vego").split(" "));

    /** Một đoạn kèm điểm BM25 và tỉ lệ từ nội dung của câu hỏi có trong đoạn. */
    public record Hit(Chunk chunk, double score, double coverage) { }

    private record Doc(Chunk chunk, Map<String, Integer> terms, int length) { }

    private final List<Doc> docs = new ArrayList<>();
    private final Map<String, Integer> docFreq = new HashMap<>();
    private double avgLength = 1;

    public TextSearch(List<Chunk> chunks) {
        long total = 0;
        for (Chunk c : chunks) {
            // Tiêu đề mục nói đúng chủ đề nhất nên tính trọng số gấp 3
            List<String> terms = new ArrayList<>(terms(c.heading()));
            terms.addAll(terms(c.heading()));
            terms.addAll(terms(c.heading()));
            terms.addAll(terms(c.title()));
            terms.addAll(terms(c.text()));
            Map<String, Integer> freq = new HashMap<>();
            for (String t : terms) freq.merge(t, 1, Integer::sum);
            for (String t : freq.keySet()) docFreq.merge(t, 1, Integer::sum);
            docs.add(new Doc(c, freq, terms.size()));
            total += terms.size();
        }
        if (!docs.isEmpty()) avgLength = (double) total / docs.size();
    }

    /** Mọi đoạn có điểm > 0, điểm cao trước. */
    public List<Hit> search(String query) {
        Set<String> queryTerms = new HashSet<>(terms(query));
        List<String> content = content(query);
        List<Hit> hits = new ArrayList<>();
        for (Doc d : docs) {
            double score = 0;
            for (String t : queryTerms) {
                Integer tf = d.terms().get(t);
                if (tf == null) continue;
                int df = docFreq.getOrDefault(t, 0);
                double idf = Math.log(1 + (docs.size() - df + 0.5) / (df + 0.5));
                double weight = t.indexOf(' ') < 0 && STOP.contains(t) ? 0.2 : 1;
                score += weight * idf * tf * (K1 + 1) / (tf + K1 * (1 - B + B * d.length() / avgLength));
            }
            if (score <= 0) continue;
            long matched = content.stream().filter(d.terms()::containsKey).count();
            hits.add(new Hit(d.chunk(), score, content.isEmpty() ? 0 : (double) matched / content.size()));
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        return hits;
    }

    /** Các từ mang nội dung (đã bỏ từ đệm). */
    static List<String> content(String text) {
        List<String> out = new ArrayList<>();
        for (String w : normalize(text).split(" ")) {
            if (!w.isEmpty() && !STOP.contains(w) && !out.contains(w)) out.add(w);
        }
        return out;
    }

    private static List<String> terms(String text) {
        String[] words = normalize(text).split(" ");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            out.add(words[i]);
            if (i + 1 < words.length) out.add(words[i] + " " + words[i + 1]);
        }
        return out;
    }

    /** Viết thường, bỏ dấu (đ -> d), gộp khoảng trắng: "Hủy VÉ  2 tiếng" -> "huy ve 2 tieng". */
    public static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String noMarks = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noMarks.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim()
                .replace("valy", "vali");
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
    }
}
