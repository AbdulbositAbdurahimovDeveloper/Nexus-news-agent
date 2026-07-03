package org.platform.nexus.digest;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface DigestRepository extends JpaRepository<Digest, Long> {

    @Transactional
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
