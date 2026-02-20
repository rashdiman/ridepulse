'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import dynamic from 'next/dynamic';
import { Activity, Users, AlertTriangle, Wifi, WifiOff, RefreshCw, LogOut } from 'lucide-react';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useAuth } from '@/context/AuthContext';
import { RiderMetrics, Alert } from '@/types/sensor';
import { RiderMetricsCard } from '@/components/RiderMetricsCard';
import { MetricsChart } from '@/components/MetricsChart';
import { AlertsPanel } from '@/components/AlertsPanel';

const RiderMap = dynamic(() => import('@/components/RiderMap').then((m) => m.RiderMap), {
  ssr: false,
});

export default function DashboardPage() {
  const router = useRouter();
  const { user, isAuthenticated, loading, accessToken, logout } = useAuth();
  const { error, getAllRidersMetrics, getRecentAlerts, acknowledgeAlert, isConnected } = useWebSocket(
    undefined,
    accessToken
  );

  const [selectedRider, setSelectedRider] = useState<RiderMetrics | null>(null);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [showMap] = useState(true);
  const [, setRefreshKey] = useState(0);

  useEffect(() => {
    if (!loading && !isAuthenticated) {
      router.replace('/login');
    }
  }, [loading, isAuthenticated, router]);

  useEffect(() => {
    const interval = setInterval(() => setRefreshKey((prev) => prev + 1), 1000);
    return () => clearInterval(interval);
  }, []);

  if (loading || !isAuthenticated) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center text-gray-600">
        Loading...
      </div>
    );
  }

  const riders = getAllRidersMetrics();
  const recentAlerts = getRecentAlerts(50);

  const handleAcknowledgeAlert = (alertId: string) => {
    acknowledgeAlert(alertId);
    setAlerts((prev) => prev.map((a) => (a.id === alertId ? { ...a, acknowledged: true } : a)));
  };

  const handleDismissAlert = (alertId: string) => {
    setAlerts((prev) => prev.filter((a) => a.id !== alertId));
  };

  const activeRidersCount = riders.length;
  const criticalAlertsCount = recentAlerts.filter((a) => !a.acknowledged && a.severity === 'critical').length;

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b border-gray-200 sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-gradient-to-br from-green-400 to-green-600 rounded-lg flex items-center justify-center">
                <Activity className="w-6 h-6 text-white" />
              </div>
              <div>
                <h1 className="text-xl font-bold text-gray-900">RidePulse</h1>
                <p className="text-xs text-gray-500">
                  Dashboard - {user?.name} ({user?.role})
                </p>
              </div>
            </div>

            <div className="flex items-center gap-6">
              <div className="hidden md:flex items-center gap-6 text-sm">
                <div className="flex items-center gap-2">
                  <Users className="w-4 h-4 text-gray-500" />
                  <span className="text-gray-600">{activeRidersCount} active</span>
                </div>
                <div className="flex items-center gap-2">
                  <AlertTriangle className={`w-4 h-4 ${criticalAlertsCount > 0 ? 'text-red-500' : 'text-gray-500'}`} />
                  <span className={`font-medium ${criticalAlertsCount > 0 ? 'text-red-600' : 'text-gray-600'}`}>
                    {criticalAlertsCount} critical
                  </span>
                </div>
              </div>

              <div className="flex items-center gap-2">
                {isConnected ? (
                  <div className="flex items-center gap-2 text-green-600">
                    <Wifi className="w-4 h-4" />
                    <span className="text-sm font-medium">Connected</span>
                  </div>
                ) : (
                  <div className="flex items-center gap-2 text-red-600">
                    <WifiOff className="w-4 h-4" />
                    <span className="text-sm font-medium">Disconnected</span>
                  </div>
                )}
              </div>

              <button
                onClick={() => setRefreshKey((prev) => prev + 1)}
                className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
                title="Refresh"
              >
                <RefreshCw className="w-5 h-5 text-gray-600" />
              </button>

              <button
                onClick={() => {
                  logout();
                  router.replace('/login');
                }}
                className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
                title="Logout"
              >
                <LogOut className="w-5 h-5 text-gray-600" />
              </button>
            </div>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {error && (
          <div className="mb-6 bg-red-50 border border-red-200 rounded-lg p-4">
            <p className="text-red-800 font-medium">{error}</p>
          </div>
        )}

        {riders.length === 0 ? (
          <div className="text-center py-16">
            <div className="w-24 h-24 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <Activity className="w-12 h-12 text-gray-400" />
            </div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">No active sessions</h2>
            <p className="text-gray-500">Waiting for riders to start sending data</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-1 space-y-4">
              <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-4">
                <h2 className="text-lg font-semibold text-gray-900 mb-4">Riders ({riders.length})</h2>
                <div className="space-y-3">
                  {riders.map((rider) => (
                    <RiderMetricsCard
                      key={rider.riderId}
                      metrics={rider}
                      onClick={() => setSelectedRider(rider)}
                    />
                  ))}
                </div>
              </div>
            </div>

            <div className="lg:col-span-2 space-y-6">
              {showMap && <RiderMap riders={riders} />}

              {selectedRider && (
                <div className="space-y-6">
                  <div className="flex items-center justify-between">
                    <h2 className="text-lg font-semibold text-gray-900">{selectedRider.riderName}</h2>
                    <button
                      onClick={() => setSelectedRider(null)}
                      className="text-sm text-gray-500 hover:text-gray-700"
                    >
                      Close
                    </button>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {selectedRider.history.length > 1 && (
                      <>
                        <MetricsChart data={selectedRider.history} metrics={['heartRate']} title="Heart Rate" />
                        <MetricsChart data={selectedRider.history} metrics={['power']} title="Power" />
                        <MetricsChart data={selectedRider.history} metrics={['cadence']} title="Cadence" />
                        <MetricsChart data={selectedRider.history} metrics={['speed']} title="Speed" />
                      </>
                    )}
                  </div>
                </div>
              )}

              {recentAlerts.length > 0 && (
                <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-4">
                  <h2 className="text-lg font-semibold text-gray-900 mb-4">Alerts</h2>
                  <AlertsPanel
                    alerts={recentAlerts}
                    onAcknowledge={handleAcknowledgeAlert}
                    onDismiss={handleDismissAlert}
                  />
                </div>
              )}
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
