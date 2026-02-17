<script lang="ts">
	import { page } from '$app/state';
	import { listDeviceSessions } from '$lib/api/devices.remote';
	import { dashboardWs } from '$lib/stores/dashboard-ws.svelte';
	import { onMount } from 'svelte';

	const deviceId = page.params.deviceId!;

	// Goal input
	let goal = $state('');
	let status = $state<'idle' | 'running' | 'completed' | 'failed'>('idle');

	// Real-time steps from WebSocket
	let currentGoal = $state('');
	let steps = $state<Array<{ step: number; action: string; reasoning: string; result?: string }>>([]);

	// Session history from DB
	interface Session {
		id: string;
		goal: string;
		status: string;
		stepsUsed: number;
		startedAt: string;
		completedAt: string | null;
	}
	const initialSessions = await listDeviceSessions(deviceId);
	let sessions = $state<Session[]>(initialSessions as Session[]);

	async function submitGoal() {
		if (!goal.trim()) return;
		status = 'running';
		currentGoal = goal;
		steps = [];

		const res = await fetch('/api/goals', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ deviceId, goal })
		});

		if (!res.ok) {
			status = 'failed';
		}
	}

	onMount(() => {
		const unsub = dashboardWs.subscribe((msg) => {
			switch (msg.type) {
				case 'goal_started': {
					if (msg.deviceId === deviceId) {
						status = 'running';
						currentGoal = msg.goal as string;
						steps = [];
					}
					break;
				}
				case 'step': {
					const action = msg.action as Record<string, unknown>;
					const actionStr = action?.action
						? `${action.action}${action.coordinates ? `(${(action.coordinates as number[]).join(',')})` : ''}`
						: JSON.stringify(action);
					steps = [
						...steps,
						{
							step: msg.step as number,
							action: actionStr,
							reasoning: (msg.reasoning as string) ?? ''
						}
					];
					break;
				}
				case 'goal_completed': {
					const success = msg.success as boolean;
					status = success ? 'completed' : 'failed';
					// Refresh session history
					listDeviceSessions(deviceId).then((s) => {
						sessions = s as Session[];
					});
					break;
				}
			}
		});
		return unsub;
	});

	function formatTime(iso: string) {
		return new Date(iso).toLocaleString();
	}
</script>

<div class="mb-6 flex items-center gap-3">
	<a href="/dashboard/devices" class="text-neutral-400 hover:text-neutral-600">&larr;</a>
	<h2 class="text-2xl font-bold">Device: {deviceId.slice(0, 8)}...</h2>
</div>

<div class="max-w-3xl">
	<!-- Goal Input -->
	<div class="mb-8 rounded-lg border border-neutral-200 p-6">
		<h3 class="mb-3 font-semibold">Send a Goal</h3>
		<div class="flex gap-3">
			<input
				type="text"
				bind:value={goal}
				placeholder="e.g., Open YouTube and search for lofi beats"
				class="flex-1 rounded border border-neutral-300 px-3 py-2 text-sm focus:border-neutral-500 focus:outline-none"
				disabled={status === 'running'}
				onkeydown={(e) => e.key === 'Enter' && submitGoal()}
			/>
			<button
				onclick={submitGoal}
				disabled={status === 'running'}
				class="rounded bg-neutral-800 px-4 py-2 text-sm text-white hover:bg-neutral-700 disabled:opacity-50"
			>
				{status === 'running' ? 'Running...' : 'Run'}
			</button>
		</div>
	</div>

	<!-- Live Steps -->
	{#if steps.length > 0 || status === 'running'}
		<div class="mb-8 rounded-lg border border-neutral-200">
			<div class="flex items-center justify-between border-b border-neutral-200 px-6 py-4">
				<h3 class="font-semibold">
					{currentGoal ? `Goal: ${currentGoal}` : 'Current Run'}
				</h3>
				{#if status === 'running'}
					<span class="flex items-center gap-2 text-sm text-amber-600">
						<span class="inline-block h-2 w-2 animate-pulse rounded-full bg-amber-500"></span>
						Running
					</span>
				{:else if status === 'completed'}
					<span class="text-sm text-green-600">Completed</span>
				{:else if status === 'failed'}
					<span class="text-sm text-red-600">Failed</span>
				{/if}
			</div>
			{#if steps.length > 0}
				<div class="divide-y divide-neutral-100">
					{#each steps as s (s.step)}
						<div class="px-6 py-3">
							<div class="flex items-baseline gap-2">
								<span class="shrink-0 rounded bg-neutral-100 px-1.5 py-0.5 text-xs font-mono text-neutral-500">
									{s.step}
								</span>
								<span class="text-sm font-medium font-mono">{s.action}</span>
							</div>
							{#if s.reasoning}
								<p class="mt-1 text-sm text-neutral-500 pl-8">{s.reasoning}</p>
							{/if}
						</div>
					{/each}
				</div>
			{:else}
				<div class="px-6 py-4 text-sm text-neutral-400">Waiting for first step...</div>
			{/if}
		</div>
	{/if}

	<!-- Session History -->
	{#if sessions.length > 0}
		<div class="rounded-lg border border-neutral-200">
			<div class="border-b border-neutral-200 px-6 py-4">
				<h3 class="font-semibold">Session History</h3>
			</div>
			<div class="divide-y divide-neutral-100">
				{#each sessions as sess (sess.id)}
					<div class="px-6 py-3">
						<div class="flex items-center justify-between">
							<p class="text-sm font-medium">{sess.goal}</p>
							<span
								class="rounded px-2 py-0.5 text-xs {sess.status === 'completed'
									? 'bg-green-50 text-green-700'
									: sess.status === 'running'
										? 'bg-amber-50 text-amber-700'
										: 'bg-red-50 text-red-700'}"
							>
								{sess.status === 'completed' ? 'Success' : sess.status === 'running' ? 'Running' : 'Failed'}
							</span>
						</div>
						<p class="mt-1 text-xs text-neutral-400">
							{formatTime(sess.startedAt)} &middot; {sess.stepsUsed} steps
						</p>
					</div>
				{/each}
			</div>
		</div>
	{/if}
</div>
