package com.comp90018.flashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.comp90018.flashcards.ui.card.AddEditCardScreen
import com.comp90018.flashcards.ui.deck.DeckDetailScreen
import com.comp90018.flashcards.ui.deck.DeckListScreen
import com.comp90018.flashcards.ui.study.StudyScreen
import com.comp90018.flashcards.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.secondary
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            DeckListScreen(
                                onNavigateToStudy = { deckId ->
                                    navController.navigate("study/$deckId")
                                },
                                onNavigateToManage = { deckId ->
                                    navController.navigate("deck_detail/$deckId")
                                }
                            )
                        }
                        composable(
                            route = "deck_detail/{deckId}",
                            arguments = listOf(navArgument("deckId") { type = NavType.StringType })
                        ) {
                            DeckDetailScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToAddCard = { deckId ->
                                    navController.navigate("add_edit_card/$deckId")
                                },
                                onNavigateToEditCard = { deckId, cardId ->
                                    navController.navigate("add_edit_card/$deckId?cardId=$cardId")
                                }
                            )
                        }
                        composable(
                            route = "add_edit_card/{deckId}?cardId={cardId}",
                            arguments = listOf(
                                navArgument("deckId") { type = NavType.StringType },
                                navArgument("cardId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) {
                            AddEditCardScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "study/{deckId}",
                            arguments = listOf(navArgument("deckId") { type = NavType.StringType })
                        ) {
                            StudyScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
