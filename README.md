# MMC

## OAuth2 Providers

У проєкті вже підключений `spring-boot-starter-oauth2-client`. Для появи кнопок входу треба задати змінні середовища для конкретного провайдера.

```powershell
$env:APP_PUBLIC_BASE_URL="https://your-public-domain.ngrok-free.dev"
$env:OAUTH2_GOOGLE_CLIENT_ID="your-google-client-id"
$env:OAUTH2_GOOGLE_CLIENT_SECRET="your-google-client-secret"
$env:OAUTH2_FACEBOOK_CLIENT_ID="your-facebook-client-id"
$env:OAUTH2_FACEBOOK_CLIENT_SECRET="your-facebook-client-secret"
.\mvnw.cmd spring-boot:run
```

Альтернатива для IntelliJ IDEA:

1. Відкрий `Run/Debug Configuration`.
2. Додай потрібні `OAUTH2_*` змінні в `Environment variables`.
3. Перезапусти застосунок.

Якщо застосунок відкривається не напряму, а через `ngrok` або інший проксі, задай ще:

```powershell
$env:APP_PUBLIC_BASE_URL="https://your-public-domain.ngrok-free.dev"
```

Тоді OAuth redirect URI буде будуватися саме від цього публічного домену, а не від локального `localhost`.

## Ngrok Dev Mode

Для тимчасового стабільного тестування через `ngrok` у проєкті є скрипт:

```powershell
.\scripts\start-ngrok-oauth.ps1 -PublicBaseUrl "https://your-current-ngrok-domain.ngrok-free.dev"
```

Що він робить:

1. Встановлює `APP_PUBLIC_BASE_URL`.
2. Показує точні redirect URI для Google і Facebook.
3. Запускає застосунок через `.\mvnw.cmd spring-boot:run`.

Для Google треба додати в Google Cloud Console саме той URI, який виведе скрипт:

```text
https://your-current-ngrok-domain.ngrok-free.dev/login/oauth2/code/google
```

Важливо: код не може зробити випадковий `ngrok` домен стабільним. Стабільним його робить лише:

1. `reserved/static domain` в `ngrok`.
2. Або власний публічний домен.

Скрипт лише прибирає помилки синхронізації між поточним `ngrok` URL і тим, що використовує застосунок для OAuth redirect.

### Google

Для Google Cloud Console потрібен OAuth Client типу `Web application`.

Redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

Для `ngrok`:

```text
https://your-current-ngrok-domain.ngrok-free.dev/login/oauth2/code/google
```

### Facebook

Для Meta for Developers потрібен `Facebook Login` застосунок.

Valid OAuth Redirect URI:

```text
http://localhost:8080/login/oauth2/code/facebook
```

Для `ngrok`:

```text
https://your-current-ngrok-domain.ngrok-free.dev/login/oauth2/code/facebook
```

Після цього на сторінці `/login` з'являться кнопки доступних провайдерів.
