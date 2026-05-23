import type { SiteId } from './theme/siteTheme';

export type ChargePointStatus =
  | 'Available' | 'Preparing' | 'Charging' | 'Faulted' | 'Unavailable' | 'Reserved' | 'SuspendedEV' | 'SuspendedEVSE' | 'Finishing';

export type ConnectorStatus = ChargePointStatus;

export type Connector = {
  connectorId: number;            // OCPP value (1, 2)
  status: ConnectorStatus;
  currentPowerKw: number | null;
  currentAmps: number | null;
  voltage: number | null;
  temperatureCelsius: number | null;
  totalEnergyKwh: number | null;
  errorCode: string | null;
  blocked: boolean;
  blockedReason: string | null;
  blockedAt: string | null;
};

export type ChargePoint = {
  chargePointId: string;
  site: SiteId | null;
  vendor: string | null;
  model: string | null;
  serialNumber: string | null;
  firmwareVersion: string | null;
  status: ChargePointStatus;
  online: boolean;
  lastHeartbeat: string | null;
  registeredAt: string | null;
  errorCode: string | null;
  connectors: Connector[];
};

export type LiveEventType =
  | 'CHARGE_POINT_CONNECTED' | 'CHARGE_POINT_DISCONNECTED'
  | 'STATUS_CHANGE' | 'SESSION_STARTED' | 'SESSION_STOPPED'
  | 'METER_UPDATE' | 'FAULT' | 'HEARTBEAT';

export type LiveEvent = {
  type: LiveEventType;
  chargePointId: string;
  connectorId: number | null;
  data: Record<string, unknown>;
  timestamp: string;
};

export type PowerPoint = { t: number; kw: number };  // t = epoch ms

export type WsState = 'CONNECTING' | 'CONNECTED' | 'RECONNECTING';

export type ScenarioName =
  | 'START_ALL' | 'START_ONE' | 'FAULT_ONE' | 'STOP_ALL' | 'DISCONNECT_ONE' | 'PEAK_LOAD' | 'RESET_ALL';
