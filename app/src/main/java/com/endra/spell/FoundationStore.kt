package com.endra.spell

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/** One validated, versioned snapshot. Legacy preferences and audio files are never deleted. */
internal interface SnapshotIO {
 fun read(): String?
 fun backup(): String?
 fun write(value: String)
}

internal class AtomicSnapshot(private val file: File): SnapshotIO {
 private val previous = File(file.path + ".last-good")
 override fun read() = if(file.exists()) file.readText() else null
 override fun backup() = if(previous.exists()) previous.readText() else null
 private fun replace(target: File, value: String) {
  target.parentFile!!.mkdirs()
  val temp = File.createTempFile("foundation-", ".tmp", target.parentFile)
  try {
   FileOutputStream(temp).use { it.write(value.toByteArray(Charsets.UTF_8)); it.fd.sync() }
   Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
  } finally { temp.delete() }
 }
 override fun write(value: String) {
  // Keep a separately validated recovery copy before replacing the current snapshot.
  val old = read()
  if(old != null && runCatching { FoundationStore.validate(JSONObject(old)) }.isSuccess) replace(previous, old)
  else if(!previous.exists()) replace(previous, value)
  replace(file, value)
 }
}

internal class FoundationStore(
 private val io: SnapshotIO,
 private val legacy: () -> Map<String, *>,
 private val today: () -> LocalDate = { LocalDate.now() }
) {
 companion object {
  private val lock = Any()
  const val VERSION = 1
  fun objects(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it) }
  fun strings(array: JSONArray) = (0 until array.length()).map { array.getString(it) }
  fun normalized(word: String) = word.trim().lowercase(Locale.ROOT)
  fun recordingKey(word: String) = MessageDigest.getInstance("SHA-256").digest(normalized(word).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
  fun parse(raw: String) = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { normalized(it) }
  private fun id() = UUID.randomUUID().toString()
  fun validate(root: JSONObject) {
   require(root.getInt("schemaVersion") == VERSION) { "Unsupported data version" }
   val profiles = objects(root.getJSONArray("learners"))
   require(profiles.map { it.getString("id") }.distinct().size == profiles.size)
   profiles.forEach { p ->
    require(p.getString("id").isNotBlank() && p.getString("name").isNotBlank())
    p.getBoolean("archived")
    val words = objects(p.getJSONArray("words"))
    val ids = words.map { it.getString("id") }.toSet()
    require(ids.size == words.size)
    require(words.map { it.getString("language") to it.getString("normalized") }.distinct().size == words.size)
    words.forEach { w ->
     require(w.getString("text").isNotBlank() && w.getString("language").isNotBlank())
     require(w.getString("normalized") == normalized(w.getString("text")))
     require(w.getString("recordingKey") == recordingKey(w.getString("text")))
     w.getBoolean("extraPractice")
    }
    val lists = objects(p.getJSONArray("lists"))
    val listIds = lists.map { it.getString("id") }.toSet()
    require(listIds.size == lists.size)
    require(lists.count { !it.getBoolean("archived") && it.getString("kind") == "spelling" } == 1)
    require(lists.count { !it.getBoolean("archived") && it.getString("kind") == "heart" } == 1)
    lists.forEach { l ->
     require(l.getString("kind") in listOf("heart", "spelling"))
     val members = strings(l.getJSONArray("wordIds"))
     require(members.distinct().size == members.size && ids.containsAll(members))
     require(l.getString("language").isNotBlank())
    }
    val events = objects(p.getJSONArray("attempts"))
    require(events.map { it.getString("id") }.distinct().size == events.size)
    events.forEach { e ->
     require(e.getString("wordId") in ids)
     require(e.isNull("listId") || e.getString("listId") in listIds)
     LocalDate.parse(e.getString("date"))
     e.getBoolean("correct"); e.getBoolean("heart"); e.getString("word")
     require(e.has("answer"))
    }
   }
  }
 }
 private fun load(): JSONObject {
  val raw = io.read()
  if(raw != null) {
   // Never silently downgrade a snapshot from a newer application.
   val version = runCatching { JSONObject(raw).getInt("schemaVersion") }.getOrNull()
   require(version == null || version <= VERSION) { "This data requires a newer EndraSpell version" }
   runCatching { JSONObject(raw).also { validate(it) } }.getOrNull()?.let { return it }
   val restored = io.backup()?.let { runCatching { JSONObject(it).also { r -> validate(r) } }.getOrNull() }
    ?: error("Saved data needs recovery; original data has been retained")
   io.write(restored.toString())
   return restored
  }
  // A surviving backup is authoritative if the main file was lost.
  io.backup()?.let { backup ->
   val restored = JSONObject(backup).also { validate(it) }; io.write(restored.toString()); return restored
  }
  val migrated = migrate(legacy())
  validate(migrated)
  io.write(migrated.toString())
  return migrated
 }
 private fun <T> read(block: (JSONObject) -> T): T = synchronized(lock) { block(load()) }
 private fun <T> change(block: (JSONObject) -> T): T = synchronized(lock) {
  val next = load() // Each transaction reads committed state; failed writes cannot leak mutable state.
  val result = block(next)
  validate(next)
  io.write(next.toString())
  result
 }
 fun snapshot(): JSONObject = read { JSONObject(it.toString()) }
 private fun profile(root: JSONObject, learner: String) = objects(root.getJSONArray("learners")).firstOrNull { it.getString("id") == learner }
  ?: error("Unknown learner")
 private fun list(p: JSONObject, heart: Boolean) = objects(p.getJSONArray("lists")).single { !it.getBoolean("archived") && it.getString("kind") == if(heart) "heart" else "spelling" }
 private fun word(p: JSONObject, text: String, language: String = "en"): JSONObject {
  val words = p.getJSONArray("words")
  return objects(words).firstOrNull { it.getString("normalized") == normalized(text) && it.getString("language") == language }
   ?: JSONObject().put("id", id()).put("text", text.trim()).put("normalized", normalized(text)).put("language", language)
    .put("englishMeaning", JSONObject.NULL).put("recordingKey", recordingKey(text)).put("recordingScope", "legacy-shared-word")
    .put("extraPractice", false).also { words.put(it) }
 }
 private fun newList(p: JSONObject, heart: Boolean, texts: List<String>, archived: Boolean = false, date: String = today().toString()): JSONObject {
  val result = JSONObject().put("id", id()).put("kind", if(heart) "heart" else "spelling").put("language", "en")
   .put("createdDate", date).put("archived", archived).put("wordIds", JSONArray(texts.map { word(p,it).getString("id") }))
  p.getJSONArray("lists").put(result)
  return result
 }
 private fun newProfile(learnerId: String, name: String, archived: Boolean) = JSONObject().put("id", learnerId).put("name", name).put("archived", archived)
  .put("words", JSONArray()).put("lists", JSONArray()).put("attempts", JSONArray())
 private fun migrate(old: Map<String, *>): JSONObject {
  val root = JSONObject().put("schemaVersion", VERSION).put("migrationDate", today().toString()).put("learners", JSONArray())
  // Preserve the exact legacy source, including unknown keys, separately from canonical relationships.
  root.put("legacySnapshot", JSONObject(old))
  val profiles = if(old.containsKey("profiles")) objects(JSONArray(old["profiles"] as String)) else {
   val ids = linkedSetOf<String>()
   old.keys.forEach { key ->
    if(key.startsWith("learner_name_")) ids.add(key.removePrefix("learner_name_"))
    listOf("_weekly", "_heart", "_history", "_events").firstOrNull { key.endsWith(it) }?.let { ids.add(key.removeSuffix(it)) }
   }
   (old["learner"] as? String)?.takeIf { it.isNotBlank() }?.let { ids.add(it) }
   // Only recognize historical built-in IDs when there is evidence of that old installation.
   if(ids.any { it in listOf("James", "Easton", "Aiden") }) ids.addAll(listOf("James", "Easton", "Aiden"))
   if(ids.isEmpty()) listOf(JSONObject().put("id", id()).put("name", "Learner 1"))
   else ids.map { JSONObject().put("id", it).put("name", old["learner_name_$it"] as? String ?: it) }
  }
  profiles.forEach { source ->
   val learnerId = source.getString("id")
   val p = newProfile(learnerId, source.getString("name"), source.optBoolean("archived"))
   root.getJSONArray("learners").put(p)
   val weeklyDefault = "because\nbefore\nagain\nfamily\nschool\nimportant\nalways"
   val heartDefault = "the\nsaid\nwas\nhave\nwhat"
   objects(JSONArray(old["${learnerId}_history"] as? String ?: "[]")).forEach { h ->
    newList(p,false,parse(h.getString("words")),true,h.getString("date")).put("legacyHistory",JSONObject(h.toString()))
   }
   newList(p,false,parse(old["${learnerId}_weekly"] as? String ?: weeklyDefault))
   newList(p,true,parse(old["${learnerId}_heart"] as? String ?: heartDefault))
   objects(JSONArray(old["${learnerId}_events"] as? String ?: "[]")).forEach { e ->
    val attempt = JSONObject(e.toString()).put("id", id()).put("wordId",word(p,e.getString("word")).getString("id"))
     .put("listId",JSONObject.NULL).put("listLink", "unknown-legacy").put("language", "en")
    p.getJSONArray("attempts").put(attempt)
   }
  }
  return root
 }
 fun learners() = read { objects(it.getJSONArray("learners")).map { p -> LearningStore.Learner(p.getString("id"),p.getString("name"),p.getBoolean("archived")) } }
 fun addLearner(name: String) = change { root ->
  require(name.trim().isNotEmpty())
  require(objects(root.getJSONArray("learners")).none { it.getString("name").equals(name.trim(),true) })
  val p = newProfile(id(),name.trim(),false);root.getJSONArray("learners").put(p)
  newList(p,false,emptyList());newList(p,true,emptyList());p.getString("id")
 }
 fun rename(learner: String, name: String) = change { root ->
  require(name.trim().isNotEmpty())
  require(objects(root.getJSONArray("learners")).none { it.getString("id") != learner && it.getString("name").equals(name.trim(),true) })
  profile(root,learner).put("name",name.trim());Unit
 }
 fun archive(learner: String, archived: Boolean) = change { profile(it,learner).put("archived",archived);Unit }
 fun words(learner: String, heart: Boolean): List<String> = read { root ->
  val p=profile(root,learner);val byId=objects(p.getJSONArray("words")).associateBy { it.getString("id") }
  strings(list(p,heart).getJSONArray("wordIds")).map { byId.getValue(it).getString("text") }
 }
 fun save(learner: String, heart: Boolean, raw: String) = change { root ->
  val p=profile(root,learner);val current=list(p,heart)
  val members=parse(raw).map { word(p,it,current.getString("language")).getString("id") }
  if(members != strings(current.getJSONArray("wordIds"))) {
   if(!heart && current.getJSONArray("wordIds").length()>0) { current.put("archived",true).put("archivedDate",today().toString());newList(p,false,parse(raw)) }
   else current.put("wordIds",JSONArray(members))
  }
  Unit
 }
 fun startNewWeek(learner: String) = change { root ->
  val p=profile(root,learner);list(p,false).put("archived",true).put("archivedDate",today().toString())
  newList(p,false,emptyList());Unit
 }
 fun history(learner: String) = read { root ->
  val p=profile(root,learner);val byId=objects(p.getJSONArray("words")).associateBy { it.getString("id") }
  JSONArray(objects(p.getJSONArray("lists")).filter { it.getBoolean("archived") && it.getString("kind")=="spelling" }.map { l ->
   if(l.has("legacyHistory")) JSONObject(l.getJSONObject("legacyHistory").toString()) else JSONObject().put("date",l.optString("archivedDate",l.getString("createdDate")))
    .put("words",strings(l.getJSONArray("wordIds")).joinToString("\n") { byId.getValue(it).getString("text") })
  })
 }
 fun events(learner: String) = read { JSONArray(profile(it,learner).getJSONArray("attempts").toString()) }
 fun record(learner: String, heart: Boolean, text: String, answer: String?) = change { root ->
  val p=profile(root,learner);val current=list(p,heart);val w=word(p,text,current.getString("language"))
  p.getJSONArray("attempts").put(JSONObject().put("id",id()).put("date",today().toString()).put("heart",heart).put("word",text)
   .put("wordId",w.getString("id")).put("listId",current.getString("id")).put("language",current.getString("language"))
   .put("answer",answer ?: JSONObject.NULL).put("correct",answer?.trim()?.equals(text,true) ?: false))
  Unit
 }
 data class Progress(val mastered: Boolean, val everMastered: Boolean, val needsReview: Boolean, val extraPractice: Boolean)
 fun progress(learner: String, text: String): Progress = read { root -> progressFor(profile(root,learner),text) }
 private fun progressFor(p:JSONObject,text:String):Progress {
  val w=objects(p.getJSONArray("words")).firstOrNull { it.getString("normalized")==normalized(text) && it.getString("language")=="en" }
   ?: return Progress(false,false,false,false)
  val correctDays=mutableSetOf<String>();val recoveryDays=mutableSetOf<String>()
  var mastered=false;var ever=false;var review=false;var misses=0
  objects(p.getJSONArray("attempts")).filter { it.getString("wordId")==w.getString("id") && !it.isNull("answer") }
   .sortedBy { it.getString("date") }.forEach { e ->
    if(e.getBoolean("correct")) {
     correctDays.add(e.getString("date"))
     if(review) {
      recoveryDays.add(e.getString("date"))
      if(recoveryDays.size>=2) { review=false;misses=0;mastered=ever || correctDays.size>=3 }
     } else if(correctDays.size>=3) mastered=true
     if(mastered) ever=true
    } else {
     misses++
     if(ever || misses>=2) { review=true;mastered=false;recoveryDays.clear() }
    }
   }
  return Progress(mastered,ever,review,w.getBoolean("extraPractice"))
 }
 fun extraPractice(learner: String, text: String, enabled: Boolean) = change { word(profile(it,learner),text).put("extraPractice",enabled);Unit }

 // Additive Checkpoint B fields; schema, IDs and Checkpoint A migration unchanged.
 fun currentWeek(learner:String)=read{list(profile(it,learner),false).getString("id")}
 fun importReviewed(learner:String,expectedWeek:String,rows:List<ReviewWord>):Int=change{root->
  require(rows.isNotEmpty()&&rows.size<=300){"Review between 1 and 300 words."}
  require(rows.all{it.text.trim().isNotEmpty()&&it.text.length<=100&&!it.text.contains('\n')}){"Check blank or oversized words."}
  val p=profile(root,learner);require(!p.getBoolean("archived")){"Learner has been archived."}
  require(list(p,false).getString("id")==expectedWeek){"The spelling week changed. Cancel and review for the current week."}
  var added=0
  rows.distinctBy{it.category to normalized(it.text)}.forEach{row->val current=list(p,row.category==WordCategory.HEART);val id=word(p,row.text,current.getString("language")).getString("id");val members=current.getJSONArray("wordIds");if(id !in strings(members)){members.put(id);added++}}
  added
 }
 fun breakdown(learner:String,text:String,value:String,approved:Boolean)=change{root->require(value.length<=160);word(profile(root,learner),text).put("breakdown",value.trim()).put("breakdownApproved",approved&&value.isNotBlank());Unit}
 fun practiceWords(learner:String):List<PracticeWord> = read{root->
  val p=profile(root,learner);val events=objects(p.getJSONArray("attempts"));val hearts=strings(list(p,true).getJSONArray("wordIds")).toSet()
  objects(p.getJSONArray("words")).map{w->val attempts=events.filter{it.getString("wordId")==w.getString("id")};val answers=attempts.filter{!it.isNull("answer")};val recent=answers.lastOrNull()?.let{!it.getBoolean("correct")&&runCatching{LocalDate.parse(it.getString("date"))>=today().minusDays(7)}.getOrDefault(false)}?:false;val progress=progressFor(p,w.getString("text"))
   PracticeWord(w.getString("text"),w.getString("id") in hearts,progress.needsReview,progress.extraPractice,recent,answers.count{it.getBoolean("correct")},attempts.count{it.isNull("answer")},if(w.optBoolean("breakdownApproved"))w.optString("breakdown").takeIf{it.isNotBlank()}else null)
  }
 }
}
