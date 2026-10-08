package com.comp90018.flashcards.ui.deck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.comp90018.flashcards.data.local.entity.DeckEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    onNavigateToDeck: (String) -> Unit,
    onNavigateToManage: (String) -> Unit,
    viewModel: DeckListViewModel = hiltViewModel(),
) {
    val decks by viewModel.decks.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var newDeckName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            DeckListTopBar(displayName = displayName, onSignOut = viewModel::signOut)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Deck")
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
            items(decks) { deck ->
                DeckItem(
                    deck = deck,
                    onOpenClick = { onNavigateToDeck(deck.deckId) },
                    onManageClick = { onNavigateToManage(deck.deckId) },
                    onDeleteClick = { viewModel.deleteDeck(deck) },
                )
            }
        }
    }

    if (showDialog) {
        CreateDeckDialog(
            name = newDeckName,
            onNameChange = { newDeckName = it },
            onConfirm = {
                if (newDeckName.isNotBlank()) {
                    viewModel.createDeck(newDeckName)
                    newDeckName = ""
                    showDialog = false
                }
            },
            onDismiss = { showDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckListTopBar(
    displayName: String,
    onSignOut: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text("My Decks")
                if (displayName.isNotBlank()) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
        actions = {
            TextButton(onClick = onSignOut) {
                Text("Log out")
            }
        },
    )
}

@Composable
private fun CreateDeckDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Deck") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Deck Name") },
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun DeckItem(
    deck: DeckEntity,
    onOpenClick: () -> Unit,
    onManageClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onOpenClick() },
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = deck.name, style = MaterialTheme.typography.titleLarge)
            }
            Row {
                IconButton(onClick = onManageClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Manage Cards")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Deck")
                }
            }
        }
    }
}
