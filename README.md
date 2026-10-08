# Fermata

Fermata lets an in-flight operation pause, hand a typed question to the UI, and resume with the answer. The name is the musical hold sign: hold until released, then continue.

It exists because MVVM + Clean Architecture flows that need a mid-operation decision ("removing this item releases a promo code, continue?", "the release failed, retry?") end up split across a check use case, a one-shot event, a dialog, and a second ViewModel method that re-enters the operation with extra flags. Fermata turns that into one top-to-bottom `suspend` function.

See [CONTEXT.md](CONTEXT.md) for the vocabulary and [docs/adr](docs/adr) for the decisions behind the design.

## Artifacts

| Artifact | Platform | Contents |
|---|---|---|
| `io.github.phompang.fermata:fermata-core` | Kotlin/JVM, coroutines only | `Interaction<R>`, `InteractionPort`, `InteractionHost` (`ask`, `answer`, `dismiss`, `answerPending`, `dismissPending`), `PendingInteraction`, `InteractionDismissedException`, `askOrNull` |
| `io.github.phompang.fermata:fermata-compose` | Android, Compose runtime only | `InteractionHandler` |
| `io.github.phompang.fermata:fermata-test` | Kotlin/JVM, Turbine | `ScriptedInteractionPort`, `awaitPending`, `awaitNothingPending` |

Domain modules depend on `fermata-core` only. It has no Android or UI dependency.

`fermata-compose` declares `minSdk 23`, the floor of the Compose runtime it is built against (1.12.1 via BOM 2026.09.00). It adds no requirement of its own, so lower it in step with the Compose version if that ever drops.

## Define Interactions

An Interaction is a typed question. Its type parameter is the Answer the UI must give. Apps define their own, usually as one sealed hierarchy per feature so the UI's `when` is exhaustive.

```kotlin
sealed interface BillInteraction<R> : Interaction<R> {
	data class ConfirmPromoCodeLoss(val code: String) : BillInteraction<Boolean>
	data class RetryRelease(val reason: String) : BillInteraction<Boolean>
	data class PickBill(val candidates: List<Bill>) : BillInteraction<Bill>
}
```

## Ask from a ViewModel (the default)

The ViewModel owns an `InteractionHost` by composition. No base class.

```kotlin
class BillItemsViewModel(...) : ViewModel() {
	val interactionHost = InteractionHost()

	fun onRemoveClicked(itemId: BillItemId) {
		viewModelScope.launch {
			val code = checkIfRemoveReleasesPromoCode(itemId)
			val release = code != null && interactionHost.ask(BillInteraction.ConfirmPromoCodeLoss(code))
			removeBillItem(RemoveBillItemUseCase.Params(itemId, releaseCode = code.takeIf { release }))
		}
	}
}
```

`ask` suspends until the UI answers. One Interaction is pending per host at a time; a second `ask` waits for the first to finish.

The host exposes `pending: StateFlow<PendingInteraction?>`. `PendingInteraction` wraps the Interaction with a monotonic `id` and has identity equality on purpose: two consecutive asks with equal content, such as the same retry prompt twice, must each reach the UI, and a `StateFlow` of the raw Interaction would swallow the second one as "unchanged".

## Ask from a use case (the exception)

When the decision is part of one domain transaction, the use case takes an `InteractionPort` as an explicit parameter. Never inject it or read it from the coroutine context; see [ADR 0002](docs/adr/0002-use-cases-may-ask-through-an-explicit-interaction-port.md).

```kotlin
class RemoveBillItemWithPromoCheckUseCase(...) {
	suspend operator fun invoke(itemId: BillItemId, port: InteractionPort): Outcome {
		val code = repository.codeReleasedBy(itemId) ?: return remove(itemId)
		if (!port.ask(BillInteraction.ConfirmPromoCodeLoss(code))) return removeKeepingCode(itemId)
		while (true) {
			try { return removeAndRelease(itemId, code) }
			catch (e: ReleaseFailedException) {
				if (!port.ask(BillInteraction.RetryRelease(e.message))) return Outcome.GaveUp(code)
			}
		}
	}
}
```

The ViewModel passes its host: `removeBillItemWithPromoCheck(itemId, interactionHost)`.

## Dismissal

Tapping outside or pressing back is a Dismissal, not an Answer. `ask` throws `InteractionDismissedException`, a `CancellationException`, so the operation unwinds like a normal cancellation with `finally` blocks running and nothing reported as an error. The exception carries the Interaction as a field, but its message names only the Interaction's class, so Interaction contents never reach logs or crash reports through it. Use `askOrNull` when you want to handle dismissal inline instead.

## Render in Compose

`InteractionHandler` collects the host's pending Interaction and hands it to your lambda. You own the dialogs, so use your Design System components. The reified type parameter lets the `when` be exhaustive over your sealed hierarchy, which is why the sealed interface carries the `R` type parameter. A pending Interaction of another type is skipped, not an error, so several handlers with disjoint hierarchies can share one host; an Interaction nobody handles simply stays pending.

