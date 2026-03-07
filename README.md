# Corporate Mail (Backend + Frontend)

Bu loyiha 2 ta alohida projectdan iborat:

- Backend: Java Spring Boot (`/`)
- Frontend: Vite Vanilla JS (`/frontend`)

Funksiyalar:

- papkalarni olish (`folders`)
- xatlar ro'yxatini olish (`get/list`)
- bitta xatni o'qish (`read`)
- o'qilgan/o'qilmagan holatini o'zgartirish
- xatni boshqa papkaga ko'chirish
- xatni o'chirish
- yangi xat yuborish (`send`, attachment bilan)

## 1) Talablar

- Java 17+
- Maven 3.9+
- Node.js 20+
- PostgreSQL 14+

## 2) Backend konfiguratsiya

```bash
cp .env.example .env
```

`.env` faylga DB, korporativ mail defaultlari va auth sozlamalarini kiriting:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JPA_DDL_AUTO` (`update` tavsiya etiladi dev uchun)

- `MAIL_IMAP_HOST`, `MAIL_IMAP_PORT`, `MAIL_IMAP_SSL`
- `MAIL_SMTP_HOST`, `MAIL_SMTP_PORT`, `MAIL_SMTP_STARTTLS`, `MAIL_SMTP_SSL`
- `MAIL_DEFAULT_FOLDER`, `MAIL_TIMEOUT_SECONDS`
- `JWT_SECRET`
- `JWT_ACCESS_TOKEN_MINUTES`, `JWT_REFRESH_TOKEN_DAYS`
- `MAIL_SETTINGS_ENCRYPTION_KEY`
- `APP_AUTH_ROLES`, `APP_AUTH_SESSION_TTL_MINUTES`
- `GOOGLE_CLIENT_ID`, `GOOGLE_TOKEN_INFO_URL`
- `APP_AUTH_SESSION_CHECK_ENABLED`, `APP_AUTH_SERVICE_URL`
- `AUTH_FEIGN_CONNECT_TIMEOUT_MS`, `AUTH_FEIGN_READ_TIMEOUT_MS`
- `FRONTEND_ORIGINS` (masalan: `http://localhost:5173`)

Mailbox username/password endi `.env`da saqlanmaydi. Har bir user o'z mail settingsini UI orqali kiritadi va ular PostgreSQL'da saqlanadi.
`mailbox_password` DB'da AES-GCM bilan shifrlanadi (`MAIL_SETTINGS_ENCRYPTION_KEY` orqali).

PostgreSQL'da `mailing` nomli DB yarating (yoki `SPRING_DATASOURCE_URL` ni o'zingizdagi DB nomiga moslang).

## 3) Backend ishga tushirish

```bash
mvn spring-boot:run
```

API `http://localhost:8000` da ishlaydi.

`application.yml` ichida `.env` avtomatik import qilinadi, shuning uchun alohida `export` shart emas.
IntelliJ'da ham project root (`/Users/home/IdeaProjects/Mailing`) dan run qilsangiz `.env` o'qiladi.

## 4) Frontend konfiguratsiya va ishga tushirish

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

Frontend URL: `http://localhost:5173`

Frontend `.env`:

- `VITE_API_BASE_URL=http://localhost:8000`
- `VITE_DEV_PROXY_TARGET=http://localhost:8000`
- `VITE_GOOGLE_CLIENT_ID=your-google-oauth-client-id.apps.googleusercontent.com`

UI ichida: email/password login, Google login, refresh token, folders, messages, read/detail, mark read/unread, move, delete, send.

## 5) Auth holati

`/mail/**` endpointlar JWT bilan himoyalangan, lekin login/register oqimi mail service ichida bor:

- `POST /auth/login`
- `POST /auth/google`
- `POST /auth/refresh`

