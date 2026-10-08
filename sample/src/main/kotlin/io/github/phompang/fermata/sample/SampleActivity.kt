package io.github.phompang.fermata.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.phompang.fermata.InteractionHost
import io.github.phompang.fermata.compose.InteractionHandler

class SampleActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			MaterialTheme {
				BillScreen(viewModel = viewModel())
			}
		}
	}
}

@Composable
private fun BillScreen(viewModel: BillViewModel) {
	val state by viewModel.uiState.collectAsState()
	Scaffold { padding ->
		Column(
			modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(text = stringResource(R.string.title), style = MaterialTheme.typography.headlineSmall)
			ModeSelector(mode = state.mode, onSelect = viewModel::onModeSelected)
			LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				items(state.items, key = { it.id }) { item ->
					BillItemRow(item = item, onRemove = { viewModel.onRemoveClicked(item.id) })
				}
			}
			state.lastOutcome?.let { Text(text = stringResource(R.string.last_outcome, it)) }
			Button(onClick = viewModel::onResetClicked, modifier = Modifier.fillMaxWidth()) {
				Text(text = stringResource(R.string.reset))
			}
			val context = LocalContext.current
			TextButton(
				onClick = { context.startActivity(Intent(context, FragmentBillActivity::class.java)) },
				modifier = Modifier.fillMaxWidth(),
			) {
				Text(text = stringResource(R.string.open_fragment_sample))
			}
		}
	}
	SampleInteractionDialogs(host = viewModel.interactionHost)
}

@Composable
private fun ModeSelector(
	mode: AskingMode,
	onSelect: (AskingMode) -> Unit,
) {
	Column {
		ModeOption(
			label = stringResource(R.string.mode_viewmodel_asks),
			selected = mode == AskingMode.VIEW_MODEL,
			onSelect = { onSelect(AskingMode.VIEW_MODEL) },
		)
		ModeOption(
			label = stringResource(R.string.mode_usecase_asks),
			selected = mode == AskingMode.USE_CASE,
			onSelect = { onSelect(AskingMode.USE_CASE) },
		)
	}
}

@Composable
private fun ModeOption(
	label: String,
	selected: Boolean,
	onSelect: () -> Unit,
) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		RadioButton(selected = selected, onClick = onSelect)
		Text(text = label)
	}
}

@Composable
private fun BillItemRow(
	item: BillItem,
	onRemove: () -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		val label = item.promoCode?.let { stringResource(R.string.item_with_code, item.name, it) } ?: item.name
		Text(text = label)
		TextButton(onClick = onRemove) { Text(text = stringResource(R.string.remove)) }
	}
}

@Composable
private fun SampleInteractionDialogs(host: InteractionHost) {
	InteractionHandler<SampleInteraction<*>>(host) { interaction ->
		when (interaction) {
			is SampleInteraction.ConfirmRemove ->
				AlertDialog(
					onDismissRequest = { host.dismiss(interaction) },
					title = { Text(text = stringResource(R.string.confirm_remove_title)) },
					text = { Text(text = stringResource(R.string.confirm_remove_message, interaction.itemName)) },
					confirmButton = {
						TextButton(onClick = { host.answer(interaction, true) }) { Text(text = stringResource(R.string.remove)) }
					},
					dismissButton = {
						TextButton(onClick = { host.answer(interaction, false) }) { Text(text = stringResource(R.string.cancel)) }
					},
				)

			is SampleInteraction.ConfirmPromoCodeLoss ->
				AlertDialog(
					onDismissRequest = { host.dismiss(interaction) },
					title = { Text(text = stringResource(R.string.confirm_code_loss_title)) },
					text = { Text(text = stringResource(R.string.confirm_code_loss_message, interaction.code)) },
					confirmButton = {
						TextButton(onClick = { host.answer(interaction, true) }) { Text(text = stringResource(R.string.release)) }
					},
					dismissButton = {
						TextButton(onClick = { host.answer(interaction, false) }) { Text(text = stringResource(R.string.keep)) }
					},
				)

			is SampleInteraction.RetryRelease ->
				AlertDialog(
					onDismissRequest = { host.dismiss(interaction) },
					title = { Text(text = stringResource(R.string.retry_title)) },
					text = { Text(text = stringResource(R.string.retry_message, interaction.reason)) },
					confirmButton = {
						TextButton(onClick = { host.answer(interaction, true) }) { Text(text = stringResource(R.string.retry)) }
					},
					dismissButton = {
						TextButton(onClick = { host.answer(interaction, false) }) { Text(text = stringResource(R.string.give_up)) }
					},
				)
		}
	}
}
