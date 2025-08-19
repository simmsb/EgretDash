package com.simmsb.egretdash

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.juul.kable.Identifier
import com.juul.khronicle.Log
import com.simmsb.egretdash.dashboard.DashScreen
import com.simmsb.egretdash.scan.ScanScreen
import com.simmsb.egretdash.ui.theme.EgretDashTheme
import kotlinx.serialization.Serializable

@Serializable
abstract class NavBarItem : Route(isTopLevel = true) {
    abstract fun icon(): ImageVector
    abstract fun description(): String
}

@Serializable
data object ScreenScan : NavBarItem() {
    override fun icon(): ImageVector {
        return Icons.Default.Search
    }

    override fun description(): String {
        return "Scan"
    }
}

@Serializable
data class ScreenDash(val identifier: Identifier) : NavBarItem() {
    override fun icon(): ImageVector {
        return Icons.Default.Home
    }

    override fun description(): String {
        return "Dashboard"
    }
}

data object ScreenDashTrips : Route()

val Context.preferredDeviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "preferredDevice")
val PREFERRED_DEVICE_KEY = stringPreferencesKey("preferredDeviceIdentifier")

private const val INITIAL_OFFSET_FACTOR = 0.10f


object MotionConstants {
    const val DefaultMotionDuration: Int = 300
    const val DefaultFadeInDuration: Int = 150
    const val DefaultFadeOutDuration: Int = 75
//    public val DefaultSlideDistance: Dp = 30.dp
}

private const val ProgressThreshold = 0.35f
private val Int.ForOutgoing: Int
    get() = (this * ProgressThreshold).toInt()

private val Int.ForIncoming: Int
    get() = this - this.ForOutgoing

@OptIn(ExperimentalAnimationApi::class)
fun materialSharedAxisX(
    initialOffsetX: (fullWidth: Int) -> Int,
    targetOffsetX: (fullWidth: Int) -> Int,
    durationMillis: Int = MotionConstants.DefaultMotionDuration,
): ContentTransform = ContentTransform(
    materialSharedAxisXIn(
        initialOffsetX = initialOffsetX,
        durationMillis = durationMillis
    ), materialSharedAxisXOut(
        targetOffsetX = targetOffsetX,
        durationMillis = durationMillis
    )
)

fun materialSharedAxisXIn(
    initialOffsetX: (fullWidth: Int) -> Int,
    durationMillis: Int = MotionConstants.DefaultMotionDuration,
): EnterTransition = slideInHorizontally(
    animationSpec = tween(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing
    ),
    initialOffsetX = initialOffsetX
) + fadeIn(
    animationSpec = tween(
        durationMillis = durationMillis.ForIncoming,
        delayMillis = durationMillis.ForOutgoing,
        easing = LinearOutSlowInEasing
    )
)

fun materialSharedAxisXOut(
    targetOffsetX: (fullWidth: Int) -> Int,
    durationMillis: Int = MotionConstants.DefaultMotionDuration,
): ExitTransition = slideOutHorizontally(
    animationSpec = tween(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing
    ),
    targetOffsetX = targetOffsetX
) + fadeOut(
    animationSpec = tween(
        durationMillis = durationMillis.ForOutgoing,
        delayMillis = 0,
        easing = FastOutLinearInEasing
    )
)

class MainActivity : ComponentActivity() {
    @Composable
    private fun topLevelRoutes(): List<NavBarItem> {
        val preferredDevice =
            preferredDevice()

        return listOf(ScreenScan) + (preferredDevice?.let { listOf(ScreenDash(it)) } ?: listOf())
    }

    @Composable
    private fun preferredDevice(): String? {
        val preferredDevice =
            applicationContext.preferredDeviceDataStore.data.collectAsStateWithLifecycle(null).value?.get(
                PREFERRED_DEVICE_KEY
            )
        return preferredDevice
    }


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLogging()
        enableEdgeToEdge()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        setContent {
            val routes = topLevelRoutes()
            EgretDashTheme {
                preferredDevice()?.let { startForegroundService(Intent(this, LoggerService::class.java)) }
                val defaultScreen = preferredDevice()?.let { ScreenDash(it) } ?: ScreenScan
                val backStack = rememberNavBackStack(defaultScreen)
                Log.info { "Default screen: $defaultScreen, backstack: $backStack"}
                val navigator = remember { Navigator<Route>( defaultScreen, backStack) }
                navigator.navigate(defaultScreen)
                val database = DashboardDatabase.getDatabase(applicationContext)

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            routes.forEach { tl ->
                                val isSelected = tl == navigator.topLevelRoute
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        navigator.navigate(tl)
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = tl.icon(),
                                            contentDescription = tl.description(),
                                        )
                                    },
                                    label = { Text(tl.description()) }
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    NavDisplay(
                        modifier = Modifier.padding(paddingValues).fillMaxSize(),
                        backStack = backStack,
                        onBack = { navigator.goBack() },
                        entryProvider = entryProvider {
                            entry<ScreenScan> { ScanScreen(navigator) }
                            entry<ScreenDash> { DashScreen(navigator, database, it.identifier) }
                        },
                        transitionSpec = {
                            // Slide in from right when navigating forward
                            slideInHorizontally(initialOffsetX = { it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { -it })
                        },
                        popTransitionSpec = {
                            // Slide in from left when navigating back
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
                        },
                        predictivePopTransitionSpec = {
                            materialSharedAxisXIn(
                                initialOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                            ) togetherWith
                                    materialSharedAxisXOut(targetOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() })
                        }
                    )
                }
            }
        }

    }
}