```kotlin
InteractionHandler<BillInteraction<*>>(host) { interaction ->
	when (interaction) {
		is BillInteraction.ConfirmPromoCodeLoss -> ConfirmDialog(
			message = stringResource(R.string.confirm_code_loss, interaction.code),
			onConfirm = { host.answer(interaction, true) },
			onDeny = { host.answer(interaction, false) },
			onDismissRequest = { host.dismiss(interaction) },
		)
		is BillInteraction.RetryRelease -> ...
		is BillInteraction.PickBill -> ...
	}
}
```

`answer` and `dismiss` take the Interaction instance so the Answer type is checked at compile time, and both return whether they resolved the pending Interaction. A late callback for an Interaction that is no longer pending returns false and does nothing.

`InteractionHandler` collects the pending state with `collectAsState()`, not `collectAsStateWithLifecycle()`, so that `fermata-compose` depends on the Compose runtime only. The trade-off is a collector that stays active while the lifecycle is stopped. It watches a `StateFlow` holding at most one small handle, so the cost is negligible, and a dialog driven by state has nothing to do off-screen. When the caller does not hold the instance, for example a ViewModel method invoked from a dialog listener, `answerPending<I, R>(value)` and `dismissPending()` act on whatever is pending, guarded by type.

## Render with Views (XML)

Collect `host.pending` inside `repeatOnLifecycle(STARTED)` and show one dialog per emission, built from `pending.interaction`. Dismiss the previous dialog on every emission, and dismiss the current one when the collector stops, so rotation tears the dialog down with the Activity and the restarted collector shows it again from the still-pending Interaction. Wire the dialog's cancel listener, not its dismiss listener, to `host.dismiss`, because the collector itself dismisses dialogs when an Answer arrives.

```kotlin
fun LifecycleOwner.showInteractionDialogs(host: InteractionHost, dialogFor: (Interaction<*>) -> Dialog) {
	lifecycleScope.launch {
		repeatOnLifecycle(Lifecycle.State.STARTED) {
			var shown: Dialog? = null
			try {
				host.pending.collect { pending ->
					shown?.dismiss()
					shown = pending?.let { dialogFor(it.interaction) }?.also { it.show() }
				}
			} finally {
				shown?.dismiss()
			}
		}
	}
}
```

```kotlin
showInteractionDialogs(host = viewModel.interactionHost) { interaction ->
	when (val i = interaction as BillInteraction<*>) {
		is BillInteraction.ConfirmPromoCodeLoss ->
			AlertDialog.Builder(this)
				.setMessage(getString(R.string.confirm_code_loss, i.code))
				.setPositiveButton(R.string.release) { _, _ -> host.answer(i, true) }
				.setNegativeButton(R.string.keep) { _, _ -> host.answer(i, false) }
				.setOnCancelListener { host.dismiss(i) }
				.create()
		is BillInteraction.RetryRelease -> ...
		is BillInteraction.PickBill -> ...
	}
}
```


## Render with DialogFragment

Use a generic, reusable `DialogFragment` such as `WnAlertDialog` from the Design System. It is configured through arguments, knows nothing about Fermata, and reports clicks to its Activity or parent Fragment through `OnDialogListener.onPositiveClick(requestCode, data)`. The listener answers the host's current pending Interaction, so no host reference ever reaches the dialog and the FragmentManager can recreate it freely on rotation.

The Activity collects `host.pending` and shows each pending Interaction under a tag that carries `PendingInteraction.id`. A dialog restored after rotation has the tag for the still-pending id and is left alone; a new ask, even with equal content, has a new id and replaces any Interaction dialog still in the FragmentManager, including one that just dismissed itself on click. Nothing Fermata-specific goes into the dialog itself.

```kotlin
lifecycleScope.launch {
	repeatOnLifecycle(Lifecycle.State.STARTED) {
		viewModel.interactionHost.pending.collect { pending ->
			val wantedTag = pending?.let { "$TAG:${it.id}" }
			supportFragmentManager.fragments
				.filterIsInstance<WnAlertDialog>()
				.filter { it.tag.orEmpty().startsWith(TAG) && it.tag != wantedTag }
				.forEach { it.dismissAllowingStateLoss() }
			if (pending != null && supportFragmentManager.findFragmentByTag(wantedTag) == null) {
				dialogFor(pending).show(supportFragmentManager, wantedTag)
			}
		}
	}
}
```

The Activity maps each Interaction to a dialog with its own request code and forwards the listener callbacks to a ViewModel method named for that decision. The ViewModel answers its own pending Interaction, so the Activity never touches the host and the dialog never knows it exists:

