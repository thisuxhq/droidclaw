<script lang="ts">
	import { page } from '$app/state';

	const deviceId = page.params.deviceId!;

	let goal = $state('');
	let steps = $state<Array<{ step: number; action: string; reasoning: string }>>([]);
	let status = $state<'idle' | 'running' | 'completed' | 'failed'>('idle');

	async function submitGoal() {
		if (!goal.trim()) return;
		status = 'running';
		steps = [];

		const res = await fetch('/api/goals', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ deviceId, goal })
		});

		if (!res.ok) {
			status = 'failed';
			return;
		}
	}
</script>

<h2 class="mb-6 text-2xl font-bold">Device: {deviceId.slice(0, 8)}...</h2>

<div class="max-w-2xl">
	<div class="mb-8 rounded-lg border border-neutral-200 p-6">
		<h3 class="mb-3 font-semibold">Send a Goal</h3>
		<div class="flex gap-3">
			<input
				type="text"
				bind:value={goal}
				placeholder="e.g., Open YouTube and search for lofi beats"
				class="flex-1 rounded border border-neutral-300 px-3 py-2 text-sm focus:border-neutral-500 focus:outline-none"
				disabled={status === 'running'}
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

	{#if steps.length > 0}
		<div class="rounded-lg border border-neutral-200">
			<div class="border-b border-neutral-200 px-6 py-4">
				<h3 class="font-semibold">Steps</h3>
			</div>
			<div class="divide-y divide-neutral-100">
				{#each steps as step (step.step)}
					<div class="px-6 py-3">
						<p class="text-sm font-medium">Step {step.step}: {step.action}</p>
						<p class="text-sm text-neutral-500">{step.reasoning}</p>
					</div>
				{/each}
			</div>
		</div>
	{/if}

	{#if status === 'completed'}
		<p class="mt-4 text-sm text-green-600">Goal completed successfully.</p>
	{:else if status === 'failed'}
		<p class="mt-4 text-sm text-red-600">Goal failed. Please try again.</p>
	{/if}
</div>
