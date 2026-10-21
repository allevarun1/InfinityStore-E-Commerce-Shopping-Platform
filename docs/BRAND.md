# Rekha Shopping Mall: brand guide

## The mark

An upright gold **R** whose leg sweeps across a flowing italic **S**: the loom and the drape. The R is structure and
tradition; the S, in rani pink, is movement. Both letters are Cormorant Garamond (the same typeface as the site's
headings), converted to outlines by `tools/make_brand.py`, so the files need no fonts and match the site exactly.

| Use | File (in `ecommerce-frontend/public/brand/`) |
|---|---|
| Website header, letterheads, bills, shop signage | `rekha-signature.svg` / `.png` (on light backgrounds) |
| Dark backgrounds, WhatsApp cover, Instagram highlight | `rekha-signature-dark.svg` / `.png` |
| App icon, favicon, WhatsApp/Instagram profile picture | `rekha-tile.svg`, `/icon-512.png` |
| Stickers, packaging seal, gift tags | `rekha-emblem.svg` / `.png` (the RS in a jharokha arch) |
| Arch badge with the name beside or below it | `rekha-logo*.svg` / `.png`, `rekha-logo-stacked*` |
| Link previews (WhatsApp, Facebook) | `og-card.jpg` (set in `index.html`) |

Use SVG wherever you can (print shops accept it); the PNGs are for apps that need an image file.

## Colours

| Name | Hex | Where |
|---|---|---|
| Maroon | `#7B1E2B` | buttons, links, the arch |
| Rani pink | `#C2185B` | the S, the script accent, banner buttons |
| Antique gold | `#B5862F` (text: `#85601C`) | the R, borders, ornaments |
| Ivory | `#FBF7F1` | page background |
| Ink | `#1E1915` | text, footer |

## Type

- Headings and the wordmark: **Cormorant Garamond** (600/700).
- Body and small capitals ("SHOPPING MALL"): **DM Sans**.
- One flourish word per banner at most: **Great Vibes** script (as in "Tradition meets *Trend*").

All three are under the SIL Open Font License and are self-hosted with the site.

## Do and don't

- Keep clear space around the mark equal to the height of the S's top curve.
- Don't recolour the letters outside the palette, stretch them, add shadows, or put the light version on a busy photo.
- The smallest size for the signature is 120 px wide on screen; below that use the tile.

To change the mark, edit `tools/make_brand.py` and run `python tools/make_brand.py`; it rewrites every SVG and the
paths the site uses (`src/components/brandPaths.js`).
