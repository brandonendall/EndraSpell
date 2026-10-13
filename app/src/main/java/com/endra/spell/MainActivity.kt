package com.endra.spell
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
private val Navy=Color(0xFF020D08); private val Forest=Color(0xFF06331D); private val Card=Color(0xFF082B1B); private val Gold=Color(0xFFE7C65B); private val Leaf=Color(0xFF5EA83F); private val Cream=Color(0xFFFFF0C2); private val Parchment=Color(0xFFF3DFA7)
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  setContent { MaterialTheme { App22() } }
 }
}
// Each illustrated panel uses the original approved artwork, with its own real
// accessible control. Source rectangles avoid cropping or shifting hit targets.
@Composable fun Home(child:String,choose:(String)->Unit,go:(String)->Unit){
 val art=androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.home_approved)
 BoxWithConstraints(Modifier.fillMaxSize().background(Navy).safeDrawingPadding()){
  val landscape=maxWidth>maxHeight
  if(landscape){
   Row(Modifier.fillMaxSize()){
    Artwork(art,0,32,992,545,Modifier.weight(0.43f).fillMaxHeight())
    HomeControls(art,child,choose,go,Modifier.weight(0.57f).fillMaxHeight(),true)
   }
  }else{
   Column(Modifier.fillMaxSize()){
    Artwork(art,0,32,992,545,Modifier.fillMaxWidth().weight(0.34f))
    HomeControls(art,child,choose,go,Modifier.fillMaxWidth().weight(0.66f),false)
   }
  }
 }
}
@Composable private fun Artwork(art:androidx.compose.ui.graphics.ImageBitmap,left:Int,top:Int,right:Int,bottom:Int,modifier:Modifier=Modifier){
 Canvas(modifier){drawImage(art,srcOffset=androidx.compose.ui.unit.IntOffset(left,top),srcSize=androidx.compose.ui.unit.IntSize(right-left,bottom-top),dstSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt()))}
}
@Composable private fun ArtControl(art:androidx.compose.ui.graphics.ImageBitmap,left:Int,top:Int,right:Int,bottom:Int,label:String,modifier:Modifier,onClick:()->Unit){
 Artwork(art,left,top,right,bottom,modifier.clickable(role=androidx.compose.ui.semantics.Role.Button,onClick=onClick).semantics{contentDescription=label})
}
@Composable private fun HomeControls(art:androidx.compose.ui.graphics.ImageBitmap,child:String,choose:(String)->Unit,go:(String)->Unit,modifier:Modifier,compact:Boolean){
 Column(modifier){
  Row(Modifier.fillMaxWidth().weight(0.21f)){
   ArtControl(art,0,545,496,750,"Practice Words",Modifier.weight(1f).fillMaxHeight()){go("quiz")}
   ArtControl(art,496,545,992,750,"Heart Words",Modifier.weight(1f).fillMaxHeight()){go("hearts")}
  }
  Row(Modifier.fillMaxWidth().weight(0.21f)){
   ArtControl(art,0,750,496,955,"Add or Edit Words",Modifier.weight(1f).fillMaxHeight()){go("edit")}
   ArtControl(art,496,750,992,955,"Progress",Modifier.weight(1f).fillMaxHeight()){go("progress")}
  }
  Artwork(art,0,955,992,1060,Modifier.fillMaxWidth().weight(if(compact)0.06f else 0.10f).semantics{contentDescription="Choose your learner"})
  Row(Modifier.fillMaxWidth().weight(if(compact)0.15f else 0.075f).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   listOf("James","Easton","Aiden").forEach{name->
    val active=child==name
    Box(Modifier.weight(1f).fillMaxHeight().selectable(selected=active,role=androidx.compose.ui.semantics.Role.RadioButton,onClick={choose(name)}).background(if(active)Gold else Forest,RoundedCornerShape(50)).border(1.5.dp,Gold,RoundedCornerShape(50)),contentAlignment=Alignment.Center){
     Text(learnerLabel(name),color=if(active)Navy else Cream,fontWeight=FontWeight.Bold,fontSize=16.sp,maxLines=1)
    }
   }
  }
  ArtControl(art,0,1125,992,1290,"Enter the Forest",Modifier.fillMaxWidth().weight(if(compact)0.21f else 0.18f)){go("dashboard")}
  Artwork(art,0,1290,992,1586,Modifier.fillMaxWidth().weight(if(compact)0.06f else 0.225f))
 }
}

