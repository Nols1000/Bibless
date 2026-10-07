package com.github.nols1000.bibless.wear

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.input.RemoteInputIntentHelper
import com.github.nols1000.bibless.normalizeAthleteId

@Composable
fun AddBarcodeScreen(onSave: (name: String, athleteId: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var athleteId by rememberSaveable { mutableStateOf("") }
    val normalized = normalizeAthleteId(athleteId)

    val nameInput = rememberTextInput("Name") { name = it }
    val idInput = rememberTextInput("Athlete ID") { athleteId = it }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = { onSave(name, athleteId) }, enabled = normalized != null) {
                Text("Save")
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ListHeaderDefaults.minimumTopListContentPadding,
                            ListHeaderDefaults.minimumBottomListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                ) { Text("Add barcode") }
            }
            item {
                FilledTonalButton(
                    onClick = nameInput,
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .fillMaxWidth(),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("Name") },
                    secondaryLabel = { Text(name.ifEmpty { "Optional" }) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = idInput,
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .fillMaxWidth(),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("Athlete ID") },
                    secondaryLabel = {
                        Text(
                            when {
                                athleteId.isEmpty() -> "A1234567"
                                normalized == null -> "Invalid: $athleteId"
                                else -> normalized
                            },
                        )
                    },
                )
            }
        }
    }
}

/** Returns a callback that opens the system text input (keyboard / voice) and reports the result. */
@Composable
private fun rememberTextInput(label: String, onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.let { RemoteInput.getResultsFromIntent(it) }?.getCharSequence(label)
        if (text != null) onResult(text.toString())
    }
    return {
        val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(
            intent,
            listOf(RemoteInput.Builder(label).setLabel(label).build()),
        )
        launcher.launch(intent)
    }
}
