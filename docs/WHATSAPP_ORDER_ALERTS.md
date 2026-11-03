# Plan: automatic WhatsApp message when an order is paid

Status: **planned, not built yet** (agreed 2026-11-03, to do later).

## What it does

When a customer pays with Razorpay and the payment is confirmed, the order turns **Paid** and a WhatsApp
message goes out automatically within a few seconds:

- **To the owner:** "New paid order #1042: customer, phone, items, amount paid, Razorpay payment id, delivery address."
- **To the customer (optional):** "Hi Priya, your order #1042 for Rs 7,497 is confirmed..." with a link to track it.
- Later, the same way: "Shipped" and "Delivered" messages.

## Rules

- Sends **only after payment is confirmed**: the hook sits in `OrderService.markPaid` (reached from checkout,
  the Razorpay webhook and the reconcile job). Unpaid or failed payments never send anything.
- Sends **once per order**, even though Razorpay often reports the same payment two or three times
  (a "WhatsApp sent at" mark on the order).
- **Never blocks or undoes an order:** sent after the database commit, separately. If WhatsApp is down the order
  still stands, the send is retried a few times, and admin shows "WhatsApp not sent" with a **Resend** button.
- Cash on delivery (if switched on): sends when the order is placed.
- With the settings left empty the feature is off and nothing changes.

## What the owner sets up (needs the owner's own login)

1. Meta Business account (business.facebook.com) and a WhatsApp app (developers.facebook.com).
2. A **sending** phone number not used in the normal WhatsApp app (a new SIM is easiest). It cannot be the same
   number that receives the alerts.
3. Two message templates, category **Utility**, submitted for Meta approval (minutes to a day). Claude writes the text:
   `new_order_alert` (owner) and `order_confirmed` (customer). Template values cannot contain line breaks,
   so items are joined with commas.
4. A permanent access token (Meta Business settings, System user) and the Phone number ID.
5. On Render: `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID` (already read by the site for login codes),
   plus new `ORDER_ALERT_WHATSAPP` (number or numbers that get the alerts) and on/off switches for owner and customer messages.

Cost: Meta charges per message; order updates in India are roughly 12-15 paise each (check Meta's current rate card).
About Rs 25-30 a month for 100 orders with 2 messages each.

Free stop-gap for owner alerts only: unofficial services such as CallMeBot (less reliable, cannot message customers).

## What gets built (about half a day)

1. `WhatsAppOrderService`: builds the message from the order, sends through the same Meta Cloud API client as
   `WhatsAppOtpService`.
2. Hook in `markPaid`, after-commit and async, once per order.
3. Order fields: WhatsApp sent / failed + last error; admin status label and **Resend** button.
4. Settings in `application.properties` / Render env.
5. Tests: sends once on payment, never on an unpaid order, a WhatsApp error does not break checkout.
6. The two template texts ready to paste into Meta.

Testing: Meta's free test number plus Razorpay test mode; place a test order and see it arrive, then switch to the real number.

## Questions to settle before building

1. Who gets it: owner, customer, or both (recommended: both)?
2. Which number or numbers get the owner alerts?
3. Is WhatsApp already set up for login codes (are `WHATSAPP_TOKEN` and `WHATSAPP_PHONE_NUMBER_ID` in Render)?
