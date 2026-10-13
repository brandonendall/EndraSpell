package com.endra.spell

import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

internal val Ink=Color(0xFF032416)
internal val Moss=Color(0xFF06351F)
internal val Gilt=Color(0xFFE8C66A)
internal val Ivory=Color(0xFFFFEAC0)
internal val Vellum=Color(0xFFF4DEA1)

private val LocalLearnerNames=compositionLocalOf<Map<String,String>> { emptyMap() }
@Composable internal fun learnerLabel(id:String)=LocalLearnerNames.current[id] ?: id

@Composable fun App22() {
 val context=LocalContext.current
 val store=remember{LearningStore(context)}
 worksheetSession() // Clear abandoned captures after process death.
 var screen by rememberSaveable{mutableStateOf("home")}
 var child by rememberSaveable{mutableStateOf(store.text("learner",store.learners().firstOrNull{!it.archived}?.id ?: ""))}
 var revision by remember{mutableIntStateOf(0)}
 var speech by remember{mutableStateOf<TextToSpeech?>(null)}
 var speechReady by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("")}
 var refresh by remember{mutableIntStateOf(0)}
 val recordings=remember{WordAudio(context)}
 val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
 DisposableEffect(owner){
  val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP){speech?.stop();recordings.close()}}
  owner.lifecycle.addObserver(observer)
  onDispose{owner.lifecycle.removeObserver(observer)}
 }
 DisposableEffect(Unit){onDispose{recordings.close()}}
 fun stopAudio(){speech?.stop();recordings.stop()}
 fun say(word:String,slow:Boolean=false,quiz:Boolean=false){
  stopAudio()
  val fallback={if(speechReady){speech?.setSpeechRate(if(slow)0.45f else store.speed());speech?.speak(if(quiz)"Spell $word" else word,TextToSpeech.QUEUE_FLUSH,null,"word");Unit}else{status="Automatic voice is unavailable. Check Android text-to-speech settings.";Unit}}
  if(store.flag("recordedVoice"))recordings.play(word,slow,fallback) else fallback()
 }
 DisposableEffect(Unit){
  lateinit var engine:TextToSpeech
  engine=TextToSpeech(context){result->
   if(result==TextToSpeech.SUCCESS){
    engine.language=Locale.US
    engine.setSpeechRate(store.speed())
    engine.voices?.firstOrNull{it.name==store.text("voice")}?.let{engine.voice=it}
    speechReady=true
   }else status="Speech is unavailable. Install an English text-to-speech voice in Android settings."
  }
  speech=engine
  onDispose{engine.stop();engine.shutdown()}
 }
 val activity=context as? Activity
 val keepAwake=store.flag("awake",true)
 DisposableEffect(keepAwake,refresh){
  if(keepAwake)activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  onDispose{activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
 }
 fun go(route:String){stopAudio();status="";screen=route}
 val words=remember(child,revision){store.words(child,false)}
 val hearts=remember(child,revision){store.words(child,true)}
 val home={go(when(screen){
  "test"->"quiz"
  "heart_test"->"hearts"
  "recordings"->"edit"
  "quiz","hearts","edit","words","progress"->"dashboard"
  else->"home"
 })}
 BackHandler(screen!="home"){home()}
 CompositionLocalProvider(LocalLearnerNames provides remember(refresh){store.learnerNames()}) {
 if(screen=="home") LearnerHome(remember(refresh){store.learners().filter{!it.archived}},{child=it;store.put("learner",it);go("dashboard")},::go)
 else if(screen=="dashboard") LearnerActivities(learnerLabel(child),::go,home)
 else {
  ForestPage(screen in listOf("quiz","hearts","test","heart_test")) {
   when(screen){
    "learners","learners_add","learners_remove"->ManageLearners(store,{refresh++},home,startAdding=screen=="learners_add")
    "recordings"->RecordWordsPage((words+hearts).distinct(),recordings,store,home)
    "quiz","hearts"->PracticePage(screen=="hearts",if(screen=="hearts")hearts else words,store,child,
      {word,slow->say(word,slow)},
      {stopAudio()}, {word->store.record(child,screen=="hearts",word);revision++},
      {go(if(screen=="hearts")"heart_test" else "test")},home)
    "test","heart_test"->SpellingPage(if(screen=="heart_test")hearts else words,store,
      {word->say(word,quiz=true)},
      {word,answer->store.record(child,screen=="heart_test",word,answer);revision++;if(store.flag("sound",true)){val tone=ToneGenerator(AudioManager.STREAM_MUSIC,35);tone.startTone(if(word.equals(answer.trim(),true))ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK,180);android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({tone.release()},300)}},home)
    "edit","words"->EditPage(child,store,{revision++},{go("recordings")},home)
    "progress"->ProgressPage(child,store,home)
    "settings"->SettingsPage(store,speech,{refresh++},home)
    "help"->HelpPage(home)
    else->ForestMenu(child,words.size,hearts.size,::go,home)
   }
   if(status.isNotEmpty())Text(status,color=Ivory,modifier=Modifier.align(Alignment.BottomCenter).background(Ink).padding(8.dp),fontSize=12.sp)
  }
 }
 }
}

