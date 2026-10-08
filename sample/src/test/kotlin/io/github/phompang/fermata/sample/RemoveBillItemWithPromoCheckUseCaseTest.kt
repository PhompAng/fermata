package io.github.phompang.fermata.sample

import io.github.phompang.fermata.InteractionDismissedException
import io.github.phompang.fermata.sample.RemoveBillItemWithPromoCheckUseCase.Outcome
import io.github.phompang.fermata.test.ScriptedInteractionPort
import io.github.phompang.fermata.test.UnscriptedInteractionException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoveBillItemWithPromoCheckUseCaseTest {
	private val repository = BillRepository()
	private val useCase = RemoveBillItemWithPromoCheckUseCase(repository)

	private val itemNames: List<String>
		get() = repository.items().map { it.name }

	@Test
	fun removesAnItemWithoutAPromoCodeWithoutAsking() =
		runTest {
			val port = ScriptedInteractionPort {}

			val outcome = useCase(CROISSANT, port)

			assertEquals(Outcome.Removed, outcome)
			assertEquals(emptyList(), port.asked)
			assertEquals(listOf("Latte", "Cake", "Tea"), itemNames)
		}

	@Test
	fun removesAnItemWhoseCodeOthersStillCarryWithoutAsking() =
		runTest {
			val port = ScriptedInteractionPort {}

			val outcome = useCase(CAKE, port)

			assertEquals(Outcome.Removed, outcome)
			assertEquals(emptyList(), port.asked)
			assertEquals(listOf("Latte", "Croissant", "Tea"), itemNames)
		}

	@Test
	fun keepingTheCodeRemovesTheItemWithoutReleasing() =
		runTest {
			val port = ScriptedInteractionPort { on(SampleInteraction.ConfirmPromoCodeLoss::class) { false } }

			val outcome = useCase(LATTE, port)

			assertEquals(Outcome.RemovedKeepingCode("PROMO-A"), outcome)
			assertEquals(listOf(SampleInteraction.ConfirmPromoCodeLoss("PROMO-A")), port.asked)
			assertEquals(listOf("Croissant", "Cake", "Tea"), itemNames)
		}

	@Test
	fun releasingRetriesOnceAfterTheFirstFailureAndSucceeds() =
		runTest {
			val port =
				ScriptedInteractionPort {
					on(SampleInteraction.ConfirmPromoCodeLoss::class) { true }
					answersInOrder(SampleInteraction.RetryRelease::class, listOf(true))
				}

			val outcome = useCase(LATTE, port)

			assertEquals(Outcome.RemovedAndReleased("PROMO-A"), outcome)
			assertEquals(
				listOf(
					SampleInteraction.ConfirmPromoCodeLoss("PROMO-A"),
					SampleInteraction.RetryRelease("Promotion service timed out releasing PROMO-A"),
				),
				port.asked,
			)
			assertEquals(listOf("Croissant", "Cake", "Tea"), itemNames)
		}

	@Test
	fun givingUpOnTheReleaseLeavesTheItemInPlace() =
		runTest {
			val port =
				ScriptedInteractionPort {
					on(SampleInteraction.ConfirmPromoCodeLoss::class) { true }
					on(SampleInteraction.RetryRelease::class) { false }
				}

			val outcome = useCase(LATTE, port)

			assertEquals(Outcome.GaveUpReleasing("PROMO-A"), outcome)
			assertEquals(listOf("Latte", "Croissant", "Cake", "Tea"), itemNames)
		}

	@Test
	fun dismissingTheConfirmationAbortsBeforeAnythingChanges() =
		runTest {
			val port = ScriptedInteractionPort { dismiss(SampleInteraction.ConfirmPromoCodeLoss::class) }

			assertFailsWith<InteractionDismissedException> { useCase(LATTE, port) }

			assertEquals(listOf("Latte", "Croissant", "Cake", "Tea"), itemNames)
		}

	@Test
	fun anUnexpectedQuestionFailsTheTestInsteadOfHanging() =
		runTest {
			val port = ScriptedInteractionPort { on(SampleInteraction.ConfirmPromoCodeLoss::class) { true } }

			val failure = assertFailsWith<UnscriptedInteractionException> { useCase(LATTE, port) }

			assertEquals(SampleInteraction.RetryRelease("Promotion service timed out releasing PROMO-A"), failure.interaction)
			assertEquals(listOf("Latte", "Croissant", "Cake", "Tea"), itemNames)
		}

	private companion object {
		const val LATTE = 1
		const val CROISSANT = 2
		const val CAKE = 3
	}
}
