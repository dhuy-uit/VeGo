package com.vego.backend.trip;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * So khớp tên thành phố người dùng gõ với tên trong CSDL: không phân biệt hoa thường, dấu, "TP.",
 * và hiểu các tên gọi khác (HCM, Sài Gòn...). Vd "da lat", "Đà Lạt", "dalat" đều ra "Đà Lạt".
 */
public final class CityNames {

    private static final Map<String, String> ALIASES = Map.of(
            "hcm", "ho chi minh",
            "tphcm", "ho chi minh",
            "sai gon", "ho chi minh",
            "saigon", "ho chi minh",
            "sg", "ho chi minh",
            "hn", "ha noi",
            "dl", "da lat");

    private CityNames() { }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9]+", " ").trim();
        s = s.replaceFirst("^(tp|thanh pho) ", "");
        return ALIASES.getOrDefault(s, s);
    }

    /** query khớp city: bằng nhau (bỏ dấu cách) hoặc là một phần đủ dài của tên. */
    public static boolean matches(String query, String city) {
        String q = normalize(query).replace(" ", "");
        String c = normalize(city).replace(" ", "");
        if (q.isEmpty()) return false;
        return q.equals(c) || (q.length() >= 3 && c.contains(q));
    }
}