@Composable internal fun ForestPage(practice:Boolean=false,content:@Composable BoxScope.()->Unit){
 Box(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()){
  Image(painterResource(if(practice)R.drawable.practice_glade else R.drawable.inner_forest),null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
  Box(Modifier.fillMaxSize().imePadding().padding(horizontal=20.dp,vertical=12.dp),contentAlignment=Alignment.TopCenter){Box(Modifier.widthIn(max=600.dp).fillMaxHeight().fillMaxWidth(),content=content)}
 }
}
@Composable internal fun PageHeader(title:String,home:()->Unit){
 Row(Modifier.fillMaxWidth().height(58.dp).background(Ink.copy(alpha=.94f),RoundedCornerShape(14.dp)).border(1.dp,Gilt,RoundedCornerShape(14.dp)),verticalAlignment=Alignment.CenterVertically){
  TextButton(onClick=home,contentPadding=PaddingValues(4.dp),modifier=Modifier.width(52.dp)){Text("‹",color=Gilt,fontSize=32.sp)}
  Text(title,color=Ivory,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,fontSize=23.sp,modifier=Modifier.weight(1f),maxLines=1)
  Text("❧",color=Gilt,fontSize=24.sp,modifier=Modifier.padding(end=10.dp))
 }
 Spacer(Modifier.height(14.dp))
}
@Composable internal fun Frame(modifier:Modifier=Modifier,parchment:Boolean=false,content:@Composable ColumnScope.()->Unit){
 val shape=RoundedCornerShape(14.dp)
 Box(modifier.clip(shape).background(if(parchment)Brush.radialGradient(listOf(Color(0xFFFFEFBE),Vellum,Color(0xFFD6AD61))) else Brush.verticalGradient(listOf(Moss.copy(alpha=.97f),Ink.copy(alpha=.97f)))).border(2.dp,Gilt,shape).padding(5.dp).border(1.dp,if(parchment)Color(0xFF9A7130) else Gilt.copy(alpha=.45f),RoundedCornerShape(10.dp))){
  Column(Modifier.fillMaxWidth().padding(12.dp),content=content)
 }
}
@Composable internal fun ForestButton(label:String,modifier:Modifier=Modifier,selected:Boolean=false,onClick:()->Unit){
 OutlinedButton(onClick=onClick,modifier=modifier.heightIn(min=46.dp),shape=RoundedCornerShape(12.dp),border=BorderStroke(1.5.dp,Gilt),colors=ButtonDefaults.outlinedButtonColors(containerColor=if(selected)Color(0xFF137A3D) else Ink.copy(alpha=.97f),contentColor=Ivory),contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp)){
  Text(label,fontFamily=FontFamily.Serif,fontSize=16.sp,textAlign=TextAlign.Center,maxLines=2)
 }
}
@Composable private fun Tabs(heart:Boolean,onChange:(Boolean)->Unit){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  ForestButton("Weekly Words",Modifier.weight(1f),!heart){onChange(false)}
  ForestButton("Heart Words",Modifier.weight(1f),heart){onChange(true)}
 }
 Spacer(Modifier.height(12.dp))
}

