package com.endra.spell

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Coordinates stay relative to the image, not the letterboxed preview or screen. */
internal object WorksheetCropGeometry {
 fun initial()=listOf(Offset(.08f,.08f),Offset(.92f,.08f),Offset(.92f,.92f),Offset(.08f,.92f))
 // Every gesture preserves an axis-aligned rectangle, including at image boundaries.
 fun resize(points:List<Offset>,corner:Int,target:Offset):List<Offset> {
  val opposite=points[(corner+2)%4]
  val leftCorner=corner==0||corner==3
  val topCorner=corner==0||corner==1
  val x=if(leftCorner)target.x.coerceIn(0f,opposite.x-.02f) else target.x.coerceIn(opposite.x+.02f,1f)
  val y=if(topCorner)target.y.coerceIn(0f,opposite.y-.02f) else target.y.coerceIn(opposite.y+.02f,1f)
  val left=if(leftCorner)x else opposite.x;val right=if(leftCorner)opposite.x else x
  val top=if(topCorner)y else opposite.y;val bottom=if(topCorner)opposite.y else y
  return listOf(Offset(left,top),Offset(right,top),Offset(right,bottom),Offset(left,bottom))
 }
 fun move(points:List<Offset>,delta:Offset):List<Offset> {
  val dx=delta.x.coerceIn(-points[0].x,1f-points[2].x)
  val dy=delta.y.coerceIn(-points[0].y,1f-points[2].y)
  return points.map{it+Offset(dx,dy)}
 }
 fun bounds(imageWidth:Int,imageHeight:Int,width:Float,height:Float):Rect {
  val scale=minOf(width/imageWidth,height/imageHeight)
  val w=imageWidth*scale;val h=imageHeight*scale
  return Rect((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
 }
 fun position(point:Offset,bounds:Rect)=Offset(bounds.left+point.x*bounds.width,bounds.top+point.y*bounds.height)
 fun normalized(point:Offset,bounds:Rect)=Offset(((point.x-bounds.left)/bounds.width).coerceIn(0f,1f),((point.y-bounds.top)/bounds.height).coerceIn(0f,1f))
}

@Composable internal fun WorksheetCropPreview(bitmap:Bitmap,corners:List<Offset>,modifier:Modifier=Modifier,onCorners:(List<Offset>)->Unit){
 val currentCorners by rememberUpdatedState(corners)
 val currentChange by rememberUpdatedState(onCorners)
 Canvas(modifier.fillMaxSize()
  .semantics{contentDescription="Crop area. Drag a corner to resize the rectangle, or drag inside to move it."}
  .pointerInput(bitmap){
   var active=-1
   detectDragGestures(onDragStart={point->
    val bounds=WorksheetCropGeometry.bounds(bitmap.width,bitmap.height,size.width.toFloat(),size.height.toFloat())
    active=currentCorners.indices.minByOrNull{(WorksheetCropGeometry.position(currentCorners[it],bounds)-point).getDistance()}?:-1
    if(active>=0&&(WorksheetCropGeometry.position(currentCorners[active],bounds)-point).getDistance()>32.dp.toPx()){
     val p=WorksheetCropGeometry.normalized(point,bounds)
     active=if(point.x in bounds.left..bounds.right&&point.y in bounds.top..bounds.bottom&&p.x in currentCorners[0].x..currentCorners[2].x&&p.y in currentCorners[0].y..currentCorners[2].y)4 else -1
    }
   },onDragEnd={active=-1},onDragCancel={active=-1}){change,dragAmount->
    if(active>=0){
     change.consume()
     val bounds=WorksheetCropGeometry.bounds(bitmap.width,bitmap.height,size.width.toFloat(),size.height.toFloat())
     currentChange(if(active==4)WorksheetCropGeometry.move(currentCorners,Offset(dragAmount.x/bounds.width,dragAmount.y/bounds.height))
     else WorksheetCropGeometry.resize(currentCorners,active,WorksheetCropGeometry.normalized(change.position,bounds)))
    }
   }
  }){
   val bounds=WorksheetCropGeometry.bounds(bitmap.width,bitmap.height,size.width,size.height)
   drawImage(bitmap.asImageBitmap(),dstOffset=IntOffset(bounds.left.toInt(),bounds.top.toInt()),dstSize=IntSize(bounds.width.toInt(),bounds.height.toInt()))
   val points=corners.map{WorksheetCropGeometry.position(it,bounds)}
   val shade=Path().apply{fillType=PathFillType.EvenOdd;addRect(bounds);moveTo(points[0].x,points[0].y);points.drop(1).forEach{lineTo(it.x,it.y)};close()}
   drawPath(shade,Color.Black.copy(alpha=.6f))
   points.indices.forEach{index->
    val a=points[index];val b=points[(index+1)%4]
    drawLine(Ivory,a,b,3.dp.toPx())
    val handle=18.dp.toPx()
    val horizontal=Offset(if(index==0||index==3)handle else -handle,0f)
    val vertical=Offset(0f,if(index<2)handle else -handle)
    for(end in listOf(a+horizontal,a+vertical)){
     drawLine(Color(0xFF073C29),a,end,8.dp.toPx())
     drawLine(Color(0xFFD4AF5B),a,end,4.dp.toPx())
    }
   }
 }
}
