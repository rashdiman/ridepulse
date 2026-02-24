import express, { Request, Response, NextFunction } from 'express';
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { Pool } from 'pg';
import { User, UserRole, AuthTokens, JwtPayload, RegisterData, hasPermission, Permission } from '@ridepulse/shared-types';
import { v4 as uuidv4 } from 'uuid';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
const JWT_SECRET = process.env.JWT_SECRET || 'your-secret-key';
const JWT_EXPIRES_IN = '7d';

// PostgreSQL пул
let pgPool: Pool | null = null;

/**
 * Подключение к PostgreSQL
 */
async function connectPostgres(): Promise<void> {
  const nextPool = new Pool({
    host: process.env.PG_HOST || 'localhost',
    port: parseInt(process.env.PG_PORT || '5432', 10),
    database: process.env.PG_DATABASE || 'ridepulse',
    user: process.env.PG_USER || 'postgres',
    password: process.env.PG_PASSWORD || 'postgres',
    max: 20,
  });

  try {
    const client = await nextPool.connect();
    await client.query('SELECT 1');
    client.release();
    pgPool = nextPool;
    console.log('✅ PostgreSQL подключён');
  } catch (error) {
    await nextPool.end().catch(() => undefined);
    throw error;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function initializePostgres(retries = 30, delayMs = 2000): Promise<void> {
  for (let attempt = 1; attempt <= retries; attempt += 1) {
    try {
      await connectPostgres();
      await createTables();
      return;
    } catch (error) {
      const shouldRetry = attempt < retries;
      console.error(
        `❌ PostgreSQL init attempt ${attempt}/${retries} failed${shouldRetry ? ', retrying...' : ''}:`,
        error
      );

      if (!shouldRetry) {
        throw error;
      }

      await sleep(delayMs);
    }
  }
}
async function createTables() {
  const client = await pgPool!.connect();
  try {
    await client.query(`
      CREATE TABLE IF NOT EXISTS users (
        id VARCHAR(36) PRIMARY KEY,
        email VARCHAR(255) UNIQUE NOT NULL,
        password_hash VARCHAR(255) NOT NULL,
        name VARCHAR(255) NOT NULL,
        role VARCHAR(50) NOT NULL,
        team_id VARCHAR(36),
        avatar VARCHAR(500),
        created_at TIMESTAMP DEFAULT NOW(),
        updated_at TIMESTAMP DEFAULT NOW()
      );
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS teams (
        id VARCHAR(36) PRIMARY KEY,
        name VARCHAR(255) NOT NULL,
        description TEXT,
        created_at TIMESTAMP DEFAULT NOW()
      );
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS refresh_tokens (
        id VARCHAR(36) PRIMARY KEY,
        user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        token VARCHAR(500) NOT NULL,
        expires_at BIGINT NOT NULL,
        created_at TIMESTAMP DEFAULT NOW()
      );
    `);

    // Создаём админа по умолчанию
    const adminEmail = process.env.ADMIN_EMAIL || 'admin@ridepulse.com';
    const adminPassword = process.env.ADMIN_PASSWORD || 'admin123';
    
    const existingAdmin = await client.query(
      'SELECT id FROM users WHERE email = $1',
      [adminEmail]
    );

    if (existingAdmin.rows.length === 0) {
      const hashedPassword = await bcrypt.hash(adminPassword, 10);
      await client.query(
        `INSERT INTO users (id, email, password_hash, name, role)
         VALUES ($1, $2, $3, $4, $5)`,
        [uuidv4(), adminEmail, hashedPassword, 'Admin', UserRole.ADMIN]
      );
      console.log('✅ Админ создан:', adminEmail);
    }

    console.log('✅ Таблицы созданы');
  } finally {
    client.release();
  }
}

// Middleware
app.use(helmet());
app.use(cors({
  origin: process.env.CORS_ORIGIN || '*',
  credentials: true,
}));
app.use(express.json());

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 минут
  max: 100, // максимум 100 запросов
});
app.use('/api/', limiter);

/**
 * Аутентификация middleware
 */
interface AuthRequest extends Request {
  user?: JwtPayload;
}

function authenticateToken(req: AuthRequest, res: Response, next: NextFunction) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ error: 'Токен не предоставлён' });
  }

  try {
    const decoded = jwt.verify(token, JWT_SECRET) as JwtPayload;
    req.user = decoded;
    next();
  } catch (error) {
    return res.status(403).json({ error: 'Неверный токен' });
  }
}

