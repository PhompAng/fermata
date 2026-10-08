package io.github.phompang.fermata.test

import app.cash.turbine.ReceiveTurbine
import io.github.phompang.fermata.Interaction
import io.github.phompang.fermata.PendingInteraction

suspend inline fun <reified I : Interaction<*>> ReceiveTurbine<PendingInteraction?>.awaitPending(): I {
	val item = awaitItem()?.interaction
	return item as? I
		?: throw AssertionError("Expected pending ${I::class.qualifiedName} but was ${item?.let { it::class.qualifiedName } ?: "nothing"}")
}

suspend fun ReceiveTurbine<PendingInteraction?>.awaitNothingPending() {
	val item = awaitItem()?.interaction
	if (item != null) throw AssertionError("Expected nothing pending but ${item::class.qualifiedName} was: $item")
}
