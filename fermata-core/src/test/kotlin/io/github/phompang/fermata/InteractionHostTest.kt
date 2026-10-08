package io.github.phompang.fermata

import app.cash.turbine.test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InteractionHostTest {
	private data class Confirm(val message: String) : Interaction<Boolean>

	private data class Choose(val options: List<String>) : Interaction<String>

	private val host = InteractionHost()

	private val pendingInteraction: Interaction<*>?
		get() = host.pending.value?.interaction

	@Test
	fun askExposesPendingAndResumesWithTheAnswer() =
		runTest {
			host.pending.test {
				assertNull(awaitItem())
				val asking = async { host.ask(Confirm("release code?")) }
				val pending = awaitItem()!!
				assertEquals(Confirm("release code?"), pending.interaction)

				host.answer(pending.interaction as Confirm, true)

				assertNull(awaitItem())
				assertTrue(asking.await())
			}
		}

	@Test
	fun dismissThrowsDismissedExceptionAndClearsPending() =
		runTest {
			host.pending.test {
				assertNull(awaitItem())
				val asking = async(start = CoroutineStart.UNDISPATCHED) { host.ask(Confirm("x")) }
				val pending = awaitItem()!!.interaction

				host.dismiss(pending)

				assertNull(awaitItem())
				val failure = assertFailsWith<InteractionDismissedException> { asking.await() }
				assertSame(pending, failure.interaction)
				assertTrue(failure is CancellationException)
				assertFalse(failure.message.orEmpty().contains("x"))
				assertTrue(failure.message.orEmpty().contains(Confirm::class.qualifiedName!!))
			}
		}

	@Test
	fun askOrNullReturnsNullOnDismissal() =
		runTest {
			val asking = async(start = CoroutineStart.UNDISPATCHED) { host.askOrNull(Confirm("x")) }
			host.dismiss(pendingInteraction!!)
			assertNull(asking.await())
		}

	@Test
	fun secondAskWaitsUntilTheFirstIsAnswered() =
		runTest {
			val first = async(start = CoroutineStart.UNDISPATCHED) { host.ask(Confirm("first")) }
			val second = async(start = CoroutineStart.UNDISPATCHED) { host.ask(Choose(listOf("a", "b"))) }
			assertEquals(Confirm("first"), pendingInteraction)

			host.answer(pendingInteraction as Confirm, false)
			assertFalse(first.await())
			assertEquals(Choose(listOf("a", "b")), pendingInteraction)

			host.answer(pendingInteraction as Choose, "b")
			assertEquals("b", second.await())
			assertNull(host.pending.value)
		}

	@Test
	fun answeringAnInteractionThatIsNotPendingIsIgnored() =
		runTest {
			val asking = async(start = CoroutineStart.UNDISPATCHED) { host.ask(Confirm("live")) }
			val stale = Confirm("live")

			assertFalse(host.answer(stale, true))
			assertFalse(host.dismiss(stale))

			assertTrue(asking.isActive)
			assertEquals(stale, pendingInteraction)
			assertTrue(host.answer(pendingInteraction as Confirm, false))
			assertFalse(asking.await())
			assertFalse(host.answer(stale, true))
		}

	@Test
	fun cancellingTheAskingCoroutineClearsPending() =
		runTest {
			val asking = launch(start = CoroutineStart.UNDISPATCHED) { host.ask(Confirm("x")) }
			assertEquals(Confirm("x"), pendingInteraction)

			asking.cancel()
			asking.join()

			assertNull(host.pending.value)
			val next = async(start = CoroutineStart.UNDISPATCHED) { host.ask(Confirm("next")) }
			assertEquals(Confirm("next"), pendingInteraction)
			host.answer(pendingInteraction as Confirm, true)
			assertTrue(next.await())
		}

	@Test
	fun answerPendingAnswersOnlyWhenThePendingTypeMatches() =
		runTest {
			val asking = async(start = CoroutineStart.UNDISPATCHED) { host.ask(Confirm("x")) }

			assertFalse(host.answerPending<Choose, String>("a"))
			assertTrue(asking.isActive)

			assertTrue(host.answerPending<Confirm, Boolean>(true))
			assertTrue(asking.await())
			assertFalse(host.answerPending<Confirm, Boolean>(true))
		}

	@Test
	fun dismissPendingDismissesWhateverIsPending() =
		runTest {
			assertFalse(host.dismissPending())
			val asking = async(start = CoroutineStart.UNDISPATCHED) { host.ask(Confirm("x")) }

			assertTrue(host.dismissPending())

			assertFailsWith<InteractionDismissedException> { asking.await() }
			assertNull(host.pending.value)
		}

	@Test
	fun consecutiveAsksWithEqualContentProduceDistinctPendingValues() =
		runTest {
			val operation =
				async(start = CoroutineStart.UNDISPATCHED) {
					host.ask(Confirm("same"))
					host.ask(Confirm("same"))
				}
			val first = host.pending.value!!

			host.answer(first.interaction as Confirm, true)
			runCurrent()
			val second = host.pending.value!!

			assertNotEquals(first, second)
			assertTrue(second.id > first.id)
			assertEquals(first.interaction, second.interaction)
			host.answer(second.interaction as Confirm, false)
			assertFalse(operation.await())
		}

	@Test
	fun secondAskWithEqualContentReachesACollectorThatRunsAfterTheOperationResumed() =
		runTest {
			var asks = 0
			val operation =
				launch(Dispatchers.Unconfined) {
					asks = 1
					host.ask(Confirm("same"))
					asks = 2
					host.ask(Confirm("same"))
				}
			val seen = mutableListOf<PendingInteraction?>()
			val collector = launch { host.pending.collect { seen += it } }
			runCurrent()
			assertEquals(listOf(Confirm("same")), seen.map { it?.interaction })

			host.answer(seen.single()!!.interaction as Confirm, true)
			assertEquals(2, asks, "operation should have resumed inline, like Main.immediate")
			runCurrent()

			assertEquals(2, seen.size, "late collector should see the second ask, saw $seen")
			assertNotEquals(seen[0], seen[1])
			host.answer(seen[1]!!.interaction as Confirm, false)
			operation.join()
			collector.cancel()
		}
}
