package com.endra.spell

import android.content.Context
import java.io.File

/** Compatibility facade: approved screens keep their original interface and visuals. */
class LearningStore(context: Context) {
 private val prefs = context.getSharedPreferences("endraspell", Context.MODE_PRIVATE)
 private val foundation = FoundationStore(AtomicSnapshot(File(context.filesDir,"foundation-v1.json")), { prefs.all })
 data class Learner(val id:String,val name:String,val archived:Boolean=false)
 fun learners() = foundation.learners()
 fun learnerNames() = learners().associate { it.id to it.name }
 fun addLearner(name:String) { foundation.addLearner(name) }
 fun renameLearner(id:String,name:String) { foundation.rename(id,name) }
 fun archiveLearner(id:String,archive:Boolean) { foundation.archive(id,archive) }
 fun text(key:String,default:String="") = prefs.getString(key,default) ?: default
 fun put(key:String,value:String) { check(prefs.edit().putString(key,value).commit()) { "Could not save setting" } }
 fun flag(key:String,default:Boolean=false) = prefs.getBoolean(key,default)
 fun putFlag(key:String,value:Boolean) { check(prefs.edit().putBoolean(key,value).commit()) { "Could not save setting" } }
 fun speed() = prefs.getFloat("speed",0.72f)
 fun setSpeed(value:Float) { check(prefs.edit().putFloat("speed",value).commit()) { "Could not save setting" } }
 fun words(child:String,heart:Boolean) = if(child.isEmpty()) emptyList() else foundation.words(child,heart)
 fun parse(raw:String) = FoundationStore.parse(raw)
 fun save(child:String,heart:Boolean,raw:String) { foundation.save(child,heart,raw) }
 fun history(child:String) = foundation.history(child)
 fun events(child:String) = foundation.events(child)
 fun record(child:String,heart:Boolean,word:String,answer:String?=null) { foundation.record(child,heart,word,answer) }
 fun startNewWeek(child:String) { foundation.startNewWeek(child) }
 fun setExtraPractice(child:String,word:String,enabled:Boolean) { foundation.extraPractice(child,word,enabled) }

 internal fun currentWeek(child:String)=foundation.currentWeek(child)
 internal fun importReviewed(child:String,week:String,rows:List<ReviewWord>)=foundation.importReviewed(child,week,rows)
 internal fun practiceWords(child:String)=foundation.practiceWords(child)
 internal fun saveBreakdown(child:String,word:String,value:String,approved:Boolean){foundation.breakdown(child,word,value,approved)}
}