```kotlin
class BillViewModel(...) : ViewModel() {
	val interactionHost = InteractionHost()

	fun confirmPromoCodeLoss(release: Boolean) {
		interactionHost.answerPending<ConfirmPromoCodeLoss, Boolean>(release)
	}

	fun retryRelease(retry: Boolean) {
		interactionHost.answerPending<RetryRelease, Boolean>(retry)
	}

	fun pickBill(bill: Bill) {
		interactionHost.answerPending<PickBill, Bill>(bill)
	}

	fun dismissPending() {
		interactionHost.dismissPending()
	}
}

class BillActivity : FragmentActivity(), WnAlertDialog.OnDialogListener {
	private fun dialogFor(pending: PendingInteraction) = when (val i = pending.interaction as BillInteraction<*>) {
		is ConfirmPromoCodeLoss -> WnAlertDialog.newInstance(
			title = getString(R.string.confirm_code_loss_title),
			description = getString(R.string.confirm_code_loss, i.code),
			positiveText = getString(R.string.release),
			negativeText = getString(R.string.keep),
			requestCode = REQUEST_CONFIRM_PROMO_CODE_LOSS,
		)
		is RetryRelease -> ...
	}

	override fun onPositiveClick(requestCode: Int, data: Parcelable?) {
		when (requestCode) {
			REQUEST_CONFIRM_PROMO_CODE_LOSS -> viewModel.confirmPromoCodeLoss(release = true)
			REQUEST_RETRY_RELEASE -> viewModel.retryRelease(retry = true)
		}
	}

	override fun onNegativeClick(requestCode: Int, data: Parcelable?) {
		when (requestCode) {
			REQUEST_CONFIRM_PROMO_CODE_LOSS -> viewModel.confirmPromoCodeLoss(release = false)
			REQUEST_RETRY_RELEASE -> viewModel.retryRelease(retry = false)
		}
	}
}
```

`InteractionHost.answerPending<I, R>(value)` answers the pending Interaction only if it is an `I`, and returns whether the answer landed, which covers a late click from a dialog the Activity has already replaced. `dismissPending()` dismisses whatever is pending. Cancellation (back press, tap outside) forwards to `dismissPending`.

The sample has no Design System dependency, so `SampleAlertDialog` stands in for `WnAlertDialog` with the same shape, and `FragmentBillActivity` is a complete version of this.

## Test

Use-case tests script the port and fail loudly on any unscripted Interaction. When several scripted types match, the most specific wins regardless of registration order, so a sealed parent that fixes the Answer type, such as `sealed interface YesNo : Interaction<Boolean>`, can act as a fallback. `answersInOrder` scripts a sequence for loops such as retry, handing out each answer once and failing when they run out:

```kotlin
val port = ScriptedInteractionPort {
	on(BillInteraction.ConfirmPromoCodeLoss::class) { true }
	answersInOrder(BillInteraction.RetryRelease::class, listOf(true, false))
	dismiss(BillInteraction.PickBill::class)
}
val outcome = useCase(itemId, port)
assertEquals(listOf(ConfirmPromoCodeLoss("PROMO-A"), RetryRelease("timeout"), RetryRelease("timeout")), port.asked)
```

ViewModel tests drive the real host with Turbine:

```kotlin
viewModel.interactionHost.pending.test {
	awaitNothingPending()
	viewModel.onRemoveClicked(itemId)
	val confirm = awaitPending<BillInteraction.ConfirmPromoCodeLoss>()
	viewModel.interactionHost.answer(confirm, true)
	awaitNothingPending()
}
```

## Scope

A pending Interaction survives configuration changes because it is ViewModel state. It does not survive process death; see [ADR 0001](docs/adr/0001-pending-interaction-does-not-survive-process-death.md).

## Sample

`:sample` is an unpublished app that removes items from a bill both ways, ViewModel-side and use-case-side. Every removal first asks `ConfirmRemove`, then, if the item carries the last use of a promo code, `ConfirmPromoCodeLoss`, and a flaky release exercises `RetryRelease`, so one tap can chain three sequential Interactions. `SampleActivity` is the Compose screen and `FragmentBillActivity` is the same screen built with Views, answering through `DialogFragment`s. Both share the same `BillViewModel` and Interactions.

## Publishing

Artifacts are published to Maven Central from `io.github.phompang.fermata` with the [vanniktech maven-publish plugin](https://github.com/vanniktech/gradle-maven-publish-plugin). POM metadata lives in `gradle.properties` (project-wide) and each module's `gradle.properties` (`POM_ARTIFACT_ID`, `POM_NAME`, `POM_DESCRIPTION`).

```
./gradlew publishToMavenLocal
```

Local publishing needs no credentials while `VERSION_NAME` is a `-SNAPSHOT`. Releases are cut by pushing a tag `vX.Y.Z`; the `Publish` workflow derives `VERSION_NAME` from the tag, runs the tests, signs, and uploads to Maven Central. It expects these repository secrets in the `maven-central` environment: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` (Central Portal user token), `SIGNING_KEY` (ASCII-armored private key), `SIGNING_KEY_ID`, `SIGNING_KEY_PASSWORD`. The `CI` workflow runs tests, assembles every module, and publishes to the runner's local Maven repository on each push and pull request so a broken POM is caught before a release.
