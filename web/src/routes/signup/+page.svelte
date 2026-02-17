<script lang="ts">
	import { signup } from '$lib/api/auth.remote';
</script>

<div class="auth">
	<h1>Sign up</h1>

	<form {...signup}>
		<label>
			Username
			<input {...signup.fields.name.as('text')} />
			{#each signup.fields.name.issues() ?? [] as issue (issue.message)}
				<p class="issue">{issue.message}</p>
			{/each}
		</label>

		<label>
			Email
			<input {...signup.fields.email.as('text')} />
			{#each signup.fields.email.issues() ?? [] as issue (issue.message)}
				<p class="issue">{issue.message}</p>
			{/each}
		</label>

		<label>
			Password
			<input {...signup.fields.password.as('password')} />
			{#each signup.fields.password.issues() ?? [] as issue (issue.message)}
				<p class="issue">{issue.message}</p>
			{/each}
		</label>

		<button type="submit">Sign up</button>
	</form>
</div>
