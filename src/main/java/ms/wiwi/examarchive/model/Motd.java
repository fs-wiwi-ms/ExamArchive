package ms.wiwi.examarchive.model;

import java.time.Instant;

public record Motd(String text, Instant expires) {

    public boolean isExpired() {
        return expires.isBefore(Instant.now());
    }

}