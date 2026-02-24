/**
 * Типы данных для метрик сенсоров
 */
export interface SensorData {
    riderId: string;
    sessionId: string;
    timestamp: number;
    heartRate?: number;
    power?: number;
    cadence?: number;
    speed?: number;
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
export declare enum SensorType {
    HEART_RATE = "HEART_RATE",
    POWER_METER = "POWER_METER",
    SPEED_CADENCE = "SPEED_CADENCE",
    SPEED_ONLY = "SPEED_ONLY",
    CADENCE_ONLY = "CADENCE_ONLY",
    UNKNOWN = "UNKNOWN"
}
/**
 * Сессия тренировки
 */
export interface Session {
    id: string;
    riderId: string;
    riderName: string;
    teamId?: string;
    startTime: number;
    endTime?: number;
    deviceInfo: DeviceInfo[];
    isActive: boolean;
    metadata?: SessionMetadata;
}
export interface SessionMetadata {
    distance?: number;
    averageHeartRate?: number;
    maxHeartRate?: number;
    averagePower?: number;
    maxPower?: number;
    averageSpeed?: number;
    maxSpeed?: number;
    totalEnergy?: number;
    elevationGain?: number;
}
/**
 * Метрики райдера с историей
 */
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
    location?: LocationData;
}
/**
 * Алерты
 */
export interface Alert {
    id: string;
    riderId: string;
    sessionId: string;
    type: AlertType;
    message: string;
    severity: AlertSeverity;
    timestamp: number;
    acknowledged: boolean;
    acknowledgedBy?: string;
    acknowledgedAt?: number;
    metadata?: AlertMetadata;
}
export declare enum AlertType {
    HIGH_HEART_RATE = "high_heart_rate",
    LOW_HEART_RATE = "low_heart_rate",
    HIGH_POWER = "high_power",
    HIGH_CADENCE = "high_cadence",
    LOW_CADENCE = "low_cadence",
    HIGH_SPEED = "high_speed",
    DISCONNECTION = "disconnection",
    SENSOR_ERROR = "sensor_error",
    CUSTOM = "custom"
}
export declare enum AlertSeverity {
    INFO = "info",
    WARNING = "warning",
    CRITICAL = "critical"
}
export interface AlertMetadata {
    threshold?: number;
    currentValue?: number;
    duration?: number;
}
/**
 * Пороги алертов для райдера
 */
export interface AlertThresholds {
    riderId: string;
    heartRate: {
        min?: number;
        max?: number;
        warningThreshold?: number;
        criticalThreshold?: number;
    };
    power: {
        max?: number;
        warningThreshold?: number;
    };
    cadence: {
        min?: number;
        max?: number;
    };
    speed: {
        max?: number;
    };
}
