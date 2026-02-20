import express from 'express';
import { createServer } from 'http';
import { Server as SocketIOServer, Socket } from 'socket.io';
import { createClient, RedisClientType } from 'redis';
import { Pool } from 'pg';
import { SensorData, Session } from '@ridepulse/shared-types';
import { v4 as uuidv4 } from 'uuid';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
const httpServer = createServer(app);
const io = new SocketIOServer(httpServer, {
  cors: {
    origin: process.env.CORS_ORIGIN || '*',
    methods: ['GET', 'POST'],
    credentials: true,
  },
  transports: ['websocket', 'polling'],
});

// Redis клиент
let redisClient: RedisClientType | null = null;

// PostgreSQL пул
let pgPool: Pool | null = null;

// Активные реплеи
const activeReplays = new Map<string, {
  sessionId: string;
  speed: number;
  isPlaying: boolean;
  currentIndex: number;
  dataPoints: SensorData[];
  interval?: NodeJS.Timeout;
}>();

/**
 * Подключение к Redis
 */
async function connectRedis() {
  try {
    redisClient = createClient({
      url: process.env.REDIS_URL || 'redis://localhost:6379',
    });

    await redisClient.connect();
    console.log('✅ Redis подключён');
  } catch (error) {
    console.error('❌ Ошибка подключения к Redis:', error);
  }
}

/**
 * Подключение к PostgreSQL
 */
async function connectPostgres() {
  try {
    pgPool = new Pool({
      host: process.env.PG_HOST || 'localhost',
      port: parseInt(process.env.PG_PORT || '5432'),
      database: process.env.PG_DATABASE || 'ridepulse',
      user: process.env.PG_USER || 'postgres',
      password: process.env.PG_PASSWORD || 'postgres',
      max: 10,
    });

    const client = await pgPool.connect();
    await client.query('SELECT 1');
    client.release();
    
    console.log('✅ PostgreSQL подключён');
  } catch (error) {
    console.error('❌ Ошибка подключения к PostgreSQL:', error);
  }
}

/**
 * Загрузка данных сессии для реплея
 */
async function loadSessionData(sessionId: string): Promise<SensorData[]> {
  if (!pgPool) {
    throw new Error('База данных недоступна');
  }

  const client = await pgPool.connect();
  try {
    // Получаем информацию о сессии
    const sessionResult = await client.query(
      'SELECT * FROM sessions WHERE id = $1',
      [sessionId]
    );

    if (sessionResult.rows.length === 0) {
      throw new Error('Сессия не найдена');
    }

    const session = sessionResult.rows[0];

    // Получаем все метрики сессии
    const metricsResult = await client.query(
      `SELECT 
        timestamp,
        heart_rate,
        power,
        cadence,
        speed,
        latitude,
        longitude,
        altitude
       FROM metrics
       WHERE session_id = $1
       ORDER BY timestamp ASC`,
      [sessionId]
    );

    return metricsResult.rows.map(row => ({
      riderId: session.rider_id,
      sessionId: session.id,
      timestamp: row.timestamp,
      heartRate: row.heart_rate,
      power: row.power,
      cadence: row.cadence,
      speed: row.speed,
      location: row.latitude ? {
        latitude: row.latitude,
        longitude: row.longitude,
        altitude: row.altitude,
      } : undefined,
    }));
  } finally {
    client.release();
  }
}

/**
 * Создание реплея
 */
async function createReplay(sessionId: string, speed: number = 1): Promise<string> {
  const replayId = uuidv4();
  
  const dataPoints = await loadSessionData(sessionId);
  
  activeReplays.set(replayId, {
    sessionId,
    speed,
    isPlaying: false,
    currentIndex: 0,
    dataPoints,
  });

  console.log(`✅ Реплей создан: ${replayId} для сессии ${sessionId}`);
  return replayId;
}

/**
 * Запуск воспроизведения
 */
async function startReplay(replayId: string, socket: Socket) {
  const replay = activeReplays.get(replayId);
  if (!replay) {
    throw new Error('Реплей не найден');
  }

  if (replay.isPlaying) {
    return;
  }

  replay.isPlaying = true;

  const playNext = () => {
    if (!replay.isPlaying || replay.currentIndex >= replay.dataPoints.length) {
      // Конец реплея
      stopReplay(replayId);
      socket.emit('replay_finished', { replayId });
      return;
    }

    const dataPoint = replay.dataPoints[replay.currentIndex];
    socket.emit('replay_data', {
      replayId,
      data: dataPoint,
      progress: (replay.currentIndex / replay.dataPoints.length) * 100,
    });

    replay.currentIndex++;

    // Рассчитываем задержку на основе скорости
    let delay = 1000; // 1 секунда по умолчанию
    if (replay.currentIndex < replay.dataPoints.length) {
      const nextTimestamp = replay.dataPoints[replay.currentIndex].timestamp;
      const currentTimestamp = dataPoint.timestamp;
      delay = (nextTimestamp - currentTimestamp) / replay.speed;
    }

    replay.interval = setTimeout(playNext, delay);
  };

  playNext();
}

/**
 * Пауза воспроизведения
 */
function pauseReplay(replayId: string) {
  const replay = activeReplays.get(replayId);
  if (!replay) {
    return;
  }

  replay.isPlaying = false;
  
  if (replay.interval) {
    clearTimeout(replay.interval);
    replay.interval = undefined;
  }

  console.log(`⏸️ Реплей на паузе: ${replayId}`);
}

/**
 * Остановка воспроизведения
 */
