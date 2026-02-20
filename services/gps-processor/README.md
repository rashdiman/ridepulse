# GPS Processor

Сервис для обработки GPS данных райдеров.

## Функции

- Обработка GPS координат из метрик
- Сохранение треков в PostgreSQL
- Расчёт расстояния и скорости
- Расчёт набора высоты
- Создание сегментов для реплея

## Стек технологий

- Node.js 18+
- TypeScript
- PostgreSQL
- Redis
- Turf.js (геопространственные вычисления)

## API endpoints

- `GET /health` — Health check
- `GET /api/gps/tracks/:sessionId` — Получение трека сессии
- `POST /api/gps/segments/:sessionId/create` — Создание сегментов
- `GET /api/gps/segments/:sessionId` — Получение сегментов

## Запуск

```bash
npm install
npm run dev
```

## Сборка

```bash
npm run build
npm start
```

## Таблицы в PostgreSQL

### gps_tracks
Хранит все GPS точки сессии:
- `id` — уникальный идентификатор
- `session_id` — ID сессии
- `rider_id` — ID райдера
- `timestamp` — временная метка
- `latitude`, `longitude`, `altitude` — координаты
- `distance_from_start` — расстояние от старта
- `elevation_gain` — набор высоты

### gps_segments
Хранит сегменты трека для реплея:
- `id` — уникальный идентификатор
- `session_id` — ID сессии
- `segment_index` — индекс сегмента
- `start_time`, `end_time` — время начала и конца
- `start_latitude`, `start_longitude` — координаты начала
- `end_latitude`, `end_longitude` — координаты конца
- `distance` — длина сегмента
- `average_speed`, `max_speed` — скорость
- `elevation_gain` — набор высоты
