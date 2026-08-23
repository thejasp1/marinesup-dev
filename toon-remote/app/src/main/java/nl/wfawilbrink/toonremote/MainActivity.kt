package nl.wfawilbrink.toonremote

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Navy = Color(0xFF07121F)
private val Panel = Color(0xFF102A43)
private val Cyan = Color(0xFF2DE2E6)
private val Blue = Color(0xFF4D8DFF)
private val Purple = Color(0xFF9B6BFF)
private val Orange = Color(0xFFFFA94D)
private val Green = Color(0xFF42E695)
private val Red = Color(0xFFFF6577)
private val TextSoft = Color(0xFFA8C2D8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Navy, surface = Panel)) {
                ToonRemoteApp()
            }
        }
    }
}

data class ToonData(
    val roomTemp: Double,
    val setpoint: Double,
    val heating: Boolean,
    val activeState: Int,
    val programState: Int,
    val nextTime: Long,
    val nextSetpoint: Double
)

data class EnergyData(val usage: Int, val production: Int, val gas: Double)

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Testing : ConnectionState()
    data class Connected(val data: ToonData) : ConnectionState()
    data class Failed(val message: String) : ConnectionState()
}

@Composable
fun ToonRemoteApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("toon_remote", Context.MODE_PRIVATE) }
    var savedIp by remember { mutableStateOf(prefs.getString("toon_ip", "") ?: "") }
    var connection by remember { mutableStateOf<ConnectionState>(ConnectionState.Idle) }
    var showSetup by remember { mutableStateOf(savedIp.isBlank()) }
    val scope = rememberCoroutineScope()

    fun test(ip: String, save: Boolean = false) {
        connection = ConnectionState.Testing
        scope.launch {
            val result = runCatching { fetchToon(ip) }
            connection = result.fold(
                onSuccess = {
                    if (save) {
                        prefs.edit().putString("toon_ip", ip).apply()
                        savedIp = ip
                        showSetup = false
                    }
                    ConnectionState.Connected(it)
                },
                onFailure = { ConnectionState.Failed(it.message ?: "Geen verbinding met Toon") }
            )
        }
    }

    LaunchedEffect(savedIp) { if (savedIp.isNotBlank()) test(savedIp) }

    if (showSetup) {
        SetupWizard(savedIp, connection) { test(it, true) }
    } else {
        EnterpriseDashboard(
            ip = savedIp,
            state = connection,
            onRefresh = { test(savedIp) },
            onSetup = { showSetup = true },
            onSetpoint = { value ->
                scope.launch {
                    runCatching { setToonTemperature(savedIp, value) }
                    test(savedIp)
                }
            }
        )
    }
}

