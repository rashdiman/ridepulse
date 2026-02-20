import { createClient, RedisClientType } from 'redis';
import { Pool, PoolClient } from 'pg';
import { SensorData, MetricHistoryPoint, RiderMetrics, Session } from '@ridepulse/shared-types';
import dotenv from 'dotenv';

dotenv.config();

// Redis клиент
let redisClient: RedisClientType | null = null;

// PostgreSQL пул
let pgPool: Pool | null = null;

// Кэш метрик райдеров
const ridersMetricsCache = new Map<string, RiderMetrics>();

// Размер истории для кэша
const HISTORY_SIZE = 300; // 5 минут при 1 Hz

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
      max: 20,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 2000,
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
 * Создание таблиц при старте
 */
async function createTables() {
  const client = await pgPool!.connect();
  try {
    await client.query(`
      CREATE TABLE IF NOT EXISTS sessions (
        id VARCHAR(36) PRIMARY KEY,
        rider_id VARCHAR(36) NOT NULL,
        rider_name VARCHAR(255) NOT NULL,
        team_id VARCHAR(36),
        start_time BIGINT NOT NULL,
        end_time BIGINT,
        device_info JSONB NOT NULL,
        is_active BOOLEAN DEFAULT true,
        metadata JSONB,
        created_at TIMESTAMP DEFAULT NOW()
      );
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS metrics (
        id SERIAL PRIMARY KEY,
        session_id VARCHAR(36) NOT NULL REFERENCES sessions(id),
        rider_id VARCHAR(36) NOT NULL,
        timestamp BIGINT NOT NULL,
        heart_rate INTEGER,
        power INTEGER,
        cadence INTEGER,
        speed DECIMAL(10, 2),
        latitude DECIMAL(10, 8),
        longitude DECIMAL(11, 8),
        altitude DECIMAL(10, 2),
        created_at TIMESTAMP DEFAULT NOW()
      );
    `);

    // Индексы для быстрого поиска
    await client.query(`
      CREATE INDEX IF NOT EXISTS idx_metrics_session_id ON metrics(session_id);
    `);

    await client.query(`
      CREATE INDEX IF NOT EXISTS idx_metrics_rider_id ON metrics(rider_id);
    `);

    await client.query(`
      CREATE INDEX IF NOT EXISTS idx_metrics_timestamp ON metrics(timestamp);
    `);

    console.log('✅ Таблицы созданы');
  } finally {
    client.release();
  }
}

/**
 * Обработка входящих метрик
 */
