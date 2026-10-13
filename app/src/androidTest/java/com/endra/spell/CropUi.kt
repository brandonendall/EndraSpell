package com.endra.spell
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo as Node
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.geometry.Offset
import java.io.File
internal object CropUi {
 fun run(i:Instrumentation):String {
  val ctx=i.targetContext;val store=LearningStore(ctx);val learner=store.learners().first{!it.archived}.id
  val original=File(ctx.filesDir,"foundation-v1.json").readBytes()
  val evidence=File(ctx.filesDir,"crop-evidence").apply{mkdirs()}
  fun nodes(n:Node?):List<Node> = if(n==null)emptyList() else listOf(n)+(0 until n.childCount).flatMap{nodes(n.getChild(it))}
  fun tree():List<Node>{val ns=nodes(i.uiAutomation.rootInActiveWindow).filter{it.isVisibleToUser};if(ns.any{it.text?.toString()=="System UI isn't responding"}){ns.firstOrNull{it.text?.toString()=="Wait"}?.performAction(Node.ACTION_CLICK);Thread.sleep(1000);return emptyList()};return ns}
  fun waitFor(label:String):Node {repeat(240){tree().firstOrNull{it.text?.toString()==label||it.contentDescription?.toString()?.startsWith(label)==true}?.let{return it};Thread.sleep(250)};error("Missing $label: "+tree().joinToString{it.text?.toString()?:it.contentDescription?.toString().orEmpty()})}
  fun click(label:String){var n:Node?=waitFor(label);while(n!=null&&!n.isClickable)n=n.parent;check(n?.performAction(Node.ACTION_CLICK)==true);Thread.sleep(1500)}
  fun scrollTo(label:String){repeat(10){if(tree().any{it.text?.toString()==label})return;tree().firstOrNull{it.isScrollable}?.performAction(Node.ACTION_SCROLL_FORWARD);Thread.sleep(1000)};waitFor(label)}
  fun snap(name:String){Thread.sleep(1000);i.uiAutomation.takeScreenshot()?.let{b->File(evidence,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}}
  val activity=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
  click(store.learners().first{it.id==learner}.name);click("Add or Edit Words");click("Scan Word List")
  lateinit var s:WorksheetSession
  val source=File(ctx.cacheDir,"crop-ui.png")
  val fixture=Bitmap.createBitmap(1000,1400,Bitmap.Config.ARGB_8888);val paint=Paint(3).apply{color=Color.BLACK;textSize=48f}
  val expected=listOf("apple","beach","chair","crayon","family","flower","jump","school","water","yellow")
  Canvas(fixture).apply{drawColor(Color.WHITE);drawText("G",450f,100f,paint);drawText("Teacher notes above the list",40f,220f,paint);expected.forEachIndexed{n,w->drawText(w,150f,350f+n*75f,paint)};drawText("Website reference",510f,1025f,paint);drawText("Teacher instructions below",40f,1270f,paint)}
  source.outputStream().use{fixture.compress(Bitmap.CompressFormat.PNG,100,it)}
  i.runOnMainSync{s=ViewModelProvider(activity)[WorksheetSession::class.java];s.load(android.net.Uri.fromFile(source))}
  repeat(240){var busy=true;i.runOnMainSync{busy=s.busy};if(busy)Thread.sleep(250)}
  scrollTo("Crop");click("Crop");waitFor("Crop area.");snap("crop-start")
  check(tree().none{it.text?.toString()=="Read Words"})
  val full=s.bitmap;scrollTo("Cancel Crop");click("Cancel Crop");check(s.bitmap===full)
  scrollTo("Crop");click("Crop");val canvas=waitFor("Crop area.");val rect=Rect();canvas.getBoundsInScreen(rect)
  val display=i.uiAutomation.takeScreenshot()!!
  check(rect.height()>display.height*.5f){"Crop preview too short: ${rect.height()} of ${display.height}"}
  display.recycle()
  check(tree().any{it.text?.toString()=="Apply Crop"})
  val fit=WorksheetCropGeometry.bounds(1000,1400,rect.width().toFloat(),rect.height().toFloat())
  fun screen(p:Offset)=WorksheetCropGeometry.position(p,fit)+Offset(rect.left.toFloat(),rect.top.toFloat())
  fun drag(from:Offset,to:Offset){
   val start=SystemClock.uptimeMillis()
   fun event(action:Int,p:Offset){val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,p.x,p.y,0);e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;check(i.uiAutomation.injectInputEvent(e,true));e.recycle()}
   event(MotionEvent.ACTION_DOWN,from);Thread.sleep(80)
   for(n in 1..16){event(MotionEvent.ACTION_MOVE,from+(to-from)*(n/16f));Thread.sleep(35)}
   event(MotionEvent.ACTION_UP,to);Thread.sleep(500)
  }
  val targets=listOf(Offset(.10f,.20f),Offset(.48f,.20f),Offset(.48f,.80f),Offset(.10f,.80f))
  var points=WorksheetCropGeometry.initial()
  targets.forEachIndexed{index,target->drag(screen(points[index]),screen(target));points=WorksheetCropGeometry.resize(points,index,target)}
  // Moving inside shifts the complete rectangle without changing its size.
  val center=(points[0]+points[2])*.5f
  drag(screen(center),screen(center+Offset(.04f,.03f)))
  drag(screen(center+Offset(.04f,.03f)),screen(center))
  snap("crop-selected");scrollTo("Apply Crop");click("Apply Crop");check(s.bitmap!==full);check(s.bitmap!!.width in 365..395&&s.bitmap!!.height in 820..860){"Unexpected crop size ${s.bitmap!!.width} x ${s.bitmap!!.height}"}
  scrollTo("Read Words");snap("crop-applied");click("Read Words");waitFor("Review & Confirm")
  check(s.rows.map{it.text.lowercase()}==expected){s.rows.toString()}
  snap("crop-review");check(File(ctx.filesDir,"foundation-v1.json").readBytes().contentEquals(original));click("Cancel / Manual Entry");waitFor("Save List");check(File(ctx.filesDir,"foundation-v1.json").readBytes().contentEquals(original));check(File(ctx.cacheDir,"worksheets").listFiles().isNullOrEmpty());check(source.exists())
  return "PASS: actual Crop button, visible handles, cancel unchanged, four rectangular corner drags, Apply Crop, cropped OCR exactly ten words, no outside text, review cancel preserves data and cleans cache."
 }
}