@Composable
fun SetupWizard(initialIp: String, state: ConnectionState, onTestAndSave: (String) -> Unit) {
    var ip by remember { mutableStateOf(initialIp) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071A2D), Color(0xFF0C3150), Color(0xFF11152C))))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            Text("TOON REMOTE", fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Enterprise Edition v1.2", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("W.F.A. Wilbrink Software", color = TextSoft, fontSize = 12.sp)
            Spacer(Modifier.height(28.dp))
            Surface(Modifier.fillMaxWidth(), color = Color(0xCC102A43), shape = RoundedCornerShape(30.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Koppel uw Toon 2", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(value = ip, onValueChange = { ip = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("Toon IP-adres") }, singleLine = true)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { if (ip.isNotBlank()) onTestAndSave(ip) }, enabled = state !is ConnectionState.Testing && ip.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(if (state is ConnectionState.Testing) "VERBINDING TESTEN…" else "TEST & VERBIND", fontWeight = FontWeight.Black)
                    }
                    if (state is ConnectionState.Failed) Text(state.message, color = Red, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
fun EnterpriseDashboard(ip: String, state: ConnectionState, onRefresh: () -> Unit, onSetup: () -> Unit, onSetpoint: (Double) -> Unit) {
    val data = (state as? ConnectionState.Connected)?.data
    var selectedTab by remember { mutableIntStateOf(0) }
    Scaffold(containerColor = Navy, bottomBar = {
        NavigationBar(containerColor = Color(0xFF091A2A)) {
            listOf(Triple(Icons.Outlined.Home, "Home", 0), Triple(Icons.Outlined.Bolt, "Energie", 1), Triple(Icons.Outlined.Schedule, "Programma", 2), Triple(Icons.Outlined.Settings, "Instellingen", 3)).forEach { item ->
                NavigationBarItem(selected = selectedTab == item.third, onClick = { selectedTab = item.third }, icon = { Icon(item.first, item.second) }, label = { Text(item.second) })
            }
        }
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(Brush.verticalGradient(listOf(Color(0xFF071522), Color(0xFF0B2940), Color(0xFF071522))))) {
            when (selectedTab) {
                0 -> HomeTab(ip, state, data, onRefresh, onSetup, onSetpoint)
                1 -> EnergyTab(ip)
                2 -> ProgramTab(data)
                else -> SettingsTab(ip, data != null, onSetup, onRefresh)
            }
        }
    }
}

@Composable
fun HomeTab(ip: String, state: ConnectionState, data: ToonData?, onRefresh: () -> Unit, onSetup: () -> Unit, onSetpoint: (Double) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Header(data != null, state is ConnectionState.Testing, onRefresh)
        Spacer(Modifier.height(18.dp))
        val live = data ?: run {
            Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(24.dp)) { Text("Toon offline", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("IP: $ip", color = TextSoft); Spacer(Modifier.height(12.dp)); Button(onClick = onSetup) { Text("Instellingen") } } }
            return@Column
        }
        Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(30.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Woonkamer", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Live van Toon · $ip", color = Green, fontSize = 11.sp)
                Spacer(Modifier.height(22.dp))
                Text(formatTemp(live.roomTemp), fontSize = 62.sp, fontWeight = FontWeight.Black)
                Text("BINNENTEMPERATUUR", color = TextSoft, fontSize = 10.sp)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSetpoint((live.setpoint - .5).coerceAtLeast(5.0)) }) { Icon(Icons.Default.Remove, null, tint = Blue) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) { Text(formatTemp(live.setpoint), fontSize = 34.sp, fontWeight = FontWeight.Black); Text("GEWENST", color = TextSoft, fontSize = 10.sp) }
                    IconButton(onClick = { onSetpoint((live.setpoint + .5).coerceAtMost(30.0)) }) { Icon(Icons.Default.Add, null, tint = Purple) }
                }
                Spacer(Modifier.height(14.dp))
                Text(if (live.heating) "VERWARMEN" else "STAND-BY", color = if (live.heating) Orange else Green, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EnergyTab(ip: String) {
    var energy by remember { mutableStateOf<EnergyData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ip) { runCatching { fetchEnergy(ip) }.onSuccess { energy = it }.onFailure { error = it.message } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("ENERGIECENTRUM", color = TextSoft, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        if (energy == null) {
            Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(24.dp)) { Text(error ?: "Energiegegevens laden…", modifier = Modifier.padding(24.dp)) }
        } else {
            val e = energy!!
            EnergyCard("STROOM NU", "${e.usage} W", "Live verbruik", Icons.Outlined.Bolt, Orange)
            Spacer(Modifier.height(12.dp))
            EnergyCard("TERUGLEVERING", "${e.production} W", "Live productie", Icons.Outlined.SolarPower, Green)
            Spacer(Modifier.height(12.dp))
            EnergyCard("GAS", String.format(Locale.US, "%.2f", e.gas), "Live gaswaarde van Toon", Icons.Outlined.LocalFireDepartment, Purple)
        }
    }
}

@Composable
fun EnergyCard(title: String, value: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color) {
    Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent.copy(alpha = .16f), shape = CircleShape) { Icon(icon, null, tint = accent, modifier = Modifier.padding(14.dp).size(28.dp)) }
            Spacer(Modifier.width(18.dp)); Column { Text(title, color = TextSoft, fontSize = 11.sp); Text(value, fontSize = 30.sp, fontWeight = FontWeight.Black); Text(subtitle, color = TextSoft, fontSize = 11.sp) }
        }
    }
}

