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

private val Bg = Color(0xFF06111C)
private val Bg2 = Color(0xFF0A1C2C)
private val Panel = Color(0xFF102A3C)
private val Aqua = Color(0xFF33E6D0)
private val Blue = Color(0xFF66A6FF)
private val Purple = Color(0xFFA887FF)
private val Orange = Color(0xFFFFB45C)
private val Green = Color(0xFF55E39A)
private val Red = Color(0xFFFF6D7C)
private val Soft = Color(0xFF9CB8C9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Aqua, background = Bg, surface = Panel)) { ToonRemoteApp() } }
    }
}

data class ToonData(val roomTemp: Double, val setpoint: Double, val heating: Boolean, val activeState: Int, val programState: Int, val nextTime: Long, val nextSetpoint: Double)
data class EnergyData(val usage: Int, val production: Int, val gas: Double)
data class Endpoint(val baseUrl: String, val mode: String)
data class QuickProgram(val name: String, val temp: Double, val accent: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector)

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Testing : ConnectionState()
    data class Connected(val data: ToonData, val endpoint: Endpoint) : ConnectionState()
    data class Failed(val message: String) : ConnectionState()
}

@Composable
fun ToonRemoteApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("toon_remote", Context.MODE_PRIVATE) }
    var localIp by remember { mutableStateOf(prefs.getString("local_ip", "192.168.2.52") ?: "192.168.2.52") }
    var remoteHost by remember { mutableStateOf(prefs.getString("remote_host", "86.87.8.101") ?: "86.87.8.101") }
    var remotePort by remember { mutableStateOf(prefs.getString("remote_port", "8089") ?: "8089") }
    var state by remember { mutableStateOf<ConnectionState>(ConnectionState.Idle) }
    var showSetup by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun connect(): ConnectionState {
        val local = Endpoint("http://$localIp", "LOKAAL")
        runCatching { return ConnectionState.Connected(fetchToon(local), local) }
        if (remoteHost.isNotBlank()) {
            val remote = Endpoint("http://$remoteHost:$remotePort", "REMOTE")
            runCatching { return ConnectionState.Connected(fetchToon(remote), remote) }
        }
        return ConnectionState.Failed("Geen verbinding met lokale Toon of extern adres")
    }

    fun refresh() { state = ConnectionState.Testing; scope.launch { state = connect() } }
    LaunchedEffect(localIp, remoteHost, remotePort) { refresh() }

    if (showSetup) {
        SetupScreen(localIp, remoteHost, remotePort, onSave = { l, r, p ->
            localIp = l; remoteHost = r; remotePort = p
            prefs.edit().putString("local_ip", l).putString("remote_host", r).putString("remote_port", p).apply()
            showSetup = false; refresh()
        }, onBack = { showSetup = false })
    } else {
        Dashboard(state, prefs, ::refresh, { showSetup = true }) { value ->
            val endpoint = (state as? ConnectionState.Connected)?.endpoint ?: return@Dashboard
            scope.launch { runCatching { setToonTemperature(endpoint, value) }; state = connect() }
        }
    }
}

@Composable
fun Dashboard(state: ConnectionState, prefs: android.content.SharedPreferences, onRefresh: () -> Unit, onSettings: () -> Unit, onSetpoint: (Double) -> Unit) {
    val connected = state as? ConnectionState.Connected
    val data = connected?.data
    val endpoint = connected?.endpoint
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(containerColor = Bg, bottomBar = {
        NavigationBar(containerColor = Color(0xFF081722)) {
            listOf(Triple(Icons.Outlined.Home, "Home", 0), Triple(Icons.Outlined.Bolt, "Energie", 1), Triple(Icons.Outlined.Schedule, "Programma", 2), Triple(Icons.Outlined.Settings, "Instellingen", 3)).forEach { item ->
                NavigationBarItem(selected = tab == item.third, onClick = { tab = item.third }, icon = { Icon(item.first, item.second) }, label = { Text(item.second) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Aqua, selectedIconColor = Bg, selectedTextColor = Aqua))
            }
        }
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(Brush.verticalGradient(listOf(Bg2, Bg, Color(0xFF07151F))))) {
            when (tab) {
                0 -> HomeScreen(state, data, endpoint, onRefresh, onSettings, onSetpoint)
                1 -> EnergyScreen(endpoint)
                2 -> ProgramScreen(data, prefs, onSetpoint)
                else -> SettingsScreen(endpoint, onSettings, onRefresh)
            }
        }
    }
}

