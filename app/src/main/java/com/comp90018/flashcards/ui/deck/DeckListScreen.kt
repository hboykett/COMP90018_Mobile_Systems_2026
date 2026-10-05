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
    onNavigateToStudy: (String) -> Unit,
    onNavigateToManage: (String) -> Unit,
    viewModel: DeckListViewModel = hiltViewModel(),
) {
    val decks by viewModel.decks.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var newDeckName by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Decks") }) },
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
                    onStudyClick = { onNavigateToStudy(deck.deckId) },
                    onManageClick = { onNavigateToManage(deck.deckId) },
                    onDeleteClick = { viewModel.deleteDeck(deck) },
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Create New Deck") },
            text = {
                OutlinedTextField(
                    value = newDeckName,
                    onValueChange = { newDeckName = it },
                    label = { Text("Deck Name") },
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newDeckName.isNotBlank()) {
                        viewModel.createDeck(newDeckName)
                        newDeckName = ""
                        showDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun DeckItem(
    deck: DeckEntity,
    onStudyClick: () -> Unit,
    onManageClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onStudyClick() },
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
