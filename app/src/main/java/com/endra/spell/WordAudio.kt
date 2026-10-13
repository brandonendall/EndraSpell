package com.endra.spell

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Shared word recordings use normalized word hashes, independent of learner names. */
class WordAudio(context:Context){
 private val directory=File(context.filesDir,"word_recordings").apply{mkdirs()}
 private var player:MediaPlayer?=null
 private var recorder:MediaRecorder?=null
 private var pending:File?=null
 private var destination:File?=null
 private fun file(word:String):File {
  val hash=MessageDigest.getInstance("SHA-256").digest(word.trim().lowercase(Locale.ROOT).toByteArray()).joinToString(""){"%02x".format(it)}
  return File(directory,"$hash.m4a")
 }
 fun has(word:String)=file(word).exists()
 fun stop(){player?.let{runCatching{it.stop()};it.release()};player=null}
 fun play(word:String,slow:Boolean=false,fallback:()->Unit={}){
  stop();val source=file(word);if(!source.exists()){fallback();return}
  val next=MediaPlayer();player=next
  try{
   next.setDataSource(source.absolutePath)
   next.setOnErrorListener{_,_,_->stop();fallback();true}
   next.setOnCompletionListener{stop()}
   next.prepare();if(slow)next.playbackParams=next.playbackParams.setSpeed(.75f);next.start()
  }catch(e:Exception){stop();fallback()}
 }
 @Suppress("DEPRECATION")
 fun start(word:String){
  stop();cancel();val temp=File.createTempFile("recording-",".m4a",directory)
  pending=temp;destination=file(word)
  val next=MediaRecorder();recorder=next
  try{next.setAudioSource(MediaRecorder.AudioSource.MIC);next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);next.setOutputFile(temp.absolutePath);next.prepare();next.start()}catch(e:Exception){cancel();throw e}
 }
 fun finish(){
  val active=recorder ?: return
  try{active.stop();active.release();recorder=null
   check(pending!!.renameTo(destination!!)){"Could not save recording"};pending=null;destination=null
  }catch(e:Exception){cancel();throw e}
 }
 fun cancel(){recorder?.let{runCatching{it.stop()};it.release()};recorder=null;pending?.delete();pending=null;destination=null}
 fun delete(word:String){stop();file(word).delete()}
 fun close(){stop();cancel()}
}
