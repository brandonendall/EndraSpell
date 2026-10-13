package com.endra.spell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Visible speech mode selection shared by practice, quizzes, and recordings. */
@Composable internal fun VoiceChoice(store:LearningStore,stop:()->Unit={}){
 var recorded by remember{mutableStateOf(store.flag("recordedVoice"))}
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  ForestButton("Automatic voice",Modifier.weight(1f),!recorded){stop();recorded=false;store.putFlag("recordedVoice",false)}
  ForestButton("My recordings",Modifier.weight(1f),recorded){stop();recorded=true;store.putFlag("recordedVoice",true)}
 }
 if(recorded)Text("Words without a recording use automatic voice.",color=Color(0xFFFFEAC0),modifier=Modifier.padding(vertical=4.dp))
}
