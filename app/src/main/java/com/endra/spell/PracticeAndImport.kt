package com.endra.spell
import kotlin.random.Random
import java.util.Locale
internal enum class WordCategory { SPELLING, HEART }
internal data class ReviewWord(val key:String=java.util.UUID.randomUUID().toString(),val text:String,val category:WordCategory,val uncertain:Boolean=false)
internal data class RecognizedLine(val text:String,val left:Float,val top:Float,val right:Float,val bottom:Float)
internal data class ClassifiedWords(val rows:List<ReviewWord>,val warnings:List<String>)
internal object WorksheetClassifier {
 fun classify(lines:List<RecognizedLine>,width:Float,height:Float):ClassifiedWords {
  fun heading(text:String):WordCategory? {val s=text.trim().lowercase(Locale.ROOT).trimEnd(':');return when{Regex("heart\\s+words?").matches(s)->WordCategory.HEART;Regex("(?:weekly\\s+)?spelling(?:\\s+words?)?").matches(s)->WordCategory.SPELLING;else->null}}
  val sorted=lines.sortedWith(compareBy<RecognizedLine>{it.top}.thenBy{it.left})
  val headers=sorted.mapNotNull{l->heading(l.text)?.let{l to it}}
  val warnings=mutableListOf("Check every word against the worksheet. Scans can miss words or misread handwriting.")
  if(headers.map{it.second}.distinct().size<2)warnings.add("Both headings were not found. Unlabelled left/right categories are suggestions only.")
  val rows=sorted.mapNotNull{l->
   if(heading(l.text)!=null)return@mapNotNull null
   val text=l.text.replace(Regex("^\\s*(?:\\d+[.)]?|[•*])\\s*"),"").trim()
   if(text.isBlank()||!text.any{it.isLetter()}||Regex("(?i)^(name|date|week|teacher|directions)\\s*[:#]").containsMatchIn(text))return@mapNotNull null
   val center=(l.left+l.right)/2
   val header=headers.filter{it.first.bottom<=l.top && kotlin.math.abs((it.first.left+it.first.right)/2-center)<width*.3f}.maxByOrNull{it.first.top}
   ReviewWord(text=text,category=header?.second ?: if(center<width/2)WordCategory.SPELLING else WordCategory.HEART,uncertain=header==null||text.contains(Regex("[^\\p{L}\\p{M} '\u2019-]")))
  }
  if(sorted.any{it.left<width*.01f||it.right>width*.99f||it.top<height*.01f||it.bottom>height*.99f})warnings.add("Text is near an edge. Check for clipped or missing words.")
  if(rows.size>300)warnings.add("Only the first 300 words are shown. Split the worksheet into smaller scans.")
  if(rows.isEmpty())warnings.add("No readable words. Retake a clear photo or use Manual Entry.")
  return ClassifiedWords(rows.take(300),warnings)
 }
 fun duplicateKeys(rows:List<ReviewWord>)=rows.groupBy{it.category to FoundationStore.normalized(it.text)}.filterValues{it.size>1}.keys
}
internal data class PracticeWord(val text:String,val heart:Boolean,val needsReview:Boolean=false,val extraPractice:Boolean=false,val recentMiss:Boolean=false,val correct:Int=0,val studied:Int=0,val breakdown:String?=null){
 val weight:Int get()=when{needsReview->8;recentMiss->6;extraPractice->4;correct<3||studied==0->2;else->1}
}
internal object PracticeEngine {
 fun hard(words:List<PracticeWord>)=words.filter{it.needsReview||it.extraPractice}
 fun shuffle(words:List<PracticeWord>,random:Random=Random.Default):List<PracticeWord>{if(words.size<2)return words.toList();val result=words.shuffled(random);return if(result==words)result.drop(1)+result.first() else result}
 fun smart(words:List<PracticeWord>,random:Random=Random.Default):List<PracticeWord>{
  val pool=words.distinctBy{FoundationStore.normalized(it.text)};if(pool.isEmpty())return emptyList()
  val result=mutableListOf<PracticeWord>()
  repeat((pool.size*2).coerceIn(10,60)){val available=pool.filter{pool.size==1||it.text!=result.lastOrNull()?.text};var ticket=random.nextInt(available.sumOf{it.weight});result.add(available.first{ticket-=it.weight;ticket<0})}
  return result
 }
}
