package com.protctus.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.protctus.app.utils.GamificationEngine
import javax.inject.Inject

data class LeaderboardUser(
    val username: String = "Unknown Hacker",
    val xp: Int = 0,
    val rankTitle: String = "Cyber Novice",
    val level: Int = 1,
    val isCurrentUser: Boolean = false
)

sealed class LeaderboardState {
    object Loading : LeaderboardState()
    data class Success(val topUsers: List<LeaderboardUser>) : LeaderboardState()
    data class Error(val message: String) : LeaderboardState()
}

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _leaderboardState = MutableStateFlow<LeaderboardState>(LeaderboardState.Loading)
    val leaderboardState: StateFlow<LeaderboardState> = _leaderboardState.asStateFlow()

    init {
        fetchTopUsers()
    }

    private var pollingJob: kotlinx.coroutines.Job? = null

    private fun fetchTopUsers() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _leaderboardState.value = LeaderboardState.Loading

            while (isActive) {
                try {
                    val currentUserUid = auth.currentUser?.uid
                    
                    val jsonArray = com.protctus.app.network.LeaderboardApiClient.fetchLeaderboard()
                    if (jsonArray == null) {
                        throw Exception("Access denied or failed to fetch leaderboard from server.")
                    }

                    val users = mutableListOf<LeaderboardUser>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val username = obj.optString("username").takeIf { it.isNotBlank() } ?: obj.optString("name", "Ghost")
                        
                        val xp = if (obj.has("total_xp")) obj.getInt("total_xp") 
                                 else if (obj.has("xp")) obj.getInt("xp")
                                 else obj.optInt("security_score", 0)
                        
                        val uid = obj.optString("uid", obj.optString("id", ""))
                        val levelData = GamificationEngine.calculateLevel(xp)
                        val isCurrentUser = (uid == currentUserUid)

                        users.add(
                            LeaderboardUser(
                                username = username,
                                xp = xp,
                                rankTitle = levelData.rankTitle,
                                level = levelData.level,
                                isCurrentUser = isCurrentUser
                            )
                        )
                    }

                    // Sort locally just in case backend didn't sort
                    val sortedUsers = users.sortedByDescending { it.xp }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _leaderboardState.value = LeaderboardState.Success(sortedUsers)
                    }
                } catch (e: Exception) {
                    if (_leaderboardState.value !is LeaderboardState.Success) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _leaderboardState.value = LeaderboardState.Error(e.message ?: "Unknown Error")
                        }
                    }
                }
                kotlinx.coroutines.delay(15000) // 15 Second Heartbeat Requested by User
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
