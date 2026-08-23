package nl.wfawilbrink.toonremote

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
private val Navy2 = Color(0xFF0B2034)
private val Panel = Color(0xFF102A43)
private val Panel2 = Color(0xFF163A57)
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
    val programState: Int
)

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

    LaunchedEffect(savedIp) {
        if (savedIp.isNotBlank()) test(savedIp)
    }

    if (showSetup) {
        SetupWizard(
            initialIp = savedIp,
            state = connection,
            onTestAndSave = { test(it, true) }
        )
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
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF071A2D), Color(0xFF0C3150), Color(0xFF11152C)))
        )
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            BrandMark(84.dp)
            Spacer(Modifier.height(20.dp))
            Text("TOON REMOTE", fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Enterprise Edition", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("W.F.A. Wilbrink Software", color = TextSoft, fontSize = 12.sp)
            Spacer(Modifier.height(34.dp))

            Surface(Modifier.fillMaxWidth(), color = Color(0xCC102A43), shape = RoundedCornerShape(30.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientIcon(Icons.Outlined.Router, Blue, Purple)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Koppel uw Toon 2", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Eenmalige installatie", color = TextSoft, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("Vul het lokale IP-adres van uw gerootte Toon 2 in.", color = TextSoft)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Toon IP-adres") },
                        placeholder = { Text("bijv. 192.168.1.100") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Wifi, null, tint = Cyan) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan,
                            focusedLabelColor = Cyan
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { if (ip.isNotBlank()) onTestAndSave(ip) },
                        enabled = state !is ConnectionState.Testing && ip.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Navy)
                    ) {
                        if (state is ConnectionState.Testing) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Navy)
                            Spacer(Modifier.width(10.dp))
                            Text("VERBINDING TESTEN…", fontWeight = FontWeight.Black)
                        } else {
                            Icon(Icons.Outlined.Link, null)
                            Spacer(Modifier.width(10.dp))
                            Text("TEST & VERBIND", fontWeight = FontWeight.Black)
                        }
                    }
                    if (state is ConnectionState.Failed) {
                        Spacer(Modifier.height(14.dp))
                        Surface(color = Red.copy(alpha = .16f), shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ErrorOutline, null, tint = Red)
                                Spacer(Modifier.width(10.dp))
                                Text(state.message, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    InfoStep("1", "Telefoon/tablet en Toon op dezelfde wifi")
                    InfoStep("2", "IP-adres van Toon invullen")
                    InfoStep("3", "De app test de échte Toon-API")
                    InfoStep("4", "Pas daarna verschijnt het dashboard")
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Geen Toon gevonden = geen nepstatus. De app meldt alleen ‘Verbonden’ na een echte API-response.", color = TextSoft, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun EnterpriseDashboard(ip: String, state: ConnectionState, onRefresh: () -> Unit, onSetup: () -> Unit, onSetpoint: (Double) -> Unit) {
    val data = (state as? ConnectionState.Connected)?.data
    val connected = data != null
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Navy,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF091A2A)) {
                val items = listOf(
                    Triple(Icons.Outlined.Home, "Home", 0),
                    Triple(Icons.Outlined.Bolt, "Energie", 1),
                    Triple(Icons.Outlined.Schedule, "Programma", 2),
                    Triple(Icons.Outlined.Settings, "Instellingen", 3)
                )
                items.forEach { item ->
                    NavigationBarItem(
                        selected = selectedTab == item.third,
                        onClick = { selectedTab = item.third },
                        icon = { Icon(item.first, item.second) },
                        label = { Text(item.second) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Navy,
                            selectedTextColor = Cyan,
                            indicatorColor = Cyan
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(
            Modifier.fillMaxSize().padding(pad).background(
                Brush.verticalGradient(listOf(Color(0xFF071522), Color(0xFF0B2940), Color(0xFF071522)))
            )
        ) {
            when (selectedTab) {
                0 -> HomeTab(ip, state, data, connected, onRefresh, onSetup, onSetpoint)
                1 -> EmptyEnterpriseTab("ENERGIE", "Energiegegevens worden alleen getoond wanneer uw Toon ze werkelijk beschikbaar stelt.", Icons.Outlined.Bolt, Orange)
                2 -> EmptyEnterpriseTab("PROGRAMMA", "Programmastanden worden in de volgende koppellaag rechtstreeks van Toon gelezen.", Icons.Outlined.Schedule, Purple)
                else -> SettingsTab(ip, connected, onSetup, onRefresh)
            }
        }
    }
}

@Composable
fun HomeTab(ip: String, state: ConnectionState, data: ToonData?, connected: Boolean, onRefresh: () -> Unit, onSetup: () -> Unit, onSetpoint: (Double) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        EnterpriseHeader(connected, state is ConnectionState.Testing, onRefresh)
        Spacer(Modifier.height(16.dp))

        if (!connected) {
            DisconnectedCard(ip, state, onRefresh, onSetup)
            return@Column
        }

        Text("KLIMAATCENTRUM", color = TextSoft, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Surface(
            Modifier.fillMaxWidth(),
            color = Color(0xCC102A43),
            shape = RoundedCornerShape(30.dp)
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Woonkamer", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Live van Toon · $ip", color = Green, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(color = if (data!!.heating) Orange.copy(alpha = .18f) else Green.copy(alpha = .14f), shape = RoundedCornerShape(99.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocalFireDepartment, null, tint = if (data.heating) Orange else Green, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (data.heating) "VERWARMEN" else "STAND-BY", color = if (data.heating) Orange else Green, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                PremiumDial(data.roomTemp, data.setpoint, data.heating)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleAction(Icons.Default.Remove, Blue) { onSetpoint((data.setpoint - .5).coerceAtLeast(5.0)) }
                    Column(Modifier.padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatTemp(data.setpoint), fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text("GEWENST", color = TextSoft, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    }
                    CircleAction(Icons.Default.Add, Purple) { onSetpoint((data.setpoint + .5).coerceAtMost(30.0)) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorMetric("BINNEN", formatTemp(data.roomTemp), "Live temperatuur", Icons.Outlined.Thermostat, Blue, Modifier.weight(1f))
            ColorMetric("SETPOINT", formatTemp(data.setpoint), "Actief doel", Icons.Outlined.Tune, Purple, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorMetric("KETEL", if (data.heating) "AAN" else "UIT", if (data.heating) "Warmtevraag actief" else "Geen warmtevraag", Icons.Outlined.LocalFireDepartment, Orange, Modifier.weight(1f))
            ColorMetric("VERBINDING", "ONLINE", "Lokale Toon API", Icons.Outlined.CloudDone, Green, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun EnterpriseHeader(connected: Boolean, loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BrandMark(48.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("TOON REMOTE", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.2.sp)
            Text("W.F.A. Wilbrink Software · Enterprise", color = TextSoft, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(1f))
        Surface(
            Modifier.clickable(enabled = !loading) { onRefresh() },
            color = if (connected) Green.copy(alpha = .14f) else Red.copy(alpha = .14f),
            shape = RoundedCornerShape(99.dp)
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Cyan)
                else Box(Modifier.size(8.dp).background(if (connected) Green else Red, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(if (loading) "TESTEN" else if (connected) "VERBONDEN" else "OFFLINE", color = if (connected) Green else if (loading) Cyan else Red, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun PremiumDial(room: Double, set: Double, heating: Boolean) {
    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            drawArc(Color(0xFF21435A), 135f, 270f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            val progress = ((set - 5.0) / 25.0).coerceIn(0.0, 1.0).toFloat()
            drawArc(Cyan, 135f, 270f * progress, false, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Purple.copy(alpha = .45f), 135f + 270f * progress - 28f, 28f, false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatTemp(room), fontSize = 56.sp, fontWeight = FontWeight.Light)
            Text("ACTUEEL", color = TextSoft, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocalFireDepartment, null, tint = if (heating) Orange else TextSoft, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (heating) "Verwarming actief" else "Verwarming stand-by", color = if (heating) Orange else TextSoft, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun CircleAction(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Surface(Modifier.size(56.dp).clickable { onClick() }, shape = CircleShape, color = color.copy(alpha = .18f)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
fun ColorMetric(title: String, value: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(color.copy(alpha = .16f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(7.dp).background(color, CircleShape))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, color = TextSoft, fontSize = 9.sp, letterSpacing = 1.2.sp)
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = TextSoft, fontSize = 10.sp)
        }
    }
}

@Composable
fun DisconnectedCard(ip: String, state: ConnectionState, onRefresh: () -> Unit, onSetup: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).background(Red.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CloudOff, null, tint = Red, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Geen Toon verbonden", fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text("De app toont pas gegevens na een echte verbinding met de gerootte Toon 2.", color = TextSoft, textAlign = TextAlign.Center)
            if (state is ConnectionState.Failed) {
                Spacer(Modifier.height(10.dp))
                Text(state.message, color = Red, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Navy), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text("OPNIEUW TESTEN", fontWeight = FontWeight.Black)
            }
            TextButton(onClick = onSetup) { Text("IP-adres wijzigen") }
            Text("Huidig adres: $ip:80", color = TextSoft, fontSize = 10.sp)
        }
    }
}

@Composable
fun SettingsTab(ip: String, connected: Boolean, onSetup: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        EnterpriseHeader(connected, false, onRefresh)
        Spacer(Modifier.height(26.dp))
        Text("INSTELLINGEN", fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("Verbinding & systeem", color = TextSoft)
        Spacer(Modifier.height(18.dp))
        SettingsRow(Icons.Outlined.Router, "Lokale Toon", "$ip · poort 80", Blue, onSetup)
        Spacer(Modifier.height(12.dp))
        SettingsRow(Icons.Outlined.Security, "Remote toegang", "HTTPS poort 443 · volgende fase", Purple, {})
        Spacer(Modifier.height(12.dp))
        SettingsRow(Icons.Outlined.Info, "App-versie", "1.1 Enterprise Preview", Green, {})
    }
}

@Composable
fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable { onClick() }, color = Panel, shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientIcon(icon, color, Cyan)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = TextSoft, fontSize = 11.sp) }
            Icon(Icons.Outlined.ChevronRight, null, tint = TextSoft)
        }
    }
}

@Composable
fun EmptyEnterpriseTab(title: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(54.dp))
        Box(Modifier.size(84.dp).background(color.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(42.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(text, color = TextSoft, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Surface(color = Panel, shape = RoundedCornerShape(20.dp)) { Text("Geen fictieve gegevens weergegeven", Modifier.padding(16.dp), color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).background(Brush.linearGradient(listOf(Cyan, Blue, Purple)), CircleShape),
        contentAlignment = Alignment.Center
    ) { Text("W", color = Navy, fontSize = (size.value * .48f).sp, fontWeight = FontWeight.Black) }
}

@Composable
fun GradientIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, c1: Color, c2: Color) {
    Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(c1.copy(alpha = .28f), c2.copy(alpha = .18f))), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c1, modifier = Modifier.size(23.dp))
    }
}

@Composable
fun InfoStep(number: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(Cyan.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) { Text(number, color = Cyan, fontWeight = FontWeight.Black, fontSize = 11.sp) }
        Spacer(Modifier.width(11.dp))
        Text(text, color = TextSoft, fontSize = 12.sp)
    }
}

private fun formatTemp(v: Double): String = String.format(Locale.US, "%.1f°", v).replace('.', ',')

private suspend fun fetchToon(ip: String): ToonData = withContext(Dispatchers.IO) {
    val clean = ip.removePrefix("http://").removePrefix("https://").substringBefore('/')
    val url = URL("http://$clean/happ_thermstat?action=getThermostatInfo")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 4000
        readTimeout = 4000
        requestMethod = "GET"
        useCaches = false
    }
    try {
        if (conn.responseCode !in 200..299) error("Toon antwoordde met HTTP ${conn.responseCode}")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val o = JSONObject(body)
        val currentRaw = o.optDouble("currentTemp", Double.NaN)
        val setRaw = o.optDouble("currentSetpoint", Double.NaN)
        if (currentRaw.isNaN() || setRaw.isNaN()) error("Dit adres geeft geen geldige Toon thermostaatdata terug")
        fun norm(v: Double) = if (v > 100.0) v / 100.0 else v
        ToonData(
            roomTemp = norm(currentRaw),
            setpoint = norm(setRaw),
            heating = o.optInt("burnerInfo", 0) > 0,
            activeState = o.optInt("activeState", -1),
            programState = o.optInt("programState", -1)
        )
    } finally {
        conn.disconnect()
    }
}

private suspend fun setToonTemperature(ip: String, value: Double) = withContext(Dispatchers.IO) {
    val clean = ip.removePrefix("http://").removePrefix("https://").substringBefore('/')
    val setpoint = (value * 100).toInt()
    val url = URL("http://$clean/happ_thermstat?action=changeTemperature&Setpoint=$setpoint")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 4000
        readTimeout = 4000
        requestMethod = "GET"
        useCaches = false
    }
    try {
        if (conn.responseCode !in 200..299) error("Temperatuur wijzigen mislukt: HTTP ${conn.responseCode}")
        conn.inputStream.close()
    } finally {
        conn.disconnect()
    }
}
