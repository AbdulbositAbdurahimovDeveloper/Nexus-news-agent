package org.platform.nexus.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.platform.nexus.category.NewsCategory;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private NewsCategory category;

    /** Vergul bilan ajratilgan vaqtlar: {@code 09:00,12:00} */
    @Column(name = "send_times", nullable = false, length = 200)
    private String sendTimes;

    /** 1 = har kuni, 2 = kun ora, N = har N kunda */
    @Column(name = "interval_days", nullable = false)
    private int intervalDays = 1;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "last_sent_date")
    private LocalDate lastSentDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public List<String> sendTimesList() {
        return Arrays.stream(sendTimes.split(",")).map(String::trim).toList();
    }
}
