# RidePulse Web Dashboard

Веб-дашборд для мониторинга метрик велосипедистов в реальном времени.

## Функции

- **Real-time обновление** метрик через WebSocket
- **Визуализация данных** на графиках
- **Карта** с позициями райдеров
- **Система алертов** для критических значений
- **Отображение метрик**:
  - Пульс (bpm)
  - Мощность (W)
  - Каденс (rpm)
  - Скорость (км/ч)
  - GPS координаты

## Стек технологий

- **Next.js 14** — React фреймворк
- **TypeScript** — типизация
- **Tailwind CSS** — стили
- **Recharts** — графики
- **React Leaflet** — карта
- **Socket.io Client** — WebSocket клиент
- **Lucide React** — иконки
- **date-fns** — работа с датами

## Структура проекта

```
src/
├── app/                  # Next.js App Router
│   ├── layout.tsx       # Корневой layout
│   ├── page.tsx         # Главная страница (дашборд)
│   └── globals.css      # Глобальные стили
├── components/           # React компоненты
│   ├── RiderMetricsCard.tsx  # Карточка райдера
│   ├── MetricsChart.tsx      # График метрик
│   ├── RiderMap.tsx          # Карта райдеров
│   └── AlertsPanel.tsx       # Панель алертов
├── hooks/                # React hooks
│   └── useWebSocket.ts  # Hook для WebSocket
├── lib/                  # Утилиты и API клиенты
│   ├── websocket.ts     # WebSocket клиент
│   └── api.ts           # HTTP API клиент
└── types/                # TypeScript типы
    └── sensor.ts        # Модели данных
```

## Установка

```bash
npm install
```

## Разработка

```bash
npm run dev
```

Приложение будет доступно по адресу http://localhost:3000

## Сборка

```bash
npm run build
npm start
```

## Переменные окружения

Создайте файл `.env.local`:

```env
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## Компоненты

### RiderMetricsCard
Карточка с текущими метриками райдера:
- Аватар и имя
- Пульс, мощность, каденс, скорость
- GPS позиция
- Активные алерты

### MetricsChart
График истории метрик:
- Поддерживает несколько метрик на одном графике
- Reference lines для критических зон (например, 140/180 bpm для пульса)
- Интерактивный tooltip

### RiderMap
Карта с позициями райдеров:
- Цветные маркеры для каждого райдера
- Popup с метриками при клике
- Трека истории движения

### AlertsPanel
Панель алертов:
- Активные и просмотренные алерты
- Разные уровни критичности (warning/critical)
- Подтверждение и удаление алертов

## WebSocket события

### Получаемые события:
- `sensor_data` — данные сенсора
- `rider_metrics` — метрики райдера
- `alert` — новый алерт
- `session_start` — начало сессии
- `session_end` — конец сессии

### Отправляемые события:
- `acknowledge_alert` — подтверждение алерта

## API endpoints

- `GET /api/riders` — список райдеров
- `GET /api/riders/:id` — детали райдера
- `GET /api/sessions/active` — активные сессии
- `GET /api/sessions/:id` — детали сессии
- `GET /api/riders/:id/sessions` — история сессий райдера
- `GET /api/sessions/:id/metrics` — метрики сессии
- `GET /api/analytics/team` — статистика команды

## Стили

Дашборд использует Tailwind CSS с кастомной цветовой схемой:

- **Primary**: Green (зелёный)
- **Heart Rate**: Red (красный)
- **Power**: Purple (фиолетовый)
- **Cadence**: Blue (синий)
- **Speed**: Cyan (голубой)

## Линтер

```bash
npm run lint
```

## Проверка типов

```bash
npm run type-check
```

## Лицензия

MIT
