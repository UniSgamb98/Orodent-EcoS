// Label date masks are deliberately independent of locale, timezone and GS1 encoding.
export const defaultDateFormat = 'dd/mm/yyyy';
export const isDateField = (key) => key === 'productionDate' || key === 'expiryDate';
export function validateDateFormat(format) {
  if (
    typeof format !== 'string' ||
    format.length > 64 ||
    (format && !/^(?:yyyy|yy|mm|dd|[0-9 /._:\-])+$/i.test(format))
  )
    throw Error(
      'Formato data non valido: usa dd, mm, yyyy, yy, numeri fissi e separatori / . - _ : o spazi.',
    );
  return format || defaultDateFormat;
}
export function validateFieldDateFormat(key, format = '') {
  validateDateFormat(format);
  if (format && !isDateField(key))
    throw Error('Il formato data è disponibile per produzione e scadenza.');
}
export function formatDate(value, format = '') {
  const mask = validateDateFormat(format);
  if (!value) return '';
  const parts = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  if (!parts) throw Error('Data non valida: atteso giorno/mese/anno.');
  const [, dd, mm, yyyy] = parts,
    year = Number(yyyy),
    month = Number(mm),
    day = Number(dd);
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (year < 1 || month < 1 || month > 12 || day < 1 || day > days[month - 1])
    throw Error('Data non valida.');
  const tokens = { dd, mm, yyyy, yy: yyyy.slice(-2) };
  return mask.replace(/yyyy|yy|mm|dd/gi, (token) => tokens[token.toLowerCase()]);
}