async function processMetrics(data: SensorData) {
  try {
    const { riderId, sessionId, timestamp, heartRate, power, cadence, speed, location } = data;

    // Сохраняем в PostgreSQL
    if (pgPool) {
      await pgPool.query(
        `INSERT INTO metrics (session_id, rider_id, timestamp, heart_rate, power, cadence, speed, latitude, longitude, altitude)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
        [
          sessionId,
          riderId,
          timestamp,
          heartRate || null,
          power || null,
          cadence || null,
          speed || null,
          location?.latitude || null,
          location?.longitude || null,
          location?.altitude || null,
        ]
      );
    }

    // Обновляем кэш
    updateMetricsCache(data);

    // Публикуем обработанные метрики
    const riderMetrics = ridersMetricsCache.get(riderId);
    if (riderMetrics && redisClient) {
      await redisClient.publish('metrics:processed', JSON.stringify(riderMetrics));
    }

    // Обновляем Redis для быстрого доступа
    if (redisClient) {
      await redisClient.setEx(
        `metrics:${riderId}`,
        60, // TTL 1 минута
        JSON.stringify(riderMetrics)
      );
    }

    console.log(`📊 Обработаны метрики для ${riderId}: HR=${heartRate}, P=${power}`);
  } catch (error) {
    console.error('❌ Ошибка обработки метрик:', error);
  }
}

/**
 * Обновление кэша метрик райдера
 */
function updateMetricsCache(data: SensorData) {
  const { riderId, sessionId, timestamp, heartRate, power, cadence, speed, location } = data;

  let riderMetrics = ridersMetricsCache.get(riderId);

  if (!riderMetrics || riderMetrics.sessionId !== sessionId) {
    // Создаём новую запись
    riderMetrics = {
      riderId,
      riderName: '', // Будет заполнено из сессии
      sessionId,
      currentMetrics: data,
      sessionStartTime: timestamp,
      history: [],
      alerts: [],
    };
  } else {
    // Обновляем текущие метрики
    riderMetrics.currentMetrics = data;

    // Добавляем в историю
    const historyPoint: MetricHistoryPoint = {
      timestamp,
      heartRate,
      power,
      cadence,
      speed,
      location,
    };

    riderMetrics.history.push(historyPoint);

    // Ограничиваем размер истории
    if (riderMetrics.history.length > HISTORY_SIZE) {
      riderMetrics.history.shift();
    }
  }

  ridersMetricsCache.set(riderId, riderMetrics);
}

/**
 * Получение агрегированных метрик за период
 */
async function getAggregatedMetrics(sessionId: string, period: number = 60) {
  if (!pgPool) {
    return null;
  }

  const client = await pgPool.connect();
  try {
    const result = await client.query(
      `SELECT 
        AVG(heart_rate) as avg_heart_rate,
        MAX(heart_rate) as max_heart_rate,
        AVG(power) as avg_power,
        MAX(power) as max_power,
        AVG(cadence) as avg_cadence,
        MAX(cadence) as max_cadence,
        AVG(speed) as avg_speed,
        MAX(speed) as max_speed,
        COUNT(*) as data_points
       FROM metrics
       WHERE session_id = $1
         AND timestamp >= $2
       GROUP BY session_id`,
      [sessionId, Date.now() - period * 1000]
    );

    return result.rows[0];
  } finally {
    client.release();
  }
}

/**
 * Подписка на канал метрик
 */
async function subscribeToMetrics() {
  if (!redisClient) {
    throw new Error('Redis не подключён');
  }

  await redisClient.subscribe('metrics:ingest', (message) => {
    const data: SensorData = JSON.parse(message);
    processMetrics(data);
  });

  console.log('✅ Подписан на канал metrics:ingest');
}

/**
 * HTTP API для получения метрик (опционально)
 */
import express from 'express';
import { createServer } from 'http';

const app = express();
app.use(express.json());

app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    cachedRiders: ridersMetricsCache.size,
    redisConnected: redisClient?.isReady ?? false,
    pgConnected: !!pgPool,
  });
});

app.get('/api/metrics/:riderId', async (req, res) => {
  try {
    const { riderId } = req.params;
    const metrics = ridersMetricsCache.get(riderId);
    
    if (!metrics) {
      return res.status(404).json({ error: 'Метрики не найдены' });
    }

    res.json(metrics);
  } catch (error) {
    res.status(500).json({ error: 'Внутренняя ошибка' });
  }
});

app.get('/api/metrics/:sessionId/aggregated', async (req, res) => {
  try {
    const { sessionId } = req.params;
    const period = parseInt(req.query.period as string) || 60;
    const metrics = await getAggregatedMetrics(sessionId, period);
    
    if (!metrics) {
      return res.status(404).json({ error: 'Метрики не найдены' });
    }

    res.json(metrics);
  } catch (error) {
    res.status(500).json({ error: 'Внутренняя ошибка' });
  }
});

/**
 * Запуск сервера
 */
const PORT = process.env.PORT || 8081;

async function startServer() {
  await connectRedis();
  await connectPostgres();
  await createTables();
  await subscribeToMetrics();

  const httpServer = createServer(app);
  httpServer.listen(PORT, () => {
    console.log(`🚀 Metrics Processor запущен на порту ${PORT}`);
  });
}

startServer().catch(console.error);
