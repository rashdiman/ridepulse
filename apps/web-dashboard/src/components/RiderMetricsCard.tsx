'use client';

import { RiderMetrics } from '@/types/sensor';
import { Heart, Activity, Gauge, Zap, MapPin } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { ru } from 'date-fns/locale';

interface RiderMetricsCardProps {
  metrics: RiderMetrics;
  onClick?: () => void;
}

export function RiderMetricsCard({ metrics, onClick }: RiderMetricsCardProps) {
  const { currentMetrics, sessionStartTime, alerts } = metrics;
  const sessionDuration = Date.now() - sessionStartTime;
  const unacknowledgedAlerts = alerts.filter(a => !a.acknowledged);

  return (
    <div
      onClick={onClick}
      className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 cursor-pointer hover:shadow-md transition-shadow"
    >
      {/* Заголовок */}
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 bg-gradient-to-br from-green-400 to-green-600 rounded-full flex items-center justify-center text-white font-bold text-lg">
            {metrics.riderName.charAt(0).toUpperCase()}
          </div>
          <div>
            <h3 className="font-semibold text-gray-900 text-lg">
              {metrics.riderName}
            </h3>
            <p className="text-sm text-gray-500">
              {formatDistanceToNow(new Date(sessionStartTime), { 
                addSuffix: true,
                locale: ru 
              })}
            </p>
          </div>
        </div>
        
        {unacknowledgedAlerts.length > 0 && (
          <div className="relative">
            <div className="absolute -top-1 -right-1 w-3 h-3 bg-red-500 rounded-full animate-pulse" />
            <div className="w-8 h-8 bg-red-100 rounded-full flex items-center justify-center">
              <span className="text-red-600 font-semibold text-sm">
                {unacknowledgedAlerts.length}
              </span>
            </div>
          </div>
        )}
      </div>

      {/* Метрики */}
      <div className="grid grid-cols-2 gap-4">
        {/* Пульс */}
        <div className="bg-red-50 rounded-lg p-3">
          <div className="flex items-center gap-2 mb-1">
            <Heart className="w-4 h-4 text-red-500" />
            <span className="text-xs text-red-600 font-medium">ПУЛЬС</span>
          </div>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-bold text-gray-900">
              {currentMetrics.heartRate || '--'}
            </span>
            <span className="text-xs text-gray-500">bpm</span>
          </div>
        </div>

        {/* Мощность */}
        <div className="bg-purple-50 rounded-lg p-3">
          <div className="flex items-center gap-2 mb-1">
            <Zap className="w-4 h-4 text-purple-500" />
            <span className="text-xs text-purple-600 font-medium">МОЩНОСТЬ</span>
          </div>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-bold text-gray-900">
              {currentMetrics.power || '--'}
            </span>
            <span className="text-xs text-gray-500">W</span>
          </div>
        </div>

        {/* Каденс */}
        <div className="bg-blue-50 rounded-lg p-3">
          <div className="flex items-center gap-2 mb-1">
            <Activity className="w-4 h-4 text-blue-500" />
            <span className="text-xs text-blue-600 font-medium">КАДЕНС</span>
          </div>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-bold text-gray-900">
              {currentMetrics.cadence || '--'}
            </span>
            <span className="text-xs text-gray-500">rpm</span>
          </div>
        </div>

        {/* Скорость */}
        <div className="bg-cyan-50 rounded-lg p-3">
          <div className="flex items-center gap-2 mb-1">
            <Gauge className="w-4 h-4 text-cyan-500" />
            <span className="text-xs text-cyan-600 font-medium">СКОРОСТЬ</span>
          </div>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-bold text-gray-900">
              {currentMetrics.speed ? currentMetrics.speed.toFixed(1) : '--'}
            </span>
            <span className="text-xs text-gray-500">км/ч</span>
          </div>
        </div>
      </div>

      {/* GPS позиция */}
      {currentMetrics.location && (
        <div className="mt-4 pt-4 border-t border-gray-100">
          <div className="flex items-center gap-2 text-sm text-gray-500">
            <MapPin className="w-4 h-4" />
            <span>
              {currentMetrics.location.latitude.toFixed(4)},{' '}
              {currentMetrics.location.longitude.toFixed(4)}
            </span>
          </div>
        </div>
      )}

      {/* Последний алерт */}
      {unacknowledgedAlerts.length > 0 && (
        <div className="mt-4 pt-4 border-t border-gray-100">
          <div className={`p-2 rounded-lg text-sm ${
            unacknowledgedAlerts[0].severity === 'critical' 
              ? 'bg-red-100 text-red-700' 
              : 'bg-yellow-100 text-yellow-700'
          }`}>
            {unacknowledgedAlerts[0].message}
          </div>
        </div>
      )}
    </div>
  );
}
