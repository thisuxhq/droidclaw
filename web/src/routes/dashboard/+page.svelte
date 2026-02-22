<script lang="ts">
	import Icon from '@iconify/svelte';
	import { DASHBOARD_CARD_CLICK } from '$lib/analytics/events';

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
</script>

<h2 class="mb-1 text-2xl font-bold">Dashboard</h2>
<p class="mb-8 text-stone-500">Welcome back, {data.user.name}.</p>

{#if data.plan}
	<div class="mb-8 flex items-center gap-4 rounded-2xl bg-white p-5">
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
