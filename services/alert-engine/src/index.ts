import { createClient, RedisClientType } from 'redis';
import { Pool } from 'pg';
import { SensorData, Alert, AlertType, AlertSeverity, AlertThresholds, AlertMetadata } from '@ridepulse/shared-types';
import { v4 as uuidv4 } from 'uuid';
import dotenv from 'dotenv';

dotenv.config();

// Redis клиент
let redisClient: RedisClientType | null = null;

// PostgreSQL пул
let pgPool: Pool | null = null;

// Кэш порогов для райдеров
const thresholdsCache = new Map<string, AlertThresholds>();

// Кэш последних значений для определения превышения порогов
const lastValuesCache = new Map<string, {
  heartRate?: number;
  power?: number;
  cadence?: number;
  speed?: number;
  aboveThresholdSince?: Map<AlertType, number>;
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
 * Создание таблицы алертов
 */
async function createTables() {
  const client = await pgPool!.connect();
  try {
    await client.query(`
      CREATE TABLE IF NOT EXISTS alerts (
        id VARCHAR(36) PRIMARY KEY,
        rider_id VARCHAR(36) NOT NULL,
        session_id VARCHAR(36) NOT NULL,
        type VARCHAR(50) NOT NULL,
        message TEXT NOT NULL,
        severity VARCHAR(20) NOT NULL,
        timestamp BIGINT NOT NULL,
        acknowledged BOOLEAN DEFAULT false,
        acknowledged_by VARCHAR(36),
        acknowledged_at BIGINT,
        metadata JSONB,
        created_at TIMESTAMP DEFAULT NOW()
      );
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS alert_thresholds (
        rider_id VARCHAR(36) PRIMARY KEY,
        heart_rate_min INTEGER,
        heart_rate_max INTEGER,
        heart_rate_warning_threshold INTEGER,
        heart_rate_critical_threshold INTEGER,
        power_max INTEGER,
        power_warning_threshold INTEGER,
        cadence_min INTEGER,
        cadence_max INTEGER,
        speed_max INTEGER,
        updated_at TIMESTAMP DEFAULT NOW()
      );
    `);

    // Индексы
    await client.query(`CREATE INDEX IF NOT EXISTS idx_alerts_rider_id ON alerts(rider_id);`);
    await client.query(`CREATE INDEX IF NOT EXISTS idx_alerts_session_id ON alerts(session_id);`);
    await client.query(`CREATE INDEX IF NOT EXISTS idx_alerts_timestamp ON alerts(timestamp);`);
    await client.query(`CREATE INDEX IF NOT EXISTS idx_alerts_acknowledged ON alerts(acknowledged);`);

    console.log('✅ Таблицы алертов созданы');
  } finally {
    client.release();
  }
}

/**
 * Получение порогов для райдера
 */
async function getThresholds(riderId: string): Promise<AlertThresholds> {
  // Проверяем кэш
  if (thresholdsCache.has(riderId)) {
    return thresholdsCache.get(riderId)!;
  }

  // Загружаем из БД
  if (!pgPool) {
    return getDefaultThresholds(riderId);
  }

  const client = await pgPool.connect();
  try {
    const result = await client.query(
      'SELECT * FROM alert_thresholds WHERE rider_id = $1',
      [riderId]
    );

    if (result.rows.length > 0) {
      const row = result.rows[0];
      const thresholds: AlertThresholds = {
        riderId,
        heartRate: {
          min: row.heart_rate_min,
          max: row.heart_rate_max,
          warningThreshold: row.heart_rate_warning_threshold,
          criticalThreshold: row.heart_rate_critical_threshold,
        },
        power: {
          max: row.power_max,
          warningThreshold: row.power_warning_threshold,
        },
        cadence: {
          min: row.cadence_min,
          max: row.cadence_max,
        },
        speed: {
          max: row.speed_max,
        },
      };

      thresholdsCache.set(riderId, thresholds);
      return thresholds;
    }

    const defaults = getDefaultThresholds(riderId);
    thresholdsCache.set(riderId, defaults);
    return defaults;
  } finally {
    client.release();
  }
}

/**
 * Пороги по умолчанию
 */
function getDefaultThresholds(riderId: string): AlertThresholds {
  return {
    riderId,
    heartRate: {
      min: 40,
      max: 220,
      warningThreshold: 160,
      criticalThreshold: 180,
    },
    power: {
      max: 500,
      warningThreshold: 400,
    },
    cadence: {
      min: 40,
      max: 140,
    },
    speed: {
      max: 80,
    },
  };
}

/**
 * Проверка метрик и генерация алертов
 */
async function checkMetrics(data: SensorData) {
  const { riderId, sessionId, timestamp, heartRate, power, cadence, speed } = data;
  const thresholds = await getThresholds(riderId);

  const alerts: Alert[] = [];

  // Проверка пульса
  if (heartRate) {
    if (heartRate > thresholds.heartRate.criticalThreshold!) {
      alerts.push(createAlert(
        riderId,
        sessionId,
        AlertType.HIGH_HEART_RATE,
        AlertSeverity.CRITICAL,
        `Критический пульс: ${heartRate} bpm (порог: ${thresholds.heartRate.criticalThreshold})`,
        { threshold: thresholds.heartRate.criticalThreshold, currentValue: heartRate }
      ));
    } else if (heartRate > thresholds.heartRate.warningThreshold!) {
      alerts.push(createAlert(
        riderId,
        sessionId,
        AlertType.HIGH_HEART_RATE,
        AlertSeverity.WARNING,
        `Высокий пульс: ${heartRate} bpm (порог: ${thresholds.heartRate.warningThreshold})`,
        { threshold: thresholds.heartRate.warningThreshold, currentValue: heartRate }
      ));
    } else if (heartRate < thresholds.heartRate.min!) {
      alerts.push(createAlert(
        riderId,
        sessionId,
        AlertType.LOW_HEART_RATE,
        AlertSeverity.WARNING,
        `Низкий пульс: ${heartRate} bpm (минимум: ${thresholds.heartRate.min})`,
        { threshold: thresholds.heartRate.min, currentValue: heartRate }
      ));
    }
  }

  // Проверка мощности
  if (power && power > thresholds.power.warningThreshold!) {
    alerts.push(createAlert(
      riderId,
      sessionId,
      AlertType.HIGH_POWER,
      AlertSeverity.WARNING,
      `Высокая мощность: ${power} W (порог: ${thresholds.power.warningThreshold})`,
      { threshold: thresholds.power.warningThreshold, currentValue: power }
    ));
  }

  // Проверка каденса
  if (cadence) {
    if (cadence > thresholds.cadence.max!) {
      alerts.push(createAlert(
        riderId,
        sessionId,
        AlertType.HIGH_CADENCE,
        AlertSeverity.WARNING,
        `Высокий каденс: ${cadence} rpm (максимум: ${thresholds.cadence.max})`,
        { threshold: thresholds.cadence.max, currentValue: cadence }
      ));
    } else if (cadence < thresholds.cadence.min!) {
      alerts.push(createAlert(
        riderId,
        sessionId,
        AlertType.LOW_CADENCE,
        AlertSeverity.WARNING,
        `Низкий каденс: ${cadence} rpm (минимум: ${thresholds.cadence.min})`,
        { threshold: thresholds.cadence.min, currentValue: cadence }
      ));
    }
  }

  // Проверка скорости
  if (speed && speed > thresholds.speed.max!) {
    alerts.push(createAlert(
      riderId,
      sessionId,
      AlertType.HIGH_SPEED,
      AlertSeverity.WARNING,
      `Высокая скорость: ${speed.toFixed(1)} км/ч (максимум: ${thresholds.speed.max})`,
      { threshold: thresholds.speed.max, currentValue: speed }
    ));
  }

  // Сохраняем и публикуем алерты
  for (const alert of alerts) {
    await saveAlert(alert);
    if (redisClient) {
      await redisClient.publish('alerts:new', JSON.stringify(alert));
    }
    console.log(`🚨 Алерт: ${alert.message}`);
  }
}

/**
 * Создание алерта
 */
function createAlert(
  riderId: string,
  sessionId: string,
  type: AlertType,
  severity: AlertSeverity,
  message: string,
  metadata?: AlertMetadata
): Alert {
  return {
    id: uuidv4(),
    riderId,
    sessionId,
    type,
    message,
    severity,
    timestamp: Date.now(),
    acknowledged: false,
    metadata,
  };
}

/**
 * Сохранение алерта в БД
 */
async function saveAlert(alert: Alert) {
  if (!pgPool) return;

  const client = await pgPool.connect();
  try {
    await client.query(
      `INSERT INTO alerts (id, rider_id, session_id, type, message, severity, timestamp, metadata)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [
        alert.id,
        alert.riderId,
        alert.sessionId,
        alert.type,
        alert.message,
        alert.severity,
        alert.timestamp,
        JSON.stringify(alert.metadata),
      ]
    );
  } finally {
    client.release();
  }
}

/**
 * Подтверждение алерта
 */
async function acknowledgeAlert(alertId: string, acknowledgedBy: string) {
  if (!pgPool) return;

  const client = await pgPool.connect();
  try {
    await client.query(
      `UPDATE alerts 
       SET acknowledged = true, acknowledged_by = $1, acknowledged_at = $2
       WHERE id = $3`,
      [acknowledgedBy, Date.now(), alertId]
    );

    console.log(`✅ Алерт подтверждён: ${alertId}`);
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

  await redisClient.subscribe('metrics:processed', (message) => {
    const data: SensorData = JSON.parse(message).currentMetrics;
    checkMetrics(data);
  });

  console.log('✅ Подписан на канал metrics:processed');
}

/**
 * Подписка на подтверждение алертов
 */
async function subscribeToAlertAcknowledgments() {
  if (!redisClient) {
    throw new Error('Redis не подключён');
  }

  await redisClient.subscribe('alerts:acknowledge', (message) => {
    const data = JSON.parse(message);
    acknowledgeAlert(data.alertId, data.acknowledgedBy);
  });

  console.log('✅ Подписан на канал alerts:acknowledge');
}

/**
 * HTTP API
 */
import express from 'express';
import { createServer } from 'http';

const app = express();
app.use(express.json());

app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    cachedThresholds: thresholdsCache.size,
    redisConnected: redisClient?.isReady ?? false,
    pgConnected: !!pgPool,
  });
});

app.get('/api/alerts/:riderId', async (req, res) => {
  try {
    const { riderId } = req.params;
    const limit = parseInt(req.query.limit as string) || 50;
    
    if (!pgPool) {
      return res.status(503).json({ error: 'База данных недоступна' });
    }

    const client = await pgPool.connect();
    try {
      const result = await client.query(
        `SELECT * FROM alerts 
         WHERE rider_id = $1 
         ORDER BY timestamp DESC 
         LIMIT $2`,
        [riderId, limit]
      );

      res.json(result.rows);
    } finally {
      client.release();
    }
  } catch (error) {
    res.status(500).json({ error: 'Внутренняя ошибка' });
  }
});

app.put('/api/alerts/:alertId/acknowledge', async (req, res) => {
  try {
    const { alertId } = req.params;
    const { acknowledgedBy } = req.body;

    await acknowledgeAlert(alertId, acknowledgedBy);
    res.json({ success: true });
  } catch (error) {
    res.status(500).json({ error: 'Внутренняя ошибка' });
  }
});

app.get('/api/thresholds/:riderId', async (req, res) => {
  try {
    const { riderId } = req.params;
    const thresholds = await getThresholds(riderId);
    res.json(thresholds);
  } catch (error) {
    res.status(500).json({ error: 'Внутренняя ошибка' });
  }
});

app.put('/api/thresholds/:riderId', async (req, res) => {
  try {
    const { riderId } = req.params;
    const thresholds = req.body;

    if (!pgPool) {
      return res.status(503).json({ error: 'База данных недоступна' });
    }

    const client = await pgPool.connect();
    try {
      await client.query(
        `INSERT INTO alert_thresholds (rider_id, heart_rate_min, heart_rate_max, heart_rate_warning_threshold, heart_rate_critical_threshold, power_max, power_warning_threshold, cadence_min, cadence_max, speed_max)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
         ON CONFLICT (rider_id) DO UPDATE SET
           heart_rate_min = EXCLUDED.heart_rate_min,
           heart_rate_max = EXCLUDED.heart_rate_max,
           heart_rate_warning_threshold = EXCLUDED.heart_rate_warning_threshold,
           heart_rate_critical_threshold = EXCLUDED.heart_rate_critical_threshold,
           power_max = EXCLUDED.power_max,
           power_warning_threshold = EXCLUDED.power_warning_threshold,
           cadence_min = EXCLUDED.cadence_min,
           cadence_max = EXCLUDED.cadence_max,
           speed_max = EXCLUDED.speed_max,
           updated_at = NOW()`,
        [
          riderId,
          thresholds.heartRate?.min,
          thresholds.heartRate?.max,
          thresholds.heartRate?.warningThreshold,
          thresholds.heartRate?.criticalThreshold,
          thresholds.power?.max,
          thresholds.power?.warningThreshold,
          thresholds.cadence?.min,
          thresholds.cadence?.max,
          thresholds.speed?.max,
        ]
      );

      // Обновляем кэш
      thresholdsCache.set(riderId, thresholds);

      res.json({ success: true });
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
const PORT = process.env.PORT || 8082;

async function startServer() {
  await connectRedis();
  await connectPostgres();
  await createTables();
  await subscribeToMetrics();
  await subscribeToAlertAcknowledgments();

  const httpServer = createServer(app);
  httpServer.listen(PORT, () => {
    console.log(`🚀 Alert Engine запущен на порту ${PORT}`);
  });
}

startServer().catch(console.error);
