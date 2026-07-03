package org.platform.nexus.telegram;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.platform.nexus.category.NewsCategory;
import org.platform.nexus.subscription.Subscription;

/**
 * Inline keyboard'lar. Callback data formatlari (64 baytdan qisqa):
 * <pre>
 *   menu:add | menu:list | menu:help | menu:home
 *   cat:IT                          — kategoriya tanlandi
 *   time:IT:01a0:9                  — 9-soat toggle (mask hex, 16 bit: 07:00..22:00)
 *   time:IT:01a0:-                  — toggle'siz grid render (edit uchun)
 *   freq:IT:01a0                    — davriylik bosqichiga o'tish
 *   freq-set:IT:01a0:2              — saqlash (interval kun)
 *   sub:del:5 | sub:pause:5 | sub:edit:5
 * </pre>
 */
public final class Keyboards {

    public static final int FIRST_HOUR = 7;
    public static final int LAST_HOUR = 22;

    private Keyboards() {
    }

    public static Map<String, Object> mainMenu(boolean admin) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        rows.add(List.of(button("➕ Obuna qo'shish", "menu:add")));
        rows.add(List.of(button("📋 Obunalarim", "menu:list"), button("ℹ️ Yordam", "menu:help")));
        if (admin) {
            rows.add(List.of(button("📬 Hozir yangiliklar", "admin:now")));
            rows.add(List.of(button("📊 Statistika", "admin:stats"), button("🔑 API holati", "admin:api")));
        }
        return keyboard(rows);
    }

    /** Admin "hozir yuborish" uchun kategoriya tanlash. */
    public static Map<String, Object> categoriesNow() {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        NewsCategory[] values = NewsCategory.values();
        for (int i = 0; i < values.length; i += 2) {
            List<Map<String, String>> row = new ArrayList<>();
            row.add(button(values[i].label(), "now:" + values[i].name()));
            if (i + 1 < values.length) {
                row.add(button(values[i + 1].label(), "now:" + values[i + 1].name()));
            }
            rows.add(row);
        }
        rows.add(List.of(button("◀️ Orqaga", "menu:home")));
        return keyboard(rows);
    }

    public static Map<String, Object> categories() {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        NewsCategory[] values = NewsCategory.values();
        for (int i = 0; i < values.length; i += 2) {
            List<Map<String, String>> row = new ArrayList<>();
            row.add(button(values[i].label(), "cat:" + values[i].name()));
            if (i + 1 < values.length) {
                row.add(button(values[i + 1].label(), "cat:" + values[i + 1].name()));
            }
            rows.add(row);
        }
        rows.add(List.of(button("◀️ Orqaga", "menu:home")));
        return keyboard(rows);
    }

    public static Map<String, Object> timeGrid(NewsCategory category, int mask) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        List<Map<String, String>> row = new ArrayList<>();
        for (int hour = FIRST_HOUR; hour <= LAST_HOUR; hour++) {
            boolean selected = (mask & (1 << (hour - FIRST_HOUR))) != 0;
            String label = (selected ? "✅ " : "") + String.format("%02d:00", hour);
            row.add(button(label, "time:%s:%s:%d".formatted(category.name(), hex(mask), hour)));
            if (row.size() == 4) {
                rows.add(row);
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        List<Map<String, String>> nav = new ArrayList<>();
        nav.add(button("◀️ Orqaga", "menu:add"));
        if (mask != 0) {
            nav.add(button("Davom ➡️", "freq:%s:%s".formatted(category.name(), hex(mask))));
        }
        rows.add(nav);
        return keyboard(rows);
    }

    public static Map<String, Object> frequency(NewsCategory category, int mask) {
        String prefix = "freq-set:%s:%s:".formatted(category.name(), hex(mask));
        return keyboard(List.of(
                List.of(button("📅 Har kuni", prefix + "1"), button("📅 Kun ora", prefix + "2")),
                List.of(button("📅 Har 3 kunda", prefix + "3"), button("📅 Haftada 1", prefix + "7")),
                List.of(button("◀️ Orqaga", "time:%s:%s:-".formatted(category.name(), hex(mask))))));
    }

    public static Map<String, Object> subscriptionList(List<Subscription> subscriptions) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (Subscription sub : subscriptions) {
            String pauseLabel = sub.isEnabled() ? "⏸" : "▶️";
            rows.add(List.of(
                    button(sub.getCategory().emoji() + " " + pauseLabel, "sub:pause:" + sub.getId()),
                    button(sub.getCategory().emoji() + " ✏️", "sub:edit:" + sub.getId()),
                    button(sub.getCategory().emoji() + " 🗑", "sub:del:" + sub.getId())));
        }
        rows.add(List.of(button("➕ Obuna qo'shish", "menu:add"), button("🏠 Menyu", "menu:home")));
        return keyboard(rows);
    }

    /** Digest sahifa navigatsiyasi: ⬅️ 2/3 ➡️ (callback: pg:&lt;digestId&gt;:&lt;sahifa&gt;). */
    public static Map<String, Object> pager(long digestId, int page, int totalPages) {
        List<Map<String, String>> row = new ArrayList<>();
        if (page > 0) {
            row.add(button("⬅️ Oldingi", "pg:%d:%d".formatted(digestId, page - 1)));
        }
        row.add(button("%d/%d".formatted(page + 1, totalPages), "noop"));
        if (page < totalPages - 1) {
            row.add(button("Keyingi ➡️", "pg:%d:%d".formatted(digestId, page + 1)));
        }
        return keyboard(List.of(row));
    }

    public static Map<String, Object> backToMenu() {
        return keyboard(List.of(List.of(button("🏠 Menyu", "menu:home"))));
    }

    public static String hex(int mask) {
        return String.format("%04x", mask);
    }

    public static int maskFromSendTimes(List<String> sendTimes) {
        int mask = 0;
        for (String time : sendTimes) {
            int hour = Integer.parseInt(time.substring(0, 2));
            if (hour >= FIRST_HOUR && hour <= LAST_HOUR) {
                mask |= 1 << (hour - FIRST_HOUR);
            }
        }
        return mask;
    }

    private static Map<String, String> button(String text, String callbackData) {
        return Map.of("text", text, "callback_data", callbackData);
    }

    private static Map<String, Object> keyboard(List<List<Map<String, String>>> rows) {
        return Map.of("inline_keyboard", rows);
    }
}
