package com.endra.spell

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.exifinterface.media.ExifInterface
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

internal class WorksheetSession(app:Application):AndroidViewModel(app){
 private val folder=File(app.cacheDir,"worksheets")
 private val handler=Handler(Looper.getMainLooper())
 var bitmap by mutableStateOf<Bitmap?>(null);private set
 var rows by mutableStateOf<List<ReviewWord>>(emptyList())
 var warnings by mutableStateOf<List<String>>(emptyList());private set
 var busy by mutableStateOf(false);private set
 var stage by mutableStateOf("choose");private set
 var error by mutableStateOf("")
 var learner="";private set
 var week="";private set
 @Volatile private var generation=0
 private var photo:File?=null
 private var camera:Uri?=null
 init{folder.deleteRecursively()}
 fun start(id:String,currentWeek:String){clear();learner=id;week=currentWeek}
 fun clear(){generation++;busy=false;bitmap=null;rows=emptyList();warnings=emptyList();stage="choose";error="";photo=null;camera?.let{getApplication<Application>().revokeUriPermission(it,android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)};camera=null;folder.deleteRecursively()}
 fun cameraUri():Uri{folder.mkdirs();photo=File.createTempFile("capture-",".jpg",folder);return FileProvider.getUriForFile(getApplication(),"${getApplication<Application>().packageName}.worksheets",photo!!).also{camera=it}}
 fun cameraResult(ok:Boolean){if(ok&&photo!=null)load(Uri.fromFile(photo))else clear()}
 fun load(uri:Uri?){
  if(uri==null)return
  val token=++generation;busy=true;error=""
  Thread{
   var copy:File?=null
   val result=runCatching{
    folder.mkdirs();copy=File.createTempFile("input-",".jpg",folder)
    getApplication<Application>().contentResolver.openInputStream(uri).use{input->requireNotNull(input);copy!!.outputStream().use{out->val bytes=ByteArray(8192);var total=0;while(true){val n=input.read(bytes);if(n<0)break;total+=n;require(total<=24*1024*1024){"Photo is too large. Choose a smaller photo."};if(token!=generation)error("Cancelled");out.write(bytes,0,n)}}}
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(copy!!.path,bounds);require(bounds.outWidth>0&&bounds.outHeight>0){"This photo could not be opened."}
    var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2400)sample*=2
    val raw=requireNotNull(BitmapFactory.decodeFile(copy!!.path,BitmapFactory.Options().apply{inSampleSize=sample}))
    val exif=ExifInterface(copy!!);val matrix=Matrix().apply{postRotate(exif.rotationDegrees.toFloat());if(exif.isFlipped)postScale(-1f,1f)}
    Bitmap.createBitmap(raw,0,0,raw.width,raw.height,matrix,true)
   }
   copy?.delete()
   handler.post{if(token==generation){busy=false;photo?.delete();photo=null;result.onSuccess{bitmap=it;stage="preview"}.onFailure{bitmap=null;stage="choose";error="Photo could not be read. Try again or use Manual Entry."};folder.deleteRecursively()}}
  }.start()
 }
 fun rotate(){bitmap?.let{bitmap=Bitmap.createBitmap(it,0,0,it.width,it.height,Matrix().apply{postRotate(90f)},true)}}
 fun crop(corners:List<Offset>):Boolean{
  val b=bitmap?:return false;if(corners.size!=4){error="Choose the crop area first.";return false}
  if(corners.any{!it.x.isFinite()||!it.y.isFinite()||it.x !in 0f..1f||it.y !in 0f..1f}){error="Keep every corner inside the photo.";return false}
  val pts=corners.map{Offset(it.x*b.width,it.y*b.height)}
  val crosses=pts.indices.map{i->val a=pts[(i+1)%4]-pts[i];val c=pts[(i+2)%4]-pts[(i+1)%4];a.x*c.y-a.y*c.x}
  if(crosses.any{it<=0f}){error="Keep the corners in order without crossing the edges.";return false}
  val w=maxOf((pts[1]-pts[0]).getDistance(),(pts[2]-pts[3]).getDistance()).toInt();val h=maxOf((pts[3]-pts[0]).getDistance(),(pts[2]-pts[1]).getDistance()).toInt()
  if(w<80||h<80){error="Choose a larger worksheet area.";return false}
  val matrix=Matrix();matrix.setPolyToPoly(pts.flatMap{listOf(it.x,it.y)}.toFloatArray(),0,floatArrayOf(0f,0f,w.toFloat(),0f,w.toFloat(),h.toFloat(),0f,h.toFloat()),0,4)
  val result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);android.graphics.Canvas(result).apply{drawColor(android.graphics.Color.WHITE);drawBitmap(b,matrix,android.graphics.Paint(3))};bitmap=result;error="";return true
 }
 fun scan(){
  val b=bitmap?:return;val token=++generation;busy=true;error=""
  val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  recognizer.process(InputImage.fromBitmap(b,0)).addOnSuccessListener{result->if(token==generation){
   val lines=result.textBlocks.flatMap{it.lines}.flatMap{line->
    val groups=mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Element>>()
    line.elements.sortedBy{it.boundingBox?.left?:0}.forEach{e->val previous=groups.lastOrNull()?.lastOrNull()?.boundingBox;val box=e.boundingBox;if(previous==null||box==null||box.left-previous.right>maxOf(b.width*.09f,previous.height()*2.5f))groups.add(mutableListOf(e))else groups.last().add(e)}
    groups.mapNotNull{g->val boxes=g.mapNotNull{it.boundingBox};if(boxes.isEmpty())null else RecognizedLine(g.joinToString(" "){it.text},boxes.minOf{it.left}.toFloat(),boxes.minOf{it.top}.toFloat(),boxes.maxOf{it.right}.toFloat(),boxes.maxOf{it.bottom}.toFloat())}
   }
   val classified=WorksheetClassifier.classify(lines,b.width.toFloat(),b.height.toFloat());rows=classified.rows;warnings=classified.warnings;busy=false
   if(rows.isEmpty()){error=warnings.joinToString(" ");stage="preview"}else stage="review"
  }}.addOnFailureListener{if(token==generation){busy=false;error="Scan failed. Retry with a clearer photo or use Manual Entry. No words were saved."}}.addOnCompleteListener{recognizer.close()}
 }
 override fun onCleared(){clear()}
}
@Composable internal fun worksheetSession():WorksheetSession{val activity=LocalContext.current as ComponentActivity;return remember(activity){ViewModelProvider(activity)[WorksheetSession::class.java]}}
@Composable internal fun WorksheetImport(session:WorksheetSession,store:LearningStore,onSaved:()->Unit,close:()->Unit){
 var confirmed by remember(session.rows){mutableStateOf(false)}
 var corners by remember(session.bitmap){mutableStateOf(WorksheetCropGeometry.initial())}
 var cropping by remember(session.bitmap){mutableStateOf(false)}
 var cropApplied by remember{mutableStateOf(false)}
 val scrollState=rememberScrollState()
 LaunchedEffect(cropping){if(cropping)scrollState.scrollTo(0)}
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){session.cameraResult(it)}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)runCatching{camera.launch(session.cameraUri())}.onFailure{session.error="Camera unavailable. Choose Existing Photo or Manual Entry."}else session.error="Camera permission declined. Choose Existing Photo and Manual Entry still work."}
 val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){session.load(it)}
 fun cancel(){session.clear();close()}
 Dialog(onDismissRequest={cancel()},properties=DialogProperties(usePlatformDefaultWidth=false)){
  ForestPage{Column(Modifier.fillMaxSize()){
   PageHeader(if(session.stage=="review")"Review & Confirm" else "Scan Word List",::cancel)
   if(cropping && session.stage=="preview"){
    Text("Drag a corner to resize the rectangle. Drag inside to move it. Keep only the words and their category headings.",color=Ivory)
    session.bitmap?.let{b->WorksheetCropPreview(b,corners,Modifier.weight(1f).fillMaxWidth()){corners=it;session.error=""}}
    if(session.error.isNotBlank())Text(session.error,color=Ivory)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     ForestButton("Cancel Crop",Modifier.weight(1f)){cropping=false;session.error=""}
     ForestButton("Apply Crop",Modifier.weight(1f)){if(!session.busy&&session.crop(corners)){cropping=false;cropApplied=true}}
    }
   }else{
   Column(Modifier.weight(1f).verticalScroll(scrollState),verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("Photos stay on this device and are discarded when you finish or cancel. Words are saved only after review.",color=Ivory)
    if(session.stage=="choose"){
     ForestButton(if(session.busy)"Opening photo…" else "Take Photo",Modifier.fillMaxWidth()){if(!session.busy)permission.launch(Manifest.permission.CAMERA)}
     ForestButton("Choose Existing Photo",Modifier.fillMaxWidth()){if(!session.busy)gallery.launch("image/*")}
    }
    if(session.stage=="preview"){
     Text(if(cropApplied)"Crop applied. Read Words will scan only the image shown below." else "Tap Crop to select just the word list. Leave logos, teacher notes and other text outside the crop area.",color=Ivory)
     session.bitmap?.let{b->
      if(cropApplied){
       BoxWithConstraints(Modifier.fillMaxWidth()){
        val ratio=b.width.toFloat()/b.height
        val previewHeight=minOf(maxWidth/ratio,480.dp)
        Canvas(Modifier.width(previewHeight*ratio).height(previewHeight).align(Alignment.Center)){
         drawImage(b.asImageBitmap(),dstSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt()))
        }
       }
      }else Canvas(Modifier.fillMaxWidth().aspectRatio(b.width.toFloat()/b.height)){
       drawImage(b.asImageBitmap(),dstSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt()))
      }
     }
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
       ForestButton("Rotate",Modifier.weight(1f)){if(!session.busy)session.rotate()}
       ForestButton("Crop",Modifier.weight(1f)){if(!session.busy){corners=WorksheetCropGeometry.initial();cropping=true;session.error=""}}
      }
      ForestButton(if(session.busy)"Reading photo…" else "Read Words",Modifier.fillMaxWidth()){if(!session.busy)session.scan()}
    }
    if(session.stage=="review"){
     session.warnings.forEach{Text(it,color=Ivory)}
     Text("Duplicates in the same category are saved once. Existing words keep their history and recordings. Reviewed words are added to the current lists.",color=Ivory)
     val duplicates=WorksheetClassifier.duplicateKeys(session.rows)
     session.rows.forEach{row->key(row.key){Frame(Modifier.fillMaxWidth(),true){
      OutlinedTextField(row.text,{text->session.rows=session.rows.map{if(it.key==row.key)it.copy(text=text.take(100))else it}},singleLine=true,label={Text("Word")},modifier=Modifier.fillMaxWidth(),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Ink,unfocusedTextColor=Ink,focusedLabelColor=Ink,unfocusedLabelColor=Ink))
      if(row.uncertain)Text("Check this reading",color=Ink)
      if((row.category to FoundationStore.normalized(row.text)) in duplicates)Text("Duplicate — saved once",color=Ink)
      if(store.words(session.learner,row.category==WordCategory.HEART).any{FoundationStore.normalized(it)==FoundationStore.normalized(row.text)})Text("Already in this list — history kept",color=Ink)
      Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){ForestButton(if(row.category==WordCategory.HEART)"♥ Heart → Spelling" else "Spelling → ♥ Heart",Modifier.weight(1f)){session.rows=session.rows.map{if(it.key==row.key)it.copy(category=if(it.category==WordCategory.HEART)WordCategory.SPELLING else WordCategory.HEART)else it}};ForestButton("Delete"){session.rows=session.rows.filter{it.key!=row.key}}}
     }}}
     ForestButton("Add Word",Modifier.fillMaxWidth()){if(session.rows.size<300)session.rows=session.rows+ReviewWord(text="",category=WordCategory.SPELLING)}
     Row{Checkbox(confirmed,{confirmed=it});Text("I checked every word and category.",color=Ivory,modifier=Modifier.padding(top=12.dp))}
     ForestButton("Confirm & Save",Modifier.fillMaxWidth()){if(!confirmed)session.error="Check every word and tick the confirmation first."else runCatching{store.importReviewed(session.learner,session.week,session.rows)}.onSuccess{onSaved();cancel()}.onFailure{session.error="Could not save: ${it.message}. No partial list was saved."}}
    }
    if(session.error.isNotBlank())Text(session.error,color=Ivory)
   }
   }
   ForestButton("Cancel / Manual Entry",Modifier.fillMaxWidth(),onClick=::cancel)
  }}
 }
}
