# Order alerts on the phone (WhatsApp + email)

Status: **built** (2026-11-04). It stays switched off until the settings below are added on Render.

## What happens

When an order is **paid** (Razorpay confirms the payment) or a cash-on-delivery order is placed:

| Who | How | What it says |
|---|---|---|
| Shop owner | WhatsApp to every number in `ORDER_ALERT_WHATSAPP` | New paid order: order no., customer, phone, sarees, amount, delivery address |
| Shop owner | Email to `ORDER_ALERT_EMAIL` (the Gmail app on the phone notifies) | The same details plus a link to Admin → Orders |
| Customer | WhatsApp to the phone number they gave at checkout | Order confirmed: order no., sarees, amount, where to track it |

Rules:

- **Only paid orders.** Nothing is sent when someone opens the payment window and leaves, when a payment fails,
  or when an order is cancelled before paying.
- **Once per order.** Razorpay often reports the same payment two or three times; each person still gets one message.
- **Never blocks an order.** Messages go out a second after the order is saved, on their own. If WhatsApp is down,
  the order still stands; it retries 3 times (2 s, 10 s, 30 s). Admin → Orders shows
  "WhatsApp: you ✓ sent · customer ✓ sent", or the problem and a **Resend** button.
- Test-mode orders say "TEST" before the order number.

## Setting it up (owner, about 30 minutes plus Meta's approval)

You need your own logins for these; Claude never types passwords or codes.

### 1. Meta WhatsApp sender (one time)

1. Go to **business.facebook.com** and create a Business account (or use an existing one).
2. Go to **developers.facebook.com → My Apps → Create app → Business** and add **WhatsApp**.
3. Add a **sending number**. It must be a number **not** used in the normal WhatsApp app (a new SIM is easiest),
   and it must be different from the owner number that receives the alerts.
4. In **Business settings → System users**, create a system user, give it the app with *full control*,
   and **Generate token** with `whatsapp_business_messaging` and `whatsapp_business_management`, expiry **Never**.
   That is `WHATSAPP_TOKEN`.
5. On the app's **WhatsApp → API Setup** page, copy the **Phone number ID**. That is `WHATSAPP_PHONE_NUMBER_ID`.

### 2. Two message templates (WhatsApp Manager → Message templates → Create)

Category **Utility**, language **English**. Copy the names and texts exactly; `{{1}}`… are filled in by the shop.

**Name:** `new_order_alert`

```
New paid order {{1}}
Customer: {{2}}
Phone: {{3}}
Sarees: {{4}}
Amount: {{5}}
Deliver to: {{6}}
Please pack and ship it from the admin page.
```

Sample values for Meta's review: `INF-20261104-AB12CD`, `Priya Sharma`, `+91 9876543210`,
`Kanchipuram Silk Saree`, `Rs. 4499 paid online`, `12 MG Road, Hyderabad, Telangana 500001`.

**Name:** `order_confirmed`

```
Hi {{1}}, thank you for shopping with Rekha Shopping Mall!
Your order {{2}} is confirmed.
Sarees: {{3}}
Amount: {{4}}
We will message you when it ships. Track it here: {{5}}
Please note: all sales are final, no returns or refunds.
```

Sample values: `Priya`, `INF-20261104-AB12CD`, `Kanchipuram Silk Saree`, `Rs. 4499 paid online`,
`https://rekha-shopping-mall.pages.dev/orders`.

Approval takes a few minutes to a day. Messages only go out once both are **Active**.

### 3. Owner email (optional, free, works without WhatsApp)

1. On the Gmail account that will send the emails, turn on 2-Step Verification, then create an **App password**
   (myaccount.google.com → Security → App passwords).
2. `MAIL_USERNAME` = that Gmail address, `MAIL_PASSWORD` = the 16-letter app password.
3. `ORDER_ALERT_EMAIL` = the email address that should receive new-order alerts (can be the same Gmail).

### 4. Add the settings on Render

Render → `rekha-shopping-mall-api` → **Environment** → add, then **Save, rebuild and deploy**:

| Key | Value |
|---|---|
| `WHATSAPP_TOKEN` | from step 1.4 |
| `WHATSAPP_PHONE_NUMBER_ID` | from step 1.5 |
| `ORDER_ALERT_WHATSAPP` | owner number(s), e.g. `9876543210` or `9876543210, 9123456789` |
| `ORDER_ALERT_EMAIL` | owner email (optional) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | for the email (optional) |

Optional switches: `WHATSAPP_NOTIFY_OWNER=false` or `WHATSAPP_NOTIFY_CUSTOMER=false` to stop one of the two;
`WHATSAPP_OWNER_TEMPLATE` / `WHATSAPP_CUSTOMER_TEMPLATE` if you named the templates differently.

## Cost

Meta charges per message; utility messages in India are roughly 12-15 paise each (check Meta's current rate card).
About Rs. 25-30 a month for 100 orders with 2 messages each. The email is free.

## Code

- `WhatsAppOrderService`: builds and sends the messages, once per order (`wa_owner_at`, `wa_customer_at`, `wa_error` on `orders`).
- Called from `OrderService` after the order is saved: `markPaid` (checkout, Razorpay webhook, reconcile job),
  cash-on-delivery placement, and an admin marking an order paid.
- `POST /api/admin/orders/{id}/whatsapp-resend` for the Resend button.
- Tests: `WhatsAppOrderAlertIntegrationTest`.
