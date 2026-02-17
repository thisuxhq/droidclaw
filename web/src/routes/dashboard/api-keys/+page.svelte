<script lang="ts">
	import { listKeys, createKey, deleteKey } from '$lib/api/api-keys.remote';

	let newKeyValue = $state<string | null>(null);

	$effect(() => {
		if (createKey.result?.key) {
			newKeyValue = createKey.result.key;
		}
	});
</script>

<h2 class="mb-6 text-2xl font-bold">API Keys</h2>

<!-- Create new key -->
<div class="mb-8 rounded-lg border border-neutral-200 p-6">
	<h3 class="mb-4 font-semibold">Create New Key</h3>
	<form {...createKey} class="flex items-end gap-4">
		<label class="flex flex-1 flex-col gap-1">
			<span class="text-sm text-neutral-600">Key Name</span>
			<input
				{...createKey.fields.name.as('text')}
				placeholder="e.g. Production, Development"
				class="rounded border border-neutral-300 px-3 py-2 text-sm focus:border-neutral-500 focus:outline-none"
			/>
			{#each createKey.fields.name.issues() ?? [] as issue (issue.message)}
				<p class="text-sm text-red-600">{issue.message}</p>
			{/each}
		</label>
		<button
			type="submit"
			class="rounded bg-neutral-800 px-4 py-2 text-sm text-white hover:bg-neutral-700"
		>
			Create
		</button>
	</form>
</div>

<!-- Newly created key warning -->
{#if newKeyValue}
	<div class="mb-8 rounded-lg border border-yellow-300 bg-yellow-50 p-6">
		<h3 class="mb-2 font-semibold text-yellow-800">Save Your API Key</h3>
		<p class="mb-3 text-sm text-yellow-700">
			Copy this key now. It will not be shown again.
		</p>
		<div class="flex items-center gap-2">
			<code class="flex-1 rounded bg-yellow-100 px-3 py-2 text-sm font-mono break-all">
				{newKeyValue}
			</code>
			<button
				onclick={() => {
					navigator.clipboard.writeText(newKeyValue!);
				}}
				class="rounded border border-yellow-400 px-3 py-2 text-sm text-yellow-800 hover:bg-yellow-100"
			>
				Copy
			</button>
		</div>
		<button
			onclick={() => (newKeyValue = null)}
			class="mt-3 text-sm text-yellow-600 hover:text-yellow-800"
		>
			Dismiss
		</button>
	</div>
{/if}

<!-- Existing keys list -->
<div class="rounded-lg border border-neutral-200">
	<div class="border-b border-neutral-200 px-6 py-4">
		<h3 class="font-semibold">Your Keys</h3>
	</div>

	{#await listKeys()}
		<div class="px-6 py-8 text-center text-sm text-neutral-500">Loading keys...</div>
	{:then keys}
		{#if keys && keys.length > 0}
			<ul class="divide-y divide-neutral-100">
				{#each keys as key (key.id)}
					<li class="flex items-center justify-between px-6 py-4">
						<div>
							<p class="font-medium">{key.name ?? 'Unnamed Key'}</p>
							<div class="mt-1 flex items-center gap-3 text-sm text-neutral-500">
								{#if key.start}
									<span class="font-mono">{key.start}...</span>
								{/if}
								<span>
									Created {new Date(key.createdAt).toLocaleDateString()}
								</span>
							</div>
						</div>
						<form {...deleteKey}>
							<input type="hidden" name="keyId" value={key.id} />
							<button
								type="submit"
								class="rounded border border-red-200 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50"
							>
								Delete
							</button>
						</form>
					</li>
				{/each}
			</ul>
		{:else}
			<div class="px-6 py-8 text-center text-sm text-neutral-500">
				No API keys yet. Create one above.
			</div>
		{/if}
	{:catch}
		<div class="px-6 py-8 text-center text-sm text-red-600">
			Failed to load keys. Please try again.
		</div>
	{/await}
</div>