/**
 * Проверка прав доступа
 */
function requirePermission(permission: Permission) {
  return (req: AuthRequest, res: Response, next: NextFunction) => {
    if (!req.user) {
      return res.status(401).json({ error: 'Не авторизован' });
    }

    if (!hasPermission(req.user.role, permission)) {
      return res.status(403).json({ error: 'Недостаточно прав' });
    }

    next();
  };
}

/**
 * Регистрация
 */
app.post('/api/auth/register', async (req: Request, res: Response) => {
  try {
    const { email, password, name, role, teamId }: RegisterData = req.body;

    // Валидация
    if (!email || !password || !name || !role) {
      return res.status(400).json({ error: 'Не все поля заполнены' });
    }

    if (!Object.values(UserRole).includes(role)) {
      return res.status(400).json({ error: 'Неверная роль' });
    }

    // Проверяем существование пользователя
    const existingUser = await pgPool!.query(
      'SELECT id FROM users WHERE email = $1',
      [email]
    );

    if (existingUser.rows.length > 0) {
      return res.status(400).json({ error: 'Пользователь уже существует' });
    }

    // Хешируем пароль
    const passwordHash = await bcrypt.hash(password, 10);

    // Создаём пользователя
    const userId = uuidv4();
    await pgPool!.query(
      `INSERT INTO users (id, email, password_hash, name, role, team_id)
       VALUES ($1, $2, $3, $4, $5, $6)`,
      [userId, email, passwordHash, name, role, teamId || null]
    );

    const user: User = {
      id: userId,
      email,
      name,
      role,
      teamId,
      createdAt: new Date(),
    };

    // Генерируем токены
    const accessToken = jwt.sign(
      { userId: user.id, email: user.email, role: user.role, teamId: user.teamId },
      JWT_SECRET,
      { expiresIn: JWT_EXPIRES_IN }
    );

    const refreshToken = uuidv4();
    const expiresAt = Date.now() + 30 * 24 * 60 * 60 * 1000; // 30 дней

    await pgPool!.query(
      `INSERT INTO refresh_tokens (id, user_id, token, expires_at)
       VALUES ($1, $2, $3, $4)`,
      [uuidv4(), userId, refreshToken, expiresAt]
    );

    const tokens: AuthTokens = {
      accessToken,
      refreshToken,
      expiresIn: 7 * 24 * 60 * 60, // 7 дней в секундах
    };

    res.status(201).json({ user, tokens });
  } catch (error) {
    console.error('Ошибка регистрации:', error);
    res.status(500).json({ error: 'Внутренняя ошибка' });
  }
});

/**
 * Логин
 */
app.post('/api/auth/login', async (req: Request, res: Response) => {
  try {
    const { email, password }: { email: string; password: string } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email и пароль обязательны' });
    }

    // Ищем пользователя
    const result = await pgPool!.query(
      'SELECT * FROM users WHERE email = $1',
      [email]
    );

    if (result.rows.length === 0) {
      return res.status(401).json({ error: 'Неверные учетные данные' });
    }

    const userRow = result.rows[0];
    const validPassword = await bcrypt.compare(password, userRow.password_hash);

    if (!validPassword) {
      return res.status(401).json({ error: 'Неверные учетные данные' });
    }

    const user: User = {
      id: userRow.id,
      email: userRow.email,
      name: userRow.name,
      role: userRow.role as UserRole,
      teamId: userRow.team_id,
      avatar: userRow.avatar,
      createdAt: userRow.created_at,
    };

    // Генерируем токены
    const accessToken = jwt.sign(
      { userId: user.id, email: user.email, role: user.role, teamId: user.teamId },
      JWT_SECRET,
      { expiresIn: JWT_EXPIRES_IN }
    );

    const refreshToken = uuidv4();
    const expiresAt = Date.now() + 30 * 24 * 60 * 60 * 1000; // 30 дней

    await pgPool!.query(
      `INSERT INTO refresh_tokens (id, user_id, token, expires_at)
       VALUES ($1, $2, $3, $4)`,
      [uuidv4(), user.id, refreshToken, expiresAt]
    );

    const tokens: AuthTokens = {
      accessToken,
      refreshToken,
      expiresIn: 7 * 24 * 60 * 60,
    };

    res.json({ user, tokens });
  } catch (error) {
    console.error('Ошибка логина:', error);
    res.status(500).json({ error: 'Внутренняя ошибка' });
  }
});

