## Change Decision Package

<!-- Required on every code PR: bug fix, feature, internal infra, UI, migration.
     Substance over headings — answer each category, don't just keep the labels.
     N/A only when structurally inapplicable, always with a one-line reason.
     If only one credible approach exists, say so and why — don't manufacture alternatives. -->

1. **Origin:** <!-- who asked / which issue / which report; for a bug: root cause + reporter -->
2. **User/operator path + experience:** <!-- the exact steps or system conditions that produce the current behavior, what the person experiences, and what should happen instead; for features: the before-and-after path -->
3. **Scale + impact:** <!-- how many users/systems affected; the experience caused or value delivered -->
4. **Approach:** <!-- the credible options (not band-aids), which was chosen, why, in plain terms -->
5. **Build notes + blast radius:** <!-- anything tricky in the build; what else this might have impacted -->
6. **Regressions:** <!-- any potential regressions this change could cause -->
7. **Proof:** <!-- the test harness / verification that proves the change does what it claims -->
8. **UI before/after:** <!-- screenshots on desktop AND mobile if UI changed, or N/A + reason -->
9. **Migrations / env:** <!-- DB migrations or env-var changes required, or none -->
10. **Staging-first:** <!-- migrations/env tested on staging and OK for production, or N/A + reason -->
