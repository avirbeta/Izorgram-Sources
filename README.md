# Izorgram — исходники

Клиент Telegram на базе форка **Goregram** (`com.amanoteam.flonne`, релиз 12.10.1).

Отдельная сборка APK: см. репозиторий [my-ai-apps](https://github.com/avirbeta/my-ai-apps).

## История проекта

Приложение создавалось и дорабатывалось при участии ИИ:

- **Отдельное спасибо команде Goregram** за отличную базу — все исходники и работа, на которых построен этот клиент: https://github.com/AmanoTeam/Goregram
- Основа — открытый код [Telegram FOSS](https://github.com/Telegram-FOSS-Team/Telegram-FOSS)/Telegram для Android.
- VPN-модуль использует движок Xray-core и tun2socks из `libgojni.so`.

## Изменения в этом форке

- Стабильность: устранён крэш `Integer → Long` на ключе `proxy_dialog` (миграция старых данных) — `MessagesController.java`.
- Релизная сборка с ART-baseline profile — быстрый запуск, плавный UI.
- Интегрирован системный VPN (VpnService + Xray-core + tun2socks через `libgojni.so`, гео-базы `geoip.dat`/`geosite.dat`).
- Упрощено меню VPN (только системное подключение).
- Одна поддерживаемая ABI: `arm64-v8a`.
- VPN: исправлен `PendingIntent` уведомления (теперь открывает приложение, а не пытается запустить сервис как Activity).
- VPN: добавлен `foregroundServiceType="specialUse"` и property для Android 14+.
- VPN: добавлен `onRevoke()` — туннель корректно закрывается при отзыве разрешения.
- VPN: переписан разбор `ss://` (SIP002 и legacy base64).
- VPN: `realitySettings` теперь на правильном уровне Xray-конфига.
- VPN: если Xray не стартовал за 15 секунд, соединение больше не помечается как «Connected».
- Исправлены утечки сокетов/HTTP-соединений при пинге и загрузке подписки.

## Сборка

Требования: Android SDK, NDK, CMake 3.22.1, JDK 17+.

```bash
./gradlew :TMessagesProj_App:assembleAfatDebug     # отладочная
./gradlew :TMessagesProj_App:assembleAfatRelease   # релизная
```

APK: `TMessagesProj_App/build/outputs/apk/afat/<variant>/app.apk`

## Структура

- `TMessagesProj/` — основная библиотека (код приложения + `jni/` — нативка: tgcalls, ffmpeg, tdlib).
- `TMessagesProj_App/` — собираемое приложение.
- `Tools/`, `fastlane/`, `buildSrc/` — вспомогательные.

## Благодарности

- [Goregram](https://github.com/AmanoTeam/Goregram) — исходная кодовая база.
- Сообщество Telegram / FOSS — оригинальный клиент.
- Xray-core и gojni-сборка — за движок VPN.

## Лицензия

Код основан на открытых проектах Telegram и Goregram; используется их лицензионный режим (см. `LICENSE`).