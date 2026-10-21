# Rekha Shopping Mall: features and how they work

Every item below is built and covered by automated tests unless it says otherwise.

## For shoppers

| Feature | How it works |
|---|---|
| Browse and filter | Categories, fabric, occasion, price bands, sort, "show more", removable filter chips, mobile filter sheet |
| Colours | A design's other colours show as photo swatches; each colour has its own photos, stock and SKU |
| Photos | Up to 6 extra photos per saree: thumbnails, swipe, full-screen viewer; a second photo appears on hover over product cards |
| Quick view | Pop-up on any card with photos, price and add to cart |
| Stylist chat | Understands "red silk for a wedding under 5k"; recommends only live sarees; answers delivery, COD and tracking questions; adds to bag from the chat; takes a call-back request |
| Delivery date check | PIN code gives an estimated delivery range and COD availability (ranges are the owner's settings) |
| Multi-buy offers | "Buy 2+ save 5%, buy 3+ save 10%": shown on product pages, announcement bar and cart with a progress nudge; applied automatically before coupons |
| Price breakup | Product page shows price, GST and shipping before checkout |
| Real social proof | "Bestseller", "sold this month", "saved by N": only from real orders and wishlists, only once the numbers are meaningful |
| Cart | Guest cart that merges on sign-in, free-shipping progress, "Complete your order" suggestions, delivery check |
| Checkout | Indian address form with PIN lookup, remembered address, coupon box, exact server-side total, Cash on delivery |
| Orders | Timeline per order, inline cancel, WhatsApp help |
| Track my order | Public page: order number plus checkout mobile number; no sign-in; never shows the address |
| Back in stock | "Notify me" on sold-out sarees; emailed the moment it returns |
| Personal shopping | Book a video call (day, time, budget, saree) or send a wedding / bulk / custom enquiry |
| Account | Register, sign in, password reset, wishlist, profile |
| Video and storytelling | "Behind the weave" story (video, or an animated loom until you add one), a "Sarees in motion" reel strip with a full-screen viewer, "Watch it drape" on product pages, a video badge and hover preview on cards. Nothing plays by itself for people who asked for less motion or are saving data |
| Motion | Word-by-word hero, scroll reveals, fly-to-cart, heart sparkles, ticker, reading-progress line, order-confirmed confetti; all calmed by the device's reduced-motion setting |
| Testimonials | Customer quotes shown one at a time under the catalogue; the owner curates them in the admin (sample quotes in local demos carry a visible "Sample" label); the section hides when empty |
| Refer a friend | Every customer has a personal code: the friend gets 10% off a first order (up to Rs. 500, above Rs. 1,499); the sharer earns a Rs. 200 single-use credit that only they can redeem, once the friend's order is delivered. No credit for cancelled orders or self-referral |
| Offer pop-up | A welcome or sale pop-up designed by the owner: headline, message, coupon code with copy button, countdown, picture, four colour themes. Shown once per visit or per day, after a delay or when the shopper is about to leave, never on checkout or sign-in, waits for the cookie choice, closes with Escape |
| Installable | Works like an app on phones (manifest and icons) |

## For the owner

| Feature | How it works |
|---|---|
| Autopilot (admin landing page) | Today's to-do list plus four automations, each Off / Ask me first / Automatic: back-in-stock alerts, abandoned-bag reminders, review requests, a morning digest |
| Leads | Everyone who opted in, scored 0-100 with visible reasons (hot / warm / cold), a ready WhatsApp message, status, notes, CSV export |
| Products | Draft, review, approve, publish lifecycle; readiness checks; margin shown; Add colour button; SKU |
| Bulk and feeds | Import many sarees from a spreadsheet (preview first; always creates drafts), export a backup, Google Shopping / Instagram / Facebook feed link, sitemap |
| Product Studio and Insights | Suggests listing text and price from cost; sales, margin, low stock, restock and range-gap findings from your own data |
| Videos | Paste a link to an mp4/webm file or a YouTube clip; choose story or reel; optionally tie it to a saree; pause or remove any time. Only https links are accepted |
| Admin console | Sidebar sections, a Today dashboard (sales, orders waiting, stock to watch, 14-day trend, shortcuts), saree cards with search and filters, an editor with a live shopper preview and a publish checklist, orders with filters and photos |
| Photos | Drag in any number of photos: each is made crisp (smart enlargement to 1200x1500, auto light and colour, careful sharpening), stored in the library and shown as a cover plus gallery; drag or use arrows to reorder, one click to enhance again |
| Media library | Upload photos (up to 8 MB) and videos (MP4 or WebM, up to 20 MB), preview, copy a link, delete what is unused; files are checked by their contents, never by name, and are served with a one-year cache |
| Photo and video studio | Before/after enhance with sliders, one-click hand-off to a saree's cover or gallery, a cinematic reel recorded in the browser from one photo (push-in, two close-ups, grain, light sweep, title) and published to "Sarees in motion", and ready-to-paste briefs for AI model photos (the app does not generate images itself) |
| Coupons and multi-buy offers | Percentage or flat coupons with caps, minimums, first-order rules, usage limits; multi-buy tiers you set |
| Orders | Status and payment state machines, stock restored on cancel |

## Safety and integrity

- Prices, GST, offers and coupons are computed on the server; the total the shopper saw must match.
- Stock is locked while an order is placed; double-clicks do not create double orders.
- Customer-facing automations start in "Ask me first". Nothing is sent to a shopper unseen until you switch it to Automatic.
- Leads need explicit consent. Tracking needs two matching details and says nothing about which one failed.
- Spreadsheet imports and exports are protected against formula injection.

## Not built yet

Online payments (needs your Razorpay keys), courier booking and live tracking numbers, returns and refunds flow,
loyalty points and referrals, gift cards, reviews with photos, staff roles.
See `docs/HOSTING.md` for where it can run.
