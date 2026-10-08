package io.github.phompang.fermata

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InteractionHost : InteractionPort {
	private class Slot<R>(val interaction: Interaction<R>, val deferred: CompletableDeferred<R>)

	private val mutex = Mutex()
	private val nextId = AtomicLong(0)
	private val slot = AtomicReference<Slot<*>?>(null)
	private val _pending = MutableStateFlow<PendingInteraction?>(null)

	val pending: StateFlow<PendingInteraction?> = _pending.asStateFlow()

	override suspend fun <R> ask(interaction: Interaction<R>): R =
		mutex.withLock {
			val handle = PendingInteraction(id = nextId.incrementAndGet(), interaction = interaction)
			val current = Slot(interaction, CompletableDeferred())
			slot.set(current)
			_pending.value = handle
			try {
				current.deferred.await()
			} finally {
				release(current)
			}
		}

	fun <R> answer(interaction: Interaction<R>, value: R): Boolean {
		val current = take(interaction) ?: return false
		@Suppress("UNCHECKED_CAST")
		return (current.deferred as CompletableDeferred<R>).complete(value)
	}

	fun dismiss(interaction: Interaction<*>): Boolean {
		val current = take(interaction) ?: return false
		return current.deferred.completeExceptionally(InteractionDismissedException(interaction))
	}

	inline fun <reified I : Interaction<R>, R> answerPending(value: R): Boolean {
		val current = pending.value?.interaction as? I ?: return false
		return answer(current, value)
	}

	fun dismissPending(): Boolean {
		val current = pending.value?.interaction ?: return false
		return dismiss(current)
	}

	private fun take(interaction: Interaction<*>): Slot<*>? {
		val current = slot.get() ?: return null
		if (current.interaction !== interaction) return null
		return if (release(current)) current else null
	}

	private fun release(current: Slot<*>): Boolean {
		if (!slot.compareAndSet(current, null)) return false
		_pending.value = null
		return true
	}
}
