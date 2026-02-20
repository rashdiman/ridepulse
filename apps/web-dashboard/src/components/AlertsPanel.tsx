'use client';

import { Alert } from '@/types/sensor';
import { AlertTriangle, CheckCircle, X, Bell } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { ru } from 'date-fns/locale';

interface AlertsPanelProps {
  alerts: Alert[];
  onAcknowledge?: (alertId: string) => void;
  onDismiss?: (alertId: string) => void;
}

export function AlertsPanel({ alerts, onAcknowledge, onDismiss }: AlertsPanelProps) {
  const activeAlerts = alerts.filter(a => !a.acknowledged);
  const dismissedAlerts = alerts.filter(a => a.acknowledged);

  return (
    <div className="space-y-4">
      {/* Активные алерты */}
      {activeAlerts.length > 0 && (
        <div className="space-y-2">
          <div className="flex items-center gap-2 mb-3">
            <Bell className="w-5 h-5 text-red-500 animate-pulse" />
            <h3 className="font-semibold text-gray-900">Активные ({activeAlerts.length})</h3>
          </div>
          
          {activeAlerts.map(alert => (
            <AlertCard
              key={alert.id}
              alert={alert}
              onAcknowledge={onAcknowledge}
            />
          ))}
        </div>
      )}

      {/* Отклонённые алерты */}
      {dismissedAlerts.length > 0 && (
        <div className="space-y-2">
          <details className="group">
            <summary className="flex items-center gap-2 cursor-pointer list-none text-gray-600 hover:text-gray-900">
              <CheckCircle className="w-5 h-5" />
              <span className="font-medium">Просмотренные ({dismissedAlerts.length})</span>
              <span className="text-xs text-gray-400">
                (развернуть)
              </span>
            </summary>
            
            <div className="mt-3 space-y-2 pl-7">
              {dismissedAlerts.map(alert => (
                <AlertCard
                  key={alert.id}
                  alert={alert}
                  dismissed
                  onDismiss={onDismiss}
                />
              ))}
            </div>
          </details>
        </div>
      )}

      {alerts.length === 0 && (
        <div className="text-center py-8">
          <CheckCircle className="w-12 h-12 text-green-500 mx-auto mb-3 opacity-50" />
          <p className="text-gray-500">Нет активных алертов</p>
        </div>
      )}
    </div>
  );
}

interface AlertCardProps {
  alert: Alert;
  dismissed?: boolean;
  onAcknowledge?: (alertId: string) => void;
  onDismiss?: (alertId: string) => void;
}

function AlertCard({ alert, dismissed, onAcknowledge, onDismiss }: AlertCardProps) {
  const severityColors = {
    critical: {
      bg: 'bg-red-50',
      border: 'border-red-200',
      icon: 'text-red-500',
      title: 'text-red-900',
      message: 'text-red-700',
    },
    warning: {
      bg: 'bg-yellow-50',
      border: 'border-yellow-200',
      icon: 'text-yellow-500',
      title: 'text-yellow-900',
      message: 'text-yellow-700',
    },
  };

  const colors = severityColors[alert.severity];

  return (
    <div
      className={`${colors.bg} ${colors.border} border rounded-lg p-4 ${
        dismissed ? 'opacity-60' : ''
      }`}
    >
      <div className="flex items-start gap-3">
        <AlertTriangle className={`w-5 h-5 ${colors.icon} flex-shrink-0 mt-0.5`} />
        
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2">
            <div>
              <p className={`font-medium ${colors.title} text-sm`}>
                {getAlertTypeLabel(alert.type)}
              </p>
              <p className={`${colors.message} text-sm mt-1`}>
                {alert.message}
              </p>
              <p className="text-xs text-gray-500 mt-2">
                {formatDistanceToNow(new Date(alert.timestamp), {
                  addSuffix: true,
                  locale: ru
                })}
              </p>
            </div>
            
            <div className="flex gap-1">
              {!dismissed && onAcknowledge && (
                <button
                  onClick={() => onAcknowledge(alert.id)}
                  className="p-1 hover:bg-white/50 rounded transition-colors"
                  title="Подтвердить"
                >
                  <CheckCircle className="w-4 h-4 text-gray-600" />
                </button>
              )}
              {dismissed && onDismiss && (
                <button
                  onClick={() => onDismiss(alert.id)}
                  className="p-1 hover:bg-white/50 rounded transition-colors"
                  title="Удалить"
                >
                  <X className="w-4 h-4 text-gray-600" />
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function getAlertTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    high_heart_rate: 'Высокий пульс',
    low_heart_rate: 'Низкий пульс',
    high_power: 'Высокая мощность',
    disconnection: 'Потеря связи',
  };
  return labels[type] || type;
}
