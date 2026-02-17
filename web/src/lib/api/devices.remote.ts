import { query, getRequestEvent } from '$app/server';
import { db } from '$lib/server/db';
import { device, agentSession, agentStep } from '$lib/server/db/schema';
import { eq, desc, and } from 'drizzle-orm';

export const listDevices = query(async () => {
	const { locals } = getRequestEvent();
	if (!locals.user) return [];

	const devices = await db
		.select()
		.from(device)
		.where(eq(device.userId, locals.user.id))
		.orderBy(desc(device.lastSeen));

	return devices.map((d) => ({
		deviceId: d.id,
		name: d.name,
		status: d.status,
		deviceInfo: d.deviceInfo,
		lastSeen: d.lastSeen?.toISOString() ?? d.createdAt.toISOString()
	}));
});

export const listDeviceSessions = query(async (deviceId: string) => {
	const { locals } = getRequestEvent();
	if (!locals.user) return [];

	const sessions = await db
		.select()
		.from(agentSession)
		.where(and(eq(agentSession.deviceId, deviceId), eq(agentSession.userId, locals.user.id)))
		.orderBy(desc(agentSession.startedAt))
		.limit(50);

	return sessions;
});

export const listSessionSteps = query(async (deviceId: string, sessionId: string) => {
	const { locals } = getRequestEvent();
	if (!locals.user) return [];

	// Verify session belongs to user
	const sess = await db
		.select()
		.from(agentSession)
		.where(and(eq(agentSession.id, sessionId), eq(agentSession.userId, locals.user.id)))
		.limit(1);

	if (sess.length === 0) return [];

	const steps = await db
		.select()
		.from(agentStep)
		.where(eq(agentStep.sessionId, sessionId))
		.orderBy(agentStep.stepNumber);

	return steps;
});
