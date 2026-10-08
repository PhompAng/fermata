package io.github.phompang.fermata.sample

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.phompang.fermata.PendingInteraction
import kotlinx.coroutines.launch

class FragmentBillActivity :
	ViewBillActivity(),
	SampleAlertDialog.OnDialogListener {
	companion object {
		private const val INTERACTION_DIALOG_TAG = "fermata.interaction"
		private const val REQUEST_CONFIRM_REMOVE = 1
		private const val REQUEST_CONFIRM_PROMO_CODE_LOSS = 2
		private const val REQUEST_RETRY_RELEASE = 3
	}

	override fun bindInteractionDialogs() {
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.interactionHost.pending.collect { pending ->
					val wantedTag = pending?.let(::tagFor)
					supportFragmentManager.fragments
						.filterIsInstance<SampleAlertDialog>()
						.filter { it.tag.orEmpty().startsWith(INTERACTION_DIALOG_TAG) && it.tag != wantedTag }
						.forEach { it.dismissAllowingStateLoss() }
					if (pending != null && supportFragmentManager.findFragmentByTag(wantedTag) == null) {
						dialogFor(pending).show(supportFragmentManager, wantedTag)
					}
				}
			}
		}
	}

	private fun tagFor(pending: PendingInteraction): String = "$INTERACTION_DIALOG_TAG:${pending.id}"

	private fun dialogFor(pending: PendingInteraction): SampleAlertDialog =
		when (val sample = pending.interaction as SampleInteraction<*>) {
			is SampleInteraction.ConfirmRemove ->
				SampleAlertDialog.newInstance(
					title = getString(R.string.confirm_remove_title),
					description = getString(R.string.confirm_remove_message, sample.itemName),
					positiveText = getString(R.string.remove),
					negativeText = getString(R.string.cancel),
					requestCode = REQUEST_CONFIRM_REMOVE,
				)

			is SampleInteraction.ConfirmPromoCodeLoss ->
				SampleAlertDialog.newInstance(
					title = getString(R.string.confirm_code_loss_title),
					description = getString(R.string.confirm_code_loss_message, sample.code),
					positiveText = getString(R.string.release),
					negativeText = getString(R.string.keep),
					requestCode = REQUEST_CONFIRM_PROMO_CODE_LOSS,
				)

			is SampleInteraction.RetryRelease ->
				SampleAlertDialog.newInstance(
					title = getString(R.string.retry_title),
					description = getString(R.string.retry_message, sample.reason),
					positiveText = getString(R.string.retry),
					negativeText = getString(R.string.give_up),
					requestCode = REQUEST_RETRY_RELEASE,
				)
		}

	override fun onPositiveClick(requestCode: Int) {
		when (requestCode) {
			REQUEST_CONFIRM_REMOVE -> viewModel.confirmRemove(remove = true)
			REQUEST_CONFIRM_PROMO_CODE_LOSS -> viewModel.confirmPromoCodeLoss(release = true)
			REQUEST_RETRY_RELEASE -> viewModel.retryRelease(retry = true)
		}
	}

	override fun onNegativeClick(requestCode: Int) {
		when (requestCode) {
			REQUEST_CONFIRM_REMOVE -> viewModel.confirmRemove(remove = false)
			REQUEST_CONFIRM_PROMO_CODE_LOSS -> viewModel.confirmPromoCodeLoss(release = false)
			REQUEST_RETRY_RELEASE -> viewModel.retryRelease(retry = false)
		}
	}

	override fun onCancel(requestCode: Int) {
		when (requestCode) {
			REQUEST_CONFIRM_REMOVE, REQUEST_CONFIRM_PROMO_CODE_LOSS, REQUEST_RETRY_RELEASE -> viewModel.dismissPending()
		}
	}
}
