package ms.wiwi.examarchive.model;

import java.time.Instant;

public record User(String id, String firstname, String lastname, Instant lastLogin, Instant createdAt, String email, Role role) {
}
