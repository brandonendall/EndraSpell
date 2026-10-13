package com.endra.spell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable fun ManageLearners(store:LearningStore,changed:()->Unit,home:()->Unit,startAdding:Boolean=false){
 var revision by remember{mutableIntStateOf(0)}
 val all=remember(revision){store.learners()}
 var archived by rememberSaveable{mutableStateOf(false)}
 var page by rememberSaveable{mutableIntStateOf(0)}
 var edit by remember{mutableStateOf<LearningStore.Learner?>(null)}
 var adding by remember{mutableStateOf(startAdding)}
 var name by remember{mutableStateOf("")}
 val visible=all.filter{it.archived==archived}
 val pages=((visible.size+3)/4).coerceAtLeast(1)
 LaunchedEffect(pages){page=page.coerceAtMost(pages-1)}
 fun update(){revision++;changed()}
 Column(Modifier.fillMaxSize()){
  PageHeader("Edit Learners",home)
  Text("${all.count{!it.archived}} active learners • History stays saved",color=Color(0xFFFFEAC0))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   ForestButton("Active",Modifier.weight(1f),!archived){archived=false;page=0}
   ForestButton("Archived",Modifier.weight(1f),archived){archived=true;page=0}
  }
  ForestButton("Add Learner",Modifier.fillMaxWidth()){name="";adding=true}
  visible.drop(page*4).take(4).forEach{learner->
   Row(Modifier.fillMaxWidth().padding(vertical=4.dp)){
    ForestButton(learner.name,Modifier.weight(1f)){edit=learner;name=learner.name}
    TextButton(onClick={store.archiveLearner(learner.id,!archived);update()}){Text(if(archived)"Restore" else "Remove",color=Color(0xFFE8C66A))}
   }
  }
  if(visible.isEmpty())Text(if(archived)"No archived learners." else "Add a learner to begin.",color=Color(0xFFFFEAC0))
  if(pages>1)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
   TextButton(enabled=page>0,onClick={page--}){Text("‹ Previous")}
   Text("${page+1} / $pages",color=Color(0xFFFFEAC0),modifier=Modifier.padding(12.dp))
   TextButton(enabled=page<pages-1,onClick={page++}){Text("Next ›")}
  }
  Text("Tap a name to rename it. Remove archives a learner without deleting their words or progress. Restore them from Archived.",color=Color(0xFFFFEAC0),modifier=Modifier.padding(top=12.dp))
 }
 if(adding||edit!=null){
  val valid=name.trim().isNotEmpty()&&!all.any{it.id!=edit?.id&&it.name.equals(name.trim(),true)}
  AlertDialog(onDismissRequest={adding=false;edit=null},title={Text(if(adding)"Add Learner" else "Rename Learner")},text={OutlinedTextField(name,{name=it.take(30)},label={Text("Learner name")},singleLine=true,isError=name.isNotBlank()&&!valid)},confirmButton={TextButton(enabled=valid,onClick={if(adding)store.addLearner(name)else store.renameLearner(edit!!.id,name);adding=false;edit=null;update()}){Text("Save")}},dismissButton={TextButton(onClick={adding=false;edit=null}){Text("Cancel")}})
 }
}
