import { Client, type IFrame } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export type StompHandlers = {
  onConnect: () => void;
  onDisconnect: () => void;
  onMessage: (body: unknown) => void;
};

export function createStompClient(handlers: StompHandlers): Client {
  const client = new Client({
    webSocketFactory: () => {
      const wsUrl = `${window.location.protocol === 'https:' ? 'https' : 'http'}://${window.location.host}/ws/live`;
      return new SockJS(wsUrl) as unknown as WebSocket;
    },
    reconnectDelay: 2000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: (_frame: IFrame) => {
      client.subscribe('/topic/events', (msg) => {
        try {
          handlers.onMessage(JSON.parse(msg.body));
        } catch (e) {
          console.warn('[stomp] malformed message body', msg.body, e);
        }
      });
      handlers.onConnect();
    },
    onWebSocketClose: () => handlers.onDisconnect(),
    onStompError: (frame) => console.warn('[stomp] error frame', frame.headers, frame.body),
  });
  return client;
}
