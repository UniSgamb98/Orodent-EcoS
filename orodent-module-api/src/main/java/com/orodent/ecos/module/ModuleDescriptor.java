package com.orodent.ecos.module;

/** Home metadata. Icons use SVG path data in a 24 by 24 coordinate system. */
public record ModuleDescriptor(String id, String name, String description, String iconSvgPath) {
    public ModuleDescriptor {
        if (id == null || !id.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Identificativo modulo non valido: " + id);
        }
        name = required(name, "Nome");
        description = required(description, "Descrizione");
        iconSvgPath = required(iconSvgPath, "Icona");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " modulo obbligatorio.");
        return value.strip();
    }
}
