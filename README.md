# Save Data — Android VPN Firewall

Приложение для управления интернет-доступом по приложениям и мониторинга трафика. **Без root**, работает через Android VPN API.

## Функции

- **Блокировка интернета** для каждого приложения (включая системные)
- **Мониторинг трафика** в реальном времени (↓↑ на каждое приложение)
- **Сброс счётчиков** трафика
- **Настройка периода** учёта (1 час — 30 дней)
- **VPN без root** — использует Android `VpnService` API
- Тёмная тема, поиск по приложениям

## Архитектура

- **VPN туннель**: `VpnService` перехватывает весь трафик. TCP SYN от заблокированного приложения → RST. Разрешённый трафик проксируется через защищённые сокеты.
- **UID lookup**: `/proc/net/tcp` + `/proc/net/tcp6` — определение приложения по порту пакета.
- **TrafficStats**: `TrafficStats.getUidRxBytes/TxBytes` — опрос каждые 2 сек, дельта с момента сброса.
- **Room DB**: хранит правила (block/allow) и базовые значения трафика.
- **MVVM**: ViewModel + LiveData/StateFlow, Coroutines.

## Сборка

### Через CodeMagic
1. Подключите этот репозиторий в CodeMagic
2. Workflow: `android-app`
3. Build → APK / AAB

### Локально
```bash
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Необходимые разрешения

| Разрешение | Зачем |
|---|---|
| `BIND_VPN_SERVICE` | Создание VPN туннеля |
| `FOREGROUND_SERVICE` | Фоновая работа VPN |
| `INTERNET` | Форвардинг пакетов |
| `QUERY_ALL_PACKAGES` | Список всех приложений |
| `PACKAGE_USAGE_STATS` | Расширенная статистика (опционально) |

## Требования

- Android 8.0+ (API 26)
- Kotlin 1.9+, AGP 8.4+

## Структура проекта

```
app/src/main/java/com/savedata/app/
├── MainActivity.kt          — главный экран, управление VPN
├── App.kt                   — Application, DI
├── vpn/
│   ├── SaveDataVpnService.kt — VPN сервис, цикл пакетов
│   └── PacketForwarder.kt   — форвардинг TCP/UDP
├── data/
│   ├── AppRule.kt           — правило блокировки
│   ├── TrafficRecord.kt     — запись трафика
│   ├── AppRepository.kt     — репозиторий
│   └── AppDatabase.kt       — Room база
├── ui/
│   ├── apps/                — экран списка приложений
│   └── settings/            — настройки
└── util/
    ├── TrafficMonitor.kt    — мониторинг TrafficStats
    ├── IpPacketUtils.kt     — парсинг IP/TCP/UDP пакетов
    ├── ProcNetUtils.kt      — UID lookup из /proc/net
    └── AppInfoLoader.kt     — загрузка списка приложений
```
