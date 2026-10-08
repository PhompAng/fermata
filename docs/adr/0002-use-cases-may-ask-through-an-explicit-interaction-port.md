# Use cases may ask through an explicitly passed InteractionPort

Our rule is that the domain layer never depends on presentation. Fermata deliberately bends it: a use case whose domain transaction genuinely contains a user decision (for example "remove the item, and if that releases a promo code, ask whether to release or keep it") may pause and ask through an `InteractionPort`. The port is passed in explicitly by the caller as a parameter, never constructor-injected or read from the coroutine context, so the dependency is visible at every call site and tests pass a scripted fake. The port lives in `fermata-core`, a pure Kotlin artifact with no Android or UI dependency, so the domain module still does not depend on presentation code, only on the abstract question/answer contract.

The primary, default pattern remains the ViewModel asking between pure use cases. The use-case-side port is the exception for cases where splitting the transaction would scatter its invariants across ViewModel methods, which is the failure mode this library exists to remove.

## Considered options

- ViewModel-only asking (use cases stay pure). Adopted as the default, but insufficient alone: multi-step domain transactions end up as two use cases plus ViewModel glue.
- Constructor-inject the port into use cases via Koin. Rejected: use cases are `factory` and the port is per-ViewModel, so it needs per-ViewModel scopes or parameter passing at resolution.
- Carry the port as a `CoroutineContext` element. Rejected: hidden dependency that fails at runtime when absent.
