package com.comp90018.flashcards.ui.deck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.comp90018.flashcards.data.local.entity.CardEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddCard: (String) -> Unit,
    onNavigateToEditCard: (String, String) -> Unit,
    viewModel: DeckDetailViewModel = hiltViewModel(),
) {
    val deck by viewModel.deck.collectAsState()
    val cards by viewModel.cards.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                title = { Text(deck?.name ?: "Deck Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToAddCard(viewModel.deckId) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Card")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(cards) { card ->
                CardItem(
                    card = card,
                    onEditClick = { onNavigateToEditCard(viewModel.deckId, card.cardId) },
                    onDeleteClick = { viewModel.deleteCard(card) },
                )
            }
        }
    }
}

@Composable
fun CardItem(
    card: CardEntity,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "F: ${card.front}", style = MaterialTheme.typography.bodyLarge)
                Text(text = "B: ${card.back}", style = MaterialTheme.typography.bodyMedium)
            }
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Card")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Card")
                }
            }
        }
    }
}
