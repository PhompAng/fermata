package io.github.phompang.fermata.sample

import io.github.phompang.fermata.Interaction

sealed interface SampleInteraction<R> : Interaction<R> {
	data class ConfirmRemove(val itemName: String) : SampleInteraction<Boolean>

	data class ConfirmPromoCodeLoss(val code: String) : SampleInteraction<Boolean>

	data class RetryRelease(val reason: String) : SampleInteraction<Boolean>
}
