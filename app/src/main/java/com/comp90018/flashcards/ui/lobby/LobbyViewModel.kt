package com.comp90018.flashcards.ui.lobby

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** What is in the opponent's half of the lobby. */
sealed interface GuestSlot {
    /** Nobody has asked to join yet. */
    data object Empty : GuestSlot

    /** A team has asked to join and the host has not answered. */
    data class Pending(val teamName: String) : GuestSlot

    /** The host accepted this team. */
    data class Joined(val teamName: String) : GuestSlot
}

data class LobbyUiState(
    val teamName: String,
    val guest: GuestSlot = GuestSlot.Empty,
) {
    // Later: also require that the deck has finished syncing to the guest.
    val canStart: Boolean get() = guest is GuestSlot.Joined
}

/**
 * ViewModel for the host's 2v2 lobby.
 *
 * Nothing here talks to the network yet. Each TODO(p2p) marks the place where the Nearby
 * Connections code should be attached.
 */
@HiltViewModel
class LobbyViewModel
    @Inject
    constructor() : ViewModel() {
        private val _state = MutableStateFlow(LobbyUiState(teamName = randomTeamName()))
        val state: StateFlow<LobbyUiState> = _state.asStateFlow()

        init {
            // TODO(p2p): start advertising the session (mode, deck name, team name, card count).
        }

        fun rerollName() {
            _state.update { it.copy(teamName = randomTeamName()) }
            // TODO(p2p): update the advertised team name.
        }

        fun accept() {
            // TODO(p2p): acceptConnection(endpointId), then send the deck to the guest.
            _state.update { s ->
                val pending = s.guest as? GuestSlot.Pending ?: return@update s
                s.copy(guest = GuestSlot.Joined(pending.teamName))
            }
        }

        fun decline() {
            // TODO(p2p): rejectConnection(endpointId).
            _state.update { it.copy(guest = GuestSlot.Empty) }
        }

        fun removeGuest() {
            // TODO(p2p): tell the guest they were removed, disconnectFromEndpoint, resume advertising.
            _state.update { it.copy(guest = GuestSlot.Empty) }
        }

        fun start() {
            // TODO(p2p): send Start (with the card-order seed) to the guest and open the game screen.
        }

        fun cancel() {
            // TODO(p2p): stop advertising and disconnect from the guest if there is one.
        }

        // DEBUG ONLY: delete this once the real connection code sets the guest slot.
        fun debugSimulateRequest() {
            _state.update { it.copy(guest = GuestSlot.Pending("Team Kiwi")) }
        }

        // TODO(p2p): when a join request arrives, call
        //   _state.update { it.copy(guest = GuestSlot.Pending(requesterTeamName)) }
    }

private val TEAM_WORDS =
    listOf("Mango", "Kiwi", "Plum", "Otter", "Comet", "Pixel", "Maple", "Falcon", "Cactus", "Lemon")

private fun randomTeamName(): String = "Team ${TEAM_WORDS.random()}"
