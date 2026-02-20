import express from 'express';
import { createServer } from 'http';
import { Server as SocketIOServer, Socket } from 'socket.io';
import { createClient, RedisClientType } from 'redis';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { SensorData, UserRole, JwtPayload, Session } from '@ridepulse/shared-types';
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

// Redis клиент для pub/sub
let redisClient: RedisClientType | null = null;

// Хранилище активных сессий
const activeSessions = new Map<string, Session>();
// Хранилище подключённых клиентов
const connectedClients = new Map<string, { socket: Socket; userId: string; role: UserRole }>();

// JWT Secret
const JWT_SECRET = process.env.JWT_SECRET || 'your-secret-key';

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

    // Подписываемся на каналы для broadcast
    await redisClient.subscribe('metrics:processed', (message) => {
      io.to('coaches').emit('rider_metrics', JSON.parse(message));
    });

    await redisClient.subscribe('alerts:new', (message) => {
      io.emit('alert', JSON.parse(message));
    });
  } catch (error) {
    console.error('❌ Ошибка подключения к Redis:', error);
  }
}

/**
 * Верификация JWT токена
 */
function verifyToken(token: string): JwtPayload | null {
  try {
    const decoded = jwt.verify(token, JWT_SECRET) as JwtPayload;
    return decoded;
  } catch (error) {
    return null;
  }
}

/**
 * Middleware для аутентификации Socket.IO
 */
io.use(async (socket, next) => {
  try {
    const token = socket.handshake.auth.token || socket.handshake.headers.authorization?.replace('Bearer ', '');
    
    if (!token) {
      return next(new Error('Токен не предоставлён'));
    }

    const decoded = verifyToken(token);
    if (!decoded) {
      return next(new Error('Неверный токен'));
    }

    socket.data.userId = decoded.userId;
    socket.data.email = decoded.email;
    socket.data.role = decoded.role;
    socket.data.teamId = decoded.teamId;

    next();
  } catch (error) {
    next(new Error('Ошибка аутентификации'));
  }
});

/**
 * Обработка подключения клиента
 */
