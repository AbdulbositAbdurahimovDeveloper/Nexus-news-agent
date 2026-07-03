package org.platform.nexus.digest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sahifali digest — "keyingi sahifa" tugmasi restart'dan keyin ham ishlashi
 * uchun sahifalar bazada saqlanadi.
 */
@Entity
@Table(name = "digests")
@Getter
@Setter
@NoArgsConstructor
public class Digest {

    /** Kontent escape qilingani uchun bu belgi matn ichida uchramaydi. */
    public static final String PAGE_SEPARATOR = "\n<!--PAGE-->\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "content", nullable = false, length = 16000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public void setPages(List<String> pages) {
        this.pageCount = pages.size();
        this.content = String.join(PAGE_SEPARATOR, pages);
    }

    public String page(int index) {
        return content.split(Pattern.quote(PAGE_SEPARATOR))[index];
    }
}
