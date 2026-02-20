export interface SensorData {
  riderId: string;
  sessionId: string;
  timestamp: number;
  heartRate?: number;           // bpm
  power?: number;               // watts
  cadence?: number;             // rpm
  speed?: number;               // km/h
  location?: LocationData;
}

export interface LocationData {
  latitude: number;
  longitude: number;
  altitude?: number;
  accuracy?: number;
}

export interface DeviceInfo {
  id: string;
  name?: string;
  type: SensorType;
  address: string;
}

export enum SensorType {
  HEART_RATE = 'HEART_RATE',
  POWER_METER = 'POWER_METER',
  SPEED_CADENCE = 'SPEED_CADENCE',
  SPEED_ONLY = 'SPEED_ONLY',
  CADENCE_ONLY = 'CADENCE_ONLY',
  UNKNOWN = 'UNKNOWN',
}

export interface Rider {
  id: string;
  name: string;
  team?: string;
  avatar?: string;
  status: 'online' | 'offline' | 'in_session';
  currentSession?: string;
}

export interface Session {
  id: string;
  riderId: string;
  riderName: string;
  startTime: number;
  endTime?: number;
  deviceInfo: DeviceInfo[];
  isActive: boolean;
}

export interface RiderMetrics {
  riderId: string;
  riderName: string;
  sessionId: string;
  currentMetrics: SensorData;
  sessionStartTime: number;
  history: MetricHistoryPoint[];
  alerts: Alert[];
}

export interface MetricHistoryPoint {
  timestamp: number;
  heartRate?: number;
  power?: number;
  cadence?: number;
  speed?: number;
}

export interface Alert {
  id: string;
  riderId: string;
  sessionId: string;
  type: 'high_heart_rate' | 'low_heart_rate' | 'high_power' | 'disconnection';
  message: string;
  severity: 'warning' | 'critical';
  timestamp: number;
  acknowledged: boolean;
}

export interface DashboardData {
  riders: Rider[];
  activeSessions: Map<string, RiderMetrics>;
  recentAlerts: Alert[];
}
