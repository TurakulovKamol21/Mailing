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

## 2) Backend konfiguratsiya

```bash
cp .env.example .env
```

`.env` faylga korporativ mail server va JWT sozlamalarini kiriting:

- `MAIL_IMAP_HOST`, `MAIL_IMAP_PORT`, `MAIL_IMAP_SSL`
- `MAIL_SMTP_HOST`, `MAIL_SMTP_PORT`, `MAIL_SMTP_STARTTLS`, `MAIL_SMTP_SSL`
- `MAIL_DEFAULT_FOLDER`, `MAIL_TIMEOUT_SECONDS`
- `JWT_SECRET`, `JWT_ACCESS_TOKEN_MINUTES`, `JWT_REFRESH_TOKEN_DAYS`
- `APP_AUTH_ROLES`, `APP_AUTH_SESSION_TTL_MINUTES`
- `APP_AUTH_SESSION_CHECK_ENABLED`, `APP_AUTH_SERVICE_URL`
- `AUTH_FEIGN_CONNECT_TIMEOUT_MS`, `AUTH_FEIGN_READ_TIMEOUT_MS`
- `FRONTEND_ORIGINS` (masalan: `http://localhost:5173`)

`turon-analitics.uz` uchun tavsiya etilgan SSL/TLS konfiguratsiya:

```env
MAIL_IMAP_HOST=mail.turon-analitics.uz
MAIL_IMAP_PORT=993
MAIL_IMAP_SSL=true

MAIL_SMTP_HOST=mail.turon-analitics.uz
MAIL_SMTP_PORT=465
MAIL_SMTP_STARTTLS=false
MAIL_SMTP_SSL=true
```

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

UI ichida: login, folders, messages, read/detail, mark read/unread, move, delete, send, token refresh.

## 5) Auth holati

`/mail/**` endpointlar JWT bilan himoyalangan.
Auth service alohida bo'lsa, o'sha service bergan `accessToken`ni ishlatishingiz mumkin.

1. Mailbox credential ulash (`/mail/connect`):

```bash
curl -X POST http://localhost:8000/mail/connect \
  -H "Authorization: Bearer ACCESS_TOKEN_FROM_AUTH_SERVICE" \
  -H "Content-Type: application/json" \
  -d '{"username":"support@turon-analitics.uz","password":"your_mailbox_password"}'
```

2. `accessToken` ni barcha `/mail/**` so'rovlarga yuboring:

```bash
curl http://localhost:8000/mail/folders \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

3. Mail session uzish (ixtiyoriy):

```bash
curl -X DELETE http://localhost:8000/mail/connect \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

## 6) Session Check (ixtiyoriy)

`APP_AUTH_SESSION_CHECK_ENABLED=true` bo'lsa, JWT ichidagi `uid/sid` bilan
`POST /internal/session/check` chaqiruvi ishlaydi.

## Endpointlar

- `GET /health`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /mail/connect`
- `DELETE /mail/connect`
- `POST /mail/test-connection`
- `GET /mail/folders`
- `GET /mail/messages?folder=INBOX&limit=20&offset=0&unseenOnly=false`
- `GET /mail/messages/{uid}?folder=INBOX`
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