@Composable
fun HomeScreen(state: ConnectionState, data: ToonData?, endpoint: Endpoint?, onRefresh: () -> Unit, onSettings: () -> Unit, onSetpoint: (Double) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(16.dp)); Header(endpoint, state is ConnectionState.Testing, onRefresh); Spacer(Modifier.height(18.dp))
        if (data == null) {
            Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(28.dp)) { Column(Modifier.padding(24.dp)) { Text("Geen verbinding", fontSize = 24.sp, fontWeight = FontWeight.Black); Text((state as? ConnectionState.Failed)?.message ?: "Toon wordt gezocht…", color = Soft); Spacer(Modifier.height(16.dp)); Button(onClick = onSettings) { Text("Verbinding instellen") } } }
            return@Column
        }
        Surface(Modifier.fillMaxWidth(), color = Color(0xD9102A3C), shape = RoundedCornerShape(34.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column { Text("Woonkamer", fontSize = 24.sp, fontWeight = FontWeight.Black); Text(endpoint?.mode ?: "", color = if (endpoint?.mode == "REMOTE") Purple else Green, fontSize = 11.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.weight(1f)); Surface(color = if (data.heating) Orange.copy(alpha=.16f) else Green.copy(alpha=.15f), shape = RoundedCornerShape(99.dp)) { Text(if (data.heating) "VERWARMEN" else "STAND-BY", color = if (data.heating) Orange else Green, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Black) } }
                Spacer(Modifier.height(8.dp)); ThermostatRing(data.roomTemp, data.setpoint, data.heating); Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { SetpointButton(Icons.Default.Remove, Blue) { onSetpoint((data.setpoint - .5).coerceAtLeast(5.0)) }; Column(Modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(formatTemp(data.setpoint), fontSize = 34.sp, fontWeight = FontWeight.Black); Text("GEWENST", color = Soft, fontSize = 10.sp, letterSpacing = 1.4.sp) }; SetpointButton(Icons.Default.Add, Purple) { onSetpoint((data.setpoint + .5).coerceAtMost(30.0)) } }
            }
        }
        Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricTile("BINNEN", formatTemp(data.roomTemp), Icons.Outlined.Thermostat, Blue, Modifier.weight(1f)); MetricTile("DOEL", formatTemp(data.setpoint), Icons.Outlined.Tune, Purple, Modifier.weight(1f)) }
        Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricTile("KETEL", if (data.heating) "AAN" else "UIT", Icons.Outlined.LocalFireDepartment, Orange, Modifier.weight(1f)); MetricTile("LINK", endpoint?.mode ?: "OFFLINE", Icons.Outlined.Wifi, Green, Modifier.weight(1f)) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ThermostatRing(room: Double, setpoint: Double, heating: Boolean) {
    Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { val stroke = 16.dp.toPx(); drawArc(Color(0xFF244355), 135f, 270f, false, style = Stroke(stroke, cap = StrokeCap.Round)); val progress = ((setpoint - 5.0) / 25.0).coerceIn(0.0, 1.0).toFloat(); drawArc(if (heating) Orange else Aqua, 135f, 270f * progress, false, style = Stroke(stroke, cap = StrokeCap.Round)); drawCircle(Color(0x221FFFFFF), radius = size.minDimension * .35f); drawCircle(Aqua.copy(alpha=.25f), radius = 5.dp.toPx(), center = Offset(size.width * .5f, size.height * .08f)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(formatTemp(room), fontSize = 58.sp, fontWeight = FontWeight.Black); Text("BINNENTEMPERATUUR", color = Soft, fontSize = 10.sp, letterSpacing = 1.3.sp) }
    }
}

@Composable
fun SetpointButton(icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: () -> Unit) { Surface(Modifier.size(56.dp).clickable { onClick() }, color = accent.copy(alpha=.16f), shape = CircleShape) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp)) } } }

@Composable
fun MetricTile(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier = Modifier) { Surface(modifier, color = Panel, shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(18.dp)) { Icon(icon, null, tint = accent); Spacer(Modifier.height(14.dp)); Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black); Text(title, color = Soft, fontSize = 10.sp) } } }

@Composable
fun EnergyScreen(endpoint: Endpoint?) {
    var energy by remember(endpoint?.baseUrl) { mutableStateOf<EnergyData?>(null) }; var error by remember(endpoint?.baseUrl) { mutableStateOf<String?>(null) }
    LaunchedEffect(endpoint?.baseUrl) { if (endpoint != null) runCatching { fetchEnergy(endpoint) }.onSuccess { energy = it }.onFailure { error = it.message } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) { Text("ENERGIE", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Live uit Toon", color = Soft); Spacer(Modifier.height(16.dp)); val e = energy; if (e == null) { Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(26.dp)) { Text(error ?: "Laden…", modifier = Modifier.padding(24.dp)) } } else { EnergyHero("Stroom nu", "${e.usage} W", Orange, Icons.Outlined.Bolt); Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { EnergySmall("Productie", "${e.production} W", Green, Modifier.weight(1f)); EnergySmall("Gas", String.format(Locale.US, "%.2f", e.gas), Purple, Modifier.weight(1f)) } } }
}

@Composable
fun EnergyHero(title: String, value: String, accent: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) { Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(30.dp)) { Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = accent.copy(alpha=.16f), shape = CircleShape) { Icon(icon, null, tint = accent, modifier = Modifier.padding(16.dp).size(32.dp)) }; Spacer(Modifier.width(20.dp)); Column { Text(title, color = Soft); Text(value, fontSize = 38.sp, fontWeight = FontWeight.Black) } } } }
@Composable
fun EnergySmall(title: String, value: String, accent: Color, modifier: Modifier) { Surface(modifier, color = Panel, shape = RoundedCornerShape(26.dp)) { Column(Modifier.padding(20.dp)) { Text(title, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); Text(value, fontSize = 26.sp, fontWeight = FontWeight.Black) } } }

