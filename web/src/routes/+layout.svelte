<script lang="ts">
	import '../app.css';
	import favicon from '$lib/assets/favicon.svg';

	let { children } = $props();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<meta property="og:title" content="DroidClaw — AI Agent for Android" />
	<meta property="og:description" content="Goal in words, action in reality. Control Android devices with AI." />
	<meta property="og:image" content="https://droidclaw.ai/og-image.png" />
	<meta property="og:type" content="website" />
	<meta property="og:url" content="https://droidclaw.ai" />
	<meta name="twitter:card" content="summary_large_image" />
	<meta name="twitter:title" content="DroidClaw — AI Agent for Android" />
	<meta name="twitter:description" content="Goal in words, action in reality. Control Android devices with AI." />
	<meta name="twitter:image" content="https://droidclaw.ai/og-image.png" />
</svelte:head>

{@render children?.()}
