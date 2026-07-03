package org.platform.nexus.category;

public enum NewsCategory {

    IT("💻", "IT/Texnologiya"),
    WORLD("🌍", "Dunyo"),
    UZ("🇺🇿", "O'zbekiston"),
    ECONOMY("📈", "Iqtisod/Biznes"),
    SCIENCE("🔬", "Fan"),
    SPORT("⚽", "Sport");

    private final String emoji;
    private final String displayName;

    NewsCategory(String emoji, String displayName) {
        this.emoji = emoji;
        this.displayName = displayName;
    }

    public String emoji() {
        return emoji;
    }

    public String displayName() {
        return displayName;
    }

    public String label() {
        return emoji + " " + displayName;
    }
}
