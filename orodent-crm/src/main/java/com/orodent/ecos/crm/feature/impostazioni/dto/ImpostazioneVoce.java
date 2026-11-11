package com.orodent.ecos.crm.feature.impostazioni.dto;

import java.util.List;
import java.util.UUID;

public record ImpostazioneVoce(UUID id, List<String> valori) {
    public ImpostazioneVoce {
        valori = List.copyOf(valori);
    }
}
