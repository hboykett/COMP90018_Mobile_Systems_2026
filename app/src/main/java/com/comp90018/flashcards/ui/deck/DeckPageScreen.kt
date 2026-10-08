package com.comp90018.flashcards.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Deck page: the deck's name and size, with buttons to play it or edit its cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckPageScreen(
    onNavigateBack: () -> Unit,
    onPlay: (String) -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeckSummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The name and size fill the free space, so the buttons below sit within thumb reach.
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = uiState.deckName.ifBlank { "Deck" },
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = cardCountLabel(uiState.cardCount),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
            }

            // A deck with no cards has nothing to play.
            Button(
                onClick = { onPlay(viewModel.deckId) },
                enabled = uiState.cardCount > 0,
                modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
                Spacer(Modifier.width(8.dp))
                Text("Play", style = MaterialTheme.typography.titleLarge)
            }

            OutlinedButton(
                onClick = { onEdit(viewModel.deckId) },
                modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
                Spacer(Modifier.width(8.dp))
                Text("Edit", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

// Shared with the choose-mode page so the two pages have matching buttons.
internal val BUTTON_HEIGHT = 72.dp
internal val ICON_SIZE = 32.dp

internal fun cardCountLabel(count: Int): String = if (count == 1) "1 card" else "$count cards"
