# RidePulse Mobile Rider

Android приложение для велогонщиков для сбора и передачи метрик с Bluetooth/ANT+ датчиков.

## Функции

- **Bluetooth LE сканирование и подключение** к датчикам:
  - Heart Rate Monitor (HRM)
  - Power Meter
  - Speed & Cadence Sensor
- **Real-time передача данных** на сервер через WebSocket
- **Foreground Service** для работы в фоновом режиме
- **Отображение метрик** в реальном времени:
  - Пульс (bpm)
  - Мощность (W)
  - Каденс (rpm)
  - Скорость (км/ч)

## Стек технологий

- **Kotlin** — основной язык
- **Jetpack Compose** — UI
- **Hilt** — Dependency Injection
- **Coroutines + Flow** — асинхронное программирование
- **OkHttp** — HTTP/WebSocket клиент
- **Nordic BLE** — библиотека для работы с BLE

## Структура проекта

```
src/main/java/com/ridepulse/rider/
├── bluetooth/           # BLE менеджеры
│   ├── BleManager.kt
│   └── SensorBleManager.kt
├── data/
│   └── model/          # Модели данных
│       └── SensorData.kt
├── network/            # Сетевой слой
│   └── DataSender.kt
├── service/            # Foreground Service
│   └── SensorMonitoringService.kt
├── ui/
│   ├── screens/        # UI экраны
│   ├── theme/          # Тема приложения
│   └── viewmodel/      # ViewModel
└── MainActivity.kt
```

## Сборка и запуск

### Требования

- Android Studio Hedgehog (2023.1.1) или новее
- JDK 17
- Android SDK 34
- Устройство с Android 8.0+ (API 26) и Bluetooth LE

### Сборка

```bash
./gradlew assembleDebug
```

### Установка на устройство

```bash
./gradlew installDebug
```

### Запуск через Android Studio

1. Откройте проект в Android Studio
2. Подключите Android устройство с включённым Bluetooth
3. Нажмите Run или используйте `./gradlew installDebug`

## Конфигурация

URL сервера настраивается в `build.gradle.kts`:

```kotlin
debug {
    buildConfigField("String", "WS_URL", "\"ws://10.0.2.2:8080/ws\"")
    buildConfigField("String", "API_URL", "\"http://10.0.2.2:8080\"")
}
```

- Для эмулятора используйте `10.0.2.2` (это localhost хоста)
- Для реального устройства замените на IP вашего сервера

## Разрешения

Приложение требует следующие разрешения:
- `BLUETOOTH_SCAN` — сканирование BLE устройств
- `BLUETOOTH_CONNECT` — подключение к BLE устройствам
- `ACCESS_FINE_LOCATION` — требуется для BLE сканирования
- `FOREGROUND_SERVICE` — работа в фоне
- `POST_NOTIFICATIONS` — показ уведомлений

## Использование

1. **Запуск приложения**
   - При первом запуске предоставьте все разрешения
   - Убедитесь, что Bluetooth включён

2. **Подключение сенсоров**
   - Нажмите иконку поиска для сканирования
   - Выберите устройство из списка
   - Дождитесь подключения

3. **Начало сессии**
   - После подключения сенсоров сессия начинается автоматически
   - Метрики будут отображаться в реальном времени
   - Данные отправляются на сервер

4. **Завершение сессии**
   - Нажмите кнопку "ЗАВЕРШИТЬ СЕССИЮ"
   - Все сенсоры будут отключены
   - Данные на сервере будут сохранены

## Поддерживаемые датчики

### Bluetooth LE (BLE)

- **Heart Rate** — стандартный сервис `0x180D`
- **Cycling Power** — стандартный сервис `0x1818`
- **Cycling Speed and Cadence** — стандартный сервис `0x1816`

### ANT+ (в планах)

Для поддержки ANT+ требуется интеграция с ANT+ Plugin Service.

## Тестирование

Для тестирования можно использовать следующие BLE симуляторы:
- nRF Connect (Android/iOS)
- LightBlue (iOS)

## Отладка

Логи приложения доступны в Logcat с тегами:
- `RidePulseBleManager` — BLE операции
- `SensorBleManager` — подключение к конкретному сенсору
- `DataSender` — отправка данных на сервер
- `SensorMonitoringService` — работа foreground service

## Известные ограничения

- ANT+ ещё не реализован (требуется сторонний SDK)
- Расчёт скорости на основе wheel revolutions упрощён
- Нет сохранения исторических данных локально

## Лицензия

MIT