@Composable private fun ForestMenu(child:String,weekly:Int,hearts:Int,go:(String)->Unit,home:()->Unit){
 Column(Modifier.fillMaxSize()){
  PageHeader("${learnerLabel(child)}’s Forest",home)
  Text("EndraSpell",color=Gilt,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,fontSize=36.sp,modifier=Modifier.align(Alignment.CenterHorizontally))
  Text("$weekly weekly words  •  $hearts heart words",color=Ivory,modifier=Modifier.align(Alignment.CenterHorizontally).padding(8.dp),fontSize=14.sp)
  Spacer(Modifier.height(12.dp))
  listOf(listOf("▤\nPractice Words" to "quiz","♥\nHeart Words" to "hearts"),listOf("✎\nAdd / Edit Words" to "edit","▥\nProgress" to "progress")).forEach{row->
   Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(10.dp)){row.forEach{(label,route)->ForestButton(label,Modifier.weight(1f).fillMaxHeight()){go(route)}}}
   Spacer(Modifier.height(10.dp))
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   ForestButton("Settings",Modifier.weight(1f)){go("settings")}
   ForestButton("Help",Modifier.weight(1f)){go("help")}
  }
  Spacer(Modifier.weight(.5f))
 }
}

@Composable private fun PracticePage(heart:Boolean,words:List<String>,store:LearningStore,child:String,speak:(String,Boolean)->Unit,stop:()->Unit,record:(String)->Unit,test:()->Unit,home:()->Unit){
 var index by rememberSaveable{mutableIntStateOf(0)}
 var paused by rememberSaveable{mutableStateOf(false)}
 var finished by rememberSaveable{mutableStateOf(false)}
 var order by rememberSaveable{mutableStateOf(words)}
 var mode by rememberSaveable{mutableStateOf("Current List")}
 var hearOnly by rememberSaveable{mutableStateOf(!store.flag("showText",true))}
 var revealed by rememberSaveable(index){mutableStateOf(false)}
 var options by remember{mutableStateOf(false)}
 var support by remember{mutableStateOf(false)}
 val all=store.practiceWords(child)
 val word=order.getOrNull(index)
 val info=all.firstOrNull{it.text==word}
 fun reset(values:List<String>,label:String){stop();order=values;mode=label;index=0;finished=false;revealed=false;options=false}

 fun next(){if(word!=null){store.record(child,if(mode=="Current List")heart else info?.heart?:heart,word);if(index<order.lastIndex)index++ else finished=true}}
 LaunchedEffect(index,paused,finished,order,hearOnly,options,support){
  revealed=false
  if(store.flag("auto")&&!paused&&!finished&&!options&&!support&&word!=null){speak(word,false);delay(6000);next()}
 }
 DisposableEffect(Unit){onDispose{stop()}}
 Column(Modifier.fillMaxSize()){
  PageHeader(if(heart)"Heart Words" else "Practice Words",home)
  VoiceChoice(store,stop)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   ForestButton("Shuffle Words",Modifier.weight(1f)){reset(PracticeEngine.shuffle(order.map{PracticeWord(it,heart)}).map{it.text},mode)}
   ForestButton("Practice Options",Modifier.weight(1f)){stop();options=true}
  }
  Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())){
  Frame(Modifier.fillMaxWidth().height(260.dp),true){
   Text(if(finished)"Practice complete" else if(word==null)"Your list is empty" else "Word ${index+1} of ${order.size}",color=Ink,fontFamily=FontFamily.Serif,fontSize=18.sp,modifier=Modifier.align(Alignment.CenterHorizontally))
   Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text(if(finished)"Well done!" else if(word==null)"Add words to begin" else if(!hearOnly||revealed)word else "Listen carefully",color=Ink,fontFamily=FontFamily.Serif,fontSize=38.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)}
   if(word!=null&&!finished){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
     ForestButton("Replay"){speak(word,false)}
     ForestButton(if(paused)"Play" else "Pause"){paused=!paused;if(paused)stop() else speak(word,false)}
     ForestButton("Slow"){speak(word,true)}
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
     ForestButton("‹ Previous"){stop();if(index>0)index--}
     ForestButton("Next ›"){stop();next()}
    }
   }
  }
  if(word!=null&&!finished&&hearOnly&&!revealed)ForestButton("Reveal",Modifier.fillMaxWidth()){revealed=true}
  if(word!=null&&!finished&&(!hearOnly||revealed)&&info?.breakdown!=null)ForestButton("Word Breakdown",Modifier.fillMaxWidth()){support=true;stop()}
  Spacer(Modifier.height(12.dp))
  if(finished)ForestButton("Practice Again",Modifier.fillMaxWidth()){reset(order,mode)}
  ForestButton("Spelling Quiz",Modifier.fillMaxWidth(),onClick=test)
  }
  }
 }
 if(options)AlertDialog(onDismissRequest={options=false},title={Text("Practice Options")},text={Column(Modifier.verticalScroll(rememberScrollState())){
  Text("Study actions do not count toward Mastery. Current mode: $mode")
  ForestButton("Hear Only",Modifier.fillMaxWidth(),hearOnly){hearOnly=!hearOnly;revealed=false;options=false}
  ForestButton("Current List",Modifier.fillMaxWidth()){reset(words,"Current List")}
  ForestButton("Practice My Hard Words",Modifier.fillMaxWidth()){reset(PracticeEngine.hard(all).map{it.text},"Hard Words")}
  ForestButton("Extra Practice",Modifier.fillMaxWidth()){reset(all.filter{it.extraPractice}.map{it.text},"Extra Practice")}
  ForestButton("Smart Practice",Modifier.fillMaxWidth()){
   val active=(store.words(child,false)+store.words(child,true)).map{FoundationStore.normalized(it)}.toSet()
   reset(PracticeEngine.smart(all.filter{FoundationStore.normalized(it.text) in active||it.needsReview||it.extraPractice}).map{it.text},"Smart Practice")
  }
 }},confirmButton={TextButton(onClick={options=false}){Text("Close")}})
 if(support)AlertDialog(onDismissRequest={support=false},title={Text("Approved Word Breakdown")},text={Text(info?.breakdown.orEmpty())},confirmButton={TextButton(onClick={support=false}){Text("Close")}})
}

