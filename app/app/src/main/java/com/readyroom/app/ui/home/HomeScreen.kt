package com.readyroom.app.ui.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.readyroom.app.domain.model.ChatMessage
import com.readyroom.app.domain.model.DisasterMode
import com.readyroom.app.ui.theme.ReadyRoomTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context.findActivity()

    // Remembered because shouldShowRequestPermissionRationale cannot tell
    // "never asked" from "denied for good" — both return false.
    var askedForCamera by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        askedForCamera = true
        if (granted) {
            onStartScan()
        } else {
            val blocked = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.CAMERA,
            ) == false
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (blocked) {
                        "Camera is blocked. Enable it in Settings to scan a room."
                    } else {
                        "Camera access is needed to scan a room."
                    }
                )
            }
        }
    }

    HomeContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onModeSelected = viewModel::selectMode,
        onDraftChanged = viewModel::updateDraft,
        onSend = viewModel::sendMessage,
        onScanClick = {
            when {
                context.hasCameraPermission() -> onStartScan()
                // Nothing asked yet this session. The system dialog still
                // works after a first denial, so always try it.
                !askedForCamera -> permissionLauncher.launch(Manifest.permission.CAMERA)

                // Asked already. shouldShowRequestPermissionRationale is true
                // only when the dialog can be shown again; false means the
                // user picked "Don't allow" twice and only Settings can undo it.
                activity?.shouldShowRequestPermissionRationale(
                    Manifest.permission.CAMERA,
                ) == true -> permissionLauncher.launch(Manifest.permission.CAMERA)

                else -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "Camera is blocked. Enable it in Settings to scan a room."
                        )
                    }
                    context.openAppSettings()
                }
            }
        },
        modifier = modifier,
    )
}

/** True when the camera permission is already held. */
private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/** Opens this app's detail page in system Settings. */
private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/**
 * Unwraps the Activity from a Compose [Context].
 *
 * Compose wraps the activity in a ContextWrapper chain, so a direct cast fails.
 */
private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onModeSelected: (DisasterMode) -> Unit,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { HomeTopBar() },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            ModePicker(
                selected = state.mode,
                onModeSelected = onModeSelected,
            )

            Spacer(Modifier.height(16.dp))

            ScanButton(
                mode = state.mode,
                onClick = onScanClick,
            )

            Spacer(Modifier.height(20.dp))

            AdviceChat(
                mode = state.mode,
                messages = state.messages,
                draft = state.draft,
                photo = state.session.photo,
                onDraftChanged = onDraftChanged,
                onSend = onSend,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Title block.
 *
 * Scaffold only applies status-bar insets itself when no topBar is supplied —
 * with one, the scaffold assumes the topBar handles them. This is a plain
 * Column rather than a TopAppBar, so it has to consume the inset explicitly,
 * or the title sits under the clock and battery icons.
 */
@Composable
private fun HomeTopBar() {
    Column(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 12.dp),
    ) {
        Text(
            text = "Ready Room",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Check a room before you need it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Horizontal row of chips, one per hazard type.
 *
 * Every mode is selectable. Switching modes selects the hazard the advice is
 * written for — it does not claim the app can scan for it. The scan button
 * carries that distinction instead, since scanning is the part that is not
 * built for any mode yet.
 */
@Composable
private fun ModePicker(
    selected: DisasterMode,
    onModeSelected: (DisasterMode) -> Unit,
) {
    Column {
        SectionLabel("Hazard type")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DisasterMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == selected,
                    onClick = { onModeSelected(mode) },
                    label = { Text(mode.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = mode.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = mode.accent.copy(alpha = 0.2f),
                        selectedLabelColor = mode.accent,
                        selectedLeadingIconColor = mode.accent,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ScanButton(
    mode: DisasterMode,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = mode.accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "Open camera",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = mode.blurb,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AdviceChat(
    mode: DisasterMode,
    messages: List<ChatMessage>,
    draft: String,
    photo: Bitmap?,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Keyed on mode as well as size: every mode starts with one message, so
    // keying on size alone would not scroll when switching between them.
    LaunchedEffect(mode, messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Campaign,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = mode.accent,
                )
                Spacer(Modifier.width(8.dp))
                SectionLabel("${mode.label} advice")
            }

            if (photo != null) {
                Spacer(Modifier.height(10.dp))
                ScannedPhoto(bitmap = photo, accent = mode.accent)
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                // weight, not heightIn: a Column hands each child the remaining
                // height in order, so an unweighted LazyColumn would eat all of
                // it once the transcript grows and collapse the input row below
                // to zero height.
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages) { message ->
                    ChatBubble(message = message, accent = mode.accent)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about this room…") },
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (draft.isNotBlank()) {
                            mode.accent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    accent: androidx.compose.ui.graphics.Color,
) {
    val alignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    val container = if (message.fromUser) {
        accent.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = container,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (message.fromUser) 12.dp else 2.dp,
                bottomEnd = if (message.fromUser) 2.dp else 12.dp,
            ),
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                textAlign = if (message.fromUser) TextAlign.End else TextAlign.Start,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The photo the current conversation is about.
 *
 * Shown above the transcript so it stays visible while scrolling the advice
 * underneath it — the assistant's replies refer to this image, and scrolling
 * it away would leave the references unexplained.
 */
@Composable
private fun ScannedPhoto(
    bitmap: Bitmap,
    accent: androidx.compose.ui.graphics.Color,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Image(
                bitmap = image,
                contentDescription = "Scanned room",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentScale = ContentScale.Crop,
            )

            Surface(
                color = accent.copy(alpha = 0.9f),
                shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text(
                    text = "Last scan",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Icons per mode. Extended icons are used, so the dependency must stay. */
private fun DisasterMode.icon() = when (this) {
    DisasterMode.EARTHQUAKE -> Icons.Filled.WarningAmber
    DisasterMode.FLOOD -> Icons.Filled.Water
    DisasterMode.CYCLONE -> Icons.Filled.Cyclone
    DisasterMode.FIRE -> Icons.Filled.LocalFireDepartment
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun HomePreview() {
    ReadyRoomTheme(dynamicColor = false) {
        HomeContent(
            state = HomeUiState(
                mode = DisasterMode.FLOOD,
                sessions = mapOf(
                    DisasterMode.FLOOD to ChatSession(
                        messages = listOf(
                            ChatMessage(
                                "Go higher, never lower. Do not shelter under furniture.",
                                fromUser = false,
                            ),
                            ChatMessage("What about the table in the middle?", fromUser = true),
                            ChatMessage(
                                "In a flood, a table is not shelter — water rises and can " +
                                    "float or trap you. Move to higher ground instead.",
                                fromUser = false,
                            ),
                        ),
                    ),
                ),
            ),
            snackbarHostState = SnackbarHostState(),
            onModeSelected = {},
            onDraftChanged = {},
            onSend = {},
            onScanClick = {},
        )
    }
}