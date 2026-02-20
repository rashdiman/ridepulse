'use client';

import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
} from 'recharts';
import { MetricHistoryPoint } from '@/types/sensor';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';

interface MetricsChartProps {
  data: MetricHistoryPoint[];
  metrics: ('heartRate' | 'power' | 'cadence' | 'speed')[];
  title?: string;
}

const metricConfig = {
  heartRate: {
    label: 'Пульс',
    color: '#ef4444',
    unit: 'bpm',
    domain: [0, 220],
  },
  power: {
    label: 'Мощность',
    color: '#a855f7',
    unit: 'W',
    domain: [0, 500],
  },
  cadence: {
    label: 'Каденс',
    color: '#3b82f6',
    unit: 'rpm',
    domain: [0, 150],
  },
  speed: {
    label: 'Скорость',
    color: '#06b6d4',
    unit: 'км/ч',
    domain: [0, 80],
  },
};

export function MetricsChart({ data, metrics, title }: MetricsChartProps) {
  const chartData = data.map(point => ({
    ...point,
    time: format(new Date(point.timestamp), 'HH:mm:ss', { locale: ru }),
  }));

  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-white p-3 rounded-lg shadow-lg border border-gray-200">
          <p className="text-sm font-medium text-gray-900 mb-2">{label}</p>
          {payload.map((entry: any, index: number) => (
            <p
              key={index}
              className="text-sm"
              style={{ color: entry.color }}
            >
              {entry.name}: {entry.value} {metricConfig[entry.dataKey as keyof typeof metricConfig].unit}
            </p>
          ))}
        </div>
      );
    }
    return null;
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
      {title && (
        <h3 className="text-lg font-semibold text-gray-900 mb-4">{title}</h3>
      )}
      
      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={chartData}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
          <XAxis
            dataKey="time"
            stroke="#6b7280"
            fontSize={12}
            tickLine={false}
            axisLine={false}
          />
          <YAxis
            stroke="#6b7280"
            fontSize={12}
            tickLine={false}
            axisLine={false}
          />
          <Tooltip content={<CustomTooltip />} />
          <Legend
            verticalAlign="top"
            height={36}
            iconType="circle"
          />
          
          {/* Reference zones */}
          {metrics.includes('heartRate') && (
            <>
              <ReferenceLine
                y={140}
                stroke="#ef4444"
                strokeDasharray="3 3"
                label={{ value: '140', fill: '#ef4444', fontSize: 10 }}
                strokeOpacity={0.5}
              />
              <ReferenceLine
                y={180}
                stroke="#dc2626"
                strokeDasharray="3 3"
                label={{ value: '180', fill: '#dc2626', fontSize: 10 }}
                strokeOpacity={0.5}
              />
            </>
          )}

          {metrics.map(metric => (
            <Line
              key={metric}
              type="monotone"
              dataKey={metric}
              name={metricConfig[metric].label}
              stroke={metricConfig[metric].color}
              strokeWidth={2}
              dot={false}
              connectNulls={false}
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
