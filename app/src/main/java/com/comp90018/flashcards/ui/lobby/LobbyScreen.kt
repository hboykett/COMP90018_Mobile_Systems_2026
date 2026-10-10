package com.comp90018.flashcards.ui.lobby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.comp90018.flashcards.ui.deck.DeckSummaryViewModel
import kotlinx.coroutines.launch

/**
 * The host's 2v2 lobby: my team on top, the opponent's slot below, Start and Cancel at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    lobbyViewModel: LobbyViewModel = hiltViewModel(),
    summaryViewModel: DeckSummaryViewModel = hiltViewModel(),
) {
    val state by lobbyViewModel.state.collectAsState()
    val summary by summaryViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    // Back, the top-bar arrow and Cancel all do the same thing.
    fun requestLeave() {
        if (state.guest is GuestSlot.Joined) {
            showCancelConfirm = true
        } else {
            lobbyViewModel.cancel()
            onNavigateBack()
        }
    }
    BackHandler { requestLeave() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("2v2") },
                navigationIcon = {
                    IconButton(onClick = { requestLeave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The middle scrolls if the screen is short, so Start and Cancel never get pushed off.
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = summary.deckName.ifBlank { "Deck" },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                MyTeamSlot(name = state.teamName, onReroll = lobbyViewModel::rerollName)

                VersusDivider()

                when (val guest = state.guest) {
                    GuestSlot.Empty -> {
                        WaitingSlot()
                        // DEBUG ONLY: delete with debugSimulateRequest() in the ViewModel.
                        OutlinedButton(
                            onClick = lobbyViewModel::debugSimulateRequest,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("DEBUG: simulate join request") }
                    }
                    is GuestSlot.Pending ->
                        PendingSlot(
                            teamName = guest.teamName,
                            onAccept = lobbyViewModel::accept,
                            onDecline = lobbyViewModel::decline,
                        )
                    is GuestSlot.Joined ->
                        JoinedSlot(
                            teamName = guest.teamName,
                            onRemove = { showRemoveConfirm = true },
                        )
                }
            }

            Button(
                onClick = {
                    lobbyViewModel.start()
                    // Placeholder until the game screen exists.
                    scope.launch { snackbarHostState.showSnackbar("Game screen not built yet") }
                },
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
            ) {
                Text("Start", style = MaterialTheme.typography.titleLarge)
            }

            TextButton(
                onClick = { requestLeave() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showRemoveConfirm) {
        val name = (state.guest as? GuestSlot.Joined)?.teamName ?: "this team"
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove $name?") },
            text = { Text("They will be disconnected and would need to ask to join again.") },
            confirmButton = {
                Button(
                    onClick = {
                        lobbyViewModel.removeGuest()
                        showRemoveConfirm = false
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Keep") }
            },
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("End this session?") },
            text = { Text("The other team will be disconnected.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirm = false
                        lobbyViewModel.cancel()
                        onNavigateBack()
                    },
                ) { Text("End session") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Stay") }
            },
        )
    }
}

@Composable
private fun MyTeamSlot(
    name: String,
    onReroll: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onReroll) {
                Icon(Icons.Default.Refresh, contentDescription = "Pick another team name")
            }
        }
    }
}

@Composable
private fun VersusDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text("VS", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/** Dashed empty slot while nobody has asked to join. */
@Composable
private fun WaitingSlot() {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .dashedBorder(outline)
                .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text("Waiting for a team to join…", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PendingSlot(
    teamName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$teamName wants to join",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("Decline") }
                Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Accept") }
            }
        }
    }
}

@Composable
private fun JoinedSlot(
    teamName: String,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = teamName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

private val BUTTON_HEIGHT = 56.dp

private fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp = 12.dp,
    strokeWidth: Dp = 2.dp,
): Modifier =
    drawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style =
                Stroke(
                    width = strokeWidth.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
                ),
        )
    }
