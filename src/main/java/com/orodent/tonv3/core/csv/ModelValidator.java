package com.orodent.tonv3.core.csv;

import java.util.List;

@FunctionalInterface
public interface ModelValidator<T> {

    List<ValidationError> validate(T value);

    static <T> ModelValidator<T> none() {
        return value -> List.of();
    }

    record ValidationError(String field, String message) {

        public ValidationError {
            field = field == null ? "" : field;
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("Il messaggio di validazione è obbligatorio");
            }
        }
    }
}