@Composable private fun SpellingPage(words:List<String>,store:LearningStore,speak:(String)->Unit,record:(String,String)->Unit,home:()->Unit){
 var index by rememberSaveable{mutableIntStateOf(0)}
 var answer by rememberSaveable{mutableStateOf("")}
 var result by rememberSaveable{mutableStateOf<Boolean?>(null)}
 var score by rememberSaveable{mutableIntStateOf(0)}
 var done by rememberSaveable{mutableStateOf(false)}
 var mistakes by rememberSaveable{mutableStateOf("")}
 var counted by rememberSaveable{mutableStateOf(false)}
 val keyboard=LocalSoftwareKeyboardController.current
 val word=words.getOrNull(index)
 Column(Modifier.fillMaxSize()){
  PageHeader("Spelling Quiz",home)
  VoiceChoice(store)
  Spacer(Modifier.weight(.08f))
  Frame(Modifier.fillMaxWidth().weight(.62f),true){
   if(done||word==null){
    Text(if(word==null)"Add words to begin" else "Quiz Complete!",color=Ink,fontSize=28.sp,fontFamily=FontFamily.Serif,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
    Text("$score / ${words.size}",color=Ink,fontSize=44.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
    Text(if(mistakes.isBlank())"Keep growing your word magic!" else "Review: $mistakes",color=Ink,modifier=Modifier.weight(1f).verticalScroll(rememberScrollState()))
   }else if(result!=null){
    Text(if(result==true)"✓" else "×",color=if(result==true)Color(0xFF087B37) else Color(0xFF992C14),fontSize=70.sp,modifier=Modifier.align(Alignment.CenterHorizontally))
    Text(if(result==true)"Correct!" else "Try Again!",color=Ink,fontFamily=FontFamily.Serif,fontSize=36.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
    Text(if(result==true)word else "You wrote: $answer",color=Ink,fontSize=20.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(12.dp))
    if(result==false)ForestButton("Hear and Try Again",Modifier.fillMaxWidth()){result=null;answer="";speak(word)}
   }else{
    Text("Word ${index+1} of ${words.size}",color=Ink,fontSize=18.sp,modifier=Modifier.align(Alignment.CenterHorizontally))
    Spacer(Modifier.weight(1f))
    ForestButton("Hear the Word",Modifier.fillMaxWidth()){speak(word)}
    OutlinedTextField(answer,{answer=it},singleLine=true,label={Text("Spell the word")},modifier=Modifier.fillMaxWidth().padding(vertical=12.dp),keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,keyboardType=KeyboardType.Text),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Ink,unfocusedTextColor=Ink,focusedLabelColor=Ink,unfocusedLabelColor=Ink,focusedBorderColor=Ink,unfocusedBorderColor=Ink,cursorColor=Ink))
    ForestButton("Check",Modifier.fillMaxWidth()){
     if(answer.isNotBlank()){
      keyboard?.hide();val correct=answer.trim().equals(word,true);result=correct;record(word,answer)
      if(!counted){if(correct)score++ else mistakes+=(if(mistakes.isBlank())"" else ", ")+word;counted=true}
     }
    }
    Spacer(Modifier.weight(1f))
   }
  }
  Spacer(Modifier.height(16.dp))
  if(result!=null&&!done)ForestButton("Next Word",Modifier.fillMaxWidth()){
   if(index==words.lastIndex)done=true else index++
   answer="";result=null;counted=false
  }
  if(done)ForestButton("Retry Quiz",Modifier.fillMaxWidth()){index=0;score=0;done=false;counted=false;result=null;mistakes=""}
  Spacer(Modifier.weight(.30f))
 }
}

@Composable private fun EditPage(child:String,store:LearningStore,saved:()->Unit,recordings:()->Unit,home:()->Unit){
 var heart by rememberSaveable{mutableStateOf(false)}
 var weekly by rememberSaveable{mutableStateOf(store.words(child,false).joinToString("\n"))}
 var hearts by rememberSaveable{mutableStateOf(store.words(child,true).joinToString("\n"))}
 var message by remember{mutableStateOf("")}
 var history by remember{mutableStateOf(false)}
 var importing by rememberSaveable{mutableStateOf(false)}
 var supports by remember{mutableStateOf(false)}
 val worksheet=worksheetSession()
 val keyboard=LocalSoftwareKeyboardController.current
 Column(Modifier.fillMaxSize()){
  PageHeader("Add / Edit Words",home)
  Tabs(heart){heart=it;message=""}
  Frame(Modifier.fillMaxWidth().weight(1f),true){
   Text("${learnerLabel(child)} • One word per line",color=Ink,fontSize=14.sp)
   TextField(if(heart)hearts else weekly,{if(heart)hearts=it else weekly=it;message=""},modifier=Modifier.fillMaxWidth().weight(1f),placeholder={Text("Enter words here")},keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.None,autoCorrectEnabled=false),colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedTextColor=Ink,unfocusedTextColor=Ink,cursorColor=Ink,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
  }
  Spacer(Modifier.height(12.dp))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
   ForestButton("Clear Editor",Modifier.weight(1f)){if(heart)hearts="" else weekly="";message="Tap Save List to apply changes."}
   ForestButton("Save List",Modifier.weight(1f)){store.save(child,heart,if(heart)hearts else weekly);saved();keyboard?.hide();message="List saved."}
  }
  ForestButton("Record Words",Modifier.fillMaxWidth()){store.save(child,heart,if(heart)hearts else weekly);saved();recordings()}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   ForestButton("Scan Word List",Modifier.weight(1f)){
    if(store.parse(weekly)!=store.words(child,false)||store.parse(hearts)!=store.words(child,true))message="Save manual changes before scanning."
    else{worksheet.start(child,store.currentWeek(child));importing=true}
   }
   ForestButton("Word Supports",Modifier.weight(1f)){supports=true}
  }
  if(!heart)ForestButton("Previous Weekly Lists",Modifier.fillMaxWidth()){history=true}
  Text(message,color=Ivory,fontSize=13.sp,modifier=Modifier.height(28.dp))
  Spacer(Modifier.height(28.dp))
 }
 if(importing&&worksheet.learner==child)WorksheetImport(worksheet,store,{weekly=store.words(child,false).joinToString("\n");hearts=store.words(child,true).joinToString("\n");saved();message="Reviewed words saved."},{importing=false})
 if(importing&&worksheet.learner!=child)LaunchedEffect(Unit){importing=false;message="Photo review was closed. No imported words were saved."}
 if(supports)WordSupports(child,store,{supports=false})
 if(history){
  val lists=store.history(child)
  AlertDialog(onDismissRequest={history=false},title={Text("Previous Weekly Lists")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){
   if(lists.length()==0)Text("Your previous list is kept whenever you save a changed weekly list.")
   for(i in lists.length()-1 downTo 0){val item=lists.getJSONObject(i);TextButton(onClick={weekly=item.getString("words");history=false;message="Previous list loaded. Tap Save List to use it."}){Text(item.getString("date")+" • "+item.getString("words").replace("\n",", "))}}
  }},confirmButton={TextButton(onClick={history=false}){Text("Close")}})
 }
}

