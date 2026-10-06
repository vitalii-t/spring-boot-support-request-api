# Support Request API

A small Spring Boot service demonstrating Java records, Bean Validation, and transactional JDBC writes. A support form saves a request and attempts an email receipt; an authenticated JSON endpoint lists recent requests.

**Stack:** Java 21, Spring Boot 3.5, H2, Jakarta Mail, JUnit.

## Run locally

Install JDK 21 and Maven 3.9 or newer. The Maven parent pins managed dependency versions.

```sh
cp .env.example .env
mvn verify
mvn spring-boot:run
```

Before starting, set `ADMIN_PASSWORD` to 16+ random characters in `.env`. For an offline walkthrough, set `MAIL_MODE=log`; no provider credentials are required. Open [localhost:8080](http://localhost:8080). All variables are explained beside their entries in `.env.example`; see [email configuration](docs/email-setup.md) for normal sandbox and production sending.

1. Enter a name, email, subject, and description in the form.
2. Submit it and keep the returned request ID. The receipt state is separate from the saved request.
3. Open `/admin` using username `admin` and the configured password. It returns up to 100 recent requests as JSON.
4. In offline mode, inspect the corresponding `.data/mail-preview/<id>.eml` file.

The same workflow is available as JSON:

```sh
curl -i http://localhost:8080/requests \
  -H 'Content-Type: application/json' \
  -d '{"name":"Sam","email":"sam@example.com","subject":"Sign-in issue","message":"I cannot sign in to my account."}'
```

The [OpenAPI document](src/main/resources/static/openapi.json) is served at `/openapi.json`. Unknown fields, invalid addresses, and invalid lengths return 400. Bodies over 16 KiB return 413. Browser submissions must have the configured `APP_URL` origin; CLI clients may omit Origin. Hourly limits are 12 per direct client address and three per recipient. Forwarded address headers are deliberately ignored; users behind one reverse proxy share its client limit.

## Where to look

- `SupportController.java`: typed request fields and HTTP endpoints.
- `SupportService.java`: request/outbox transaction and locked rate counters.
- `Receipts.java`: MIME receipt, transport selection, and persisted send states.
- `AccessFilter.java`: private operator access and bounded request bodies.
- `schema.sql`: embedded persistence, automatically initialized at startup.
- `SupportTest.java`: workflow, concurrency, validation, access, and transport checks.

Java source is under `src/main/java/dev/support/` and tests under `src/test/java/dev/support/`.

## Package and deploy

```sh
mvn verify
java -jar target/support-request-api-1.0.0.jar
```

Run one process with a persistent, private `.data` directory. Set `APP_ENV=production`, `HOST=0.0.0.0`, a public HTTPS `APP_URL`, and operator credentials in the host environment. Use a TLS reverse proxy. H2 is embedded and is intended for a small single-instance app, not distributed deployments. Configure the selected email mode separately.

An optional container build is included:

```sh
docker build -t support-request-api .
docker run --rm -p 127.0.0.1:8080:8080 --env-file .env -e HOST=0.0.0.0 support-request-api
```

That quick container example is disposable. For persistent use, mount `/app/.data` with ownership suitable for UID 10001. No database browser or public receipt log is exposed.

## Receipt recovery and limits

Requests and a pending outbox entry are committed together before SMTP runs. `accepted` means the SMTP server accepted the message, not that it reached an inbox. `previewed` means only a local file was created. Missing configuration is `failed`; a send exception becomes `unknown`. A process interrupted during sending leaves `sending`. Both ambiguous states require inspection of provider history before manual reconciliation.

After correcting configuration, retry only known unsent receipts:

```sh
java -jar target/support-request-api-1.0.0.jar --retry-email
```

Stop the web process before running this command against its embedded file database. The command retries `pending` and `failed`, never `accepted`, `unknown`, or `sending`. This app has no staffed helpdesk, email-delivery webhook, user accounts, retention scheduler, or automatic retry worker. Submitting the form twice creates two requests, subject to the limits. Operators should remove stored personal data according to their retention needs.

Tests use local MIME files and transport doubles. Real delivery requires the operator's own credentials and is optional when publishing the source.

MIT license; see [LICENSE](LICENSE).

Reference: [Spring Boot documentation](https://docs.spring.io/spring-boot/3.5/).
