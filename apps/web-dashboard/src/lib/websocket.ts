import { io, Socket } from 'socket.io-client';
import { SensorData, RiderMetrics, Alert } from '@/types/sensor';

class WebSocketClient {
  private socket: Socket | null = null;
  private listeners: Map<string, Set<(data: any) => void>> = new Map();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000;

  connect(url: string, token?: string) {
    if (this.socket?.connected) {
      console.log('WebSocket уже подключён');
      return;
    }

    console.log('Подключение к WebSocket:', url);

    this.socket = io(url, {
      transports: ['websocket', 'polling'],
      reconnection: true,
      reconnectionDelay: this.reconnectDelay,
      reconnectionAttempts: this.maxReconnectAttempts,
      auth: token ? { token } : undefined,
    });

    this.socket.on('connect', () => {
      console.log('WebSocket подключён:', this.socket?.id);
      this.reconnectAttempts = 0;
      this.emit('connection_state', { state: 'connected' });
    });

    this.socket.on('disconnect', (reason) => {
      console.log('WebSocket отключён:', reason);
      this.emit('connection_state', { state: 'disconnected', reason });
    });

    this.socket.on('connect_error', (error) => {
      console.error('Ошибка подключения WebSocket:', error);
      this.reconnectAttempts++;
      
      if (this.reconnectAttempts >= this.maxReconnectAttempts) {
        this.emit('connection_state', { 
          state: 'error', 
          message: 'Не удалось подключиться к серверу' 
        });
      }
    });

    // Обработка сообщений от сервера
    this.socket.on('sensor_data', (data: SensorData) => {
      this.emit('sensor_data', data);
    });

    this.socket.on('rider_metrics', (data: RiderMetrics) => {
      this.emit('rider_metrics', data);
    });

    this.socket.on('alert', (alert: Alert) => {
      this.emit('alert', alert);
    });

    this.socket.on('session_start', (data: any) => {
      this.emit('session_start', data);
    });

    this.socket.on('session_end', (data: any) => {
      this.emit('session_end', data);
    });

    this.socket.on('active_sessions', (data: any) => {
      this.emit('active_sessions', data);
    });
  }

  disconnect() {
    if (this.socket) {
      this.socket.disconnect();
      this.socket = null;
      console.log('WebSocket отключён');
    }
  }

  on(event: string, callback: (data: any) => void) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event)!.add(callback);
  }

  off(event: string, callback: (data: any) => void) {
    const eventListeners = this.listeners.get(event);
    if (eventListeners) {
      eventListeners.delete(callback);
    }
  }

  private emit(event: string, data: any) {
    const eventListeners = this.listeners.get(event);
    if (eventListeners) {
      eventListeners.forEach(callback => callback(data));
    }
  }

  send(event: string, data: any) {
    if (this.socket?.connected) {
      this.socket.emit(event, data);
    } else {
      console.warn('WebSocket не подключён, невозможно отправить:', event);
    }
  }

  isConnected(): boolean {
    return this.socket?.connected ?? false;
  }
}

// Singleton instance
export const wsClient = new WebSocketClient();
