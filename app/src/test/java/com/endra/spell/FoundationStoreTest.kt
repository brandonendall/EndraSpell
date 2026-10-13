package com.endra.spell

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.Locale

class FoundationStoreTest {
 private class MemoryIO: SnapshotIO {
  var current:String?=null;var previous:String?=null;var fail=false
  override fun read()=current
  override fun backup()=previous
  override fun write(value:String) { if(fail) error("disk full");previous=current ?: value;current=value }
 }
 private class Fixture(val old:Map<String,Any> = emptyMap()) {
  val io=MemoryIO();var day=LocalDate.parse("2026-10-05")
  fun open()=FoundationStore(io,{old},{day})
  val store=open()
  val learner get()=store.learners().first().id
  fun nextDay(){day=day.plusDays(1)}
 }
 private fun legacy() = mapOf<String,Any>(
  "profiles" to """[{"id":"stable-a","name":"Existing Student","archived":false},{"id":"stable-b","name":"Archived Student","archived":true}]""",
  "stable-a_weekly" to "apple\nbanana", "stable-a_heart" to "said\nthe",
  "stable-b_weekly" to "isolated", "stable-b_heart" to "was",
  "stable-a_history" to """[{"date":"2026-09-20","words":"before\nagain","custom":"preserved"}]""",
  "stable-a_events" to """[{"date":"2026-09-21","heart":false,"word":"before","answer":"befor","correct":false},{"date":"2026-09-22","heart":true,"word":"said","answer":null,"correct":false}]""",
  "learner" to "stable-a", "recordedVoice" to true, "unknown_future_setting" to "keep")
 @Test fun freshInstallAndSettingsOnlyInstallAreGenericAndPersistent(){
  listOf(emptyMap(),mapOf<String,Any>("speed" to .72f)).forEach { old ->
   val f=Fixture(old);assertEquals(listOf("Learner 1"),f.store.learners().map{it.name})
   assertEquals(f.store.learners(),f.open().learners())
  }
 }
 @Test fun migrationPreservesAllLegacyFieldsAndDoesNotMutateSource(){
  val old=legacy().toMutableMap();val before=old.toMap();val f=Fixture(old)
  assertEquals(listOf("stable-a","stable-b"),f.store.learners().map{it.id})
  assertTrue(f.store.learners()[1].archived)
  assertEquals(listOf("apple","banana"),f.store.words("stable-a",false))
  assertEquals(listOf("said","the"),f.store.words("stable-a",true))
  assertEquals(JSONArray(old["stable-a_history"] as String).toString(),f.store.history("stable-a").toString())
  val events=f.store.events("stable-a");val source=JSONArray(old["stable-a_events"] as String)
  for(i in 0 until source.length()) source.getJSONObject(i).keys().forEach { key -> assertEquals(source.getJSONObject(i).get(key),events.getJSONObject(i).get(key)) }
  assertTrue(events.getJSONObject(0).isNull("listId")) // No invented historical week association.
  assertEquals(before,old);assertEquals("keep",f.store.snapshot().getJSONObject("legacySnapshot").getString("unknown_future_setting"))
 }
 @Test fun migrationIsIdempotentAndRetainsRecordingRelationships(){
  val f=Fixture(legacy());val first=f.store.snapshot().toString()
  assertEquals(first,f.open().snapshot().toString())
  val p=f.store.snapshot().getJSONArray("learners").getJSONObject(0)
  FoundationStore.objects(p.getJSONArray("words")).forEach { w ->
   assertEquals("en",w.getString("language"));assertEquals(FoundationStore.recordingKey(w.getString("text")),w.getString("recordingKey"))
  }
  assertEquals(FoundationStore.recordingKey(" SAID "),FoundationStore.recordingKey("said"))
 }
 @Test fun renameArchiveRestoreKeepIdsListsHistoryAndAudioLinks(){
  val f=Fixture(legacy());val p=f.store.snapshot().getJSONArray("learners").getJSONObject(0)
  f.store.rename("stable-a","Renamed");f.store.archive("stable-a",true);f.store.archive("stable-a",false)
  val after=f.open().snapshot().getJSONArray("learners").getJSONObject(0)
  listOf("id","words","lists","attempts").forEach { assertEquals(p.get(it).toString(),after.get(it).toString()) }
  assertEquals("Renamed",f.store.learners()[0].name)
 }
 @Test fun archiveAllDoesNotReseedAndNewLearnersAreIsolated(){
  val f=Fixture();val a=f.learner;f.store.archive(a,true)
  assertTrue(f.open().learners().all{it.archived})
  val b=f.store.addLearner("Student B");assertNotEquals(a,b)
  f.store.save(b,false,"unique");assertFalse(f.store.words(a,false).contains("unique"))
  f.store.record(b,false,"unique","wrong");assertEquals(0,f.store.events(a).length())
 }
 @Test fun startingWeekArchivesSpellingAndPreservesHeartsProgressAndRecordings(){
  val f=Fixture(legacy());val id=f.learner
  f.store.extraPractice(id,"said",true);f.store.record(id,true,"said","sad");f.store.record(id,true,"said","sad")
  val hearts=f.store.words(id,true);val progress=f.store.progress(id,"said");val events=f.store.events(id).toString()
  val before=f.store.snapshot().getJSONArray("learners").getJSONObject(0)
  val heartId=FoundationStore.objects(before.getJSONArray("lists")).single{it.getString("kind")=="heart"}.getString("id")
  f.store.startNewWeek(id)
  assertTrue(f.store.words(id,false).isEmpty());assertEquals(hearts,f.store.words(id,true))
  assertEquals(progress,f.store.progress(id,"said"));assertEquals(events,f.store.events(id).toString())
  assertEquals(2,f.store.history(id).length())
  val after=f.open().snapshot().getJSONArray("learners").getJSONObject(0)
  assertEquals(heartId,FoundationStore.objects(after.getJSONArray("lists")).single{it.getString("kind")=="heart"}.getString("id"))
  assertEquals(before.getJSONArray("words").toString(),after.getJSONArray("words").toString())
 }
 @Test fun editingHeartListDoesNotArchiveWeeklyAndDuplicateSaveDoesNotDuplicateHistory(){
  val f=Fixture();val id=f.learner;f.store.save(id,false,"one\ntwo")
  val history=f.store.history(id).toString();f.store.save(id,true,"said\nSAID\n the ")
  assertEquals(listOf("said","the"),f.store.words(id,true));assertEquals(history,f.store.history(id).toString())
  f.store.save(id,false,"one\ntwo");assertEquals(history,f.store.history(id).toString())
 }
 @Test fun shuffledCopiesCannotAlterSavedOrderOrHistoricalMembership(){
  val f=Fixture();val id=f.learner;f.store.save(id,false,"one\ntwo\nthree")
  val copy=f.store.words(id,false).toMutableList();copy.reverse();copy.clear()
  assertEquals(listOf("one","two","three"),f.store.words(id,false))
  f.store.startNewWeek(id);f.store.save(id,false,"four")
  assertEquals("one\ntwo\nthree",f.store.history(id).getJSONObject(f.store.history(id).length()-1).getString("words"))
 }
 @Test fun populatingNewEmptyWeekKeepsItsIdentityAndDoesNotArchiveBlankLists(){
  val f=Fixture();val id=f.learner;f.store.startNewWeek(id)
  val p=f.store.snapshot().getJSONArray("learners").getJSONObject(0)
  val weekId=FoundationStore.objects(p.getJSONArray("lists")).single{it.getString("kind")=="spelling" && !it.getBoolean("archived")}.getString("id")
  val history=f.store.history(id).toString()
  f.store.save(id,false,"new word")
  val after=f.store.snapshot().getJSONArray("learners").getJSONObject(0)
  assertEquals(weekId,FoundationStore.objects(after.getJSONArray("lists")).single{it.getString("kind")=="spelling" && !it.getBoolean("archived")}.getString("id"))
  assertEquals(history,f.store.history(id).toString())
 }
 @Test fun threeSeparateDaysRequiredForMasteryAndListeningDoesNotCountAsMiss(){
  val f=Fixture();val id=f.learner
  repeat(5){f.store.record(id,false,"apple","apple")}
  assertFalse(f.store.progress(id,"apple").mastered)
  repeat(5){f.store.record(id,false,"apple",null)}
  assertFalse(f.store.progress(id,"apple").needsReview)
  f.nextDay();f.store.record(id,false,"apple","apple");assertFalse(f.store.progress(id,"apple").mastered)
  f.nextDay();f.store.record(id,false,"apple","apple");assertTrue(f.open().progress(id,"apple").mastered)
 }
 @Test fun normalWordNeedsTwoMissesAndMasteredWordNeedsOne(){
  val f=Fixture();val id=f.learner
  f.store.record(id,false,"apple","aple");assertFalse(f.store.progress(id,"apple").needsReview)
  f.store.record(id,false,"apple","aple");assertTrue(f.store.progress(id,"apple").needsReview)
  repeat(3){f.store.record(id,false,"banana","banana");f.nextDay()}
  assertTrue(f.store.progress(id,"banana").mastered)
  f.store.record(id,false,"banana","banan");val p=f.store.progress(id,"banana")
  assertTrue(p.needsReview);assertTrue(p.everMastered);assertFalse(p.mastered)
 }
 @Test fun recoveryNeedsTwoDaysAndExtraPracticeIsIndependent(){
  val f=Fixture();val id=f.learner
  repeat(3){f.store.record(id,true,"said","said");f.nextDay()}
  f.store.extraPractice(id,"said",true);f.store.record(id,true,"said","sad")
  repeat(5){f.store.record(id,true,"said","said")};assertTrue(f.store.progress(id,"said").needsReview)
  f.nextDay();f.store.record(id,true,"said","said")
  val p=f.store.progress(id,"said");assertFalse(p.needsReview);assertTrue(p.mastered);assertTrue(p.extraPractice)
  f.store.extraPractice(id,"said",false);assertTrue(f.store.progress(id,"said").mastered)
 }
 @Test fun missDuringRecoveryRestartsTwoDayRecovery(){
  val f=Fixture();val id=f.learner;repeat(2){f.store.record(id,false,"apple","bad")}
  f.store.record(id,false,"apple","apple");f.nextDay();f.store.record(id,false,"apple","bad")
  f.store.record(id,false,"apple","apple");assertTrue(f.store.progress(id,"apple").needsReview)
  f.nextDay();f.store.record(id,false,"apple","apple");assertFalse(f.store.progress(id,"apple").needsReview)
 }
 @Test fun wrongLearnerAndDuplicateNamesCannotOverwriteData(){
  val f=Fixture();val before=f.store.snapshot().toString()
  assertThrows(Exception::class.java){f.store.rename("missing","Bad")}
  assertThrows(Exception::class.java){f.store.addLearner(" learner 1 ")}
  assertEquals(before,f.store.snapshot().toString())
 }
 @Test fun failedMigrationRetainsOriginalAndCanRetry(){
  val f=Fixture(legacy());f.io.fail=true
  assertThrows(Exception::class.java){f.store.learners()};assertNull(f.io.current)
  assertEquals("apple\nbanana",f.old["stable-a_weekly"])
  f.io.fail=false;assertEquals(listOf("apple","banana"),f.open().words("stable-a",false))
 }
 @Test fun failedTransactionLeavesLastCommittedDataUsable(){
  val f=Fixture();val id=f.learner;val before=f.store.snapshot().toString();f.io.fail=true
  assertThrows(Exception::class.java){f.store.save(id,false,"should not persist")}
  f.io.fail=false;assertEquals(before,f.open().snapshot().toString())
 }
 @Test fun corruptLegacyFailsClosedRatherThanReseeding(){
  val f=Fixture(mapOf("profiles" to "broken"));assertThrows(Exception::class.java){f.store.learners()};assertNull(f.io.current)
 }
 @Test fun corruptedSnapshotRecoversLastGoodButUnknownSchemaNeverDowngrades(){
  val f=Fixture();val id=f.learner;f.store.rename(id,"New Name")
  val backup=f.io.previous;f.io.current="corrupted"
  assertEquals(backup,f.open().snapshot().toString())
  f.io.current=JSONObject(f.io.current!!).put("schemaVersion",99).toString()
  assertThrows(Exception::class.java){f.open().learners()}
  assertEquals(99,JSONObject(f.io.current!!).getInt("schemaVersion"))
 }
 @Test fun corruptMainAndBackupNeverResetToDefaults(){
  val f=Fixture();f.store.learners();f.io.current="bad";f.io.previous="bad"
  assertThrows(Exception::class.java){f.open().learners()}
  assertEquals("bad",f.io.current)
 }
 @Test fun atomicFileSurvivesRestartAndInterruptedTempWrite(){
  val dir=Files.createTempDirectory("endraspell-test").toFile()
  try {
   val file=File(dir,"foundation.json");val a=FoundationStore(AtomicSnapshot(file),{legacy()})
   a.rename("stable-a","Saved");File(dir,"foundation-interrupted.tmp").writeText("half written")
   val b=FoundationStore(AtomicSnapshot(file),{emptyMap<String,Any>()})
   assertEquals("Saved",b.learners()[0].name)
   file.writeText("broken");assertEquals("Existing Student",b.learners()[0].name)
  } finally { dir.deleteRecursively() }
 }
 @Test fun independentStoreInstancesDoNotLoseUpdates(){
  val f=Fixture();val a=f.store;val b=f.open();val id=f.learner
  a.record(id,false,"apple","apple");b.record(id,false,"banana","banana")
  assertEquals(2,a.events(id).length())
 }
 @Test fun localeIndependentWordKeysAndRetainedIds(){
  val previous=Locale.getDefault()
  try { Locale.setDefault(Locale.forLanguageTag("tr-TR"));assertEquals("important",FoundationStore.normalized("IMPORTANT")) }
  finally { Locale.setDefault(previous) }
  val f=Fixture(mapOf("learner" to "James","learner_name_James" to "Saved Name","James_weekly" to "kept"))
  assertEquals("Saved Name",f.store.learners().first().name);assertEquals("James",f.learner)
 }
}