@Composable
fun ProgramTab(data: ToonData?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("PROGRAMMA", color = TextSoft, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        if (data == null) {
            Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(24.dp)) { Text("Geen live Toon-data beschikbaar.", modifier = Modifier.padding(24.dp)) }
        } else {
            ProgramCard("PROGRAMMASTATUS", programLabel(data.programState), "Waarde: ${data.programState}", Purple)
            Spacer(Modifier.height(12.dp))
            ProgramCard("ACTIEVE STAND", activeLabel(data.activeState), "Waarde: ${data.activeState}", Blue)
            Spacer(Modifier.height(12.dp))
            ProgramCard("VOLGENDE SETPOINT", if (data.nextSetpoint > 0) formatTemp(data.nextSetpoint) else "Niet beschikbaar", if (data.nextTime > 0) "Toon nextTime: ${data.nextTime}" else "Geen volgend schakelmoment gemeld", Orange)
            Spacer(Modifier.height(12.dp))
            Text("Deze Toon-firmware ondersteunt geen getProgramInfo/getThermostatStates. Daarom toont deze tab uitsluitend werkelijk beschikbare thermostat-data.", color = TextSoft, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ProgramCard(title: String, value: String, subtitle: String, accent: Color) {
    Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(22.dp)) { Text(title, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(value, fontSize = 26.sp, fontWeight = FontWeight.Black); Text(subtitle, color = TextSoft, fontSize = 11.sp) } }
}

@Composable
fun SettingsTab(ip: String, connected: Boolean, onSetup: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) { Text("INSTELLINGEN", color = TextSoft, fontSize = 11.sp); Spacer(Modifier.height(12.dp)); Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(22.dp)) { Text("Toon IP", color = TextSoft); Text(ip, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(if (connected) "Verbonden" else "Offline", color = if (connected) Green else Red); Spacer(Modifier.height(14.dp)); Row { Button(onClick = onRefresh) { Text("Vernieuwen") }; Spacer(Modifier.width(10.dp)); OutlinedButton(onClick = onSetup) { Text("Wijzigen") } } } } }
}

@Composable
fun Header(connected: Boolean, loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column { Text("TOON REMOTE", fontWeight = FontWeight.Black, fontSize = 18.sp); Text("W.F.A. Wilbrink Software · Enterprise v1.2", color = TextSoft, fontSize = 10.sp) }; Spacer(Modifier.weight(1f)); Surface(Modifier.clickable(enabled = !loading) { onRefresh() }, color = if (connected) Green.copy(alpha = .14f) else Red.copy(alpha = .14f), shape = RoundedCornerShape(99.dp)) { Text(if (loading) "TESTEN" else if (connected) "VERBONDEN" else "OFFLINE", color = if (connected) Green else Red, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Black) } }
}

suspend fun fetchToon(ip: String): ToonData = withContext(Dispatchers.IO) {
    val o = getJson("http://$ip/happ_thermstat?action=getThermostatInfo")
    fun temp(name: String): Double {
        val raw = o.optString(name).toDoubleOrNull() ?: o.optDouble(name, Double.NaN)
        if (raw.isNaN()) error("Ongeldige $name")
        return raw / 100.0
    }
    ToonData(temp("currentTemp"), temp("currentSetpoint"), o.optInt("burnerInfo", 0) > 0, o.optInt("activeState", -1), o.optInt("programState", -1), o.optLong("nextTime", 0L), (o.optString("nextSetpoint").toDoubleOrNull() ?: 0.0) / 100.0)
}

suspend fun fetchEnergy(ip: String): EnergyData = withContext(Dispatchers.IO) {
    val o = getJson("http://$ip/happ_pwrusage?action=GetCurrentUsage")
    val usage = o.optJSONObject("powerUsage")?.optInt("value", 0) ?: 0
    val production = o.optJSONObject("powerProduction")?.optInt("value", 0) ?: 0
    val gasRaw = o.optJSONObject("gasUsage")?.optDouble("value", 0.0) ?: 0.0
    EnergyData(usage, production, gasRaw)
}

suspend fun setToonTemperature(ip: String, value: Double) = withContext(Dispatchers.IO) {
    val hundredths = (value * 100).toInt()
    val o = getJson("http://$ip/happ_thermstat?action=setSetpoint&Setpoint=$hundredths")
    if (o.optString("result") != "ok") error(o.optString("error", "Toon weigerde setpoint"))
}

private fun getJson(url: String): JSONObject {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 3500; conn.readTimeout = 3500; conn.requestMethod = "GET"
    val code = conn.responseCode
    if (code !in 200..299) error("Toon antwoordt met HTTP $code")
    val body = conn.inputStream.bufferedReader().use { it.readText() }
    return JSONObject(body)
}

private fun formatTemp(value: Double) = String.format(Locale.US, "%.1f°", value)
private fun programLabel(v: Int) = when (v) { 0 -> "Programma uit / handmatig"; 1 -> "Programma actief"; else -> "Onbekend" }
private fun activeLabel(v: Int) = when (v) { -1 -> "Geen actieve programmastand"; 0 -> "Thuis"; 1 -> "Slapen"; 2 -> "Weg"; 3 -> "Comfort"; else -> "Stand $v" }