`/auth/login` email/password orqali mailbox connection tekshiradi va access/refresh token qaytaradi.
`/auth/google` Google ID token orqali sign-in/sign-up qiladi va access/refresh token qaytaradi.
Google login mailboxga to'g'ridan-to'g'ri kirmaydi, shuning uchun user birinchi kirgandan keyin `Mail Settings` ichida IMAP/SMTP sozlamalarini to'ldirishi kerak.

`user_mail_settings` saqlash uchun system user JWT ichidan `uid` olinadi. Agar `uid` bo'lmasa, servis `user.id` claimdan foydalanadi.
Token ichidagi `roles` string (`"ADMIN,HR"`), list, yoki `user.roles` object-list ko'rinishida bo'lishi mumkin.

Frontend'da login 2 xil usulda ishlaydi:

1. Login oynasida email/password bilan kirish.
2. `Continue with Google` orqali kirish.

1. Email/password login:

```bash
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username":"support@turon-analitics.uz",
    "password":"your_mailbox_password"
  }'
```

2. Google login:

```bash
curl -X POST http://localhost:8000/auth/google \
  -H "Content-Type: application/json" \
  -d '{
    "idToken":"GOOGLE_ID_TOKEN"
  }'
```

3. Refresh token:

```bash
curl -X POST http://localhost:8000/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken":"REFRESH_TOKEN"
  }'
```

4. Mailbox settings olish:

```bash
curl http://localhost:8000/mail/settings \
  -H "Authorization: Bearer ACCESS_TOKEN" \
```

5. Mailbox settings saqlash (save paytida connection test qilinadi):

```bash
curl -X PUT http://localhost:8000/mail/settings \
  -H "Authorization: Bearer ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "imapHost":"mail.turon-analitics.uz",
    "imapPort":993,
    "imapSsl":true,
    "smtpHost":"mail.turon-analitics.uz",
    "smtpPort":465,
    "smtpStarttls":false,
    "smtpSsl":true,
    "username":"support@turon-analitics.uz",
    "password":"your_mailbox_password",
    "fromEmail":"support@turon-analitics.uz",
    "defaultFolder":"INBOX",
    "timeoutSeconds":30
  }'
```

6. `accessToken` ni barcha `/mail/**` so'rovlarga yuboring:

```bash
curl http://localhost:8000/mail/folders \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

## 6) Session Check (ixtiyoriy)

`APP_AUTH_SESSION_CHECK_ENABLED=true` bo'lsa, JWT ichidagi `uid/sid` bilan
`POST /internal/session/check` chaqiruvi ishlaydi.

## Endpointlar

- `GET /health`
- `POST /auth/login`
- `POST /auth/google`
- `POST /auth/refresh`
- `GET /mail/settings`
- `PUT /mail/settings`
- `POST /mail/test-connection`
- `GET /mail/folders`
- `GET /mail/messages?folder=INBOX&limit=20&offset=0&unseenOnly=false`
- `GET /mail/messages/{uid}?folder=INBOX`
- `GET /mail/messages/{uid}/thread?folder=INBOX`
- `POST /mail/messages/{uid}/read?folder=INBOX`
- `POST /mail/messages/{uid}/move?folder=INBOX`
- `DELETE /mail/messages/{uid}?folder=INBOX`
- `POST /mail/send`

## `POST /mail/send` namunaviy body

```json
{
  "to": ["recipient@company.com"],
  "cc": [],
  "bcc": [],
  "subject": "Test message",
  "bodyText": "Salom, bu test xat.",
  "bodyHtml": "<p>Salom, bu <b>test</b> xat.</p>",
  "attachments": [
    {
      "filename": "note.txt",
      "contentBase64": "SGVsbG8gZnJvbSBhdHRhY2htZW50",
      "contentType": "text/plain"
    }
  ]
}
```

## Eslatma

- Ba'zi korporativ tizimlarda IMAP/SMTP default o'chirilgan bo'lishi mumkin.
- Exchange/O365 ishlatayotgan bo'lsangiz, admin panelda IMAP/SMTP va app-password policy ni tekshiring.
# Mailing
