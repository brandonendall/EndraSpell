package com.endra.spell

import android.content.Context
import android.content.SharedPreferences
import java.nio.file.Files
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

class LearningStoreTest {
 private val directories=java.util.IdentityHashMap<MutableMap<String,Any>,File>()
 @org.junit.After fun cleanup(){directories.values.forEach{it.deleteRecursively()}}
 private fun store(values:MutableMap<String,Any> = mutableMapOf()):LearningStore {
  val context=mock(Context::class.java)
  val directory=directories.getOrPut(values){Files.createTempDirectory("learning-store-test").toFile()}
  `when`(context.filesDir).thenReturn(directory)
  val prefs=mock(SharedPreferences::class.java)
  val editor=mock(SharedPreferences.Editor::class.java)
  `when`(context.getSharedPreferences("endraspell",Context.MODE_PRIVATE)).thenReturn(prefs)
  `when`(prefs.all).thenAnswer{values.toMap()}
  `when`(prefs.contains(anyString())).thenAnswer{values.containsKey(it.getArgument<String>(0))}
  `when`(prefs.getString(anyString(),anyString())).thenAnswer{values[it.getArgument<String>(0)] as? String ?: it.getArgument<String>(1)}
  `when`(prefs.edit()).thenReturn(editor)
  `when`(editor.putString(anyString(),anyString())).thenAnswer{values[it.getArgument<String>(0)]=it.getArgument<String>(1);editor}
  return LearningStore(context)
 }
 @Test fun freshInstallHasOneGenericPersistentLearner(){
  val values=mutableMapOf<String,Any>();val first=store(values).learners()
  assertEquals(listOf("Learner 1"),first.map{it.name})
  assertEquals(first,store(values).learners())
 }
 @Test fun renameRemoveAndRestorePreserveListsAndHistory(){
  val values=mutableMapOf<String,Any>();val store=store(values);val learner=store.learners().single()
  store.save(learner.id,false,"apple\nbanana")
  store.record(learner.id,false,"apple","apple")
  val history=store.history(learner.id).toString();val events=store.events(learner.id).toString()
  store.renameLearner(learner.id,"Student A")
  store.archiveLearner(learner.id,true)
  assertTrue(store.learners().single().archived)
  store.archiveLearner(learner.id,false)
  assertEquals("Student A",store.learners().single().name)
  assertEquals(listOf("apple","banana"),store.words(learner.id,false))
  assertEquals(events,store.events(learner.id).toString())
  assertEquals(history,store.history(learner.id).toString())
  store.addLearner("Student B")
  assertEquals(2,store.learners().size)
  assertEquals(2,store.learners().map{it.id}.distinct().size)
 }
 @Test fun oldInstallRetainsExistingIdsAndSavedNames(){
  val store=store(mutableMapOf("learner" to "James","learner_name_James" to "Saved Name","James_weekly" to "kept"))
  assertEquals("Saved Name",store.learners().first().name)
  assertEquals("James",store.learners().first().id)
  assertEquals(listOf("kept"),store.words("James",false))
 }
 @Test fun removingAllDoesNotReseedProfiles(){
  val store=store();store.learners().forEach{store.archiveLearner(it.id,true)}
  assertTrue(store.learners().none{!it.archived})
  assertEquals(1,store.learners().size)
 }
}
