<script lang="ts">
	import { listDevices } from '$lib/api/devices.remote';
	import { dashboardWs } from '$lib/stores/dashboard-ws.svelte';
	import { onMount } from 'svelte';

	const initialDevices = await listDevices();

	let devices = $state(
		initialDevices.map((d: Record<string, string>) => ({
			deviceId: d.deviceId,
			name: d.name,
			status: d.status as 'online' | 'offline',
			lastSeen: d.lastSeen
		}))
	);

	onMount(() => {
		const unsub = dashboardWs.subscribe((msg) => {
			if (msg.type === 'device_online') {
				const id = msg.deviceId as string;
				const name = msg.name as string;
				const existing = devices.find((d) => d.deviceId === id);
				if (existing) {
					existing.status = 'online';
					existing.lastSeen = new Date().toISOString();
					devices = [...devices];
				} else {
					devices = [
						{ deviceId: id, name, status: 'online', lastSeen: new Date().toISOString() },
						...devices
					];
				}
			} else if (msg.type === 'device_offline') {
				const id = msg.deviceId as string;
				const existing = devices.find((d) => d.deviceId === id);
				if (existing) {
					existing.status = 'offline';
					devices = [...devices];
				}
			}
		});
		return unsub;
	});
</script>

<h2 class="mb-6 text-2xl font-bold">Devices</h2>

{#if devices.length === 0}
	<div class="rounded-lg border border-neutral-200 p-8 text-center">
		<p class="text-neutral-500">No devices connected.</p>
		<p class="mt-2 text-sm text-neutral-400">
			Install the Android app, paste your API key, and your device will appear here.
		</p>
		<a href="/dashboard/api-keys" class="mt-4 inline-block text-sm text-blue-600 hover:underline">
			Create an API key
		</a>
	</div>
{:else}
	<div class="space-y-3">
		{#each devices as d (d.deviceId)}
			<a
				href="/dashboard/devices/{d.deviceId}"
				class="flex items-center justify-between rounded-lg border border-neutral-200 p-4 hover:border-neutral-400"
			>
				<div>
					<p class="font-medium">{d.name}</p>
					<p class="text-sm text-neutral-500">
						{d.status === 'online' ? 'Connected now' : `Last seen ${new Date(d.lastSeen).toLocaleString()}`}
					</p>
				</div>
				<span
					class="inline-block h-2 w-2 rounded-full {d.status === 'online'
						? 'bg-green-500'
						: 'bg-neutral-300'}"
				></span>
			</a>
		{/each}
	</div>
{/if}
