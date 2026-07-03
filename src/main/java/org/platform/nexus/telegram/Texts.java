package org.platform.nexus.telegram;

import java.util.List;
import java.util.Map;
import org.platform.nexus.category.NewsCategory;
import org.platform.nexus.subscription.Subscription;

/** HTML formatdagi bot matnlari (Telegram parse_mode=HTML). */
public final class Texts {

    private static final Map<Integer, String> FREQUENCY_LABELS = Map.of(
            1, "Har kuni",
            2, "Kun ora",
            3, "Har 3 kunda",
            7, "Haftada 1 marta");

    private Texts() {
    }

    public static String unauthorized(long chatId) {
        return """
                ⛔ <b>Bu shaxsiy bot.</b>

                Sizning chat ID: <code>%d</code>

                Kirish uchun bot egasiga shu ID'ni yuboring — \
                u sizni ruxsat ro'yxatiga qo'shadi.""".formatted(chatId);
    }

    public static String welcome() {
        return """
                👋 <b>Salom! Men — News Agent</b>

                Tanlagan mavzularingiz bo'yicha yangiliklarni AI xulosasi \
                bilan belgilangan vaqtda yuboraman.

                🔻 Quyidagi menyudan foydalaning:""";
    }

    public static String help() {
        return """
                ℹ️ <b>Qo'llanma</b>

                ➕ <b>Obuna qo'shish</b> — kategoriya, vaqt(lar) va davriylikni tanlaysiz
                📋 <b>Obunalarim</b> — mavjud obunalarni boshqarish:
                   ⏸ pauza/davom · ✏️ vaqtni o'zgartirish · 🗑 o'chirish

                <b>Buyruqlar:</b>
                /start — botni qayta ishga tushirish
                /menu — asosiy menyu
                /list — obunalarim
                /help — shu qo'llanma

                Belgilangan vaqtda yangi maqolalar AI xulosasi bilan keladi. \
                Bir marta yuborilgan maqola qayta yuborilmaydi.""";
    }

    public static String chooseCategory() {
        return "🗂 <b>Kategoriyani tanlang:</b>";
    }

    public static String chooseTimes(NewsCategory category, int selectedCount) {
        String hint = selectedCount == 0
                ? "Kamida bitta vaqt tanlang."
                : "Tanlangan: <b>" + selectedCount + " ta</b>. Yana qo'shishingiz yoki davom etishingiz mumkin.";
        return "🕐 <b>%s</b> — yuborish vaqt(lar)ini tanlang:%n%n%s".formatted(category.label(), hint);
    }

    public static String chooseFrequency(NewsCategory category) {
        return "🔁 <b>%s</b> — qanchalik tez-tez yuborilsin?".formatted(category.label());
    }

    public static String saved(Subscription sub) {
        return """
                ✅ <b>Obuna saqlandi!</b>

                %s
                🕐 Vaqt: <b>%s</b>
                🔁 Davriylik: <b>%s</b>""".formatted(
                sub.getCategory().label(),
                String.join(", ", sub.sendTimesList()),
                frequencyLabel(sub.getIntervalDays()));
    }

    public static String subscriptionList(List<Subscription> subscriptions) {
        if (subscriptions.isEmpty()) {
            return "📭 Hozircha obunalar yo'q.\n\n➕ tugmasi bilan birinchi obunani qo'shing!";
        }
        StringBuilder sb = new StringBuilder("📋 <b>Obunalarim</b>\n");
        for (Subscription sub : subscriptions) {
            sb.append("\n%s%s — %s — %s".formatted(
                    sub.isEnabled() ? "" : "⏸ ",
                    sub.getCategory().label(),
                    String.join(", ", sub.sendTimesList()),
                    frequencyLabel(sub.getIntervalDays())));
        }
        sb.append("\n\nHar bir obunani ostidagi tugmalar bilan boshqaring:");
        return sb.toString();
    }

    /** Admin statistikasi: qaysi foydalanuvchi qaysi obunani faollashtirgan. */
    public static String adminStats(List<Subscription> all) {
        if (all.isEmpty()) {
            return "📊 <b>Statistika</b>\n\nHozircha obunalar yo'q.";
        }
        Map<Long, List<Subscription>> byChat = new java.util.LinkedHashMap<>();
        for (Subscription sub : all) {
            byChat.computeIfAbsent(sub.getChatId(), k -> new java.util.ArrayList<>()).add(sub);
        }
        StringBuilder sb = new StringBuilder("📊 <b>Statistika</b>\n\n");
        sb.append("👥 Foydalanuvchilar: <b>%d</b> · Obunalar: <b>%d</b>\n".formatted(
                byChat.size(), all.size()));
        for (var entry : byChat.entrySet()) {
            sb.append("\n👤 <code>%d</code>:\n".formatted(entry.getKey()));
            for (Subscription sub : entry.getValue()) {
                sb.append("   %s%s — %s — %s\n".formatted(
                        sub.isEnabled() ? "" : "⏸ ",
                        sub.getCategory().label(),
                        String.join(", ", sub.sendTimesList()),
                        frequencyLabel(sub.getIntervalDays())));
            }
        }
        return sb.toString();
    }

    public static String frequencyLabel(int intervalDays) {
        return FREQUENCY_LABELS.getOrDefault(intervalDays, "Har " + intervalDays + " kunda");
    }

    /** Telegram HTML rejimi uchun maxsus belgilarni ekranlash. */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
