package com.endra.spell

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable fun RecordWordsPage(words:List<String>,audio:WordAudio,store:LearningStore,home:()->Unit){
 val context=LocalContext.current
 val lifecycle=LocalLifecycleOwner.current
 var index by rememberSaveable{mutableIntStateOf(0)}
 var recording by remember{mutableStateOf(false)}
 var message by remember{mutableStateOf("")}
 var revision by remember{mutableIntStateOf(0)}
 val word=words.getOrNull(index)
 fun start(){if(word!=null)try{audio.start(word);recording=true;message="Recording… Tap Stop & Save when finished."}catch(e:Exception){message="Could not start the microphone. Check microphone permission."}}
 fun finish(){try{audio.finish();message="Recording saved.";revision++}catch(e:Exception){message="Recording was too short or could not be saved. Please try again."};recording=false}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)start()else message="Allow microphone access to record. Automatic voice is still available."}
 DisposableEffect(lifecycle){
  val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){audio.close();recording=false}}
  lifecycle.lifecycle.addObserver(observer)
  onDispose{lifecycle.lifecycle.removeObserver(observer);audio.close()}
 }
 LaunchedEffect(recording){if(recording){delay(30000);finish()}}
 Column(Modifier.fillMaxWidth()){
  PageHeader("Record Words",home)
  VoiceChoice(store,{audio.stop()})
  Text("Record just the word. Your recordings can be used for every learner. Choose My recordings below to use them.",color=Color(0xFFFFEAC0))
  Spacer(Modifier.height(20.dp))
  if(word==null)Text("Save some weekly or heart words first.",color=Color(0xFFFFEAC0))else{
   Text("Word ${index+1} of ${words.size}",color=Color(0xFFFFEAC0))
   Text(word,color=Color(0xFFE8C66A),fontSize=32.sp,modifier=Modifier.padding(vertical=16.dp))
   val exists=remember(word,revision){audio.has(word)}
   Text(if(exists)"Recording available" else "Uses automatic voice until recorded",color=Color(0xFFFFEAC0))
   ForestButton(if(recording)"Stop & Save" else if(exists)"Re-record" else "Record",Modifier.fillMaxWidth()){
    if(recording)finish()else if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)start()else permission.launch(Manifest.permission.RECORD_AUDIO)
   }
   if(!recording){
    if(exists)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     ForestButton("Preview",Modifier.weight(1f)){audio.play(word){message="Could not play recording. Try recording again."}}
     ForestButton("Remove recording",Modifier.weight(1f)){audio.delete(word);revision++;message="Recording removed. Automatic voice will be used."}
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     ForestButton("‹ Previous",Modifier.weight(1f)){audio.stop();index=(index-1).coerceAtLeast(0);message=""}
     ForestButton("Next ›",Modifier.weight(1f)){audio.stop();index=(index+1).coerceAtMost(words.lastIndex);message=""}
    }
   }
   Text(message,color=Color(0xFFFFEAC0),modifier=Modifier.padding(top=12.dp))
  }
 }
}