@Composable private fun ProgressPage(child:String,store:LearningStore,home:()->Unit){
 var heart by rememberSaveable{mutableStateOf(false)}
 val raw=store.events(child)
 val events=(0 until raw.length()).map{raw.getJSONObject(it)}.filter{it.getBoolean("heart")==heart}
 val answers=events.filter{!it.isNull("answer")}
 val right=answers.count{it.getBoolean("correct")}
 val today=LocalDate.now()
 val dates=(6 downTo 0).map{today.minusDays(it.toLong())}
 val counts=dates.map{date->events.count{it.getString("date")==date.toString()}}
 val practiced=events.map{it.getString("word")}.distinct().size
 val activeDays=events.map{it.getString("date")}.toSet()
 var streak=0;var day=if(today.toString() in activeDays)today else today.minusDays(1)
 while(day.toString() in activeDays){streak++;day=day.minusDays(1)}
 Column(Modifier.fillMaxSize()){
  PageHeader("Progress",home);Tabs(heart){heart=it}
  Frame(Modifier.fillMaxWidth().weight(.52f)){
   Text("${learnerLabel(child)} • Words Practiced",color=Ivory,fontFamily=FontFamily.Serif,fontSize=19.sp,modifier=Modifier.align(Alignment.CenterHorizontally))
   Row(Modifier.fillMaxWidth().weight(1f).padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.Bottom){
    counts.forEachIndexed{i,count->Column(Modifier.weight(1f).fillMaxHeight(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom){
     Text("$count",color=Ivory,fontSize=11.sp)
     Box(Modifier.fillMaxWidth().fillMaxHeight((count.toFloat()/counts.maxOrNull()!!.coerceAtLeast(1)*.7f).coerceAtLeast(.02f)).background(Brush.verticalGradient(listOf(Gilt,Color(0xFF137A3D)))).border(1.dp,Gilt))
     Text(dates[i].dayOfWeek.name.take(3),color=Ivory,fontSize=9.sp,modifier=Modifier.padding(top=5.dp))
    }}
   }
  }
  Spacer(Modifier.height(10.dp))
  Row(Modifier.fillMaxWidth().height(100.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
   listOf("$practiced" to "Words",(if(answers.isEmpty())"—" else "${right*100/answers.size}%") to "Accuracy","$streak" to "Day Streak").forEach{(number,label)->Frame(Modifier.weight(1f).fillMaxHeight()){Text(number,color=Gilt,fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.CenterHorizontally));Text(label,color=Ivory,fontSize=12.sp,modifier=Modifier.align(Alignment.CenterHorizontally))}}
  }
  Spacer(Modifier.height(10.dp))
  Frame(Modifier.fillMaxWidth().weight(.30f)){
   Text("Recent attempts",color=Gilt,fontSize=17.sp)
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
    if(answers.isEmpty())Text("Take a spelling quiz to see your answers here.",color=Ivory,fontSize=13.sp)
    answers.takeLast(20).reversed().forEach{Text("${it.getString("word")}  •  ${it.getString("answer")}  ${if(it.getBoolean("correct"))"✓" else "— try again"}",color=Ivory,fontSize=13.sp,modifier=Modifier.padding(vertical=3.dp))}
   }
  }
  Spacer(Modifier.weight(.18f))
 }
}

