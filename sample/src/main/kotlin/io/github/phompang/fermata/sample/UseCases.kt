package io.github.phompang.fermata.sample

import io.github.phompang.fermata.InteractionPort

class CheckIfRemoveReleasesPromoCodeUseCase(private val repository: BillRepository) {
	operator fun invoke(itemId: Int): String? = repository.codeReleasedBy(itemId)
}

class RemoveBillItemUseCase(private val repository: BillRepository) {
	data class Params(val itemId: Int, val releaseCode: String?)

	operator fun invoke(params: Params) {
		params.releaseCode?.let(repository::releaseCode)
		repository.remove(params.itemId)
	}
}

class RemoveBillItemWithPromoCheckUseCase(private val repository: BillRepository) {
	sealed interface Outcome {
		data object Removed : Outcome

		data class RemovedAndReleased(val code: String) : Outcome

		data class RemovedKeepingCode(val code: String) : Outcome

		data class GaveUpReleasing(val code: String) : Outcome
	}

	suspend operator fun invoke(
		itemId: Int,
		port: InteractionPort,
	): Outcome {
		val code = repository.codeReleasedBy(itemId) ?: return Outcome.Removed.also { repository.remove(itemId) }
		val release = port.ask(SampleInteraction.ConfirmPromoCodeLoss(code))
		if (!release) {
			repository.remove(itemId)
			return Outcome.RemovedKeepingCode(code)
		}
		while (true) {
			try {
				repository.releaseCode(code)
				repository.remove(itemId)
				return Outcome.RemovedAndReleased(code)
			} catch (e: IllegalStateException) {
				val retry = port.ask(SampleInteraction.RetryRelease(e.message ?: "Unknown error"))
				if (!retry) return Outcome.GaveUpReleasing(code)
			}
		}
	}
}
