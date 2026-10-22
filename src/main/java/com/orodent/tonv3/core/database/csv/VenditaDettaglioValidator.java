package com.orodent.tonv3.core.database.csv;

import com.orodent.tonv3.core.csv.ModelValidator;
import com.orodent.tonv3.core.database.model.VenditaDettaglio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class VenditaDettaglioValidator implements ModelValidator<VenditaDettaglio> {

    @Override
    public List<ValidationError> validate(VenditaDettaglio vendita) {
        List<ValidationError> errors = new ArrayList<>();

        requiredText(errors, VenditaDettaglioCsvMapper.SORGENTE, vendita.sorgente(), 30);
        requiredText(errors, VenditaDettaglioCsvMapper.NUMERO_DOCUMENTO, vendita.documentoId(), 100);
        optionalText(errors, VenditaDettaglioCsvMapper.NUMERO_DOCUMENTO, vendita.numeroDocumento(), 50);
        if (vendita.numeroRiga() < 1) {
            errors.add(error("numeroRiga", "Il numero di riga deve essere positivo"));
        }
        if (vendita.dataVendita() == null) {
            errors.add(error(VenditaDettaglioCsvMapper.DATA_REGISTRAZIONE, "La data di vendita è obbligatoria"));
        }
        requiredText(errors, VenditaDettaglioCsvMapper.CODICE_CLIENTE, vendita.codiceCliente(), 50);
        requiredText(errors, VenditaDettaglioCsvMapper.CODICE_ARTICOLO, vendita.codiceProdotto(), 50);
        optionalText(errors, VenditaDettaglioCsvMapper.DESCRIZIONE, vendita.descrizioneProdotto(), 250);
        validateQuantity(errors, vendita.quantita());
        validateDecimal(errors, VenditaDettaglioCsvMapper.PREZZO_UNITARIO, vendita.prezzoUnitario(), 15, 4);
        validateDiscount(errors, vendita.scontoPercentuale());
        validateDecimal(errors, VenditaDettaglioCsvMapper.IMPORTO_SCONTO, vendita.scontoImporto(), 15, 2);
        validateDecimal(errors, VenditaDettaglioCsvMapper.IMPORTO_RIGA, vendita.importoNetto(), 15, 2);
        if (vendita.tipoOperazione() == null) {
            errors.add(error(VenditaDettaglioCsvMapper.CAUSALE, "Il tipo di operazione è obbligatorio"));
        }

        return List.copyOf(errors);
    }

    private void validateQuantity(List<ValidationError> errors, BigDecimal quantity) {
        if (quantity == null) {
            errors.add(error(VenditaDettaglioCsvMapper.QUANTITA, "La quantità è obbligatoria"));
            return;
        }
        if (quantity.signum() == 0) {
            errors.add(error(VenditaDettaglioCsvMapper.QUANTITA, "La quantità non può essere zero"));
        }
        validateDecimal(errors, VenditaDettaglioCsvMapper.QUANTITA, quantity, 15, 3);
    }

    private void validateDiscount(List<ValidationError> errors, BigDecimal discount) {
        if (discount == null) {
            return;
        }
        if (discount.signum() < 0 || discount.compareTo(BigDecimal.valueOf(100)) > 0) {
            errors.add(error(VenditaDettaglioCsvMapper.SCONTO, "Lo sconto deve essere compreso tra 0 e 100"));
        }
        validateDecimal(errors, VenditaDettaglioCsvMapper.SCONTO, discount, 7, 4);
    }

    private void validateDecimal(
            List<ValidationError> errors,
            String field,
            BigDecimal value,
            int precision,
            int scale
    ) {
        if (value == null) {
            return;
        }

        BigDecimal normalized = value.stripTrailingZeros();
        int actualScale = Math.max(normalized.scale(), 0);
        int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        if (actualScale > scale || integerDigits > precision - scale) {
            errors.add(error(
                    field,
                    "Il valore supera il formato DECIMAL(" + precision + "," + scale + ")"
            ));
        }
    }

    private void requiredText(
            List<ValidationError> errors,
            String field,
            String value,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            errors.add(error(field, "Il valore è obbligatorio"));
            return;
        }
        optionalText(errors, field, value, maxLength);
    }

    private void optionalText(
            List<ValidationError> errors,
            String field,
            String value,
            int maxLength
    ) {
        if (value != null && value.length() > maxLength) {
            errors.add(error(field, "La lunghezza massima è " + maxLength + " caratteri"));
        }
    }

    private ValidationError error(String field, String message) {
        return new ValidationError(field, message);
    }
}
