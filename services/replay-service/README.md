# Replay Service

Сервис для воспроизведения заездов.

## Функции

- Загрузка данных сессии для реплея
- Воспроизведение с регулируемой скоростью
- Пауза/остановка/перемотка
- WebSocket трансляция данных реплея

## Стек технологий

- Node.js 18+
- TypeScript
- PostgreSQL
- Redis
- Socket.IO

## API endpoints

- `GET /health` — Health check
- `GET /api/sessions` — Список завершённых сессий
- `GET /api/sessions/:id` — Детали сессии

## WebSocket события

### Клиент → Сервер
- `create_replay` — Создание реплея
  ```json
  {
    "sessionId": "session-id",
    "speed": 1.0
  }
  ```
- `start_replay` — Запуск воспроизведения
- `pause_replay` — Пауза
- `stop_replay` — Остановка
- `change_speed` — Изменение скорости
  ```json
  {
    "replayId": "replay-id",
    "speed": 2.0
  }
  ```
- `seek` — Перемотка к позиции
  ```json
  {
    "replayId": "replay-id",
    "position": 50
  }
  ```
- `delete_replay` — Удаление реплея
- `get_replay_info` — Получение информации о реплее

### Сервер → Клиент
- `replay_created` — Реплей создан
- `replay_data` — Данные реплея
  ```json
  {
    "replayId": "replay-id",
    "data": { /* SensorData */ },
    "progress": 50.5
  }
  ```
- `replay_finished` — Воспроизведение завершено
- `replay_info` — Информация о реплее

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

## Использование

### Создание реплея

```javascript
const socket = io('ws://localhost:8084');

socket.emit('create_replay', {
  sessionId: 'session-uuid',
  speed: 1.0
});

socket.on('replay_created', ({ replayId }) => {
  console.log('Replay created:', replayId);
});
```

### Воспроизведение

```javascript
socket.emit('start_replay', { replayId });

socket.on('replay_data', ({ replayId, data, progress }) => {
  console.log(`Progress: ${progress}%`);
  console.log('Data:', data);
});

socket.on('replay_finished', ({ replayId }) => {
  console.log('Replay finished');
});
```

### Управление воспроизведением

```javascript
// Пауза
socket.emit('pause_replay', { replayId });

// Изменение скорости
socket.emit('change_speed', { replayId, speed: 2.0 });

// Перемотка
socket.emit('seek', { replayId, position: 75 });

// Остановка
socket.emit('stop_replay', { replayId });
```

### Получение информации

```javascript
socket.emit('get_replay_info', { replayId });

socket.on('replay_info', (info) => {
  console.log('Replay info:', info);
});
```

## Пример интеграции с Web Dashboard

```tsx
import { io, Socket } from 'socket.io-client';

function ReplayViewer({ sessionId }: { sessionId: string }) {
  const [replayId, setReplayId] = useState<string | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [speed, setSpeed] = useState(1);
  const [metrics, setMetrics] = useState<SensorData | null>(null);

  const socketRef = useRef<Socket>();

  useEffect(() => {
    socketRef.current = io('ws://localhost:8084');

    socketRef.current.on('replay_created', ({ replayId }) => {
      setReplayId(replayId);
    });

    socketRef.current.on('replay_data', ({ replayId, data, progress }) => {
      setMetrics(data);
      setProgress(progress);
    });

    socketRef.current.on('replay_finished', () => {
      setIsPlaying(false);
    });

    return () => {
      socketRef.current?.disconnect();
    };
  }, []);

  const createReplay = () => {
    socketRef.current?.emit('create_replay', { sessionId, speed });
  };

  const togglePlay = () => {
    if (isPlaying) {
      socketRef.current?.emit('pause_replay', { replayId });
    } else {
      socketRef.current?.emit('start_replay', { replayId });
    }
    setIsPlaying(!isPlaying);
  };

  const changeSpeed = (newSpeed: number) => {
    socketRef.current?.emit('change_speed', { replayId, speed: newSpeed });
    setSpeed(newSpeed);
  };

  const seek = (position: number) => {
    socketRef.current?.emit('seek', { replayId, position });
  };

  return (
    <div>
      <button onClick={createReplay}>Create Replay</button>
      {replayId && (
        <>
          <button onClick={togglePlay}>
            {isPlaying ? 'Pause' : 'Play'}
          </button>
          <input
            type="range"
            min="0.5"
            max="5"
            step="0.5"
            value={speed}
            onChange={(e) => changeSpeed(parseFloat(e.target.value))}
          />
          <input
            type="range"
            min="0"
            max="100"
            value={progress}
            onChange={(e) => seek(parseInt(e.target.value))}
          />
          <div>Progress: {progress.toFixed(1)}%</div>
          {metrics && <MetricsDisplay data={metrics} />}
        </>
      )}
    </div>
  );
}
```
