package com.simmsb.egretdash.dashboard

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.himanshoe.charty.bar.SignalProgressBarChart
import com.himanshoe.charty.circle.SpeedometerProgressBar
import com.himanshoe.charty.common.ChartColor
import com.himanshoe.charty.common.TextConfig
import com.himanshoe.charty.common.asSolidChartColor
import com.himanshoe.charty.line.LineChart
import com.himanshoe.charty.line.config.LineChartColorConfig
import com.himanshoe.charty.line.model.LineData
import com.juul.kable.Identifier
import com.simmsb.egretdash.DashboardDatabase
import com.simmsb.egretdash.Navigator
import com.simmsb.egretdash.Route
import com.simmsb.egretdash.components.BluetoothDisabled
import com.simmsb.egretdash.rememberSystemControl
import com.simmsb.egretdash.requirements.rememberBluetoothRequirementsFactory
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toKotlinTimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashScreen(
    navigator: Navigator<Route>,
    database: DashboardDatabase,
    identifier: Identifier,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    val screenModel = rememberScreenModel(navigator, database, identifier)
    val viewState = screenModel.state.collectAsState().value

    DisposableEffect(lifecycleOwner) {
        onDispose { screenModel.onDispose() }
    }

    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Column(
        Modifier
//                    .background(color = MaterialTheme.colors.background)
            .fillMaxSize(),
    ) {
        val systemControl = rememberSystemControl()
        when (viewState) {
            ViewState.BluetoothOff -> BluetoothDisabled(systemControl::requestToTurnBluetoothOn)
            is ViewState.Connected -> ScooterPane(screenModel)
            else -> Connecting()
        }

    }
}

@Composable
private fun rememberScreenModel(
    navigator: Navigator<Route>, database: DashboardDatabase, identifier: Identifier
): DashModel {
    val bluetoothRequirementsFactory = rememberBluetoothRequirementsFactory()
    val screenModel = remember {
        val bluetoothRequirements = bluetoothRequirementsFactory.create()
        DashModel(navigator, database, identifier, bluetoothRequirements)
    }
    return screenModel
}


enum class DashTab(val description: String) {
    Dashboard("Dashboard"), Trips("Trips")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScooterPane(
    model: DashModel,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { DashTab.entries.size })

    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
        DashTab.entries.forEachIndexed { index, destination ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                text = { Text(destination.description) })
        }
    }
    HorizontalPager(pagerState) { page ->
        when (page) {
            DashTab.Dashboard.ordinal -> DashboardTab(model)
            DashTab.Trips.ordinal -> TripsTab(model)
        }
    }


}

@Composable
private fun RowScope.InfoPiece(name: String, value: String) {
    Box(
        Modifier
            .fillMaxSize()
            .weight(1f)
    ) {
        Text(
            text = name, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
            textAlign = TextAlign.Center,
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .squareSize(), contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                //style = MaterialTheme.typography.labelLarge,
                fontSize = 24.sp,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripsTab(
    model: DashModel,
) {
    val odo = model.odometer.collectAsStateWithLifecycle().value
    val trips = model.trips.collectAsStateWithLifecycle().value
    val dateFormatter = LocalDateTime.Format {
        day(); char('.'); monthNumber(); char('.'); yearTwoDigits(1960)
        char(' ')
        hour(); char(':'); minute()
    }

    Column(Modifier
        .padding(20.dp)
        .fillMaxSize()) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            trips.toList().sortedBy { it.first }.map { it.second }.forEach {
                val start =
                    it.start.timestamp.toLocalDateTime(ZoneId.systemDefault().toKotlinTimeZone())
                        .format(dateFormatter)
                val end = it.end?.let {
                    it.timestamp.toLocalDateTime(ZoneId.systemDefault().toKotlinTimeZone())
                        .format(dateFormatter)
                } ?: "In progress"
                val distance = it.end?.let { end -> end.odoTotal - it.start.odoTotal }
                    ?: (odo.total - it.start.odoTotal)

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ), modifier = Modifier.fillMaxWidth()

                ) {
                    Row {
                        InfoPiece("Start", start)
                        InfoPiece("End", end)
                        InfoPiece("Distance", "$distance km")
                    }
                }
            }
        }
    }
}

@Stable
fun Modifier.squareSize(
    position: Float = 0.5f,
): Modifier = layout { measurable, constraints ->
    val constraintsSize = min(constraints.maxWidth, constraints.maxHeight)
    val minWidth = min(constraints.minWidth, constraintsSize)
    val minHeight = min(constraints.minHeight, constraintsSize)
//    Log.info { "Constraints: ${constraints}, constraintsSize: ${constraintsSize}" }
    val squareConstraints = Constraints(
        minWidth = minWidth,
        minHeight = minHeight,
        maxWidth = constraintsSize,
        maxHeight = constraintsSize,
    )

    val placeable = measurable.measure(squareConstraints)
    val size = max(placeable.width, placeable.height)

    layout(width = size, height = size) {
        val x = ((size - placeable.width) * position).toInt()
        val y = ((size - placeable.height) * position).toInt()
        placeable.placeRelative(x, y)
    }
}

