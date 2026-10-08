package io.github.phompang.fermata.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.phompang.fermata.InteractionHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AskingMode { VIEW_MODEL, USE_CASE }

data class BillUiState(
	val items: List<BillItem>,
	val mode: AskingMode,
	val lastOutcome: String?,
)

class BillViewModel : ViewModel() {
	private val repository = BillRepository()
	private val checkIfRemoveReleasesPromoCode = CheckIfRemoveReleasesPromoCodeUseCase(repository)
	private val removeBillItem = RemoveBillItemUseCase(repository)
	private val removeBillItemWithPromoCheck = RemoveBillItemWithPromoCheckUseCase(repository)

	val interactionHost = InteractionHost()

	private val _uiState =
		MutableStateFlow(BillUiState(items = repository.items(), mode = AskingMode.VIEW_MODEL, lastOutcome = null))
	val uiState: StateFlow<BillUiState> = _uiState.asStateFlow()

	fun onModeSelected(mode: AskingMode) {
		_uiState.update { it.copy(mode = mode) }
	}

	fun onResetClicked() {
		repository.reset()
		_uiState.update { it.copy(items = repository.items(), lastOutcome = null) }
	}

	fun confirmRemove(remove: Boolean) {
		interactionHost.answerPending<SampleInteraction.ConfirmRemove, Boolean>(remove)
	}

	fun confirmPromoCodeLoss(release: Boolean) {
		interactionHost.answerPending<SampleInteraction.ConfirmPromoCodeLoss, Boolean>(release)
	}

	fun retryRelease(retry: Boolean) {
		interactionHost.answerPending<SampleInteraction.RetryRelease, Boolean>(retry)
	}

	fun dismissPending() {
		interactionHost.dismissPending()
	}

	fun onRemoveClicked(itemId: Int) {
		val item = _uiState.value.items.firstOrNull { it.id == itemId } ?: return
		viewModelScope.launch {
			val outcome =
				if (!interactionHost.ask(SampleInteraction.ConfirmRemove(item.name))) {
					"Kept ${item.name}"
				} else {
					when (_uiState.value.mode) {
						AskingMode.VIEW_MODEL -> removeAskingHere(itemId)
						AskingMode.USE_CASE -> removeBillItemWithPromoCheck(itemId, interactionHost).toString()
					}
				}
			_uiState.update { it.copy(items = repository.items(), lastOutcome = outcome) }
		}
	}

	private suspend fun removeAskingHere(itemId: Int): String {
		val code = checkIfRemoveReleasesPromoCode(itemId)
		val release = code != null && interactionHost.ask(SampleInteraction.ConfirmPromoCodeLoss(code))
		while (true) {
			try {
				removeBillItem(RemoveBillItemUseCase.Params(itemId = itemId, releaseCode = code.takeIf { release }))
				return if (release) "Removed and released $code" else "Removed"
			} catch (e: IllegalStateException) {
				val retry = interactionHost.ask(SampleInteraction.RetryRelease(e.message ?: "Unknown error"))
				if (!retry) return "Gave up releasing $code"
			}
		}
	}
}
