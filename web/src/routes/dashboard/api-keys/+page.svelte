<script lang="ts">
	import { listKeys, createKey, deleteKey } from '$lib/api/api-keys.remote';
	import Icon from '@iconify/svelte';
	import { toast } from '$lib/toast';
	import { track } from '$lib/analytics/track';
	import { APIKEY_CREATE, APIKEY_COPY, APIKEY_DELETE } from '$lib/analytics/events';

	let newKeyValue = $state<string | null>(null);
	let keysPromise = $state(listKeys());
</script>

<h2 class="mb-6 text-2xl font-bold">API Keys</h2>

<!-- Create new key -->
<p class="mb-3 text-sm font-medium text-stone-500">Create new key</p>
<div class="mb-8 rounded-2xl bg-white p-6">
	<form
		{...createKey.enhance(async ({ submit }) => {
			await submit().updates(listKeys());
			newKeyValue = createKey.result?.key ?? null;
			keysPromise = listKeys();
			toast.success('API key created');
			track(APIKEY_CREATE);
		})}
		class="flex items-end gap-4"
	>
		<label class="flex flex-1 flex-col gap-1">
			<span class="text-sm text-stone-600">Key Name</span>
			<input
				{...createKey.fields.name.as('text')}
				placeholder="e.g. Production, Development"
				class="rounded-lg border border-stone-200 bg-stone-50 px-3 py-2 text-sm focus:border-stone-400 focus:outline-none"
			/>
			{#each createKey.fields.name.issues() ?? [] as issue (issue.message)}
				<p class="text-sm text-red-600">{issue.message}</p>
			{/each}
		</label>
		<button
			type="submit"
			class="flex items-center gap-2 rounded-lg bg-stone-900 px-4 py-2 text-sm font-medium text-white hover:bg-stone-800"
		>
			<Icon icon="solar:add-circle-bold-duotone" class="h-4 w-4" />
			Create
		</button>
	</form>
</div>

<!-- Newly created key warning -->
{#if newKeyValue}
	<div class="mb-8 rounded-2xl bg-amber-50 p-6">
		<div class="mb-2 flex items-center gap-2">
			<Icon icon="solar:danger-triangle-bold-duotone" class="h-5 w-5 text-amber-600" />
			<h3 class="font-semibold text-amber-800">Save your API key</h3>
		</div>
		<p class="mb-3 text-sm text-amber-700">
			Copy this key now. It will not be shown again.
		</p>
		<div class="flex items-center gap-2">
			<code class="flex-1 rounded-lg bg-amber-100 px-3 py-2 font-mono text-sm break-all">
				{newKeyValue}
			</code>
			<button
				onclick={() => {
					navigator.clipboard.writeText(newKeyValue!);
					toast.success('Copied to clipboard');
					track(APIKEY_COPY);
				}}
				class="flex items-center gap-1.5 rounded-lg border border-amber-300 px-3 py-2 text-sm font-medium text-amber-800 hover:bg-amber-100"
			>
				<Icon icon="solar:copy-bold-duotone" class="h-4 w-4" />
				Copy
			</button>
		</div>
		<button
			onclick={() => (newKeyValue = null)}
			class="mt-3 text-sm text-amber-600 hover:text-amber-800"
		>
			Dismiss
		</button>
	</div>
{/if}

<!-- Existing keys list -->
<p class="mb-3 text-sm font-medium text-stone-500">Your keys</p>
<div class="rounded-2xl bg-white">
	{#await keysPromise}
		<div class="flex items-center justify-center gap-2 px-6 py-8 text-sm text-stone-500">
			<Icon icon="solar:refresh-circle-bold-duotone" class="h-5 w-5 animate-spin text-stone-400" />
			Loading keys...
		</div>
	{:then keys}
		{#if keys && keys.length > 0}
			{#each keys as key, i (key.id)}
				<div class="flex items-center justify-between px-6 py-4
					{i > 0 ? 'border-t border-stone-100' : ''}">
					<div class="flex items-center gap-3">
						<div class="flex h-9 w-9 items-center justify-center rounded-full bg-amber-100">
							<Icon icon="solar:key-bold-duotone" class="h-4 w-4 text-amber-600" />
						</div>
						<div>
							<p class="font-medium text-stone-900">{key.name ?? 'Unnamed Key'}</p>
							<div class="mt-0.5 flex items-center gap-3 text-sm text-stone-500">
								{#if key.start}
									<span class="font-mono">{key.start}...</span>
								{/if}
								<span>
									Created {new Date(key.createdAt).toLocaleDateString()}
								</span>
							</div>
						</div>
					</div>
					<form
						{...deleteKey.enhance(async ({ submit }) => {
							await submit().updates(listKeys());
							keysPromise = listKeys();
							toast.success('API key deleted');
							track(APIKEY_DELETE);
						})}
					>
						<input type="hidden" name="keyId" value={key.id} />
						<button
							type="submit"
							class="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm text-red-500 hover:bg-red-50"
						>
							<Icon icon="solar:trash-bin-trash-bold-duotone" class="h-4 w-4" />
							Delete
						</button>
					</form>
				</div>
			{/each}
		{:else}
			<div class="px-6 py-10 text-center">
				<Icon icon="solar:key-bold-duotone" class="mx-auto mb-3 h-8 w-8 text-stone-300" />
				<p class="text-sm text-stone-500">No API keys yet. Create one above.</p>
			</div>
		{/if}
	{:catch}
		<div class="flex items-center justify-center gap-2 px-6 py-8 text-sm text-red-600">
			<Icon icon="solar:danger-triangle-bold-duotone" class="h-5 w-5" />
			Failed to load keys. Please try again.
		</div>
	{/await}
</div>