function stopReplay(replayId: string) {
  const replay = activeReplays.get(replayId);
  if (!replay) {
    return;
  }

  replay.isPlaying = false;
  
  if (replay.interval) {
    clearTimeout(replay.interval);
    replay.interval = undefined;
  }

  replay.currentIndex = 0;
  console.log(`⏹️ Реплей остановлен: ${replayId}`);
}

/**
 * Изменение скорости воспроизведения
 */
function changeReplaySpeed(replayId: string, speed: number) {
  const replay = activeReplays.get(replayId);
  if (!replay) {
    return;
  }

  replay.speed = speed;
  console.log(`⚡ Скорость реплея изменена: ${replayId} -> ${speed}x`);
}

/**
 * Перемотка к позиции
 */
function seekReplay(replayId: string, position: number) {
  const replay = activeReplays.get(replayId);
  if (!replay) {
    return;
  }

  const targetIndex = Math.floor((position / 100) * replay.dataPoints.length);
  replay.currentIndex = Math.max(0, Math.min(targetIndex, replay.dataPoints.length - 1));
  
  console.log(`⏪ Перемотка: ${replayId} -> ${position}%`);
}

/**
 * Удаление реплея
 */
function deleteReplay(replayId: string) {
  stopReplay(replayId);
  activeReplays.delete(replayId);
  console.log(`🗑️ Реплей удалён: ${replayId}`);
}

/**
 * WebSocket обработчики
 */
io.on('connection', (socket: Socket) => {
  console.log(`🔗 Клиент подключён к Replay Service: ${socket.id}`);

  /**
   * Создание реплея
   */
  socket.on('create_replay', async (data: { sessionId: string; speed?: number }) => {
    try {
      const replayId = await createReplay(data.sessionId, data.speed);
      socket.emit('replay_created', { replayId });
    } catch (error: any) {
      socket.emit('error', { message: error.message });
    }
  });

  /**
   * Запуск воспроизведения
   */
  socket.on('start_replay', (data: { replayId: string }) => {
    try {
      startReplay(data.replayId, socket);
    } catch (error: any) {
      socket.emit('error', { message: error.message });
    }
  });

  /**
   * Пауза
   */
  socket.on('pause_replay', (data: { replayId: string }) => {
    pauseReplay(data.replayId);
  });

  /**
   * Остановка
   */
  socket.on('stop_replay', (data: { replayId: string }) => {
    stopReplay(data.replayId);
  });

  /**
   * Изменение скорости
   */
  socket.on('change_speed', (data: { replayId: string; speed: number }) => {
    changeReplaySpeed(data.replayId, data.speed);
  });

  /**
   * Перемотка
   */
  socket.on('seek', (data: { replayId: string; position: number }) => {
    seekReplay(data.replayId, data.position);
  });

  /**
   * Удаление реплея
   */
  socket.on('delete_replay', (data: { replayId: string }) => {
    deleteReplay(data.replayId);
  });

  /**
   * Получение информации о реплее
   */
  socket.on('get_replay_info', (data: { replayId: string }) => {
    const replay = activeReplays.get(data.replayId);
    if (replay) {
      socket.emit('replay_info', {
        replayId: data.replayId,
        sessionId: replay.sessionId,
        speed: replay.speed,
        isPlaying: replay.isPlaying,
        progress: (replay.currentIndex / replay.dataPoints.length) * 100,
        totalPoints: replay.dataPoints.length,
      });
    } else {
      socket.emit('error', { message: 'Реплей не найден' });
    }
  });

  /**
   * Отключение
   */
  socket.on('disconnect', () => {
    console.log(`🔌 Клиент отключён от Replay Service: ${socket.id}`);
  });
});

/**
 * HTTP API
 */
app.use(express.json());

app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    activeReplays: activeReplays.size,
    redisConnected: redisClient?.isReady ?? false,
    pgConnected: !!pgPool,
  });
});

app.get('/api/sessions', async (req, res) => {
  try {
    if (!pgPool) {
      return res.status(503).json({ error: 'База данных недоступна' });
    }

    const client = await pgPool.connect();
    try {
      const result = await client.query(
        `SELECT 
          s.id,
          s.rider_id,
          s.rider_name,
          s.start_time,
          s.end_time,
          s.is_active,
          s.metadata,
          COUNT(m.id) as metrics_count
         FROM sessions s
         LEFT JOIN metrics m ON s.id = m.session_id
         WHERE s.is_active = false
         GROUP BY s.id
         ORDER BY s.start_time DESC
         LIMIT 100`
      );

      res.json(result.rows);
    } finally {
      client.release();
    }
  } catch (error) {
    res.status(500).json({ error: 'Внутренняя ошибка' });
  }
});

app.get('/api/sessions/:id', async (req, res) => {
  try {
    const { id } = req.params;
    
    if (!pgPool) {
      return res.status(503).json({ error: 'База данных недоступна' });
    }

    const client = await pgPool.connect();
    try {
      const result = await client.query(
        'SELECT * FROM sessions WHERE id = $1',
        [id]
      );

      if (result.rows.length === 0) {
        return res.status(404).json({ error: 'Сессия не найдена' });
      }

      res.json(result.rows[0]);
    } finally {
      client.release();
    }
  } catch (error) {
    res.status(500).json({ error: 'Внутренняя ошибка' });
  }
});

/**
 * Запуск сервера
 */
const PORT = process.env.PORT || 8084;

async function startServer() {
  await connectRedis();
  await connectPostgres();

  httpServer.listen(PORT, () => {
    console.log(`🚀 Replay Service запущен на порту ${PORT}`);
  });
}

startServer().catch(console.error);