io.on('connection', async (socket: Socket) => {
  const userId = socket.data.userId;
  const role = socket.data.role as UserRole;
  const teamId = socket.data.teamId;

  console.log(`🔗 Клиент подключён: ${userId}, роль: ${role}`);

  // Сохраняем подключение
  connectedClients.set(socket.id, { socket, userId, role });

  // Тренеры и админы подписываются на обновления
  if (role === UserRole.COACH || role === UserRole.ADMIN) {
    socket.join('coaches');
    console.log(`👨‍🏫 Тренер подключён: ${userId}`);
  }

  // Отправляем список активных сессий
  if (role === UserRole.COACH || role === UserRole.ADMIN) {
    const sessions = Array.from(activeSessions.values());
    socket.emit('active_sessions', sessions);
  }

  /**
   * Приём метрик от райдера
   */
  socket.on('sensor_data', async (data: SensorData) => {
    try {
      if (role !== UserRole.RIDER) {
        console.warn(`⚠️ Несанкционированная попытка отправки метрик от ${role}: ${userId}`);
        return;
      }

      // Проверяем, что сессия активна
      const session = activeSessions.get(data.sessionId);
      if (!session || session.riderId !== userId) {
        console.warn(`⚠️ Неверная сессия: ${data.sessionId}`);
        return;
      }

      // Публикуем данные в Redis для обработки
      await redisClient?.publish('metrics:ingest', JSON.stringify(data));

      // Отправляем подтверждение
      socket.emit('data_received', { timestamp: data.timestamp });
    } catch (error) {
      console.error('❌ Ошибка обработки sensor_data:', error);
    }
  });

  /**
   * Создание сессии (вызывается через HTTP API, но здесь для наглядности)
   */
  socket.on('session_start', async (data: { riderId: string; deviceInfo: any[] }) => {
    try {
      if (role !== UserRole.RIDER) {
        return;
      }

      if (data.riderId !== userId) {
        return;
      }

      const sessionId = uuidv4();
      const session: Session = {
        id: sessionId,
        riderId: data.riderId,
        riderName: socket.data.email.split('@')[0], // Временно
        teamId: teamId,
        startTime: Date.now(),
        deviceInfo: data.deviceInfo,
        isActive: true,
      };

      activeSessions.set(sessionId, session);
      socket.join(`session:${sessionId}`);

      // Уведомляем тренеров
      io.to('coaches').emit('session_start', session);

      socket.emit('session_created', { sessionId });
      console.log(`✅ Сессия создана: ${sessionId}`);
    } catch (error) {
      console.error('❌ Ошибка создания сессии:', error);
    }
  });

  /**
   * Завершение сессии
   */
  socket.on('session_end', async (data: { sessionId: string }) => {
    try {
      if (role !== UserRole.RIDER) {
        return;
      }

      const session = activeSessions.get(data.sessionId);
      if (!session || session.riderId !== userId) {
        return;
      }

      session.endTime = Date.now();
      session.isActive = false;
      activeSessions.delete(data.sessionId);

      socket.leave(`session:${data.sessionId}`);

      // Уведомляем тренеров
      io.to('coaches').emit('session_end', session);

      socket.emit('session_ended', { sessionId: data.sessionId });
      console.log(`✅ Сессия завершена: ${data.sessionId}`);
    } catch (error) {
      console.error('❌ Ошибка завершения сессии:', error);
    }
  });

  /**
   * Подтверждение алерта (для тренеров)
   */
  socket.on('acknowledge_alert', async (data: { alertId: string }) => {
    try {
      if (role !== UserRole.COACH && role !== UserRole.ADMIN) {
        return;
      }

      // Публикуем в Redis для обработки
      await redisClient?.publish('alerts:acknowledge', JSON.stringify({
        alertId: data.alertId,
        acknowledgedBy: userId,
        acknowledgedAt: Date.now(),
      }));

      console.log(`✅ Алерт подтверждён: ${data.alertId} пользователем ${userId}`);
    } catch (error) {
      console.error('❌ Ошибка подтверждения алерта:', error);
    }
  });

  /**
   * Подписка на конкретного райдера (для тренеров)
   */
  socket.on('subscribe_rider', async (data: { riderId: string }) => {
    try {
      if (role !== UserRole.COACH && role !== UserRole.ADMIN) {
        return;
      }

      // Проверяем доступ к райдеру (та же команда или админ)
      if (role === UserRole.COACH && teamId) {
        const riderSessions = Array.from(activeSessions.values())
          .filter(s => s.riderId === data.riderId && s.teamId === teamId);
        
        if (riderSessions.length === 0) {
          socket.emit('error', { message: 'Нет доступа к райдеру' });
          return;
        }
      }

      socket.join(`rider:${data.riderId}`);
      console.log(`👀 ${userId} подписан на райдера: ${data.riderId}`);
    } catch (error) {
      console.error('❌ Ошибка подписки на райдера:', error);
    }
  });

  /**
   * Отписка от райдера
   */
  socket.on('unsubscribe_rider', async (data: { riderId: string }) => {
    socket.leave(`rider:${data.riderId}`);
    console.log(`👋 ${userId} отписан от райдера: ${data.riderId}`);
  });

  /**
   * Пинг-понг для проверки соединения
   */
  socket.on('ping', () => {
    socket.emit('pong', { timestamp: Date.now() });
  });

  /**
   * Обработка отключения
   */
  socket.on('disconnect', () => {
    connectedClients.delete(socket.id);
    console.log(`🔌 Клиент отключён: ${userId}`);
  });

  /**
   * Обработка ошибок
   */
  socket.on('error', (error) => {
    console.error(`❌ Ошибка сокета ${socket.id}:`, error);
  });
});

/**
 * HTTP endpoint для health check
 */
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    connectedClients: connectedClients.size,
    activeSessions: activeSessions.size,
    redisConnected: redisClient?.isReady ?? false,
  });
});

/**
 * HTTP endpoint для получения списка активных сессий
 */
app.get('/api/sessions/active', (req, res) => {
  const sessions = Array.from(activeSessions.values());
  res.json(sessions);
});

/**
 * Запуск сервера
 */
const PORT = process.env.PORT || 8080;

async function startServer() {
  await connectRedis();
  
  httpServer.listen(PORT, () => {
    console.log(`🚀 WebSocket сервер запущен на порту ${PORT}`);
    console.log(`📡 CORS origin: ${process.env.CORS_ORIGIN || '*'}`);
  });
}

startServer().catch(console.error);
