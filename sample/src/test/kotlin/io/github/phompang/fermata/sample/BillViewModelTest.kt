package io.github.phompang.fermata.sample

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.phompang.fermata.PendingInteraction
import io.github.phompang.fermata.test.awaitNothingPending
import io.github.phompang.fermata.test.awaitPending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class BillViewModelTest {
	private val dispatcher = StandardTestDispatcher()
	private lateinit var viewModel: BillViewModel

	@Before
	fun setUp() {
		Dispatchers.setMain(dispatcher)
		viewModel = BillViewModel()
	}

	@After
	fun tearDown() {
		Dispatchers.resetMain()
	}

	private val itemNames: List<String>
		get() = viewModel.uiState.value.items.map { it.name }

	private val fullBill = listOf("Latte", "Croissant", "Cake", "Tea")

	private suspend fun TestScope.clickRemoveAndConfirm(
		turbine: ReceiveTurbine<PendingInteraction?>,
		itemId: Int,
		itemName: String,
	) {
		viewModel.onRemoveClicked(itemId)
		advanceUntilIdle()
		val confirm = turbine.awaitPending<SampleInteraction.ConfirmRemove>()
		assertEquals(itemName, confirm.itemName)
		viewModel.confirmRemove(remove = true)
		advanceUntilIdle()
		turbine.awaitNothingPending()
	}

	@Test
	fun everyRemovalStartsWithAConfirmation() =
		runTest(dispatcher) {
			viewModel.interactionHost.pending.test {
				awaitNothingPending()
				viewModel.onRemoveClicked(CROISSANT)
				advanceUntilIdle()

				assertEquals("Croissant", awaitPending<SampleInteraction.ConfirmRemove>().itemName)
				assertEquals(fullBill, itemNames)
			}
		}

	@Test
	fun decliningTheConfirmationKeepsTheItem() =
		runTest(dispatcher) {
			viewModel.interactionHost.pending.test {
				awaitNothingPending()
				viewModel.onRemoveClicked(CROISSANT)
				advanceUntilIdle()
				awaitPending<SampleInteraction.ConfirmRemove>()

				viewModel.confirmRemove(remove = false)
				advanceUntilIdle()

				awaitNothingPending()
				assertEquals(fullBill, itemNames)
				assertEquals("Kept Croissant", viewModel.uiState.value.lastOutcome)
			}
		}

	@Test
	fun dismissingTheConfirmationAbortsWithoutAnOutcome() =
		runTest(dispatcher) {
			viewModel.interactionHost.pending.test {
				awaitNothingPending()
				viewModel.onRemoveClicked(CROISSANT)
				advanceUntilIdle()
				awaitPending<SampleInteraction.ConfirmRemove>()

				viewModel.dismissPending()
				advanceUntilIdle()

				awaitNothingPending()
				assertEquals(fullBill, itemNames)
				assertNull(viewModel.uiState.value.lastOutcome)
			}
		}

	@Test
	fun removingAnItemWithoutAPromoCodeAsksNothingMore() =
		runTest(dispatcher) {
			viewModel.interactionHost.pending.test {
				awaitNothingPending()

				clickRemoveAndConfirm(this, CROISSANT, "Croissant")
				expectNoEvents()

				assertEquals(listOf("Latte", "Cake", "Tea"), itemNames)
				assertEquals("Removed", viewModel.uiState.value.lastOutcome)
			}
		}

	@Test
	fun removingAnItemWhoseCodeOthersShareAsksNothingMore() =
		runTest(dispatcher) {
			viewModel.interactionHost.pending.test {
				awaitNothingPending()

				clickRemoveAndConfirm(this, CAKE, "Cake")
				expectNoEvents()

				assertEquals(listOf("Latte", "Croissant", "Tea"), itemNames)
			}
		}

	@Test
	fun keepingTheCodeRemovesTheItemWithoutReleasing() =
		runTest(dispatcher) {
			viewModel.interactionHost.pending.test {
				awaitNothingPending()
				clickRemoveAndConfirm(this, LATTE, "Latte")
				val confirm = awaitPending<SampleInteraction.ConfirmPromoCodeLoss>()
				assertEquals("PROMO-A", confirm.code)

				viewModel.confirmPromoCodeLoss(release = false)
				advanceUntilIdle()

				awaitNothingPending()
				assertEquals(listOf("Croissant", "Cake", "Tea"), itemNames)
				assertEquals("Removed", viewModel.uiState.value.lastOutcome)
			}
		}

	@Test
	fun releasingRetriesAfterAFailedReleaseAndThenSucceeds() =
		runTest(dispatcher) {
			viewModel.interactionHost.pending.test {
				awaitNothingPending()
				clickRemoveAndConfirm(this, LATTE, "Latte")
				awaitPending<SampleInteraction.ConfirmPromoCodeLoss>()

				viewModel.confirmPromoCodeLoss(release = true)
				advanceUntilIdle()
				awaitNothingPending()
				val retry = awaitPending<SampleInteraction.RetryRelease>()
				assertEquals("Promotion service timed out releasing PROMO-A", retry.reason)
				assertEquals(fullBill, itemNames)

				viewModel.retryRelease(retry = true)
				advanceUntilIdle()

				awaitNothingPending()
				assertEquals(listOf("Croissant", "Cake", "Tea"), itemNames)
				assertEquals("Removed and released PROMO-A", viewModel.uiState.value.lastOutcome)
			}
		}

	@Test
	fun givingUpOnTheReleaseLeavesTheItemInPlace() =
		runTest(dispatcher) {
			viewModel.interactionHost.pending.test {
				awaitNothingPending()
				clickRemoveAndConfirm(this, LATTE, "Latte")
				awaitPending<SampleInteraction.ConfirmPromoCodeLoss>()
				viewModel.confirmPromoCodeLoss(release = true)
				advanceUntilIdle()
				awaitNothingPending()
				awaitPending<SampleInteraction.RetryRelease>()

				viewModel.retryRelease(retry = false)
				advanceUntilIdle()

				awaitNothingPending()
				assertEquals(fullBill, itemNames)
				assertEquals("Gave up releasing PROMO-A", viewModel.uiState.value.lastOutcome)
			}
		}

	@Test
	fun dismissingThePromoCodeConfirmationAbortsTheRemoval() =
		runTest(dispatcher) {
			viewModel.interactionHost.pending.test {
				awaitNothingPending()
				clickRemoveAndConfirm(this, LATTE, "Latte")
				awaitPending<SampleInteraction.ConfirmPromoCodeLoss>()

				viewModel.dismissPending()
				advanceUntilIdle()

				awaitNothingPending()
				assertEquals(fullBill, itemNames)
				assertNull(viewModel.uiState.value.lastOutcome)
			}
		}

	@Test
	fun aLateAnswerForTheWrongDialogIsIgnored() =
		runTest(dispatcher) {
			viewModel.interactionHost.pending.test {
				awaitNothingPending()
				viewModel.onRemoveClicked(LATTE)
				advanceUntilIdle()
				awaitPending<SampleInteraction.ConfirmRemove>()

				viewModel.confirmPromoCodeLoss(release = true)
				viewModel.retryRelease(retry = true)
				advanceUntilIdle()
				expectNoEvents()

				viewModel.confirmRemove(remove = false)
				advanceUntilIdle()
				awaitNothingPending()
				assertEquals("Kept Latte", viewModel.uiState.value.lastOutcome)
			}
		}

	@Test
	fun useCaseModeAsksThroughThePortAndReportsTheOutcome() =
		runTest(dispatcher) {
			viewModel.onModeSelected(AskingMode.USE_CASE)
			viewModel.interactionHost.pending.test {
				awaitNothingPending()
				clickRemoveAndConfirm(this, LATTE, "Latte")
				awaitPending<SampleInteraction.ConfirmPromoCodeLoss>()
				viewModel.confirmPromoCodeLoss(release = true)
				advanceUntilIdle()
				awaitNothingPending()
				awaitPending<SampleInteraction.RetryRelease>()

				viewModel.retryRelease(retry = true)
				advanceUntilIdle()

				awaitNothingPending()
				assertEquals(listOf("Croissant", "Cake", "Tea"), itemNames)
				assertEquals(
					RemoveBillItemWithPromoCheckUseCase.Outcome.RemovedAndReleased("PROMO-A").toString(),
					viewModel.uiState.value.lastOutcome,
				)
			}
		}

	@Test
	fun removingAnUnknownItemDoesNothing() =
		runTest(dispatcher) {
			viewModel.interactionHost.pending.test {
				awaitNothingPending()

				viewModel.onRemoveClicked(99)
				advanceUntilIdle()

				expectNoEvents()
				assertEquals(fullBill, itemNames)
			}
		}

	@Test
	fun resetRestoresTheBillAndClearsTheOutcome() =
		runTest(dispatcher) {
			viewModel.interactionHost.pending.test {
				awaitNothingPending()
				clickRemoveAndConfirm(this, CROISSANT, "Croissant")
				assertEquals("Removed", viewModel.uiState.value.lastOutcome)

				viewModel.onResetClicked()

				assertEquals(fullBill, itemNames)
				assertNull(viewModel.uiState.value.lastOutcome)
			}
		}

	private companion object {
		const val LATTE = 1
		const val CROISSANT = 2
		const val CAKE = 3
	}
}
