<script lang="ts">
	import { activateLicense, activateFromCheckout } from '$lib/api/license.remote';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { onMount } from 'svelte';
	import Icon from '@iconify/svelte';
	import { LICENSE_ACTIVATE_CHECKOUT, LICENSE_ACTIVATE_MANUAL, LICENSE_PURCHASE_CLICK } from '$lib/analytics/events';

	const checkoutId = page.url.searchParams.get('checkout_id');

	let showKeyInput = $state(false);
	let checkoutStatus = $state<'activating' | 'error' | 'idle'>('idle');
	let checkoutError = $state('');

	async function activateCheckout() {
		if (!checkoutId) return;
		checkoutStatus = 'activating';
		checkoutError = '';
		try {
			await activateFromCheckout({ checkoutId });
			goto('/dashboard');
		} catch (e: any) {
			checkoutError = e.message ?? 'Failed to activate from checkout';
			checkoutStatus = 'error';
		}
	}

	onMount(() => {
		if (checkoutId) activateCheckout();
	});
</script>

{#if checkoutId}
	<!-- Auto-activate from Polar checkout -->
	<div class="mx-auto max-w-md pt-20">
		{#if checkoutStatus === 'activating'}
			<div class="mb-8 text-center">
				<div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-neutral-100">
					<Icon icon="ph:spinner-duotone" class="h-6 w-6 animate-spin text-neutral-600" />
				</div>
				<h2 class="text-2xl font-bold">Activating your license...</h2>
				<p class="mt-1 text-neutral-500">
					We're setting up your account. This will only take a moment.
				</p>
			</div>
		{:else if checkoutStatus === 'error'}
			<div class="mb-8 text-center">
				<div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-red-50">
					<Icon icon="ph:warning-duotone" class="h-6 w-6 text-red-500" />
				</div>
				<h2 class="text-2xl font-bold">Activation failed</h2>
				<p class="mt-1 text-neutral-500">{checkoutError}</p>
			</div>

			<button
				onclick={activateCheckout}
				data-umami-event={LICENSE_ACTIVATE_CHECKOUT}
				class="flex w-full items-center justify-center gap-2 rounded-xl bg-neutral-900 px-4 py-2.5 font-medium text-white hover:bg-neutral-800"
			>
				<Icon icon="ph:arrow-clockwise-duotone" class="h-4 w-4" />
				Retry
			</button>
		{/if}

		<div class="mt-6 rounded-xl border border-neutral-200 bg-neutral-50 p-4">
			<p class="text-center text-sm text-neutral-500">
				If activation doesn't work automatically, check your email for the license key and enter it manually below.
			</p>
		</div>

		<div class="mt-8 mb-4 text-center">
			<p class="text-sm font-medium text-neutral-400">Or activate manually</p>
		</div>

		<form {...activateLicense} class="space-y-4">
			<label class="block">
				<span class="flex items-center gap-1.5 text-sm font-medium text-neutral-700">
					<Icon icon="ph:key-duotone" class="h-4 w-4 text-neutral-400" />
					License Key
				</span>
				<input
					{...activateLicense.fields.key.as('text')}
					placeholder="DROIDCLAW-XXXX-XXXX-XXXX"
					class="mt-1 block w-full rounded-xl border border-neutral-300 px-4 py-2.5 focus:border-neutral-900 focus:outline-none"
				/>
				{#each activateLicense.fields.key.issues() ?? [] as issue (issue.message)}
					<p class="mt-1 text-sm text-red-600">{issue.message}</p>
				{/each}
			</label>

			<button
				type="submit"
				data-umami-event={LICENSE_ACTIVATE_MANUAL}
				class="flex w-full items-center justify-center gap-2 rounded-xl bg-neutral-900 px-4 py-2.5 font-medium text-white hover:bg-neutral-800"
			>
				<Icon icon="ph:seal-check-duotone" class="h-4 w-4" />
				Activate
			</button>
		</form>
	</div>
{:else}
	<!-- Purchase-first flow -->
	<div class="mx-auto max-w-md pt-20">
		<div class="mb-8 text-center">
			<div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-neutral-900">
				<Icon icon="ph:robot-duotone" class="h-6 w-6 text-white" />
			</div>
			<h2 class="text-2xl font-bold">Get started with DroidClaw</h2>
			<p class="mt-2 text-neutral-500">
				Unlock AI-powered Android device control.
			</p>
		</div>

		<a
			href="https://sandbox-api.polar.sh/v1/checkout-links/polar_cl_5pGavRIJJhM8ge6p0UaeaadT2bCiqL04CYXgW3bwVac/redirect"
			data-umami-event={LICENSE_PURCHASE_CLICK}
			class="flex w-full items-center justify-center gap-2 rounded-xl bg-neutral-900 px-4 py-3 text-base font-medium text-white hover:bg-neutral-800"
		>
			<Icon icon="ph:credit-card-duotone" class="h-5 w-5" />
			Purchase Now
		</a>

		<div class="mt-10">
			<button
				type="button"
				onclick={() => showKeyInput = !showKeyInput}
				class="flex w-full items-center justify-center gap-1.5 text-sm text-neutral-400 hover:text-neutral-600"
			>
				Already have a license key?
				<Icon
					icon="ph:caret-down"
					class="h-3.5 w-3.5 transition-transform {showKeyInput ? 'rotate-180' : ''}"
				/>
			</button>

			{#if showKeyInput}
				<div class="mt-4">
					<form {...activateLicense} class="space-y-4">
						<label class="block">
							<span class="flex items-center gap-1.5 text-sm font-medium text-neutral-700">
								<Icon icon="ph:key-duotone" class="h-4 w-4 text-neutral-400" />
								License Key
							</span>
							<input
								{...activateLicense.fields.key.as('text')}
								placeholder="DROIDCLAW-XXXX-XXXX-XXXX"
								class="mt-1 block w-full rounded-xl border border-neutral-300 px-4 py-2.5 focus:border-neutral-900 focus:outline-none"
							/>
							{#each activateLicense.fields.key.issues() ?? [] as issue (issue.message)}
								<p class="mt-1 text-sm text-red-600">{issue.message}</p>
							{/each}
						</label>

						<button
							type="submit"
							data-umami-event={LICENSE_ACTIVATE_MANUAL}
							class="flex w-full items-center justify-center gap-2 rounded-xl border border-neutral-300 px-4 py-2.5 text-sm font-medium text-neutral-700 hover:bg-neutral-50"
						>
							<Icon icon="ph:seal-check-duotone" class="h-4 w-4" />
							Activate
						</button>
					</form>
				</div>
			{/if}
		</div>
	</div>
{/if}