@Composable private fun Scene(content:@Composable BoxWithConstraintsScope.()->Unit){
 BoxWithConstraints(Modifier.fillMaxSize().background(Navy).safeDrawingPadding()){
  Image(painterResource(R.drawable.castle_seamless),null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
  content()
 }
}

@Composable private fun LearnerPlaque(name:String,onClick:()->Unit){
 val art=androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.learner_plaque)
 BoxWithConstraints(Modifier.fillMaxWidth()){
  val plaqueHeight=(maxWidth/6).coerceIn(58.dp,96.dp)
  Box(Modifier.fillMaxWidth().height(plaqueHeight).clickable(role=androidx.compose.ui.semantics.Role.Button,onClick=onClick),contentAlignment=Alignment.Center){
   Canvas(Modifier.fillMaxSize()){
    val sw=art.width/5
    val fixed=sw.toFloat()/art.height*size.height
    val flexible=(size.width-3*fixed)/2
    var x=0f
    for(i in 0..4){
     val width=if(i%2==0)fixed else flexible
     drawImage(art,srcOffset=androidx.compose.ui.unit.IntOffset(i*sw,0),srcSize=androidx.compose.ui.unit.IntSize(if(i==4)art.width-i*sw else sw,art.height),dstOffset=androidx.compose.ui.unit.IntOffset(x.toInt(),0),dstSize=androidx.compose.ui.unit.IntSize(width.toInt()+1,size.height.toInt()))
     x+=width
    }
   }
   Text(name,color=Cream,fontSize=21.sp,fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,fontWeight=FontWeight.Bold,maxLines=1,textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.fillMaxWidth(.72f).offset(y=plaqueHeight*.035f))
  }
 }
}

@Composable fun LearnerHome(learners:List<LearningStore.Learner>,open:(String)->Unit,go:(String)->Unit){
 var menu by remember{mutableStateOf(false)}
 var page by rememberSaveable{mutableIntStateOf(0)}
 Scene {
  val shortScreen=maxHeight<500.dp
  val perPage=if(shortScreen)1 else 3
  val pages=((learners.size+perPage-1)/perPage).coerceAtLeast(1)
  LaunchedEffect(pages){page=page.coerceAtMost(pages-1)}
  val start=maxHeight*(if(shortScreen).20f else .39f)
  Column(Modifier.align(Alignment.TopCenter).padding(top=start).fillMaxWidth(.82f).widthIn(max=760.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
   Text("✦ Choose your learner ✦",color=Gold,fontSize=21.sp,fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,fontWeight=FontWeight.Bold,modifier=Modifier.padding(bottom=10.dp))
   learners.drop(page*perPage).take(perPage).forEach{learner->LearnerPlaque(learner.name){open(learner.id)}}
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
    ForestButton("Add Learner",Modifier.weight(1f)){go("learners_add")}
    ForestButton("Remove Learner",Modifier.weight(1f)){go("learners_remove")}
   }
   if(pages>1)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
    TextButton(onClick={page=(page-1).coerceAtLeast(0)},enabled=page>0){Text("‹ Previous",color=Gold)}
    Text("${page+1} / $pages",color=Cream,modifier=Modifier.padding(top=12.dp))
    TextButton(onClick={page=(page+1).coerceAtMost(pages-1)},enabled=page<pages-1){Text("Next ›",color=Gold)}
   }
  }
  Box(Modifier.align(Alignment.TopEnd).padding(12.dp)){
   TextButton(onClick={menu=true},modifier=Modifier.background(Forest,RoundedCornerShape(12.dp)).border(1.dp,Gold,RoundedCornerShape(12.dp)).semantics{contentDescription="Home menu"}){Text("☰",color=Gold,fontSize=25.sp)}
   DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
    listOf("Settings" to "settings","Edit Learners" to "learners","Help" to "help").forEach{(title,route)->DropdownMenuItem(text={Text(title)},onClick={menu=false;go(route)})}
   }
  }
 }
}

@Composable fun LearnerActivities(name:String,go:(String)->Unit,home:()->Unit){
 val art=androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.activity_tiles)
 Scene {
  Row(Modifier.align(Alignment.TopStart).padding(10.dp).background(Forest.copy(alpha=.92f),RoundedCornerShape(12.dp)).border(1.dp,Gold,RoundedCornerShape(12.dp)),verticalAlignment=Alignment.CenterVertically){
   TextButton(onClick=home){Text("‹ Learners",color=Gold)}
   Text(name,color=Cream,fontSize=18.sp,maxLines=1,modifier=Modifier.padding(end=16.dp))
  }
  val tileWidth=minOf(maxWidth*.94f,maxHeight*.88f)
  Column(Modifier.align(Alignment.TopCenter).padding(top=maxHeight*.43f).width(tileWidth)){
   val w=art.width/2;val h=art.height/2
   listOf(listOf("Practice Words" to "quiz","Heart Words" to "hearts"),listOf("Add or Edit Words" to "edit","Progress" to "progress")).forEachIndexed{row,items->
    Row(Modifier.fillMaxWidth()){
     items.forEachIndexed{col,(label,route)->ArtControl(art,col*w,row*h,(col+1)*w,(row+1)*h,label,Modifier.weight(1f).aspectRatio(w.toFloat()/h)){go(route)}}
    }
   }
  }
 }
}
