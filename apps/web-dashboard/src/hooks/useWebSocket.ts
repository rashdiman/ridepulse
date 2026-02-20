'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import { wsClient } from '@/lib/websocket';
import { SensorData, RiderMetrics, Alert } from '@/types/sensor';

type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'error';

export function useWebSocket(url?: string, token?: string | null) {
  const [connectionState, setConnectionState] = useState<ConnectionState>('disconnected');
  const [error, setError] = useState<string | null>(null);
  
  const sensorDataRef = useRef<Map<string, SensorData>>(new Map());
  const ridersMetricsRef = useRef<Map<string, RiderMetrics>>(new Map());
  const alertsRef = useRef<Alert[]>([]);

  useEffect(() => {
    if (!token) {
      setConnectionState('disconnected');
      return;
    }

    const wsUrl = url || process.env.NEXT_PUBLIC_WS_URL || 'ws://localhost:8080/ws';
    
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

    wsClient.on('connection_state', handleConnectionState);
    wsClient.on('sensor_data', handleSensorData);
    wsClient.on('rider_metrics', handleRiderMetrics);
    wsClient.on('alert', handleAlert);

    return () => {
      wsClient.off('connection_state', handleConnectionState);
      wsClient.off('sensor_data', handleSensorData);
      wsClient.off('rider_metrics', handleRiderMetrics);
      wsClient.off('alert', handleAlert);
      wsClient.disconnect();
    };
  }, [url, token]);

  const getRiderMetrics = useCallback((riderId: string): RiderMetrics | undefined => {
    return ridersMetricsRef.current.get(riderId);
  }, []);

  const getAllRidersMetrics = useCallback((): RiderMetrics[] => {
    return Array.from(ridersMetricsRef.current.values());
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
