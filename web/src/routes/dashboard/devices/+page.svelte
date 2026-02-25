<script lang="ts">
	import { listDevices } from '$lib/api/devices.remote';
	import { getConfig } from '$lib/api/settings.remote';
	import { createPairingCode, getPairingStatus } from '$lib/api/pairing.remote';
	import { dashboardWs } from '$lib/stores/dashboard-ws.svelte';
	import { onMount } from 'svelte';
	import DeviceCard from '$lib/components/DeviceCard.svelte';
	import Icon from '@iconify/svelte';
	import { toast } from '$lib/toast';

	interface DeviceEntry {
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

	const [initialDevices, llmConfig] = await Promise.all([listDevices(), getConfig()]);
	const hasLlmConfig = llmConfig !== null;

	let devices = $state<DeviceEntry[]>(
		initialDevices.map((d) => ({
			deviceId: d.deviceId,
			name: d.name,
			status: d.status as 'online' | 'offline',
			model: d.model as string | null,
			manufacturer: d.manufacturer as string | null,
			androidVersion: d.androidVersion as string | null,
			screenWidth: d.screenWidth as number | null,
			screenHeight: d.screenHeight as number | null,
			batteryLevel: d.batteryLevel as number | null,
			isCharging: d.isCharging as boolean,
			lastSeen: d.lastSeen,
			lastGoal: d.lastGoal as DeviceEntry['lastGoal']
		}))
	);

	// ─── Pairing modal state ───────────────────────────────────
	type ModalState = 'closed' | 'loading' | 'code' | 'expired' | 'paired';
	let modalState = $state<ModalState>('closed');
	let pairingCode = $state('');
	let expiresAt = $state('');
	let secondsLeft = $state(0);
	let countdownTimer: ReturnType<typeof setInterval> | null = null;
	let pollTimer: ReturnType<typeof setInterval> | null = null;

	function clearTimers() {
		if (countdownTimer) {
			clearInterval(countdownTimer);
			countdownTimer = null;
		}
		if (pollTimer) {
			clearInterval(pollTimer);
			pollTimer = null;
		}
	}

	function closeModal() {
		clearTimers();
		modalState = 'closed';
		pairingCode = '';
		expiresAt = '';
		secondsLeft = 0;
	}

	function startCountdown() {
		const updateCountdown = () => {
			const now = Date.now();
			const expires = new Date(expiresAt).getTime();
			const remaining = Math.max(0, Math.floor((expires - now) / 1000));
			secondsLeft = remaining;
			if (remaining <= 0) {
				clearTimers();
				modalState = 'expired';
			}
		};
		updateCountdown();
		countdownTimer = setInterval(updateCountdown, 1000);
	}

	function startPolling() {
		pollTimer = setInterval(async () => {
			try {
				const status = await getPairingStatus();
				if (status.paired) {
					clearTimers();
					modalState = 'paired';
					// Refresh devices list
					const refreshed = await listDevices();
					devices = refreshed.map((d) => ({
						deviceId: d.deviceId,
						name: d.name,
						status: d.status as 'online' | 'offline',
						model: d.model as string | null,
						manufacturer: d.manufacturer as string | null,
						androidVersion: d.androidVersion as string | null,
						screenWidth: d.screenWidth as number | null,
						screenHeight: d.screenHeight as number | null,
						batteryLevel: d.batteryLevel as number | null,
						isCharging: d.isCharging as boolean,
						lastSeen: d.lastSeen,
						lastGoal: d.lastGoal as DeviceEntry['lastGoal']
					}));
				} else if (status.expired) {
					clearTimers();
					modalState = 'expired';
				}
			} catch {
				// Silently ignore polling errors
			}
		}, 2000);
	}

	async function generateCode() {
		modalState = 'loading';
		try {
			const result = await createPairingCode();
			pairingCode = result.code;
			expiresAt = result.expiresAt;
			modalState = 'code';
			startCountdown();
			startPolling();
		} catch (e: any) {
			toast.error(e.message ?? 'Failed to generate pairing code');
			closeModal();
		}
	}

	async function openPairingModal() {
		await generateCode();
	}

	async function regenerateCode() {
		clearTimers();
		await generateCode();
	}

	function formatTime(seconds: number): string {
		const m = Math.floor(seconds / 60);
		const s = seconds % 60;
		return `${m}:${s.toString().padStart(2, '0')}`;
	}

	// ─── WebSocket subscriptions ───────────────────────────────
	onMount(() => {
		// Auto-open pairing modal if ?pair query param is present
		const params = new URLSearchParams(window.location.search);
		if (params.has('pair')) {
			openPairingModal();
			// Clean up the URL
			history.replaceState({}, '', window.location.pathname);
		}

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
						{
							deviceId: id,
							name,
							status: 'online',
							model: null,
							manufacturer: null,
							androidVersion: null,
							screenWidth: null,
							screenHeight: null,
							batteryLevel: null,
							isCharging: false,
							lastSeen: new Date().toISOString(),
							lastGoal: null
						},
						...devices
					];
				}
				toast.success(`${name} connected`);
			} else if (msg.type === 'device_offline') {
				const id = msg.deviceId as string;
				const existing = devices.find((d) => d.deviceId === id);
				if (existing) {
					existing.status = 'offline';
					devices = [...devices];
					toast.info(`${existing.name} disconnected`);
				}
			} else if (msg.type === 'device_status') {
				const id = msg.deviceId as string;
				const existing = devices.find((d) => d.deviceId === id);
				if (existing) {
					existing.batteryLevel = msg.batteryLevel as number;
					existing.isCharging = msg.isCharging as boolean;
					devices = [...devices];
				}
			}
		});
		return () => {
			unsub();
			clearTimers();
		};
	});
