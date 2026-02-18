import { form, command, getRequestEvent } from '$app/server';
import { redirect } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import * as v from 'valibot';
import { activateLicenseSchema, activateCheckoutSchema } from '$lib/schema/license';

/** Forward a request to the DroidClaw server with internal auth */
async function serverFetch(path: string, body: Record<string, unknown>) {
	const { locals } = getRequestEvent();
	if (!locals.user) throw new Error('Not authenticated');

	const serverUrl = env.SERVER_URL || 'http://localhost:8080';
	const internalSecret = env.INTERNAL_SECRET || '';

	const res = await fetch(`${serverUrl}${path}`, {
		method: 'POST',
		headers: {
			'Content-Type': 'application/json',
			'x-internal-secret': internalSecret,
			'x-internal-user-id': locals.user.id
		},
		body: JSON.stringify(body)
	});

	const data = await res.json().catch(() => ({ error: 'Unknown error' }));
	if (!res.ok) throw new Error(data.error ?? `Error ${res.status}`);
	return data;
}

export const activateLicense = form(activateLicenseSchema, async (data) => {
	const { locals } = getRequestEvent();
	if (!locals.user) return;

	await serverFetch('/license/activate', { key: data.key });
	redirect(303, '/dashboard');
});

export const activateFromCheckout = command(
	v.object({ checkoutId: v.string() }),
	async ({ checkoutId }) => {
		const result = await serverFetch('/license/activate-checkout', { checkoutId });
		return result;
	}
);
