package com.comp90018.flashcards.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.comp90018.flashcards.domain.model.Rating
import com.comp90018.flashcards.ui.study.components.FlashcardComponent
import com.comp90018.flashcards.ui.study.utils.TiltFlipDetector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    onNavigateBack: () -> Unit,
    viewModel: StudyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Initialize TiltFlipDetector to flip the card when the device is flipped face down
    DisposableEffect(viewModel) {
        val detector =
            TiltFlipDetector(context) {
                // Only trigger flip if the card is not already flipped
                if (!viewModel.uiState.value.isFlipped && !viewModel.uiState.value.isSessionComplete) {
                    viewModel.flipCard()
                }
            }
        detector.start()
        onDispose {
            detector.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Session") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.isSessionComplete) {
                SessionCompleteContent(onNavigateBack = onNavigateBack)
            } else {
                StudyContent(
                    uiState = uiState,
                    onFlip = viewModel::flipCard,
                    onRate = viewModel::rateCard,
                )
            }
        }
    }
}

@Composable
private fun StudyContent(
    uiState: StudyUiState,
    onFlip: () -> Unit,
    onRate: (Rating) -> Unit,
) {
    val currentCard = uiState.currentCard
    // Cards can come back for another step, so progress is reviews done out of reviews known so far.
    val total = uiState.reviewedCount + uiState.remainingCount
    val progress = if (total > 0) uiState.reviewedCount.toFloat() / total else 0f

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Text(
            text = "${uiState.remainingCount} left · ${uiState.reviewedCount} reviewed",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (currentCard != null) {
            FlashcardComponent(
                front = currentCard.card.front,
                back = currentCard.card.back,
                isFlipped = uiState.isFlipped,
                onFlip = onFlip,
                frontImageUri = currentCard.card.frontImageUri,
                backImageUri = currentCard.card.backImageUri,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            if (!uiState.isFlipped) {
                Button(
                    onClick = onFlip,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                ) {
                    Text("Flip")
                }
            } else {
                RatingButtons(onRate = onRate)
            }
        } else if (!uiState.isSessionComplete) {
            // This case should be handled by loadDueCards and isSessionComplete
            Text("No cards due for review.")
        }
    }
}

@Composable
private fun RatingButtons(onRate: (Rating) -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RatingButton(
            text = "Again",
            color = Color(0xFFEF5350), // Red
            onClick = { onRate(Rating.AGAIN) },
            modifier = Modifier.weight(1f),
        )
        RatingButton(
            text = "Hard",
            color = Color(0xFFFFA726), // Orange
            onClick = { onRate(Rating.HARD) },
            modifier = Modifier.weight(1f),
        )
        RatingButton(
            text = "Good",
            color = Color(0xFF66BB6A), // Green
            onClick = { onRate(Rating.GOOD) },
            modifier = Modifier.weight(1f),
        )
        RatingButton(
            text = "Easy",
            color = Color(0xFF42A5F5), // Blue
            onClick = { onRate(Rating.EASY) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RatingButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = modifier,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SessionCompleteContent(onNavigateBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Session Finished!",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onNavigateBack) {
                Text("Back")
            }
        }
    }
}
