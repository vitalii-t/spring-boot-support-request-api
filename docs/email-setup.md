# Email configuration

Mailtrap is the default SMTP provider for support receipts. Copy `.env.example` to `.env` and supply your own account values. Credentials stay on the server and are never included in the repository.

## Capture messages in Sandbox

Open Mailtrap **Sandboxes**, select your inbox, and copy its **Integration → SMTP** username and password into `MAILTRAP_SANDBOX_USER` and `MAILTRAP_SANDBOX_PASSWORD`. Keep `MAIL_MODE=sandbox`. The app connects to `sandbox.smtp.mailtrap.io:2525` with required STARTTLS and certificate verification. Set `MAIL_FROM` to a valid example address.

Complete the app's normal workflow to see the message in the inbox. Sandbox does not send to the named recipient unless forwarding is separately enabled in the inbox settings.

## Send live transactional mail

Verify your sending domain under **API/SMTP → Sending Setup**, select the transactional stream, and obtain its SMTP sending token. Set:

- `MAIL_MODE=production`
- `MAIL_FROM` to an address on your verified domain
- `MAILTRAP_PRODUCTION_TOKEN` to your transactional token

The app uses `live.smtp.mailtrap.io:587`, username `api`, and mandatory STARTTLS with certificate verification. Production mail does not read Sandbox credentials. Host-environment values take precedence over `.env`; restart the app after changes. These are individual transactional messages, not a bulk campaign.

## Work without credentials

With `APP_ENV=development` and `MAIL_MODE=log`, the app writes `.eml` files into `.data/mail-preview/`. This mode sends nothing and is rejected in production. Keep previews private; they contain workflow and recipient data.

`accepted` means the SMTP server accepted the message, not confirmed inbox delivery. Saved records survive send errors. After correcting a configuration failure, run `java -jar target/support-request-api-1.0.0.jar --retry-email` to process known unsent entries. `unknown` and `sending` need a provider-history check before manual reconciliation; the retry command deliberately skips them. No delivery webhooks or automatic background retries are included.

Read the README for the app's operator access, persistent storage, and deployment instructions. Account credentials are needed only when running an actual Sandbox or production send, not when publishing source code.

Official references: [Sandbox SMTP](https://docs.mailtrap.io/email-sandbox/setup/sandbox-smtp-integration) and [production SMTP](https://docs.mailtrap.io/email-api-smtp/setup/smtp-integration).
