'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import { wsClient } from '@/lib/websocket';
import { SensorData, RiderMetrics, Alert, Session } from '@/types/sensor';

type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'error';

export function useWebSocket(url?: string, token?: string | null) {
  const [connectionState, setConnectionState] = useState<ConnectionState>('disconnected');
  const [error, setError] = useState<string | null>(null);
  
  const sensorDataRef = useRef<Map<string, SensorData>>(new Map());
  const ridersMetricsRef = useRef<Map<string, RiderMetrics>>(new Map());
  const alertsRef = useRef<Alert[]>([]);
  const activeSessionsRef = useRef<Map<string, Session>>(new Map());

  useEffect(() => {
    if (!token) {
      setConnectionState('disconnected');
      return;
    }

    const runtimeWsUrl =
      typeof window !== 'undefined'
        ? `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.hostname}:8080`
        : 'ws://localhost:8080';
    const wsUrl = url || runtimeWsUrl;
    
    setConnectionState('connecting');
    wsClient.connect(wsUrl, token || undefined);

    const handleConnectionState = (data: { state: string; message?: string }) => {
      setConnectionState(data.state as ConnectionState);
      if (data.message) {
        setError(data.message);
      } else {
        setError(null);
      }
    };

    const handleSensorData = (data: SensorData) => {
      sensorDataRef.current.set(data.riderId, data);
    };

    const handleRiderMetrics = (data: RiderMetrics) => {
      ridersMetricsRef.current.set(data.riderId, data);
    };

    const handleAlert = (alert: Alert) => {
      alertsRef.current = [alert, ...alertsRef.current].slice(0, 100);
    };

    const toSession = (payload: any): Session | null => {
      if (!payload || typeof payload !== 'object') return null;
      if (!payload.id || !payload.riderId) return null;

      return {
        id: String(payload.id),
        riderId: String(payload.riderId),
        riderName: payload.riderName ? String(payload.riderName) : String(payload.riderId),
        startTime: Number(payload.startTime || Date.now()),
        endTime: payload.endTime ? Number(payload.endTime) : undefined,
        deviceInfo: Array.isArray(payload.deviceInfo) ? payload.deviceInfo : [],
        isActive: payload.isActive !== false,
      };
    };

    const handleActiveSessions = (payload: any) => {
      const next = new Map<string, Session>();
      const list = Array.isArray(payload) ? payload : [payload];
      list.forEach((item) => {
        const parsed = toSession(item);
        if (parsed) next.set(parsed.id, parsed);
      });
      activeSessionsRef.current = next;
    };

    const handleSessionStart = (payload: any) => {
      const parsed = toSession(payload);
      if (parsed) {
        activeSessionsRef.current.set(parsed.id, parsed);
      }
    };

    const handleSessionEnd = (payload: any) => {
      const parsed = toSession(payload);
      if (parsed) {
        activeSessionsRef.current.delete(parsed.id);
      }
    };

    wsClient.on('connection_state', handleConnectionState);
    wsClient.on('sensor_data', handleSensorData);
    wsClient.on('rider_metrics', handleRiderMetrics);
    wsClient.on('alert', handleAlert);
    wsClient.on('active_sessions', handleActiveSessions);
    wsClient.on('session_start', handleSessionStart);
    wsClient.on('session_end', handleSessionEnd);

    return () => {
      wsClient.off('connection_state', handleConnectionState);
      wsClient.off('sensor_data', handleSensorData);
      wsClient.off('rider_metrics', handleRiderMetrics);
      wsClient.off('alert', handleAlert);
      wsClient.off('active_sessions', handleActiveSessions);
      wsClient.off('session_start', handleSessionStart);
      wsClient.off('session_end', handleSessionEnd);
      wsClient.disconnect();
    };
  }, [url, token]);

  const getRiderMetrics = useCallback((riderId: string): RiderMetrics | undefined => {
    return ridersMetricsRef.current.get(riderId);
  }, []);

  const getAllRidersMetrics = useCallback((): RiderMetrics[] => {
    const result = new Map<string, RiderMetrics>();

    activeSessionsRef.current.forEach((session) => {
      const riderMetrics = ridersMetricsRef.current.get(session.riderId);
      if (riderMetrics) {
        result.set(session.riderId, riderMetrics);
        return;
      }

      const riderAlerts = alertsRef.current.filter(
        (alert) => alert.riderId === session.riderId && alert.sessionId === session.id
      );

      result.set(session.riderId, {
        riderId: session.riderId,
        riderName: session.riderName || session.riderId,
        sessionId: session.id,
        currentMetrics: {
          riderId: session.riderId,
          sessionId: session.id,
          timestamp: Date.now(),
        },
        sessionStartTime: session.startTime,
        history: [],
        alerts: riderAlerts,
      });
    });

    ridersMetricsRef.current.forEach((metrics, riderId) => {
      if (!result.has(riderId)) {
        result.set(riderId, metrics);
      }
    });

    return Array.from(result.values());
  }, []);

  const getRecentAlerts = useCallback((limit = 20): Alert[] => {
    return alertsRef.current.slice(0, limit);
  }, []);

  const acknowledgeAlert = useCallback((alertId: string) => {
    alertsRef.current = alertsRef.current.map(alert =>
      alert.id === alertId ? { ...alert, acknowledged: true } : alert
    );
    wsClient.send('acknowledge_alert', { alertId });
  }, []);

  return {
    connectionState,
    error,
    getRiderMetrics,
    getAllRidersMetrics,
    getRecentAlerts,
    acknowledgeAlert,
    isConnected: connectionState === 'connected',
  };
}
