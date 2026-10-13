package com.endra.spell
import android.app.Application
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import java.io.File
internal object CheckpointBDevice {
 fun fixture(file:File){
  val image=Bitmap.createBitmap(1400,1200,Bitmap.Config.ARGB_8888);val paint=Paint(3).apply{color=Color.BLACK;textSize=48f;typeface=android.graphics.Typeface.DEFAULT_BOLD}
  Canvas(image).apply{drawColor(Color.WHITE);drawText("Spelling Words",80f,120f,paint);drawText("Heart Words",820f,120f,paint);paint.typeface=android.graphics.Typeface.DEFAULT;drawText("apple",80f,260f,paint);drawText("banana",80f,380f,paint);drawText("said",820f,260f,paint);drawText("the",820f,380f,paint)}
  file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
 }
 fun run(i:Instrumentation):String{
  val ctx=i.targetContext;val store=LearningStore(ctx);val learner=store.learners().first{!it.archived}.id
  val original=File(ctx.filesDir,"foundation-v1.json").readText();val source=File(ctx.cacheDir,"worksheet-test.png");fixture(source)
  lateinit var session:WorksheetSession
  fun waitIdle(){for(n in 0..180){var busy=true;i.runOnMainSync{busy=session.busy};if(!busy)return;Thread.sleep(500)};error("OCR timed out")}
  i.runOnMainSync{session=WorksheetSession(ctx.applicationContext as Application);session.start(learner,store.currentWeek(learner));session.load(Uri.fromFile(source))};waitIdle();check(session.stage=="preview"){session.error}
  i.runOnMainSync{session.scan()};waitIdle()
  check(session.stage=="review"){session.error};check(session.rows.filter{it.category==WordCategory.SPELLING}.map{it.text.lowercase()}==listOf("apple","banana")){session.rows.toString()};check(session.rows.filter{it.category==WordCategory.HEART}.map{it.text.lowercase()}==listOf("said","the")){session.rows.toString()}
  check(File(ctx.filesDir,"foundation-v1.json").readText()==original){"Scan wrote before confirm"}
  val edited=session.rows.map{if(it.text=="banana")it.copy(text="pear",category=WordCategory.HEART)else it}.filter{it.text!="the"}+ReviewWord(text="added",category=WordCategory.SPELLING)
  store.importReviewed(learner,session.week,edited);check("added" in store.words(learner,false)&&"pear" in store.words(learner,true))
  i.runOnMainSync{session.clear()};check(session.bitmap==null&&session.rows.isEmpty());check(!File(ctx.cacheDir,"worksheets").exists());check(source.exists())
  val saved=File(ctx.filesDir,"foundation-v1.json").readText()
  i.runOnMainSync{session.start(learner,store.currentWeek(learner));session.load(Uri.fromFile(source))};waitIdle();i.runOnMainSync{session.scan();session.clear()};Thread.sleep(2500);check(session.rows.isEmpty()&&session.bitmap==null);check(File(ctx.filesDir,"foundation-v1.json").readText()==saved)
  i.runOnMainSync{session.load(Uri.parse("file:///does-not-exist"))};waitIdle();check(session.error.isNotEmpty());check(!File(ctx.cacheDir,"worksheets").exists())
  val exifPhoto=File(ctx.cacheDir,"worksheet-exif.jpg")
  android.graphics.BitmapFactory.decodeFile(source.path).let{b->exifPhoto.outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,95,it)}}
  androidx.exifinterface.media.ExifInterface(exifPhoto).apply{setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
  i.runOnMainSync{session.load(Uri.fromFile(exifPhoto))};waitIdle();check(session.bitmap!!.width==1200&&session.bitmap!!.height==1400);exifPhoto.delete()
  i.runOnMainSync{session.load(Uri.fromFile(source))};waitIdle()
  i.runOnMainSync{session.rotate();check(session.bitmap!!.width==1200);session.rotate();session.rotate();session.rotate();session.crop(listOf(androidx.compose.ui.geometry.Offset(.02f,.02f),androidx.compose.ui.geometry.Offset(.98f,.02f),androidx.compose.ui.geometry.Offset(.98f,.98f),androidx.compose.ui.geometry.Offset(.02f,.98f)));check(session.bitmap!!.width<1400);session.scan()};waitIdle();check(session.rows.any{it.text=="apple"});i.runOnMainSync{session.clear()}
  val blank=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);Canvas(blank).drawColor(Color.WHITE);source.outputStream().use{blank.compress(Bitmap.CompressFormat.PNG,100,it)}
  i.runOnMainSync{session.load(Uri.fromFile(source))};waitIdle();i.runOnMainSync{session.scan()};waitIdle();check(session.stage=="preview"&&session.rows.isEmpty());check(session.error.isNotEmpty());i.runOnMainSync{session.clear()};check(File(ctx.filesDir,"foundation-v1.json").readText()==saved)
  val orphan=File(ctx.cacheDir,"worksheets/orphan.jpg");orphan.parentFile!!.mkdirs();orphan.writeText("interrupted");i.runOnMainSync{WorksheetSession(ctx.applicationContext as Application)};check(!orphan.exists())
  val permissions=ctx.packageManager.getPackageInfo(ctx.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty();check("android.permission.INTERNET" !in permissions);check("android.permission.READ_EXTERNAL_STORAGE" !in permissions);check("android.permission.READ_MEDIA_IMAGES" !in permissions)
  fixture(source) // Keep synthetic worksheet only in test cache for UI picker testing.
  return "PASS: bundled offline OCR; spatial categories; no write before confirmation; reviewed edit/move/delete/add commit; late-result cancellation; bad image; blank scan; rotation/crop OCR; temporary/orphan cleanup; source photo retained; no Internet or broad storage permission."
 }
}
