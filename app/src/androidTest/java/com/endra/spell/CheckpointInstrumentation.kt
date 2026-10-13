package com.endra.spell

import android.app.Instrumentation
import android.os.Bundle
import android.media.MediaPlayer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Device-only acceptance probe; never packaged in the installable application. */
class CheckpointInstrumentation: Instrumentation() {
 private var seed=false
 private var mode=""
 override fun onCreate(arguments: Bundle?) { super.onCreate(arguments);mode=arguments?.getString("mode").orEmpty();seed=arguments?.getString("mode")=="seed";start() }
 override fun onStart() {
  val result=Bundle()
  if(mode=="ui"||mode=="ui-import"){try{result.putString("stream",CheckpointUi.run(this,mode=="ui-import")+"\n");finish(-1,result)}catch(t:Throwable){result.putString("stream",t.stackTraceToString());finish(0,result)};return}
  if(mode=="crop-ui"){try{result.putString("stream",CropUi.run(this)+"\n");finish(-1,result)}catch(t:Throwable){result.putString("stream",t.stackTraceToString());finish(0,result)};return}
  if(mode=="b"){try{result.putString("stream",CheckpointBDevice.run(this)+"\n");finish(-1,result)}catch(t:Throwable){result.putString("stream",t.stackTraceToString());finish(0,result)};return}
  if(seed) {
   try {
    val values=org.json.JSONObject(context.assets.open("legacy-fixture.json").bufferedReader().use{it.readText()})
    val prefs=targetContext.getSharedPreferences("endraspell",0)
    val editor=prefs.edit().clear()
    values.keys().forEach{editor.putString(it,values.getString(it))}
    check(editor.commit())
    val directory=File(targetContext.filesDir,"word_recordings").apply{mkdirs()}
    context.assets.open("sample.m4a").use{input->File(directory,"fb46c79b7eb7af175f493755b491afa9b25574a9230b8822c629f224a2df4e58.m4a").outputStream().use{input.copyTo(it)}}
    result.putString("stream","PASS: native legacy fixture saved\n");finish(-1,result)
   } catch(t:Throwable) {result.putString("stream",t.stackTraceToString());finish(0,result)}
   return
  }
  try {
   val store=LearningStore(targetContext)
   val before=store.learners()
   check(before.any{it.id=="upgrade-a" && it.name=="Upgrade Student"})
   check(before.any{it.id=="upgrade-b" && it.archived})
   check(store.words("upgrade-a",false)==listOf("apple","banana"))
   check(store.words("upgrade-a",true)==listOf("said","the"))
   check(store.events("upgrade-a").length()==2)
   check(store.history("upgrade-a").length()==1)
   val audio=WordAudio(targetContext)
   check(audio.has("said")) { "Existing custom recording missing" }
   var playing=false;var failed=false
   runOnMainSync {
    audio.play("said",false){failed=true}
    val field=WordAudio::class.java.getDeclaredField("player");field.isAccessible=true
    playing=(field.get(audio) as? MediaPlayer)?.isPlaying==true
   }
   check(playing && !failed) { "Migrated recording did not play" }
   runOnMainSync { audio.stop() }
   val missing=CountDownLatch(1)
   runOnMainSync { audio.play("no recording exists",false){missing.countDown()} }
   check(missing.await(5,TimeUnit.SECONDS)) { "Missing recording did not fall back" }
   val bad=File(targetContext.filesDir,"word_recordings/${FoundationStore.recordingKey("corrupt test")}.m4a")
   bad.writeText("not audio")
   val corrupt=CountDownLatch(1)
   runOnMainSync { audio.play("corrupt test",false){corrupt.countDown()} }
   check(corrupt.await(5,TimeUnit.SECONDS)) { "Corrupt recording did not fall back" }
   runOnMainSync { audio.close() };bad.delete()
   // Rollover is intentionally a backend-only Checkpoint A operation.
   val events=store.events("upgrade-a").toString()
   store.startNewWeek("upgrade-a")
   check(store.words("upgrade-a",false).isEmpty())
   check(store.words("upgrade-a",true)==listOf("said","the"))
   check(store.events("upgrade-a").toString()==events)
   check(WordAudio(targetContext).has("said"))
   val reopened=LearningStore(targetContext)
   check(reopened.words("upgrade-a",false).isEmpty())
   check(reopened.history("upgrade-a").length()==2)
   result.putString("stream","PASS: on-device migrated learners, archived learner, weekly/Heart lists, attempts, history, recording playback, missing/corrupt audio fallback, rollover, recording retention, restart persistence.\n")
   finish(-1,result)
  } catch(t:Throwable) { result.putString("stream",t.stackTraceToString());finish(0,result) }
 }
}
