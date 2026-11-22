package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.signOut
import com.example.data.ChickenShopRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.daily.DailyAccountScreen
import com.example.ui.daily.DailyAccountViewModel
import com.example.ui.history.HistoryScreen
import com.example.ui.history.HistoryViewModel
import com.example.ui.theme.DeepGreenContainer
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.UniqueChickenTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniqueChickenTheme {
                AppContent()
            }
        }
    }
}

private enum class AppTab {
    DAILY_ACCOUNT,
    HISTORY,
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = { /* Interactive button available in AuthScreen */ },
                scope = scope,
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
        )
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedShopApp(
                currentUserId = uid,
                onSignOut = {
                    signOut(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { currentUser = null },
                    )
                },
            )
        }
    }
}

@Composable
private fun AuthenticatedShopApp(
    currentUserId: String,
    onSignOut: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.DAILY_ACCOUNT) }

    val dailyViewModel: DailyAccountViewModel = viewModel(
        key = "daily_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                DailyAccountViewModel(ChickenShopRepository(db), currentUserId)
            }
        },
    )

    val historyViewModel: HistoryViewModel = viewModel(
        key = "history_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                HistoryViewModel(ChickenShopRepository(db), currentUserId)
            }
        },
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PureWhite,
        bottomBar = {
            NavigationBar(
                containerColor = PureWhite,
                tonalElevation = androidx.compose.ui.unit.Dp.Hairline,
            ) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.DAILY_ACCOUNT,
                    onClick = { selectedTab = AppTab.DAILY_ACCOUNT },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.DAILY_ACCOUNT) {
                                Icons.Filled.EditNote
                            } else {
                                Icons.Outlined.EditNote
                            },
                            contentDescription = stringResource(R.string.nav_daily_account),
                        )
                    },
                    label = { Text(stringResource(R.string.nav_daily_account)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ForestGreen,
                        selectedTextColor = ForestGreen,
                        indicatorColor = DeepGreenContainer,
                        unselectedIconColor = SecondaryInk,
                        unselectedTextColor = SecondaryInk,
                    ),
                    modifier = Modifier.testTag("tab_daily_account"),
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.HISTORY,
                    onClick = { selectedTab = AppTab.HISTORY },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.HISTORY) {
                                Icons.Filled.History
                            } else {
                                Icons.Outlined.History
                            },
                            contentDescription = stringResource(R.string.nav_history),
                        )
                    },
                    label = { Text(stringResource(R.string.nav_history)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ForestGreen,
                        selectedTextColor = ForestGreen,
                        indicatorColor = DeepGreenContainer,
                        unselectedIconColor = SecondaryInk,
                        unselectedTextColor = SecondaryInk,
                    ),
                    modifier = Modifier.testTag("tab_history"),
                )
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.DAILY_ACCOUNT -> {
                DailyAccountScreen(
                    viewModel = dailyViewModel,
                    onSignOutClick = onSignOut,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            AppTab.HISTORY -> {
                HistoryScreen(
                    viewModel = historyViewModel,
                    onOpenDate = { dateId ->
                        dailyViewModel.selectDate(dateId)
                        selectedTab = AppTab.DAILY_ACCOUNT
                    },
                    onNavigateBack = { selectedTab = AppTab.DAILY_ACCOUNT },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}
