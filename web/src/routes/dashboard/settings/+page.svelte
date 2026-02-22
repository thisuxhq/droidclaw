<script lang="ts">
	import { getConfig, updateConfig } from '$lib/api/settings.remote';
	import { page } from '$app/state';
	import Icon from '@iconify/svelte';
	import { toast } from '$lib/toast';
	import { track } from '$lib/analytics/track';
	import { SETTINGS_SAVE } from '$lib/analytics/events';

	let config = $state(await getConfig());
	const layoutData = page.data;
</script>

<h2 class="mb-6 text-2xl font-bold">Settings</h2>

<!-- Account -->
<p class="mb-3 text-sm font-medium text-stone-500">Account</p>
<div class="mb-8 rounded-2xl bg-white">
	<div class="flex items-center justify-between px-6 py-4">
		<span class="text-sm text-stone-500">Email</span>
		<span class="text-sm font-medium text-stone-900 blur-sm transition-all duration-200 hover:blur-none">{layoutData.user.email}</span>
	</div>
	{#if layoutData.plan}
		<div class="flex items-center justify-between border-t border-stone-100 px-6 py-4">
			<span class="text-sm text-stone-500">Plan</span>
			<span class="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700">
				<Icon icon="solar:verified-check-bold-duotone" class="h-3.5 w-3.5" />
				{layoutData.plan === 'ltd' ? 'Lifetime' : layoutData.plan}
			</span>
		</div>
	{/if}
	{#if layoutData.licenseKey}
		<div class="flex items-center justify-between border-t border-stone-100 px-6 py-4">
			<span class="text-sm text-stone-500">License</span>
			<span class="font-mono text-sm text-stone-600">{layoutData.licenseKey}</span>
		</div>
	{/if}
</div>

<!-- LLM Provider -->
<p class="mb-3 text-sm font-medium text-stone-500">LLM Provider</p>
<div class="rounded-2xl bg-white p-6">
	<form
		{...updateConfig.enhance(async ({ submit }) => {
			await submit().updates(getConfig());
			config = await getConfig();
			toast.success('Settings saved');
			track(SETTINGS_SAVE);
		})}
		class="space-y-4"
	>
		<label class="block">
			<span class="text-sm text-stone-600">Provider</span>
			<select
				{...updateConfig.fields.provider.as('text')}
				class="mt-1 block w-full rounded-lg border border-stone-200 bg-stone-50 px-3 py-2 text-sm focus:border-stone-400 focus:outline-none"
			>
				<option value="openai">OpenAI</option>
				<option value="groq">Groq</option>
				<option value="ollama">Ollama</option>
				<option value="bedrock">AWS Bedrock</option>
				<option value="openrouter">OpenRouter</option>
			</select>
			{#each updateConfig.fields.provider.issues() ?? [] as issue (issue.message)}
				<p class="text-sm text-red-600">{issue.message}</p>
			{/each}
		</label>

		<label class="block">
			<span class="text-sm text-stone-600">API Key</span>
			<input
				{...updateConfig.fields.apiKey.as('password')}
				placeholder={config?.apiKey ?? 'Enter your API key'}
				class="mt-1 block w-full rounded-lg border border-stone-200 bg-stone-50 px-3 py-2 text-sm focus:border-stone-400 focus:outline-none"
			/>
			{#each updateConfig.fields.apiKey.issues() ?? [] as issue (issue.message)}
				<p class="text-sm text-red-600">{issue.message}</p>
			{/each}
		</label>

		<label class="block">
			<span class="text-sm text-stone-600">Model (optional)</span>
			<input
				{...updateConfig.fields.model.as('text')}
				placeholder="e.g., gpt-4o, llama-3.3-70b-versatile"
				class="mt-1 block w-full rounded-lg border border-stone-200 bg-stone-50 px-3 py-2 text-sm focus:border-stone-400 focus:outline-none"
			/>
		</label>

		<button
			type="submit"
			class="flex items-center gap-2 rounded-lg bg-stone-900 px-4 py-2 text-sm font-medium text-white hover:bg-stone-800"
		>
			<Icon icon="solar:diskette-bold-duotone" class="h-4 w-4" />
			Save
		</button>
	</form>

	{#if config}
		<div class="mt-4 flex items-center gap-2 rounded-lg bg-stone-50 px-3 py-2 text-sm text-stone-500">
			<Icon icon="solar:info-circle-bold-duotone" class="h-4 w-4 shrink-0 text-stone-400" />
			Current: {config.provider} &middot; Key: {config.apiKey}
			{#if config.model} &middot; Model: {config.model}{/if}
		</div>
	{/if}
</div>
