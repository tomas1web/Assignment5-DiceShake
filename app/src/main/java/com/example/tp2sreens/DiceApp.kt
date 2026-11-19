package com.example.tp2sreens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun DiceApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {

        // Ecrã principal
        composable("main") {
            MainDiceScreen(
                onGoToResult = { value -> navController.navigate("result/$value") }
            )
        }

        // Ecrãs de resultado: "value" = ecrã/dado; "prev" = dado atual (só vem do jogo do ecrã 6)
        composable(
            route = "result/{value}?prev={prev}",
            arguments = listOf(
                navArgument("value") { type = NavType.IntType },
                navArgument("prev") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val value = backStackEntry.arguments?.getInt("value") ?: 1
            val prev = backStackEntry.arguments?.getInt("prev") ?: 0

            val goTo: (Int) -> Unit = { navController.navigate("result/$it") }
            val goToFromGame: (Int, Int) -> Unit = { newDie, currentDie ->
                navController.navigate("result/$newDie?prev=$currentDie")
            }
            val back: () -> Unit = { navController.popBackStack() }

            Column(modifier = Modifier.fillMaxSize()) {
                // Se vimos do jogo (ecrã 6), mostra os dois dados: o atual e o novo
                if (prev != 0) {
                    DicePair(current = prev, new = value)
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (value) {
                        1 -> DiceResult1(onBack = back)
                        2 -> DiceResult2(onBack = back)
                        3 -> DiceResult3(onBack = back)
                        4 -> DiceResult4(onGoTo = goTo, onBack = back)
                        5 -> DiceResult5(onBack = back)
                        else -> DiceResult6(onGoToResult = goToFromGame, onBack = back)
                    }
                }
            }
        }
    }
}

// Mostra o dado atual e o novo lado a lado (resultado do jogo)
@Composable
fun DicePair(current: Int, new: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Current Die")
            DiceImage(current, size = 70.dp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("New Die")
            DiceImage(new, size = 70.dp)
        }
    }
}

// ============================================================
//  TP5: shake, botão arrastável e flick
// ============================================================

@Composable
fun MainDiceScreen(onGoToResult: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // rememberSaveable: o valor mantém-se quando voltas do "Back"
    var result by rememberSaveable { mutableIntStateOf(1) }

    // Posição do dado em relação ao centro do ecrã (animável, para o "lançamento")
    val diceOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // 1) Abanar o telemóvel (acelerómetro) lança o dado
    ShakeDetector {
        result = Random.nextInt(1, 7)
        Toast.makeText(context, "Shake event detected", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 3) Flick: gesto rápido no ecrã atira o dado nessa direção
            .pointerInput(Unit) {
                val limitX = (size.width - 120.dp.toPx()) / 2f
                val limitY = (size.height - 120.dp.toPx()) / 2f

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)

                    // Segue o dedo até ele levantar (se o botão arrastável consumir o gesto, isto cancela)
                    drag(down.id) { change ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                    }

                    val velocity = tracker.calculateVelocity()
                    if (hypot(velocity.x, velocity.y) > 1500f) {
                        scope.launch {
                            diceOffset.stop()
                            // O dado pára nas bordas do ecrã
                            diceOffset.updateBounds(
                                Offset(-limitX, -limitY),
                                Offset(limitX, limitY)
                            )
                            result = Random.nextInt(1, 7)
                            Toast.makeText(context, "Flick detected", Toast.LENGTH_SHORT).show()
                            diceOffset.animateDecay(
                                Offset(velocity.x, velocity.y),
                                exponentialDecay()
                            )
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Main Dice Screen", style = MaterialTheme.typography.titleLarge)
            Text("Current Roll: $result")
            Text(
                "Shake the phone, flick the screen or tap the Roll button",
                style = MaterialTheme.typography.bodySmall
            )
        }

        // O dado, que se mexe com o flick
        DiceImage(
            value = result,
            modifier = Modifier
                .align(Alignment.Center)
                .offset {
                    IntOffset(
                        diceOffset.value.x.roundToInt(),
                        diceOffset.value.y.roundToInt()
                    )
                }
        )

        // 2) Botão "Roll" que se pode arrastar pelo ecrã (e clicar)
        DraggableRollButton(
            onRoll = { result = Random.nextInt(1, 7) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        // Só aqui há transição
        Button(
            onClick = { onGoToResult(result) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) { Text("Go to Result") }
    }
}

// Botão que se arrasta com o dedo. Se o dedo quase não se mexer, conta como clique.
@Composable
fun DraggableRollButton(onRoll: () -> Unit, modifier: Modifier = Modifier) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    val currentOnRoll by rememberUpdatedState(onRoll)

    Surface(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var moved = 0f
                    drag(down.id) { change ->
                        val delta = change.positionChange()
                        moved += delta.getDistance()
                        if (moved > viewConfiguration.touchSlop) {
                            offset += delta        // arrasta o botão
                        }
                        change.consume()           // o resto do ecrã não trata este gesto
                    }
                    // Quase sem movimento = toque = lançar o dado
                    if (moved <= viewConfiguration.touchSlop) currentOnRoll()
                }
            },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = "Roll Die",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

// Deteta o "abanar" com o acelerómetro e chama onShake
@Composable
fun ShakeDetector(onShake: () -> Unit) {
    val context = LocalContext.current
    val currentOnShake by rememberUpdatedState(onShake)

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastShakeTime = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Aceleração total em "g" (parado = cerca de 1 g)
                val gX = event.values[0] / SensorManager.GRAVITY_EARTH
                val gY = event.values[1] / SensorManager.GRAVITY_EARTH
                val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
                val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

                val now = System.currentTimeMillis()
                // Acima de 2,5 g conta como abanão (no máximo um por segundo)
                if (gForce > 2.5f && now - lastShakeTime > 1000) {
                    lastShakeTime = now
                    currentOnShake()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        // Pára de ouvir o sensor quando o ecrã sai (poupa bateria)
        onDispose { sensorManager.unregisterListener(listener) }
    }
}