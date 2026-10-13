package com.endra.spell
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
class WorksheetCropTest {
 @Test fun portraitImageHasHorizontalLetterbox(){val r=WorksheetCropGeometry.bounds(100,200,400f,400f);assertEquals(100f,r.left,0f);assertEquals(300f,r.right,0f);assertEquals(0f,r.top,0f)}
 @Test fun landscapeImageHasVerticalLetterbox(){val r=WorksheetCropGeometry.bounds(200,100,400f,400f);assertEquals(100f,r.top,0f);assertEquals(300f,r.bottom,0f)}
 @Test fun draggingUsesImageCoordinates(){val r=WorksheetCropGeometry.bounds(100,200,400f,400f);assertEquals(Offset(.25f,.75f),WorksheetCropGeometry.normalized(Offset(150f,300f),r))}
 @Test fun draggingOutsideImageClampsToEdges(){val r=WorksheetCropGeometry.bounds(100,200,400f,400f);assertEquals(Offset(0f,1f),WorksheetCropGeometry.normalized(Offset(-50f,600f),r))}
 @Test fun cornerPositionRoundTripsAcrossPhoneAndTablet(){for(size in listOf(360f to 288f,600f to 480f)){val r=WorksheetCropGeometry.bounds(900,1400,size.first,size.second);for(point in WorksheetCropGeometry.initial()){val p=WorksheetCropGeometry.normalized(WorksheetCropGeometry.position(point,r),r);assertEquals(point.x,p.x,.0001f);assertEquals(point.y,p.y,.0001f)}}}

 @Test fun everyCornerKeepsRectangleAndOppositeCornerFixed(){
  for(corner in 0..3){
   val old=WorksheetCropGeometry.initial()
   val p=WorksheetCropGeometry.resize(old,corner,Offset(.4f,.6f))
   assertEquals(old[(corner+2)%4],p[(corner+2)%4])
   assertEquals(p[0].x,p[3].x,0f);assertEquals(p[1].x,p[2].x,0f)
   assertEquals(p[0].y,p[1].y,0f);assertEquals(p[2].y,p[3].y,0f)
  }
 }
 @Test fun cornersCannotCrossOrLeaveImage(){
  for(corner in 0..3)for(target in listOf(Offset(-9f,-9f),Offset(9f,9f))){
   val p=WorksheetCropGeometry.resize(WorksheetCropGeometry.initial(),corner,target)
   assertTrue(p.all{it.x in 0f..1f&&it.y in 0f..1f})
   assertTrue(p[2].x-p[0].x>=.0199f);assertTrue(p[2].y-p[0].y>=.0199f)
  }
 }
 @Test fun movingClampsWithoutChangingSize(){
  val original=WorksheetCropGeometry.initial()
  for(delta in listOf(Offset(-9f,-9f),Offset(9f,9f))){
   val p=WorksheetCropGeometry.move(original,delta)
   assertTrue(p.all{it.x in 0f..1f&&it.y in 0f..1f})
   assertEquals(original[2].x-original[0].x,p[2].x-p[0].x,.0001f)
   assertEquals(original[2].y-original[0].y,p[2].y-p[0].y,.0001f)
  }
 }
}
