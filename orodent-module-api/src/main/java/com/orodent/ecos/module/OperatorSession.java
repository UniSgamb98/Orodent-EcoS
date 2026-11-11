package com.orodent.ecos.module;

import java.util.Optional;
import java.util.UUID;

/**
 * Operator selection belongs to the running EcoS application, independently of module UI lifetime.
 */
public final class OperatorSession {
    public record Operator(UUID id, String username) {
        public Operator {
            java.util.Objects.requireNonNull(id);
            java.util.Objects.requireNonNull(username);
        }
    }

    private volatile Operator current;

    public Optional<Operator> current() {
        return Optional.ofNullable(current);
    }

    public void select(UUID id, String username) {
        current = new Operator(id, username);
    }

    public void clear() {
        current = null;
    }
}
