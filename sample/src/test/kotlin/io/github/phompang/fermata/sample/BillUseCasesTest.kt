package io.github.phompang.fermata.sample

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BillUseCasesTest {
	private val repository = BillRepository()
	private val checkIfRemoveReleasesPromoCode = CheckIfRemoveReleasesPromoCodeUseCase(repository)
	private val removeBillItem = RemoveBillItemUseCase(repository)

	@Test
	fun checkReportsTheCodeOnlyWhenRemovingItsLastCarrier() {
		assertEquals("PROMO-A", checkIfRemoveReleasesPromoCode(LATTE))
		assertNull(checkIfRemoveReleasesPromoCode(CROISSANT))
		assertNull(checkIfRemoveReleasesPromoCode(CAKE))

		repository.remove(TEA)

		assertEquals("PROMO-B", checkIfRemoveReleasesPromoCode(CAKE))
	}

	@Test
	fun checkReportsNothingForAnUnknownItem() {
		assertNull(checkIfRemoveReleasesPromoCode(99))
	}

	@Test
	fun removeWithoutACodeOnlyRemoves() {
		removeBillItem(RemoveBillItemUseCase.Params(itemId = CROISSANT, releaseCode = null))

		assertEquals(listOf("Latte", "Cake", "Tea"), repository.items().map { it.name })
	}

	@Test
	fun removeWithACodeReleasesBeforeRemovingAndKeepsTheItemWhenTheReleaseFails() {
		assertFailsWith<IllegalStateException> {
			removeBillItem(RemoveBillItemUseCase.Params(itemId = LATTE, releaseCode = "PROMO-A"))
		}
		assertEquals(listOf("Latte", "Croissant", "Cake", "Tea"), repository.items().map { it.name })

		removeBillItem(RemoveBillItemUseCase.Params(itemId = LATTE, releaseCode = "PROMO-A"))

		assertEquals(listOf("Croissant", "Cake", "Tea"), repository.items().map { it.name })
	}

	private companion object {
		const val LATTE = 1
		const val CROISSANT = 2
		const val CAKE = 3
		const val TEA = 4
	}
}