@Composable
fun ProgramScreen(data: ToonData?, prefs: android.content.SharedPreferences, onSetpoint: (Double) -> Unit) {
    var p1 by remember { mutableDoubleStateOf(prefs.getFloat("p1", 21.0f).toDouble()) }
    var p2 by remember { mutableDoubleStateOf(prefs.getFloat("p2", 20.0f).toDouble()) }
    var p3 by remember { mutableDoubleStateOf(prefs.getFloat("p3", 17.0f).toDouble()) }
    var p4 by remember { mutableDoubleStateOf(prefs.getFloat("p4", 15.0f).toDouble()) }
    val programs = listOf(
        QuickProgram("Comfort", p1, Orange, Icons.Outlined.WbSunny),
        QuickProgram("Thuis", p2, Aqua, Icons.Outlined.Home),
        QuickProgram("Slapen", p3, Purple, Icons.Outlined.Bedtime),
        QuickProgram("Weg", p4, Blue, Icons.Outlined.Luggage)
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("PROGRAMMA'S", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Vier eigen temperatuurstanden", color = Soft); Spacer(Modifier.height(16.dp))
        programs.forEachIndexed { index, p ->
            ProgramControlCard(p, data?.setpoint, onMinus = {
                val v = (p.temp - .5).coerceAtLeast(5.0)
                when (index) { 0 -> { p1 = v; prefs.edit().putFloat("p1", v.toFloat()).apply() }; 1 -> { p2 = v; prefs.edit().putFloat("p2", v.toFloat()).apply() }; 2 -> { p3 = v; prefs.edit().putFloat("p3", v.toFloat()).apply() }; else -> { p4 = v; prefs.edit().putFloat("p4", v.toFloat()).apply() } }
            }, onPlus = {
                val v = (p.temp + .5).coerceAtMost(30.0)
                when (index) { 0 -> { p1 = v; prefs.edit().putFloat("p1", v.toFloat()).apply() }; 1 -> { p2 = v; prefs.edit().putFloat("p2", v.toFloat()).apply() }; 2 -> { p3 = v; prefs.edit().putFloat("p3", v.toFloat()).apply() }; else -> { p4 = v; prefs.edit().putFloat("p4", v.toFloat()).apply() } }
            }, onActivate = { onSetpoint(p.temp) })
            Spacer(Modifier.height(12.dp))
        }
        if (data != null) { Text("Huidige Toon-setpoint: ${formatTemp(data.setpoint)}", color = Soft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
    }
}

@Composable
fun ProgramControlCard(program: QuickProgram, currentSetpoint: Double?, onMinus: () -> Unit, onPlus: () -> Unit, onActivate: () -> Unit) {
    val active = currentSetpoint != null && kotlin.math.abs(currentSetpoint - program.temp) < .01
    Surface(Modifier.fillMaxWidth(), color = if (active) program.accent.copy(alpha=.13f) else Panel, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Surface(color = program.accent.copy(alpha=.16f), shape = CircleShape) { Icon(program.icon, null, tint = program.accent, modifier = Modifier.padding(12.dp).size(24.dp)) }; Spacer(Modifier.width(14.dp)); Column { Text(program.name, fontSize = 20.sp, fontWeight = FontWeight.Black); Text(if (active) "ACTIEF" else "Eigen programma", color = if (active) Green else Soft, fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.weight(1f)); Text(formatTemp(program.temp), fontSize = 26.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { OutlinedIconButton(onClick = onMinus) { Icon(Icons.Default.Remove, null) }; Spacer(Modifier.width(8.dp)); OutlinedIconButton(onClick = onPlus) { Icon(Icons.Default.Add, null) }; Spacer(Modifier.weight(1f)); Button(onClick = onActivate, colors = ButtonDefaults.buttonColors(containerColor = program.accent, contentColor = Bg), shape = RoundedCornerShape(16.dp)) { Text("ACTIVEREN", fontWeight = FontWeight.Black) } }
        }
    }
}

@Composable
fun SettingsScreen(endpoint: Endpoint?, onSettings: () -> Unit, onRefresh: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Text("INSTELLINGEN", fontSize = 28.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(16.dp)); Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(28.dp)) { Column(Modifier.padding(22.dp)) { Text("Actieve verbinding", color = Soft); Text(endpoint?.mode ?: "OFFLINE", color = if (endpoint?.mode == "REMOTE") Purple else Green, fontSize = 24.sp, fontWeight = FontWeight.Black); Text(endpoint?.baseUrl ?: "Geen verbinding", color = Soft); Spacer(Modifier.height(16.dp)); Row { Button(onClick = onRefresh) { Text("Vernieuwen") }; Spacer(Modifier.width(10.dp)); OutlinedButton(onClick = onSettings) { Text("Netwerk") } } } } } }

@Composable
fun SetupScreen(localIp: String, remoteHost: String, remotePort: String, onSave: (String,String,String)->Unit, onBack:()->Unit) { var l by remember { mutableStateOf(localIp) }; var r by remember { mutableStateOf(remoteHost) }; var p by remember { mutableStateOf(remotePort) }; Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) { Text("VERBINDING", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Lokaal + extern", color = Soft); Spacer(Modifier.height(18.dp)); Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(28.dp)) { Column(Modifier.padding(22.dp)) { OutlinedTextField(l,{l=it.trim()},Modifier.fillMaxWidth(),label={Text("Lokaal Toon IP")}); Spacer(Modifier.height(12.dp)); OutlinedTextField(r,{r=it.trim()},Modifier.fillMaxWidth(),label={Text("Extern IP / host")}); Spacer(Modifier.height(12.dp)); OutlinedTextField(p,{p=it.trim()},Modifier.fillMaxWidth(),label={Text("Externe poort")}); Spacer(Modifier.height(18.dp)); Button(onClick={onSave(l,r,p)},Modifier.fillMaxWidth()){Text("OPSLAAN",fontWeight=FontWeight.Black)}; TextButton(onClick=onBack,Modifier.fillMaxWidth()){Text("Annuleren")} } } } }

@Composable
fun Header(endpoint: Endpoint?, loading: Boolean, onRefresh: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column { Text("TOON REMOTE", fontWeight = FontWeight.Black, fontSize = 18.sp); Text("W.F.A. Wilbrink Software · v1.4", color = Soft, fontSize = 10.sp) }; Spacer(Modifier.weight(1f)); Surface(Modifier.clickable(enabled=!loading){onRefresh()}, color = if(endpoint!=null) Green.copy(alpha=.14f) else Red.copy(alpha=.14f), shape=RoundedCornerShape(99.dp)){ Text(if(loading)"TESTEN" else endpoint?.mode ?: "OFFLINE", color=if(endpoint!=null)Green else Red, modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp), fontSize=10.sp,fontWeight=FontWeight.Black)} } }

suspend fun fetchToon(endpoint: Endpoint): ToonData = withContext(Dispatchers.IO) { val o=getJson("${endpoint.baseUrl}/happ_thermstat?action=getThermostatInfo"); fun t(n:String):Double{val raw=o.optString(n).toDoubleOrNull()?:o.optDouble(n,Double.NaN);if(raw.isNaN())error("Ongeldige $n");return raw/100.0}; ToonData(t("currentTemp"),t("currentSetpoint"),o.optInt("burnerInfo",0)>0,o.optInt("activeState",-1),o.optInt("programState",-1),o.optLong("nextTime",0L),(o.optString("nextSetpoint").toDoubleOrNull()?:0.0)/100.0) }
suspend fun fetchEnergy(endpoint: Endpoint): EnergyData = withContext(Dispatchers.IO) { val o=getJson("${endpoint.baseUrl}/happ_pwrusage?action=GetCurrentUsage"); val pu=o.optJSONObject("powerUsage")?:JSONObject(); val pp=o.optJSONObject("powerProduction")?:JSONObject(); val gu=o.optJSONObject("gasUsage")?:JSONObject(); EnergyData(pu.optInt("value",0),pp.optInt("value",0),gu.optDouble("value",0.0)) }
suspend fun setToonTemperature(endpoint: Endpoint,value:Double)=withContext(Dispatchers.IO){ val v=(value*100).toInt(); getJson("${endpoint.baseUrl}/happ_thermstat?action=setSetpoint&Setpoint=$v") }
fun getJson(url:String):JSONObject{ val c=(URL(url).openConnection() as HttpURLConnection); c.connectTimeout=3500;c.readTimeout=3500;c.requestMethod="GET"; if(c.responseCode !in 200..299) error("HTTP ${c.responseCode}"); val b=c.inputStream.bufferedReader().use{it.readText()}; val o=JSONObject(b); if(o.optString("result")!="ok") error(o.optString("error","Toon-fout")); return o }
fun formatTemp(v:Double)=String.format(Locale.US,"%.1f°",v)
