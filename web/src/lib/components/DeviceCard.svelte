<script lang="ts">
	import Icon from '@iconify/svelte';
	import { DEVICE_CARD_CLICK } from '$lib/analytics/events';

	interface Props {
		deviceId: string;
		name: string;
		status: 'online' | 'offline';
		model: string | null;
		manufacturer: string | null;
		androidVersion: string | null;
		screenWidth: number | null;
		screenHeight: number | null;
		batteryLevel: number | null;
		isCharging: boolean;
		lastSeen: string;
		lastGoal: { goal: string; status: string; startedAt: string } | null;
	}

	let {
		deviceId,
		name,
		status,
		model,
		manufacturer,
		androidVersion,
		batteryLevel,
		isCharging,
		screenWidth,
		screenHeight,
		lastSeen,
		lastGoal
	}: Props = $props();

	function relativeTime(iso: string) {
		const diff = Date.now() - new Date(iso).getTime();
		const mins = Math.floor(diff / 60000);
		if (mins < 1) return 'just now';
		if (mins < 60) return `${mins}m ago`;
		const hrs = Math.floor(mins / 60);
		if (hrs < 24) return `${hrs}h ago`;
		const days = Math.floor(hrs / 24);
		return `${days}d ago`;
	}

	function batteryIcon(level: number | null, charging: boolean): string {
		if (level === null || level < 0) return 'solar:battery-charge-bold-duotone';
		if (charging) return 'solar:battery-charge-bold-duotone';
		if (level > 75) return 'solar:battery-full-bold-duotone';
		if (level > 50) return 'solar:battery-full-bold-duotone';
		if (level > 25) return 'solar:battery-low-bold-duotone';
		return 'solar:battery-low-bold-duotone';
	}
</script>

<a
	href="/dashboard/devices/{deviceId}"
	data-umami-event={DEVICE_CARD_CLICK}
	data-umami-event-device={model ?? name}
	class="group flex min-h-[280px] flex-col rounded-[1.75rem] bg-white p-5 transition-all hover:bg-stone-50"
>
	<!-- Header: status + battery -->
	<div class="mb-4 flex items-center justify-between">
		<div class="flex items-center gap-1.5">
			<span
				class="inline-block h-2 w-2 rounded-full {status === 'online'
					? 'bg-emerald-500'
					: 'bg-stone-300'}"
			></span>
			<span class="text-xs font-medium {status === 'online' ? 'text-emerald-600' : 'text-stone-400'}">
				{status === 'online' ? 'Online' : 'Offline'}
			</span>
		</div>
		{#if batteryLevel !== null && batteryLevel >= 0}
			<div class="flex items-center gap-1 text-stone-400">
				<Icon
					icon={batteryIcon(batteryLevel, isCharging)}
					class="h-4 w-4 {batteryLevel <= 20 ? 'text-red-500' : ''}"
				/>
				<span class="text-xs {batteryLevel <= 20 ? 'text-red-500' : ''}">{batteryLevel}%</span>
			</div>
		{/if}
	</div>

	<!-- Device info -->
	<div class="mb-4 flex items-center gap-3">
		<div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full {status === 'online' ? 'bg-emerald-100' : 'bg-stone-100'}">
			<Icon icon="solar:smartphone-bold-duotone" class="h-5 w-5 {status === 'online' ? 'text-emerald-600' : 'text-stone-400'}" />
		</div>
		<div class="min-w-0">
			<p class="truncate text-sm font-semibold text-stone-900">{model ?? name}</p>
			{#if manufacturer}
				<p class="text-xs text-stone-400">{manufacturer}</p>
			{/if}
		</div>
	</div>

	<!-- Specs -->
	<div class="mb-4 flex flex-wrap gap-1.5">
		{#if androidVersion}
			<span class="rounded-full bg-stone-100 px-2 py-0.5 text-[11px] text-stone-500">
				Android {androidVersion}
			</span>
		{/if}
		{#if screenWidth && screenHeight}
			<span class="rounded-full bg-stone-100 px-2 py-0.5 text-[11px] text-stone-500">
				{screenWidth}&times;{screenHeight}
			</span>
		{/if}
	</div>

	<!-- Last goal -->
	<div class="mt-auto border-t border-stone-100 pt-3">
		{#if lastGoal}
			<p class="truncate text-xs text-stone-500">{lastGoal.goal}</p>
			<div class="mt-1 flex items-center justify-between">
				<span
					class="flex items-center gap-1 text-xs font-medium
						{lastGoal.status === 'completed' ? 'text-emerald-600'
						: lastGoal.status === 'running' ? 'text-amber-600'
						: lastGoal.status === 'scheduled' ? 'text-blue-600'
						: 'text-red-500'}"
				>
					<Icon
						icon={lastGoal.status === 'completed' ? 'solar:check-circle-bold-duotone'
							: lastGoal.status === 'running' ? 'solar:refresh-circle-bold-duotone'
							: lastGoal.status === 'scheduled' ? 'solar:clock-circle-bold-duotone'
							: 'solar:close-circle-bold-duotone'}
						class="h-3.5 w-3.5"
					/>
					{lastGoal.status === 'completed' ? 'Success'
						: lastGoal.status === 'running' ? 'Running'
						: lastGoal.status === 'scheduled' ? 'Scheduled'
						: 'Failed'}
				</span>
				<span class="text-[11px] text-stone-400">{relativeTime(lastGoal.startedAt)}</span>
			</div>
		{:else}
			<p class="text-xs text-stone-400">No goals yet</p>
		{/if}
	</div>
</a>
