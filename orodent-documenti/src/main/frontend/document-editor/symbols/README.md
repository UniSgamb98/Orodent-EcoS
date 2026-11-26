Symbols sourced from medical-device-symbols 2.3.1 (https://www.npmjs.com/package/medical-device-symbols), MIT license. Original SVG paths retained; PNG rasterizations by Inkscape, then cropped to their alpha bounds without resampling. Symbols inserted in documents are embedded PNG copies, unaffected by later library updates. CE is a generic mark without a notified-body number; import the applicable artwork from PC.

The model number (#), keep dry and use-by date artwork is drawn locally for EcoS. SVG sources and PNG renderings are versioned with the editor.

`symbols.json` records cropped dimensions and the original embedded PNG and crop
bounds under `legacy`. `symbol-layout.js` recognizes these exact library images
when an existing template is opened for editing. It removes transparent padding
and contain letterboxing while preserving the visible artwork's position and
scale. Read-only models, previously emitted HTML, uploaded images, and GS1
DataMatrix quiet zones are unchanged.
If a historical symbol would become smaller than the model's 8px minimum on
either axis, it is left unchanged rather than enlarging the visible artwork.

To reproduce the PNGs and catalogue, run `npm run prepare:symbols` with
ImageMagick (`identify` and `convert`) installed, then `npm test` and
`npm run build`. This preparation tool is development-only: the application and
normal test suite do not require ImageMagick. Tests decode the PNGs independently
to verify tight alpha bounds and preservation of the original visible pixels.