@Composable
private fun RowScope.NumericMetric(
    name: String,
    value: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ), modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .squareSize()
    ) {
        Box(Modifier
            .fillMaxSize()
            .weight(1f)) {
            Text(
                text = name, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
            )

            Box(
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    //style = MaterialTheme.typography.labelLarge,
                    fontSize = 36.sp,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTab(
    model: DashModel,
) {
    val state = model.scooterStatus.collectAsStateWithLifecycle().value
    val odo = model.odometer.collectAsStateWithLifecycle().value
    val diag = model.diagnostics.collectAsStateWithLifecycle().value
    Column(Modifier.padding(20.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                .weight(2f)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .squareSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpeedometerProgressBar(
                        progress = { state.speed / 25f },
                        title = "Speed: ${state.speed} km/h",
                        color = ChartColor.Solid(MaterialTheme.colorScheme.primary),
                        progressIndicatorColor = ChartColor.Solid(MaterialTheme.colorScheme.primary),
                        trackColor = ChartColor.Solid(MaterialTheme.colorScheme.secondary),
                        titleTextConfig = TextConfig.default(
                            color = MaterialTheme.colorScheme.onBackground.asSolidChartColor(),
                            style = MaterialTheme.typography.labelMedium
                        ),
//                        titleTextConfig = TextConfig.default().copy(isVisible = false),
                        subTitleTextConfig = TextConfig.default().copy(isVisible = false),
                        modifier = Modifier
//                            .fillMaxSize()
//                            .weight(1f)
                            .padding(10.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .squareSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpeedometerProgressBar(
                        progress = { state.powerOutput / 255f },
                        title = "Power: ${state.powerOutput}%",
                        color = ChartColor.Solid(MaterialTheme.colorScheme.primary),
                        progressIndicatorColor = ChartColor.Solid(MaterialTheme.colorScheme.primary),
                        trackColor = ChartColor.Solid(MaterialTheme.colorScheme.secondary),
                        titleTextConfig = TextConfig.default(
                            color = MaterialTheme.colorScheme.onBackground.asSolidChartColor(),
                            style = MaterialTheme.typography.labelMedium
                        ),
//                    titleTextConfig = TextConfig.default().copy(isVisible = false),
                        subTitleTextConfig = TextConfig.default().copy(isVisible = false),
                        modifier = Modifier
//                        .fillMaxHeight()
//                        .weight(1f)
                            .padding(10.dp),
                    )

                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Throttle", style = MaterialTheme.typography.labelLarge
                )

                SignalProgressBarChart(
                    progress = { state.throttle.toFloat() },
                    maxProgress = 100f,
                    progressColor = ChartColor.Solid(MaterialTheme.colorScheme.primary),
                    trackColor = ChartColor.Solid(MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        // .rotate(90f)
                        .fillMaxSize()
                        .weight(1f)
                        .padding(10.dp),
                    totalBlocks = 25,
                )
            }


        }

        Row(
            Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            val chartVals = model.chartVals.collectAsStateWithLifecycle(persistentListOf()).value

            LineChart(
                data = { chartVals.asIterable().withIndex().map { LineData(it.value.speed, it.index.toFloat()) }.toCollection(mutableListOf()) },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(10.dp),
                target = 25f,
                smoothLineCurve = true,
                colorConfig = LineChartColorConfig(
                    axisColor = ChartColor.Solid(MaterialTheme.colorScheme.secondary),
                    gridLineColor = ChartColor.Solid(MaterialTheme.colorScheme.outline),
                    lineColor = ChartColor.Solid(MaterialTheme.colorScheme.primary),
                    selectionBarColor = ChartColor.Solid(Color.Transparent),
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NumericMetric("Odometer", "${odo.total} km")
            NumericMetric("Motor RPM", "${diag.motorRpm}")
            NumericMetric("Motor Temp", "${diag.motorTemperature} C")

//            Column(
//                modifier = Modifier
//                    .shadow(
//                        elevation = 10.dp, shape = RoundedCornerShape(8.dp)
//                    )
//                    .background(MaterialTheme.colorScheme.primaryContainer)
//                    .fillMaxSize()
//                    .weight(1f)
//                    .squareSize(),
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                Text(text = "Odometer", style = MaterialTheme.typography.titleSmall)
//                Text(text = "${odo.total} km")
//            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Button(model::markTrip) { Text("Mark trip") }
        }
    }
}


@Composable
private fun Connecting() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

val ClosedRange<Duration>.inWholeMilliseconds: LongRange
    get() = start.inWholeMilliseconds..endInclusive.inWholeMilliseconds

private fun LongRange.toFloat(): ClosedFloatingPointRange<Float> =
    start.toFloat()..endInclusive.toFloat()