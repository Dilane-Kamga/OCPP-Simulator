import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useConsoleStore } from '../consoleStore';
import type { ChargePoint, LiveEvent } from '../../types';

const sampleCp: ChargePoint = {
  chargePointId: 'BORNE_A',
  site: 'NEX_TOWER',
  vendor: 'Legrand',
  model: "Green'Up Control",
  serialNumber: 'LGR-NXT-001',
  firmwareVersion: '2.1.0',
  status: 'Available',
  online: true,
  lastHeartbeat: '2026-05-23T10:00:00Z',
  registeredAt: '2026-05-23T09:00:00Z',
  errorCode: 'NoError',
  connectors: [
    { connectorId: 1, status: 'Available', currentPowerKw: 0, currentAmps: 0, voltage: 230, temperatureCelsius: 22, totalEnergyKwh: 0, errorCode: 'NoError', blocked: false, blockedReason: null, blockedAt: null },
    { connectorId: 2, status: 'Available', currentPowerKw: 0, currentAmps: 0, voltage: 230, temperatureCelsius: 22, totalEnergyKwh: 0, errorCode: 'NoError', blocked: false, blockedReason: null, blockedAt: null },
  ],
};

function makeEvent(partial: Partial<LiveEvent>): LiveEvent {
  return {
    type: 'STATUS_CHANGE',
    chargePointId: 'BORNE_A',
    connectorId: 1,
    data: {},
    timestamp: '2026-05-23T10:00:00Z',
    ...partial,
  };
}

describe('consoleStore', () => {
  beforeEach(() => {
    useConsoleStore.getState().reset();
  });

  it('hydrate populates chargePoints keyed by id', () => {
    useConsoleStore.getState().hydrate([sampleCp]);
    expect(useConsoleStore.getState().chargePoints['BORNE_A']).toEqual(sampleCp);
  });

  it('applyEvent STATUS_CHANGE patches connector status by connectorId', () => {
    useConsoleStore.getState().hydrate([sampleCp]);
    useConsoleStore.getState().applyEvent(makeEvent({
      type: 'STATUS_CHANGE',
      connectorId: 2,
      data: { status: 'Charging' },
    }));
    const conn2 = useConsoleStore.getState().chargePoints['BORNE_A'].connectors.find(c => c.connectorId === 2)!;
    expect(conn2.status).toBe('Charging');
  });

  it('applyEvent METER_UPDATE pushes to powerHistory and trims to 60 points', () => {
    useConsoleStore.getState().hydrate([sampleCp]);
    for (let i = 0; i < 70; i++) {
      useConsoleStore.getState().applyEvent(makeEvent({
        type: 'METER_UPDATE',
        connectorId: 1,
        data: { readings: { 'Power.Active.Import': 7000 } },
        timestamp: new Date(Date.UTC(2026, 4, 23, 10, 0, i)).toISOString(),
      }));
    }
    expect(useConsoleStore.getState().powerHistory['BORNE_A'].length).toBe(60);
    expect(useConsoleStore.getState().powerHistory['BORNE_A'][0].kw).toBeCloseTo(7.0);
  });

  it('applyEvent METER_UPDATE updates the connector currentPowerKw/Amps/Voltage from readings', () => {
    useConsoleStore.getState().hydrate([sampleCp]);
    useConsoleStore.getState().applyEvent(makeEvent({
      type: 'METER_UPDATE',
      connectorId: 1,
      data: {
        readings: {
          'Power.Active.Import': 7200,
          'Current.Import': 31.3,
          'Voltage': 230.2,
        },
      },
    }));
    const conn1 = useConsoleStore.getState().chargePoints['BORNE_A'].connectors.find(c => c.connectorId === 1)!;
    expect(conn1.currentPowerKw).toBeCloseTo(7.2);
    expect(conn1.currentAmps).toBeCloseTo(31.3);
    expect(conn1.voltage).toBeCloseTo(230.2);
  });

  it('applyEvent FAULT sets errorCode', () => {
    useConsoleStore.getState().hydrate([sampleCp]);
    useConsoleStore.getState().applyEvent(makeEvent({
      type: 'FAULT',
      connectorId: 1,
      data: { errorCode: 'GroundFailure' },
    }));
    const conn1 = useConsoleStore.getState().chargePoints['BORNE_A'].connectors.find(c => c.connectorId === 1)!;
    expect(conn1.errorCode).toBe('GroundFailure');
  });

  it('eventsByCp keeps at most 50 entries per charge point, oldest dropped', () => {
    useConsoleStore.getState().hydrate([sampleCp]);
    for (let i = 0; i < 60; i++) {
      useConsoleStore.getState().applyEvent(makeEvent({ type: 'HEARTBEAT', data: { i } }));
    }
    expect(useConsoleStore.getState().eventsByCp['BORNE_A'].length).toBe(50);
    expect((useConsoleStore.getState().eventsByCp['BORNE_A'][0].data as any).i).toBe(59);
  });

  it('totalKw is the sum of currentPowerKw across all connectors', () => {
    const cp: ChargePoint = {
      ...sampleCp,
      connectors: [
        { ...sampleCp.connectors[0], currentPowerKw: 7.2 },
        { ...sampleCp.connectors[1], currentPowerKw: 3.5 },
      ],
    };
    useConsoleStore.getState().hydrate([cp]);
    expect(useConsoleStore.getState().totalKw()).toBeCloseTo(10.7);
  });

  it('event for unknown chargePointId triggers console.warn and is a no-op', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    useConsoleStore.getState().hydrate([sampleCp]);
    useConsoleStore.getState().applyEvent(makeEvent({ chargePointId: 'BORNE_X' }));
    expect(warn).toHaveBeenCalled();
    expect(useConsoleStore.getState().chargePoints['BORNE_X']).toBeUndefined();
    warn.mockRestore();
  });
});
