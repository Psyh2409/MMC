# MMC

## OAuth2 Providers

У проєкті вже підключений `spring-boot-starter-oauth2-client`. Для появи кнопок входу треба задати змінні середовища для конкретного провайдера.

```powershell
$env:OAUTH2_GOOGLE_CLIENT_ID="your-google-client-id"
$env:OAUTH2_GOOGLE_CLIENT_SECRET="your-google-client-secret"
$env:OAUTH2_FACEBOOK_CLIENT_ID="your-facebook-client-id"
$env:OAUTH2_FACEBOOK_CLIENT_SECRET="your-facebook-client-secret"
mvn spring-boot:run
```

Альтернатива для IntelliJ IDEA:

1. Відкрий `Run/Debug Configuration`.
2. Додай потрібні `OAUTH2_*` змінні в `Environment variables`.
3. Перезапусти застосунок.

### Google

Для Google Cloud Console потрібен OAuth Client типу `Web application`.

Redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

### Facebook

Для Meta for Developers потрібен `Facebook Login` застосунок.

Valid OAuth Redirect URI:

```text
http://localhost:8080/login/oauth2/code/facebook
```

Після цього на сторінці `/login` з'являться кнопки доступних провайдерів.
