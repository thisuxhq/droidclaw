<script lang="ts">
	import Icon from '@iconify/svelte';
	import { DASHBOARD_CARD_CLICK } from '$lib/analytics/events';
	import { getConfig } from '$lib/api/settings.remote';
	import { listDevices } from '$lib/api/devices.remote';

	let { data } = $props();

	const cards = [
		{
			href: '/dashboard/devices',
			icon: 'solar:smartphone-bold-duotone',
			title: 'Devices',
			desc: 'Manage connected phones',
			color: 'bg-emerald-100 text-emerald-600'
		},
		{
			href: '/dashboard/api-keys',
			icon: 'solar:key-bold-duotone',
			title: 'API Keys',
			desc: 'Create keys for your devices',
			color: 'bg-amber-100 text-amber-600'
		},
		{
			href: '/dashboard/settings',
			icon: 'solar:settings-bold-duotone',
			title: 'Settings',
			desc: 'Configure LLM provider',
			color: 'bg-purple-100 text-purple-600'
		}
	];

	// Setup checklist data
	const [config, devices] = await Promise.all([getConfig(), listDevices()]);
	const hasConfig = config !== null;
	const hasDevice = (devices as unknown[]).length > 0;

	const checklist = [
		{
			label: 'Configure LLM provider',
			desc: 'Choose your AI model and add credentials',
			href: '/dashboard/settings',
			done: hasConfig
		},
		{
			label: 'Install the Android app',
			desc: 'Download and install DroidClaw on your phone',
			href: 'https://github.com/unitedbyai/droidclaw/releases/latest',
			done: hasDevice
		},
		{
			label: 'Connect your device',
			desc: 'Pair your phone with the dashboard',
			href: '/dashboard/devices?pair',
			done: hasDevice
		}
	];

	const completedCount = checklist.filter((s) => s.done).length;
	const allComplete = completedCount === checklist.length;
</script>

<h2 class="mb-1 text-xl md:text-2xl font-bold">Dashboard</h2>
<p class="mb-8 text-stone-500">Welcome back, {data.user.name}.</p>

{#if data.plan}
	<div class="mb-8 flex items-center gap-4 rounded-2xl bg-white p-4 md:p-5">
		<div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-emerald-100">
			<Icon icon="solar:verified-check-bold-duotone" class="h-5 w-5 text-emerald-600" />
		</div>
		<div>
			<h3 class="font-semibold text-emerald-900">{data.plan === 'ltd' ? 'Lifetime Deal' : data.plan} Plan</h3>
			<p class="mt-0.5 text-sm text-emerald-700">
				License: {data.licenseKey ?? 'Active'}
			</p>
		</div>
	</div>
{/if}

{#if !allComplete}
	<div class="mb-6">
		<p class="mb-3 text-sm font-medium text-stone-500">{completedCount} of {checklist.length} complete</p>
		<div class="rounded-2xl bg-white">
			{#each checklist as step, i}
				<a
					href={step.href}
					class="flex items-center gap-4 p-5 transition-colors hover:bg-stone-50/80
						{i > 0 ? 'border-t border-stone-100' : ''}
						{i === 0 ? 'rounded-t-2xl' : ''}
						{i === checklist.length - 1 ? 'rounded-b-2xl' : ''}"
				>
					<div class="flex h-8 w-8 shrink-0 items-center justify-center">
						{#if step.done}
							<Icon icon="solar:check-circle-bold" class="h-6 w-6 text-emerald-500" />
						{:else}
							<Icon icon="solar:circle-linear" class="h-6 w-6 text-stone-300" />
						{/if}
					</div>
					<div class="flex-1">
						<h3 class="text-sm font-semibold {step.done ? 'text-stone-400 line-through' : 'text-stone-900'}">{step.label}</h3>
						<p class="mt-0.5 text-xs text-stone-400">{step.desc}</p>
					</div>
					<Icon icon="solar:alt-arrow-right-linear" class="h-5 w-5 text-stone-300" />
				</a>
			{/each}
		</div>
	</div>
{/if}

<div class="rounded-2xl bg-white">
	{#each cards as card, i}
		<a
			href={card.href}
			data-umami-event={DASHBOARD_CARD_CLICK}
			data-umami-event-section={card.title.toLowerCase().replace(' ', '-')}
			class="flex items-center gap-4 p-5 transition-colors hover:bg-stone-50/80
				{i > 0 ? 'border-t border-stone-100' : ''}
				{i === 0 ? 'rounded-t-2xl' : ''}
				{i === cards.length - 1 ? 'rounded-b-2xl' : ''}"
		>
			<div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full {card.color}">
				<Icon icon={card.icon} class="h-5 w-5" />
			</div>
			<div class="flex-1">
				<h3 class="font-semibold text-stone-900">{card.title}</h3>
				<p class="mt-0.5 text-sm text-stone-500">{card.desc}</p>
			</div>
			<Icon icon="solar:alt-arrow-right-linear" class="h-5 w-5 text-stone-300" />
		</a>
	{/each}
</div>