@Composable private fun SettingsPage(store:LearningStore,speech:TextToSpeech?,changed:()->Unit,home:()->Unit){
 var speed by remember{mutableFloatStateOf(store.speed())}
 var voicesOpen by remember{mutableStateOf(false)}

 var voiceName by remember{mutableStateOf(store.text("voice","Default"))}
 val voices=speech?.voices?.filter{it.locale.language=="en"&&!it.isNetworkConnectionRequired}?.sortedBy{it.name}.orEmpty()
 Column(Modifier.fillMaxWidth().widthIn(max=560.dp)){
  PageHeader("Settings",home)
  Frame(Modifier.fillMaxWidth()){
   Text("Speech Speed",color=Ivory,fontSize=18.sp)
   Slider(speed,{speed=it},valueRange=.4f..1.0f,onValueChangeFinished={store.setSpeed(speed);speech?.setSpeechRate(speed)},colors=SliderDefaults.colors(thumbColor=Gilt,activeTrackColor=Gilt))
   Text("Slow                         Normal",color=Ivory,fontSize=12.sp)
   Spacer(Modifier.height(8.dp))
   ForestButton("Voice: ${if(voiceName=="Default")"Default" else voiceName.take(22)}",Modifier.fillMaxWidth()){voicesOpen=true}
   var recorded by remember{mutableStateOf(store.flag("recordedVoice"))}
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
    ForestButton("Automatic",Modifier.weight(1f),!recorded){recorded=false;store.putFlag("recordedVoice",false)}
    ForestButton("My recordings",Modifier.weight(1f),recorded){recorded=true;store.putFlag("recordedVoice",true)}
   }
   Text("Unrecorded words use the automatic voice.",color=Ivory,fontSize=12.sp)
   listOf(Triple("showText","Show Text with Audio",true),Triple("auto","Auto Play Next Word",false),Triple("sound","Sound Effects",true),Triple("awake","Keep Screen Awake",true)).forEach{(key,label,default)->
    var checked by remember{mutableStateOf(store.flag(key,default))}
    Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically){
     Text(label,color=Ivory,fontSize=15.sp,modifier=Modifier.weight(1f))
     Switch(checked,{checked=it;store.putFlag(key,it);changed()},colors=SwitchDefaults.colors(checkedTrackColor=Color(0xFF158341),checkedThumbColor=Ivory))
    }
   }
  }
  Spacer(Modifier.height(8.dp))
 }
 if(voicesOpen)AlertDialog(onDismissRequest={voicesOpen=false},title={Text("English voices")},text={Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState())){
  if(voices.isEmpty())Text("No offline English voices are installed. Add a voice in Android text-to-speech settings.")
  voices.forEach{voice->TextButton(onClick={speech?.voice=voice;voiceName=voice.name;store.put("voice",voice.name);voicesOpen=false}){Text(voice.name)}}
 }},confirmButton={TextButton(onClick={voicesOpen=false}){Text("Close")}})
}