/**
 * Обновление токена
 */
app.post('/api/auth/refresh', async (req: Request, res: Response) => {
  try {
    const { refreshToken }: { refreshToken: string } = req.body;

    if (!refreshToken) {
      return res.status(400).json({ error: 'Refresh token обязателен' });
    }

    // Проверяем refresh token
    const result = await pgPool!.query(
      `SELECT rt.*, u.id as user_id, u.email, u.role, u.team_id 
       FROM refresh_tokens rt
       JOIN users u ON rt.user_id = u.id
       WHERE rt.token = $1 AND rt.expires_at > $2`,
      [refreshToken, Date.now()]
    );

    if (result.rows.length === 0) {
      return res.status(401).json({ error: 'Неверный или истёкший refresh token' });
    }

    const tokenRow = result.rows[0];

    // Генерируем новый access token
    const accessToken = jwt.sign(
      { userId: tokenRow.user_id, email: tokenRow.email, role: tokenRow.role, teamId: tokenRow.team_id },
      JWT_SECRET,
      { expiresIn: JWT_EXPIRES_IN }
    );

    res.json({ accessToken });
  } catch (error) {
    console.error('Ошибка обновления токена:', error);
    res.status(500).json({ error: 'Внутренняя ошибка' });
  }
});

/**
 * Получение текущего пользователя
 */
app.get('/api/auth/me', authenticateToken, async (req: AuthRequest, res: Response) => {
  try {
    const result = await pgPool!.query(
      'SELECT id, email, name, role, team_id, avatar, created_at FROM users WHERE id = $1',
      [req.user!.userId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Пользователь не найден' });
    }

    const userRow = result.rows[0];
    const user: User = {
      id: userRow.id,
      email: userRow.email,
      name: userRow.name,
      role: userRow.role as UserRole,
      teamId: userRow.team_id,
      avatar: userRow.avatar,
      createdAt: userRow.created_at,
    };

    res.json({ user });
  } catch (error) {
    res.status(500).json({ error: 'Внутренняя ошибка' });
  }
});

/**
 * Получение списка райдеров (для тренеров)
 */
app.get('/api/riders', authenticateToken, requirePermission('riders:view_all' as Permission), async (req: AuthRequest, res: Response) => {
  try {
    let query = 'SELECT id, email, name, role, team_id, avatar, created_at FROM users WHERE role = $1';
    const params: any[] = [UserRole.RIDER];

    // Если тренер, показываем только своей команды
    if (req.user!.role === UserRole.COACH && req.user!.teamId) {
      query += ' AND team_id = $2';
      params.push(req.user!.teamId);
    }

    const result = await pgPool!.query(query, params);
    res.json({ riders: result.rows });
  } catch (error) {
    res.status(500).json({ error: 'Внутренняя ошибка' });
  }
});

/**
 * Health check
 */
app.get('/health', async (req, res) => {
  try {
    if (!pgPool) {
      return res.status(503).json({
        status: 'degraded',
        pgConnected: false,
      });
    }

    await pgPool.query('SELECT 1');
    res.json({
      status: 'ok',
      pgConnected: true,
    });
  } catch (error) {
    res.status(503).json({
      status: 'degraded',
      pgConnected: false,
    });
  }
});

/**
 * Прокси к другим сервисам
 */
app.use('/api/sessions', authenticateToken, async (req: AuthRequest, res: Response) => {
  // Проксируем запрос к ingest-ws
  res.status(501).json({ error: 'Не реализовано' });
});

/**
 * Запуск сервера
 */
const PORT = process.env.PORT || 3000;

async function startServer() {
  await initializePostgres();

  app.listen(PORT, () => {
    console.log(`API Gateway started on port ${PORT}`);
    console.log(`Admin email: ${process.env.ADMIN_EMAIL || 'admin@ridepulse.com'}`);
    console.log(`Admin password: ${process.env.ADMIN_PASSWORD || 'admin123'}`);
  });
}

startServer().catch((error) => {
  console.error('Fatal startup error:', error);
  process.exit(1);
});

