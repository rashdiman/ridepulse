"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.AlertSeverity = exports.AlertType = exports.SensorType = void 0;
var SensorType;
(function (SensorType) {
    SensorType["HEART_RATE"] = "HEART_RATE";
    SensorType["POWER_METER"] = "POWER_METER";
    SensorType["SPEED_CADENCE"] = "SPEED_CADENCE";
    SensorType["SPEED_ONLY"] = "SPEED_ONLY";
    SensorType["CADENCE_ONLY"] = "CADENCE_ONLY";
    SensorType["UNKNOWN"] = "UNKNOWN";
})(SensorType || (exports.SensorType = SensorType = {}));
var AlertType;
(function (AlertType) {
    AlertType["HIGH_HEART_RATE"] = "high_heart_rate";
    AlertType["LOW_HEART_RATE"] = "low_heart_rate";
    AlertType["HIGH_POWER"] = "high_power";
    AlertType["HIGH_CADENCE"] = "high_cadence";
    AlertType["LOW_CADENCE"] = "low_cadence";
    AlertType["HIGH_SPEED"] = "high_speed";
    AlertType["DISCONNECTION"] = "disconnection";
    AlertType["SENSOR_ERROR"] = "sensor_error";
    AlertType["CUSTOM"] = "custom";
})(AlertType || (exports.AlertType = AlertType = {}));
var AlertSeverity;
(function (AlertSeverity) {
    AlertSeverity["INFO"] = "info";
    AlertSeverity["WARNING"] = "warning";
    AlertSeverity["CRITICAL"] = "critical";
})(AlertSeverity || (exports.AlertSeverity = AlertSeverity = {}));
