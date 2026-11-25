export const defaultPage = Object.freeze({ format: 'a4', orientation: 'portrait', margin: 15, width: 210, height: 297 });

export function paperSize(page) {
  return page.format === 'custom' ? { width: page.width, height: page.height } :
    page.orientation === 'landscape' ? { width: 297, height: 210 } : { width: 210, height: 297 };
}
export function validatePage(page) {
  if (!['a4','custom'].includes(page.format) || !['portrait','landscape'].includes(page.orientation)) throw new Error('Formato carta non valido.');
  const size = paperSize(page);
  if (![size.width,size.height,page.margin].every(Number.isFinite) || size.width < 20 || size.height < 20 || size.width > 1000 || size.height > 1000 || page.margin < 0 || page.margin > 40 || page.margin * 2 >= Math.min(size.width,size.height))
    throw new Error('Misure carta: da 20 a 1000 mm. I margini devono lasciare spazio al contenuto.');
  return page;
}
