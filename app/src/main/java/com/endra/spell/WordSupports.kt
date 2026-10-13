package com.endra.spell
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable internal fun WordSupports(child:String,store:LearningStore,close:()->Unit){
 var revision by remember{mutableIntStateOf(0)};var selected by remember{mutableStateOf<PracticeWord?>(null)};var breakdown by remember{mutableStateOf("")};var approved by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
 AlertDialog(onDismissRequest=close,title={Text("Word Supports")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())){
  Text("Extra Practice is independent of Mastery and Needs Review. Optional breakdowns are shown only after parent or teacher approval.")
  val words=remember(revision){store.practiceWords(child)}
  if(words.isEmpty())Text("Add words first.")
  words.forEach{word->
   Text(word.text)
   Row{Checkbox(word.extraPractice,{enabled->runCatching{store.setExtraPractice(child,word.text,enabled)}.onSuccess{revision++}.onFailure{error="Could not save. Retry."}});Text("Extra Practice",Modifier.padding(top=12.dp))}
   if(word.needsReview)Text("Needs Review")
   TextButton(onClick={selected=word;breakdown=word.breakdown.orEmpty();approved=word.breakdown!=null;error=""}){Text("Edit optional breakdown")}
  }
  if(error.isNotBlank())Text(error)
 }},confirmButton={TextButton(onClick=close){Text("Done")}})
 selected?.let{word->AlertDialog(onDismissRequest={selected=null},title={Text(word.text)},text={Column{
  OutlinedTextField(breakdown,{breakdown=it.take(160);approved=false},label={Text("Parent / teacher breakdown")})
  Row{Checkbox(approved,{approved=it});Text("I approve this breakdown.",Modifier.padding(top=12.dp))}
  Text("Nothing is generated automatically. Leave empty to remove it.")
  if(error.isNotBlank())Text(error)
 }},confirmButton={TextButton(onClick={runCatching{store.saveBreakdown(child,word.text,breakdown,approved)}.onSuccess{revision++;selected=null}.onFailure{error="Could not save breakdown."}}){Text("Save")}},dismissButton={TextButton(onClick={selected=null}){Text("Cancel")}})}
}
