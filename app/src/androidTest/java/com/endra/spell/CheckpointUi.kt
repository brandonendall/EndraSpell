package com.endra.spell
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo as Node
import java.io.File

/** Native accessibility acceptance of the actual Compose controls; test APK only. */
internal object CheckpointUi {
 fun run(i:Instrumentation,imports:Boolean=false):String {
  val ctx=i.targetContext;val store=LearningStore(ctx);val id=store.learners().first{!it.archived}.id
  store.putFlag("auto",false);store.putFlag("recordedVoice",false)
  val before=store.events(id).toString();val teacher=store.words(id,false)
  check(teacher.isNotEmpty())
  val evidence=File(ctx.filesDir,"ui-evidence").apply{mkdirs()}
  fun nodes(n:Node?):List<Node> = if(n==null)emptyList() else listOf(n)+(0 until n.childCount).flatMap{nodes(n.getChild(it))}
  fun tree()=nodes(i.uiAutomation.rootInActiveWindow).filter{it.isVisibleToUser}
  fun describe()=tree().joinToString("\n"){"${it.className}|${it.text}|${it.contentDescription}"}
  fun waitFor(label:String):Node {repeat(if(label=="Review & Confirm")360 else 80){tree().firstOrNull{it.text?.toString()==label||it.contentDescription?.toString()==label||it.contentDescription?.toString()?.startsWith(label+",")==true}?.let{return it};Thread.sleep(250)};error("Missing $label\n${describe()}")}
  fun clickNode(n:Node){var v:Node?=n;while(v!=null&&!v.isClickable)v=v.parent;check(v?.performAction(Node.ACTION_CLICK)==true){"Not clickable: ${n.text}"};Thread.sleep(2500)}
  fun click(label:String)=clickNode(waitFor(label))
  fun snap(name:String){Thread.sleep(2500);i.uiAutomation.takeScreenshot()?.let{b->File(evidence,"$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}};File(evidence,"$name.txt").writeText(describe())}
  fun back(){i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);Thread.sleep(2500)}
  fun set(n:Node,s:String){check(n.performAction(Node.ACTION_SET_TEXT,Bundle().apply{putCharSequence(Node.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,s)}));Thread.sleep(500)}
  fun checkbox():Node{repeat(80){tree().firstOrNull{it.isCheckable||it.className?.toString()=="android.widget.CheckBox"}?.let{return it};Thread.sleep(250)};error("No checkbox\n${describe()}")}
  fun home(){i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(2000);click(store.learners().first{it.id==id}.name)}
  if(imports){
   fun scrollTo(label:String){repeat(15){if(tree().any{it.text?.toString()==label})return;tree().firstOrNull{it.isScrollable}?.performAction(Node.ACTION_SCROLL_FORWARD);Thread.sleep(1500)};waitFor(label)}
   home();click("Add or Edit Words");click("Scan Word List");click("Choose Existing Photo");click("Show roots");click("Downloads");click("EndraSpell-worksheet.png");waitFor("Read Words");snap("photo-preview");click("Read Words");waitFor("Review & Confirm");snap("review-top")
   val original=File(ctx.filesDir,"foundation-v1.json").readText();set(tree().first{it.isEditable},"apple");click("Spelling → ♥ Heart");click("Delete");scrollTo("Add Word");click("Add Word");scrollTo("Confirm & Save");snap("review-bottom");click("Confirm & Save");check(File(ctx.filesDir,"foundation-v1.json").readText()==original)
   if(!checkbox().isChecked)clickNode(checkbox());click("Confirm & Save");check(File(ctx.filesDir,"foundation-v1.json").readText()==original)
   tree().firstOrNull{it.isEditable&&it.text.isNullOrBlank()}?.let{set(it,"uiword")}?:error("Added editable row missing\n${describe()}")
   scrollTo("Confirm & Save");if(!checkbox().isChecked)clickNode(checkbox());click("Confirm & Save");waitFor("Save List");check("uiword" in LearningStore(ctx).words(id,false));check(File(ctx.cacheDir,"worksheets").listFiles().isNullOrEmpty());snap("import-saved")
   return "PASS: system photo picker, preview/OCR, review editing/moving/deleting/adding, mandatory confirmation and blank rejection, save and cache cleanup."
  }
  home();click("Add or Edit Words");snap("editor");click("Word Supports");if(!checkbox().isChecked)clickNode(checkbox());check(LearningStore(ctx).practiceWords(id).any{it.extraPractice});click("Edit optional breakdown");waitFor("Parent / teacher breakdown");set(tree().first{it.isEditable},"ap • ple");if(!checkbox().isChecked)clickNode(checkbox());click("Save");click("Done");back()
  click("Practice Words");snap("practice");click("Shuffle Words");check(store.words(id,false)==teacher);click("Practice Options");click("Hear Only");waitFor("Listen carefully");click("Replay");click("Reveal");check(tree().none{it.text?.toString()=="Listen carefully"});snap("hear-revealed");click("Next ›")
  click("Practice Options");snap("practice-options");click("Practice My Hard Words");check(tree().none{it.text?.toString()=="Your list is empty"});click("Practice Options");click("Extra Practice");click("Reveal");waitFor("Word Breakdown");click("Word Breakdown");waitFor("ap • ple");snap("approved-breakdown");click("Close");click("Practice Options");click("Smart Practice");check(tree().none{it.text?.toString()=="Your list is empty"});click("My recordings");click("Replay");click("Automatic voice");click("Replay");snap("smart-practice");back()
  // Study events may grow, but no scored attempt or mastery input may be added.
  val old=org.json.JSONArray(before);val now=store.events(id);fun scored(a:org.json.JSONArray)=(0 until a.length()).map{a.getJSONObject(it)}.filter{!it.isNull("answer")}.map{it.toString()}
  check(scored(old)==scored(now));check(store.words(id,false)==teacher)
  click("Add or Edit Words");val field=tree().first{it.isEditable};clickNode(field);snap("editor-keyboard");val bounds=android.graphics.Rect();tree().firstOrNull{it.isEditable}?.getBoundsInScreen(bounds);check(bounds.height()>40){"Manual field collapsed with keyboard: $bounds"};back();click("Scan Word List");snap("scan-choose");click("Cancel / Manual Entry");waitFor("Save List");check(store.words(id,false)==teacher)
  return "PASS: actual controls for supports/approved breakdown, shuffle, Hear Only, Replay/Reveal, Hard/Extra/Smart, voice selection/fallback, scan/manual cancellation; teacher order and scored attempts unchanged. Layout images saved."
 }
}
