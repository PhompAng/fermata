package io.github.phompang.fermata.test

import app.cash.turbine.test
import io.github.phompang.fermata.Interaction
import io.github.phompang.fermata.InteractionDismissedException
import io.github.phompang.fermata.InteractionHost
import io.github.phompang.fermata.askOrNull
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScriptedInteractionPortTest {
	private data class Confirm(val message: String) : Interaction<Boolean>

	private data class Choose(val options: List<String>) : Interaction<String>

	private sealed interface BillInteraction : Interaction<Boolean> {
		data class ConfirmPromoCodeLoss(val code: String) : BillInteraction

		data class RetryRelease(val reason: String) : BillInteraction
	}

	@Test
	fun answersFromTheScriptAndRecordsWhatWasAsked() =
		runTest {
			val port =
				ScriptedInteractionPort {
					on(Confirm::class) { it.message == "yes?" }
					on(Choose::class) { it.options.last() }
				}

			assertTrue(port.ask(Confirm("yes?")))
			assertEquals("b", port.ask(Choose(listOf("a", "b"))))
			assertEquals(listOf(Confirm("yes?"), Choose(listOf("a", "b"))), port.asked)
		}

	@Test
	fun failsLoudlyOnAnUnscriptedInteraction() =
		runTest {
			val port = ScriptedInteractionPort { on(Confirm::class) { true } }

			val failure = assertFailsWith<UnscriptedInteractionException> { port.ask(Choose(emptyList())) }
			assertEquals(Choose(emptyList()), failure.interaction)
		}

	@Test
	fun scriptedDismissalBehavesLikeTheRealHost() =
		runTest {
			val port = ScriptedInteractionPort { dismiss(Confirm::class) }

			assertFailsWith<InteractionDismissedException> { port.ask(Confirm("x")) }
			assertNull(port.askOrNull(Confirm("x")))
		}

	@Test
	fun awaitPendingAssertsTheInteractionType() =
		runTest {
			val host = InteractionHost()
			host.pending.test {
				awaitNothingPending()
				val asking = async(start = CoroutineStart.UNDISPATCHED) { host.ask(Confirm("x")) }
				val pending = awaitPending<Confirm>()
				assertEquals("x", pending.message)
				host.answer(pending, true)
				awaitNothingPending()
				assertTrue(asking.await())
			}
		}

	@Test
	fun awaitPendingFailsOnTheWrongType() =
		runTest {
			val host = InteractionHost()
			host.pending.test {
				awaitNothingPending()
				val asking = async(start = CoroutineStart.UNDISPATCHED) { host.ask(Confirm("x")) }
				assertFailsWith<AssertionError> { awaitPending<Choose>() }
				host.dismiss(host.pending.value!!.interaction)
				cancelAndIgnoreRemainingEvents()
				assertFailsWith<InteractionDismissedException> { asking.await() }
			}
		}

	@Test
	fun theMostSpecificScriptedTypeWinsRegardlessOfRegistrationOrder() =
		runTest {
			val port =
				ScriptedInteractionPort {
					on(BillInteraction::class) { false }
					on(BillInteraction.RetryRelease::class) { true }
				}

			assertTrue(port.ask(BillInteraction.RetryRelease("timeout")))
			assertEquals(false, port.ask(BillInteraction.ConfirmPromoCodeLoss("PROMO-A")))
		}

	@Test
	fun answersInOrderHandsOutEachAnswerOnceThenFailsLoudly() =
		runTest {
			val port =
				ScriptedInteractionPort {
					answersInOrder(BillInteraction.RetryRelease::class, listOf(true, false))
				}

			assertTrue(port.ask(BillInteraction.RetryRelease("first")))
			assertEquals(false, port.ask(BillInteraction.RetryRelease("second")))
			assertFailsWith<UnscriptedInteractionException> { port.ask(BillInteraction.RetryRelease("third")) }
			assertEquals(3, port.asked.size)
		}
}
