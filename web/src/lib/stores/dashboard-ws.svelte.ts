import { env } from '$env/dynamic/public';

const WS_URL = env.PUBLIC_SERVER_WS_URL || 'ws://localhost:8080';

export interface DashboardDevice {
	deviceId: string;
	name: string;
	status: 'online' | 'offline';
}

export interface StepEvent {
	sessionId: string;
	step: number;
	action: Record<string, unknown>;
	reasoning: string;
}

export interface GoalStartedEvent {
	sessionId: string;
	goal: string;
	deviceId: string;
}

export interface GoalCompletedEvent {
	sessionId: string;
	success: boolean;
	stepsUsed: number;
}

type MessageHandler = (msg: Record<string, unknown>) => void;

class DashboardWebSocket {
	private ws: WebSocket | null = null;
	private handlers = new Set<MessageHandler>();
	private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
	private sessionToken: string | null = null;

	connected = $state(false);

	connect(sessionToken: string) {
		this.sessionToken = sessionToken;
		this.doConnect();
	}

	private doConnect() {
		if (!this.sessionToken) return;
		if (this.ws?.readyState === WebSocket.OPEN) return;

		try {
			this.ws = new WebSocket(`${WS_URL}/ws/dashboard`);
		} catch {
			this.scheduleReconnect();
			return;
		}

		this.ws.onopen = () => {
			this.ws?.send(JSON.stringify({ type: 'auth', token: this.sessionToken }));
		};

		this.ws.onmessage = (event) => {
			try {
				const msg = JSON.parse(event.data) as Record<string, unknown>;
				if (msg.type === 'auth_ok') {
					this.connected = true;
					return;
				}
				if (msg.type === 'auth_error') {
					console.error('[DashboardWS] Auth failed:', msg.message);
					return;
				}
				for (const handler of this.handlers) {
					handler(msg);
				}
			} catch {
				// ignore parse errors
			}
		};

		this.ws.onclose = () => {
			this.connected = false;
			this.scheduleReconnect();
		};

		this.ws.onerror = () => {
			this.connected = false;
		};
	}

	private scheduleReconnect() {
		if (this.reconnectTimer) return;
		this.reconnectTimer = setTimeout(() => {
			this.reconnectTimer = null;
			this.doConnect();
		}, 3000);
	}

	subscribe(handler: MessageHandler) {
		this.handlers.add(handler);
		return () => this.handlers.delete(handler);
	}

	disconnect() {
		this.sessionToken = null;
		if (this.reconnectTimer) {
			clearTimeout(this.reconnectTimer);
			this.reconnectTimer = null;
		}
		if (this.ws) {
			this.ws.onclose = null;
			this.ws.close();
			this.ws = null;
		}
		this.connected = false;
	}
}

export const dashboardWs = new DashboardWebSocket();