</script>

<!-- Page header -->
<div class="mb-6 flex items-center justify-between">
	<h2 class="text-xl md:text-2xl font-bold">Devices</h2>
	<div class="flex items-center gap-2">
		<a
			href="https://github.com/unitedbyai/droidclaw/releases/download/v0.5.3/app-debug.apk"
			class="inline-flex items-center gap-1.5 rounded-lg border border-stone-200 px-4 py-2 text-sm font-medium text-stone-600 hover:bg-stone-50"
		>
			<Icon icon="solar:download-bold-duotone" class="h-4 w-4" />
			Download APK
		</a>
		<button
			onclick={openPairingModal}
			class="inline-flex items-center gap-1.5 rounded-lg bg-stone-900 px-4 py-2 text-sm font-medium text-white hover:bg-stone-800"
		>
			<Icon icon="solar:link-round-bold-duotone" class="h-4 w-4" />
			Pair Device
		</button>
	</div>
</div>

<!-- LLM not configured banner -->
{#if !hasLlmConfig && devices.length > 0}
	<a
		href="/dashboard/settings"
		class="mb-6 flex items-center gap-3 rounded-2xl bg-amber-50 p-4 transition-colors hover:bg-amber-100/80"
	>
		<div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-amber-100">
			<Icon icon="solar:danger-triangle-bold-duotone" class="h-5 w-5 text-amber-600" />
		</div>
		<div class="flex-1">
			<p class="text-sm font-semibold text-amber-900">Set up your LLM provider</p>
			<p class="mt-0.5 text-xs text-amber-700">Your device is paired but needs an AI model configured to run tasks.</p>
		</div>
		<Icon icon="solar:alt-arrow-right-linear" class="h-5 w-5 text-amber-400" />
	</a>
{/if}

{#if devices.length === 0}
	<div class="rounded-2xl bg-white p-6 md:p-10 text-center">
		<div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-stone-100">
			<Icon icon="solar:smartphone-bold-duotone" class="h-6 w-6 text-stone-400" />
		</div>
		<p class="font-medium text-stone-600">No devices connected</p>
		<p class="mt-1 text-sm text-stone-400">
			Install the Android app and pair your device to get started.
		</p>
		<button
			onclick={openPairingModal}
			class="mt-5 inline-flex items-center gap-1.5 rounded-lg bg-stone-900 px-4 py-2 text-sm font-medium text-white hover:bg-stone-800"
		>
			<Icon icon="solar:link-round-bold-duotone" class="h-4 w-4" />
			Pair Device
		</button>
	</div>
{:else}
	<div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
		{#each devices as d (d.deviceId)}
			<DeviceCard {...d} />
		{/each}
	</div>
{/if}

<!-- Pairing Modal -->
{#if modalState !== 'closed'}
	<!-- Backdrop -->
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
		onkeydown={(e) => { if (e.key === 'Escape') closeModal(); }}
		onclick={(e) => { if (e.target === e.currentTarget) closeModal(); }}
	>
		<!-- Modal -->
		<div class="relative w-full max-w-md rounded-2xl bg-white shadow-xl">
			<!-- Header -->
			<div class="flex items-center justify-between border-b border-stone-100 px-6 py-4">
				<h3 class="text-lg font-semibold text-stone-900">Pair Your Device</h3>
				<button
					onclick={closeModal}
					class="flex h-8 w-8 items-center justify-center rounded-lg text-stone-400 hover:bg-stone-100 hover:text-stone-600"
				>
					<Icon icon="solar:close-circle-bold-duotone" class="h-5 w-5" />
				</button>
			</div>

			<!-- Body -->
			<div class="px-6 py-8">
				{#if modalState === 'loading'}
					<!-- Loading state -->
					<div class="flex flex-col items-center gap-3">
						<Icon icon="solar:refresh-circle-bold-duotone" class="h-8 w-8 animate-spin text-stone-400" />
						<p class="text-sm text-stone-500">Generating pairing code...</p>
					</div>
				{:else if modalState === 'code'}
					<!-- Code display state -->
					<div class="flex flex-col items-center">
						<p class="mb-6 text-center text-sm text-stone-500">
							Open DroidClaw on your Android device and enter this code:
						</p>

						<!-- OTP digits -->
						<div class="mb-5 flex gap-2">
							{#each pairingCode.split('') as digit}
								<div class="flex h-14 w-11 items-center justify-center rounded-xl border-2 border-stone-200 bg-stone-50">
									<span class="font-mono text-2xl font-bold text-stone-900">{digit}</span>
								</div>
							{/each}
						</div>

						<!-- Countdown -->
						<p class="mb-4 text-sm text-stone-400">
							Expires in {formatTime(secondsLeft)}
						</p>

						<!-- Waiting indicator -->
						<div class="flex items-center gap-2 text-sm text-stone-500">
							<Icon icon="solar:refresh-circle-bold-duotone" class="h-4 w-4 animate-spin" />
							Waiting for device...
						</div>
					</div>
				{:else if modalState === 'expired'}
					<!-- Expired state -->
					<div class="flex flex-col items-center gap-4">
						<div class="flex h-12 w-12 items-center justify-center rounded-full bg-stone-100">
							<Icon icon="solar:clock-circle-bold-duotone" class="h-6 w-6 text-stone-400" />
						</div>
						<p class="font-medium text-stone-600">Code expired</p>
						<button
							onclick={regenerateCode}
							class="inline-flex items-center gap-1.5 rounded-lg bg-stone-900 px-4 py-2 text-sm font-medium text-white hover:bg-stone-800"
						>
							<Icon icon="solar:refresh-bold-duotone" class="h-4 w-4" />
							Generate new code
						</button>
					</div>
				{:else if modalState === 'paired'}
					<!-- Success state -->
					<div class="flex flex-col items-center gap-4">
						<div class="flex h-12 w-12 items-center justify-center rounded-full bg-green-100">
							<Icon icon="solar:check-circle-bold-duotone" class="h-6 w-6 text-green-600" />
						</div>
						<p class="text-lg font-semibold text-stone-900">Device Paired!</p>
						<p class="text-sm text-stone-500">Your device is now connected and ready to use.</p>
						<button
							onclick={closeModal}
							class="inline-flex items-center gap-1.5 rounded-lg bg-stone-900 px-4 py-2 text-sm font-medium text-white hover:bg-stone-800"
						>
							Done
						</button>
					</div>
				{/if}
			</div>

			<!-- Footer (shown only during code/expired states) -->
			{#if modalState === 'code' || modalState === 'expired'}
				<div class="border-t border-stone-100 px-6 py-4">
					<a
						href="/dashboard/api-keys"
						class="flex items-center justify-center gap-1.5 text-sm text-stone-400 hover:text-stone-600"
					>
						<Icon icon="solar:key-bold-duotone" class="h-3.5 w-3.5" />
						Developer? Use API keys for manual setup
					</a>
				</div>
			{/if}
		</div>
	</div>
{/if}
