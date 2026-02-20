'use client';

import { useEffect, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Polyline } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { RiderMetrics } from '@/types/sensor';

// Fix for default marker icon in Next.js
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

interface RiderMapProps {
  riders: RiderMetrics[];
  center?: [number, number];
  zoom?: number;
}

export function RiderMap({ riders, center = [55.7558, 37.6173], zoom = 13 }: RiderMapProps) {
  const [mapKey, setMapKey] = useState(0);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    // Force remount when riders change significantly
    if (riders.length > 0) {
      setMapKey(prev => prev + 1);
    }
  }, [riders.length]);

  if (!mounted) {
    return (
      <div className="bg-gray-100 rounded-xl h-96 flex items-center justify-center">
        <p className="text-gray-500">Загрузка карты...</p>
      </div>
    );
  }

  // Calculate center based on rider positions
  const ridersWithLocation = riders.filter(r => r.currentMetrics.location);
  let mapCenter = center;
  
  if (ridersWithLocation.length > 0) {
    const avgLat = ridersWithLocation.reduce((sum, r) => 
      sum + (r.currentMetrics.location?.latitude || 0), 0) / ridersWithLocation.length;
    const avgLng = ridersWithLocation.reduce((sum, r) => 
      sum + (r.currentMetrics.location?.longitude || 0), 0) / ridersWithLocation.length;
    mapCenter = [avgLat, avgLng];
  }

  // Create custom colored markers for each rider
  const createCustomIcon = (color: string) => {
    return L.divIcon({
      className: 'custom-marker',
      html: `<div style="
        background-color: ${color};
        width: 24px;
        height: 24px;
        border-radius: 50%;
        border: 3px solid white;
        box-shadow: 0 2px 4px rgba(0,0,0,0.3);
      "></div>`,
      iconSize: [24, 24],
      iconAnchor: [12, 12],
      popupAnchor: [0, -12],
    });
  };

  const riderColors = ['#ef4444', '#3b82f6', '#22c55e', '#f59e0b', '#8b5cf6'];

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
      <div className="p-4 border-b border-gray-200">
        <h3 className="font-semibold text-gray-900">Карта райдеров</h3>
        <p className="text-sm text-gray-500 mt-1">
          Отображено: {ridersWithLocation.length} из {riders.length} райдеров
        </p>
      </div>
      
      <MapContainer
        key={mapKey}
        center={mapCenter}
        zoom={zoom}
        style={{ height: '400px', width: '100%' }}
        className="z-0"
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        
        {ridersWithLocation.map((rider, index) => {
          const location = rider.currentMetrics.location!;
          const color = riderColors[index % riderColors.length];
          
          // Build path from history
          const pathPositions = rider.history
            .filter(h => rider.currentMetrics.location)
            .map(() => [location.latitude, location.longitude] as [number, number]);

          return (
            <div key={rider.riderId}>
              <Marker
                position={[location.latitude, location.longitude]}
                icon={createCustomIcon(color)}
              >
                <Popup>
                  <div className="min-w-48">
                    <h4 className="font-semibold text-gray-900 mb-2">
                      {rider.riderName}
                    </h4>
                    <div className="space-y-1 text-sm">
                      <p>
                        <span className="text-gray-500">Пульс:</span>{' '}
                        <span className="font-medium text-red-600">
                          {rider.currentMetrics.heartRate || '--'} bpm
                        </span>
                      </p>
                      <p>
                        <span className="text-gray-500">Мощность:</span>{' '}
                        <span className="font-medium text-purple-600">
                          {rider.currentMetrics.power || '--'} W
                        </span>
                      </p>
                      <p>
                        <span className="text-gray-500">Скорость:</span>{' '}
                        <span className="font-medium text-cyan-600">
                          {rider.currentMetrics.speed ? rider.currentMetrics.speed.toFixed(1) : '--'} км/ч
                        </span>
                      </p>
                    </div>
                  </div>
                </Popup>
              </Marker>
              
              {pathPositions.length > 1 && (
                <Polyline
                  positions={pathPositions}
                  color={color}
                  weight={3}
                  opacity={0.6}
                />
              )}
            </div>
          );
        })}
      </MapContainer>
    </div>
  );
}
