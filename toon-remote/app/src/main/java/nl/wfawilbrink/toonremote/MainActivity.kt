package nl.wfawilbrink.toonremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg=Color(0xFF0B1014); private val Card=Color(0xFF141B20); private val Soft=Color(0xFF1B252B); private val Mint=Color(0xFF64D7C6); private val Muted=Color(0xFF9AA9B2); private val Warm=Color(0xFFFFC66D)

class MainActivity: ComponentActivity(){ override fun onCreate(b:Bundle?){super.onCreate(b);setContent{ MaterialTheme(colorScheme=darkColorScheme(primary=Mint,background=Bg,surface=Card)){ ToonRemote() }}}}

@Composable fun ToonRemote(){ var set by remember{mutableDoubleStateOf(21.0)}; Scaffold(containerColor=Bg,bottomBar={NavigationBar(containerColor=Card){listOf(Icons.Outlined.Home to "Home",Icons.Outlined.Bolt to "Energie",Icons.Outlined.Schedule to "Programma",Icons.Outlined.Settings to "Instellingen").forEachIndexed{i,x->NavigationBarItem(selected=i==0,onClick={},icon={Icon(x.first,x.second)},label={Text(x.second)},colors=NavigationBarItemDefaults.colors(indicatorColor=Mint,selectedIconColor=Bg))}}}){p->Column(Modifier.fillMaxSize().padding(p).padding(horizontal=20.dp)){Header();Spacer(Modifier.height(8.dp));Text("WOONKAMER",color=Muted,fontSize=12.sp,letterSpacing=2.sp,modifier=Modifier.align(Alignment.CenterHorizontally));Dial(set);Row(Modifier.align(Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically){Round(Icons.Default.Remove){set=(set-.5).coerceAtLeast(5.0)};Column(Modifier.padding(horizontal=28.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(String.format("%.1f°",set).replace('.',','),fontSize=29.sp,fontWeight=FontWeight.Bold);Text("ingesteld",color=Muted,fontSize=12.sp)};Round(Icons.Default.Add){set=(set+.5).coerceAtMost(30.0)}};Spacer(Modifier.height(22.dp));Modes();Spacer(Modifier.height(18.dp));Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Metric("VERWARMING","Actief","Ketel verwarmt",Icons.Outlined.LocalFireDepartment,Modifier.weight(1f));Metric("VOLGEND","22:30","Slapen · 18,0°",Icons.Outlined.Schedule,Modifier.weight(1f))};Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Metric("STROOM NU","428 W","Realtime",Icons.Outlined.Bolt,Modifier.weight(1f));Metric("GAS VANDAAG","0,42 m³","Vandaag",Icons.Outlined.LocalFireDepartment,Modifier.weight(1f))}}}}

@Composable fun Header(){Row(Modifier.fillMaxWidth().padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).background(Mint,CircleShape),contentAlignment=Alignment.Center){Text("W",color=Bg,fontSize=23.sp,fontWeight=FontWeight.Black)};Spacer(Modifier.width(12.dp));Column{Text("TOON REMOTE",fontWeight=FontWeight.ExtraBold,letterSpacing=1.sp);Text("W.F.A. Wilbrink Software",color=Muted,fontSize=11.sp)};Spacer(Modifier.weight(1f));Surface(color=Soft,shape=RoundedCornerShape(99.dp)){Row(Modifier.padding(horizontal=10.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).background(Mint,CircleShape));Spacer(Modifier.width(6.dp));Text("Verbonden",color=Mint,fontSize=11.sp)}}}}

@Composable fun Dial(set:Double){Box(Modifier.size(245.dp).padding(6.dp).wrapContentSize().let{Modifier.size(245.dp)},contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize()){val s=10.dp.toPx();drawArc(Soft,135f,270f,false,style=Stroke(s,cap=StrokeCap.Round));drawArc(Mint,135f,270f*((set-5)/25).toFloat(),false,style=Stroke(s,cap=StrokeCap.Round))};Column(horizontalAlignment=Alignment.CenterHorizontally){Text("20,7°",fontSize=55.sp,fontWeight=FontWeight.Light);Text("BINNEN",color=Muted,fontSize=11.sp,letterSpacing=2.sp);Spacer(Modifier.height(12.dp));Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.LocalFireDepartment,null,tint=Warm,modifier=Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Verwarming actief",color=Warm,fontSize=12.sp)}}}}

@Composable fun Round(icon:androidx.compose.ui.graphics.vector.ImageVector,click:()->Unit){Surface(Modifier.size(54.dp).clickable{click()},shape=CircleShape,color=Soft){Box(contentAlignment=Alignment.Center){Icon(icon,null,tint=Mint)}}}
@Composable fun Modes(){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Thuis","Weg","Slapen","Comfort").forEach{m->Surface(Modifier.weight(1f),color=if(m=="Comfort")Mint else Soft,shape=RoundedCornerShape(16.dp)){Text(m,Modifier.padding(vertical=12.dp),textAlign=TextAlign.Center,color=if(m=="Comfort")Bg else Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold)}}}}
@Composable fun Metric(t:String,v:String,s:String,icon:androidx.compose.ui.graphics.vector.ImageVector,mod:Modifier){Surface(mod,shape=RoundedCornerShape(22.dp),color=Card){Column(Modifier.padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Mint,modifier=Modifier.size(18.dp));Spacer(Modifier.width(7.dp));Text(t,color=Muted,fontSize=10.sp)};Spacer(Modifier.height(10.dp));Text(v,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(s,color=Muted,fontSize=11.sp)}}}