@Composable private fun HelpPage(home:()->Unit){
 var topic by remember{mutableStateOf<Pair<String,String>?>(null)}
 val topics=listOf(
  "How to Use EndraSpell" to "Choose a learner on the home screen. Manage learners from the home menu → Edit Learners. Tap a learner to open their activities. Each learner has separate word lists and progress. The home menu also opens Settings and Help.",
  "Practice Words" to "Hear reads the current word. Slow reads it more slowly. Previous and Next move through your weekly list. Tap Spelling Quiz to type answers and get feedback.",
  "Heart Words" to "Heart Words has a separate list that stays saved until you edit it. Add new words on new lines to keep earlier heart words available.",
  "Add / Edit Words" to "Enter one word per line, then tap Save List. Weekly and Heart Words save separately for each learner. Previous Weekly Lists lets you load an earlier saved list. Clear Editor changes the text only until you save.",
  "Progress Tracking" to "The chart counts words practiced during the last seven days. Accuracy is based on all submitted quiz attempts, including retries. Words counts distinct words practiced. Recent attempts shows your answers.",
  "Settings" to "Change speech speed, choose an installed offline English voice, hide the displayed word, turn on automatic practice, and control sounds or keeping the screen awake. Settings are saved on this device.",
  "Tips for Success" to "Listen, say the word, then spell it. Practice a few words each day. Use Slow when you need more time. Review missed words and try again."
 )
 Column(Modifier.fillMaxSize()){
  PageHeader("Help",home)
  Frame(Modifier.fillMaxWidth().weight(1f)){
   topics.forEach{item->TextButton(onClick={topic=item},modifier=Modifier.fillMaxWidth().weight(1f),contentPadding=PaddingValues(4.dp)){
    Text("❧  ${item.first}",color=Ivory,fontFamily=FontFamily.Serif,fontSize=16.sp,modifier=Modifier.weight(1f),textAlign=TextAlign.Start);Text("›",color=Gilt,fontSize=23.sp)
   }}
  }
  Spacer(Modifier.height(100.dp))
 }
 topic?.let{item->AlertDialog(onDismissRequest={topic=null},title={Text(item.first)},text={Text(item.second)},confirmButton={TextButton(onClick={topic=null}){Text("Close")}})}
}
