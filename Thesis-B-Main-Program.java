import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import processing.video.*; 
import blobscanner.*; 
import processing.serial.*; 
import java.util.Arrays; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class Motion_tracking_calibration extends PApplet {

/*
Characterize camera, put grid in front of camera, use matlab to get transformation matrix
*/




Map map, imageMap;
Serial port;
VideoFeed videoFeed;
Calibrator sensorToActual, actualToSensor;
Rectangle calibratorScreenRect;
Float2 sensorPosNoOff = new Float2(0, 0), mappedOut = new Float2(0, 0);
Rectangle outGridRect;
Log dataLog = new Log();
int fps = 30;

public void setup() {
  
  frameRate(fps);
  //size(640, 480);
  //colorMode(360, 1, 1, 1);
  PFont font = createFont("", 10);
  String[] cameras = Capture.list();
  String cap = cameras[0];
  if (cameras.length == 0) {
    println("There are no cameras available for capture.");
    exit();
  } else {
    println("Available cameras:");
    for (int i = 0; i < cameras.length; i++) {
      println(cameras[i]);
    }
    // The camera can be initialized directly using an 
    // element from the array returned by list():
    textSize(10);
    for(int i=0; i < cameras.length; i++) {
      if(cameras[i].equals("name=DroidCam Source 3,size=640x480,fps=30")) {
        cap = "name=DroidCam Source 3,size=640x480,fps=30";
        //delay(10000);
      } else {
        //cap = cameras[0];
      }
    }
  }      
  
  //videoFeed = new VideoFeed(this, new Rectangle(5, 0 + 480/2, 640/2, 480/2, CORNER), cap);
  //videoFeed.setRawRect(new Rectangle(5, 0, 640/2, 480/2, CORNER));
  float vidH = height/3;
  float vidW = vidH * 640.0f/480;
  videoFeed = new VideoFeed(this, new Rectangle(5 + vidW/2, vidH * 1.5f, vidW, vidH, CENTER), cap);
  videoFeed.setRawRect(new Rectangle(5 + vidW/2, vidH * 0.5f, vidW, vidH, CENTER));
  videoFeed.setPlateRect(new Rectangle(5 + vidW/2, vidH * 2.5f, vidW, vidH, CENTER));
  while(port == null ) {
    port = new Serial(this, "COM4", 115200);
  }
  port.bufferUntil('\n');
  //colorMode(HSB, 360, 1, 1, 1);
  map = new Map(1000, 240, 400);
  imageMap = new Map(1000, 240, 400);
  outGridRect = new Rectangle(1000, 240, 400, 400, CENTER);
  
  sensorPosNoOff = new Float2(0, 0);
  //centerSensor();
  //calibrationTimer = millis();
  
  calibratorScreenRect = new Rectangle(580, 240, 400, 400, CENTER);
  sensorToActual = new Calibrator(11, 1, 1, 0.5f, 0.5f, 0.3f, calibratorScreenRect);
}

public void draw() {
  checkCalibration();
  background(120);
  videoFeed.update();
  videoFeed.display();
  //map.display();
  map.displayBox();
  //imageMap.displayDot();
  //sensorToActual.mouseUpdate();
  sensorToActual.update(sensorPosNoOff.x, -sensorPosNoOff.y, videoFeed.fracPos.x, -videoFeed.fracPos.y);
  sensorToActual.display();
  sensorToActual.displayGrid(outGridRect);
  sensorToActual.displayPoints(outGridRect);
  showFPS();
  fill(255, 255, 255);
  //textSize(20);
  //text("Scale:" + videoFeed.imgScale, 700, 20);
  
  //set(0, 0, cam);
  dataLog.add();
}

int fpsTimer = 0, fpsElapsedTimer, prevFrameTime, frameCounter = 0, frames = 0;
public void showFPS(){
  colorMode(HSB, 360, 1, 1);
  fill( 120, 1, 1 );
  //textFont( arial );
  textSize( 15 );
  textAlign( RIGHT );  
  text( frames, width - 5, 15 );
  textAlign(LEFT);
  frameCounter++;
  if( millis() - fpsTimer > 1000 ){  
    fpsTimer = millis();
    frames = frameCounter; 
    if( frames == 59 ) frames = 60;
    frameCounter = 0;
  }
  colorMode(RGB, 255, 255, 255, 255);
}

public boolean saveSettings() {
  ArrayList<String> outStr = new ArrayList<String>(); 
  outStr.add("imgScale:" + videoFeed.imgScale);
  
  return true;
}
float xPos = 0, yPos = 0, xOffset = 0, yOffset = 0;
float quadScale = 1;
boolean first = true;
int delayTime = 250;
DelayedFloat2 sensorDelay = new DelayedFloat2(delayTime);

public void serialEvent(Serial myPort) {
  String dataIn = port.readStringUntil('\n'); 
  if(first) {
    first = false;
    return;
  }
  String[] xy = dataIn.split(" ");
  float xIn = PApplet.parseFloat(xy[0])*quadScale;
  float yIn = PApplet.parseFloat(xy[1])*quadScale;
  xPos = xIn*0.1f + xPos*0.9f;
  yPos = yIn*0.1f + yPos*0.9f;
  
  sensorDelay.add((xPos - xOffset)/quadScale, (yPos - yOffset)/quadScale, millis());
  //sensorPosNoOff.set((xPos - xOffset)/quadScale, (yPos - yOffset)/quadScale);
  Float2 delayedPos = sensorDelay.get();
  if(delayedPos != null) {
    sensorPosNoOff.set(delayedPos.x, delayedPos.y); 
  } else {
    //sensorPosNoOff.set(0, 0); 
  }
  map.setDot(xPos - xOffset, yPos - yOffset);
}

public void centerSensor(){
  xOffset = xPos;
  yOffset = yPos;
}

int calibrationTimer;
boolean initialCalibrated = false;
public void checkCalibration() {
  if(!initialCalibrated && calibrationTimer - millis() > 1000) {
    calibrate();
    initialCalibrated = true;
  }
}

public void calibrate() {
  centerSensor();
  videoFeed.centerImage(); 
}
class HSIColor{
  public float h = 0, s = 0, i = 0, a = 1;
  boolean alphaSet = false;
  
  HSIColor( float hVal, float sVal, float iVal ){
    h = hVal;
    s = sVal;
    i = iVal;
  }
  
  HSIColor( float hVal, float sVal, float iVal, float aVal ){
    h = hVal;
    s = sVal;
    i = iVal;
    a = aVal;
    if( a < 1 ) alphaSet = true;
  } 
  
  HSIColor( float[] array ){
    this( array[0], array[1], array[2], array[3] ); //hope you don't get an out of bounds error  
  }
  
  public void setFill(){
    if( !alphaSet ) fill( h, s, i ); 
    else fill( h, s, i, a );
  }
  
  public void setStroke(){
    if( !alphaSet ) stroke( h, s, i );
    else stroke( h, s, i, a ); 
  }
  
  public HSIColor clone(){
    HSIColor retVal;
    if( !alphaSet ) retVal = new HSIColor( h, s, i );
    else retVal = new HSIColor( h, s, i, a );
    return retVal;
  }
  
}
class DelayedFloat2 {
  int delay; //ms
  ArrayList<Entry> array = new ArrayList<Entry>();
  DelayedFloat2(int ms) {
    delay = ms; 
  }
  
  public void add(float x, float y, int t) {
    //println("add:" + x + "," + y);
    array.add(new Entry(x, y, t)); //println("Added, size =" + array.size());
  }
  
  public Float2 get() {
    if(array.size() == 0) return null;
    int curTime = millis();
    Entry entry = null;
    while(curTime - array.get(0).time > delay) {
      entry = array.remove(0);
      //println("Added, size =" + array.size());
    }
    if(entry == null){
      return null;
    } 
    //println(curTime + "," + entry.time + "\t" + entry.data.x + "," + entry.data.y);
    return new Float2(entry.data.x, entry.data.y);
  }
  
  private class Entry{
    Float2 data;
    int time;
    Entry(float x, float y, int t) {
       data = new Float2(x, y);
       time = t;
    }
  }
}
class Log {
  ArrayList<String> strings = new ArrayList<String>();
  boolean isLogging = false;
  String header = "Time\tSensor X raw\tSensor Y raw\tSensor X (mapped)\tSensor Y (mapped)\t" +
                  "Pillar X(Cam)\tPillar Y(Cam)\tPlatform X\tPlatform Y";
  int logStartTime = 0;
  int sampleCount;
  Log() {
    
  }
  
  public void start() {
    ArrayList<String> strings = new ArrayList<String>();
    this.add(header);
    isLogging = true;
    logStartTime = millis();
    sampleCount = 0;
  }
  
  public void stop() {
     isLogging = false;
  }
  
  public void save(String addr) {
    String[] saveStr = strings.toArray(new String[0]);
    saveStrings(addr, saveStr);
  }
  
  public void add(String add){
    strings.add(add); 
    sampleCount++;
  }
  
  //use default logging setup
  public void add() {
    if(isLogging) {
      String timeStr = "" + (millis() - logStartTime);
      String rawStr = "\t" + sensorPosNoOff.x + "\t" + sensorPosNoOff.y;
      String mapStr;
      if(mappedOut == null) {
        mapStr = "\t0.0\t0.0";
      } else mapStr = "\t" + mappedOut.x + "\t" + mappedOut.y;
      
      String tipStr = "\t" + sensorToActual.outPos.x + "\t" + sensorToActual.outPos.y;
      String platStr = "\t" + videoFeed.plateFracPos.x + "\t" + videoFeed.plateFracPos.y;
      
      String outStr = timeStr + rawStr + mapStr + tipStr + platStr;
      this.add(outStr);
    }
  }
  
  
}
class Map{
  int xPos, yPos, size;
  float dotX, dotY;
  Map(int x, int y, int s){
    xPos = x;
    yPos = y;
    size = s;
  }
  
  public void setDot(float x, float y){
    dotX = x;
    dotY = y;
  }
  
  public void display(){
     colorMode(HSB, 360, 1, 1, 1);
     fill(0, 0, 0);
     stroke(0, 0, 1);
     strokeWeight(1);
     rectMode(CENTER);
     rect(xPos, yPos, size, size);
     line(xPos, yPos - size/2, xPos, yPos + size/2);
     line(xPos - size/2, yPos, xPos + size/2, yPos);
     noFill();
     strokeWeight(3);
     ellipse(xPos + size*dotX/2, yPos - size*dotY/2, 6, 6);
     fill(0, 0, 1);
     strokeWeight(1);
     textSize(20);
     textAlign(LEFT);
     String strX = "X:" + (int)(dotX*100) + "%";
     String strY = "Y:" + (int)(dotY*100) + "%";
     text(strX, xPos - size/2 + 10, yPos + size/2 + 20);
     text(strY, xPos + 10, yPos + size/2 + 20);
     colorMode(RGB, 255, 255, 255, 255);
  }
  
  public void displayBox() {
     colorMode(HSB, 360, 1, 1, 1);
     fill(0, 0, 0);
     stroke(0, 0, 1);
     strokeWeight(1);
     rectMode(CENTER);
     rect(xPos, yPos, size, size);
     line(xPos, yPos - size/2, xPos, yPos + size/2);
     line(xPos - size/2, yPos, xPos + size/2, yPos);
     colorMode(RGB, 255, 255, 255, 255);
  }
  
  public void displayDot(){
     colorMode(HSB, 360, 1, 1, 1);
     fill(0, 0, 1);
     noFill();
     strokeWeight(3);
     rect(xPos + size*dotX/2, yPos - size*dotY/2, 12, 12);
     fill(0, 0, 1);
     strokeWeight(1);
     colorMode(RGB, 255, 255, 255, 255);
  }
  
  public Float2 getScreenPos(float x, float y) {
    return new Float2(x*size/2 + xPos, -y*size/2 + yPos);
  }
}
class Calibrator {
  float tolerance;
  int gridSize;
  Mapping mapping;
  Rectangle screenRect;
  boolean logMode = false, mouseMode = false;
  int[] activeIndex, hoverIndex, logIndex, prevHoverIndex;
  ArrayList<Float2> log;
  Float2 inPos, outPos, inMax, outMax;
  Float2[][] gridFrac;
  int hoverTime = 0, prevHoverTime = 0, hoverTimerStart, logStart = 1000, logTime = 1000, logTimer = 0;
  boolean isLogging = false;
  float logFrac = 0;
  
  int messageTimer = 0;
  String messageString = "";
  
  float outDispScale = 2;
  
  Calibrator(int gs, float imx, float imy, float omx, float omy, float tol, Rectangle sr) {
    inMax = new Float2(imx, imy);
    outMax = new Float2(omx, omy);
    gridSize = gs;
    tolerance = tol;
    screenRect = sr;
    inPos = new Float2(0, 0);
    outPos = new Float2(0, 0);
    
    activeIndex = new int[2];
    hoverIndex = new int[2];
    logIndex = new int[2];
    setActive(-1, -1);
    setHover(-1, -1);
    setLogging(-1, -1);
    mapping = new Mapping(gs, omx, omy);
    gridFrac = new Float2[gs][gs];
    for(int iy=0; iy<gridSize; iy++) {
      for(int ix=0; ix<gridSize; ix++) {
        float unitSpace = 1.0f/gridSize;
        float xFrac = ((ix + 0.5f) - PApplet.parseFloat(gridSize)/2)*2*unitSpace;
        float yFrac = ((iy + 0.5f) - PApplet.parseFloat(gridSize)/2)*2*unitSpace;
        gridFrac[ix][iy] = new Float2(xFrac, yFrac);
        //println(xFrac + " "+ yFrac);
        //print(
      }
    }
  }
  
  public void setLogMode(boolean mode) {
    logMode = mode; 
  }
  
  public boolean toggleLogMode() {
    logMode = !logMode;
    return logMode;
  }
  
  public void setActive(int x, int y) {
    activeIndex[0] = x;
    activeIndex[1] = y;
  }
  
  public void setHover(int x, int y) {
    hoverIndex[0] = x;
    hoverIndex[1] = y;
  }
  
  public void setLogging(int x, int y) {
    logIndex[0] = x;
    logIndex[1] = y;
  }
  
  public boolean isHovering() {
    return hoverIndex[0] > 0 && hoverIndex[0] < gridSize && hoverIndex[1] > 0 && hoverIndex[1] < gridSize;
  }
  
  public boolean isActive() {
    return activeIndex[0] > 0 && activeIndex[0] < gridSize && activeIndex[1] > 0 && activeIndex[1] < gridSize;
  }
  
  private boolean isWithin(float x, float y) {
    int[] val = pointToIndex(x, y);
    return val[0] != -1 && val[1] != -1;
  }
  
  private int[] pointToIndex(float x, float y) {
    float gap = screenRect.boxW/(gridSize + 1);
    float margin = gap*tolerance;
    int[] retval = new int[]{-1,-1};
    for(int iy=0; iy<gridSize; iy++) {
      for(int ix=0; ix<gridSize; ix++) {
        float pointX = screenRect.x1 + (ix + 1)*gap;
        float pointY = screenRect.y1 + (iy + 1)*gap;
        if(abs(x - pointX) < gap/2 && abs(y - pointY) < gap/2) {
          retval[0] = ix;
          retval[1] = iy;
          return retval;
        }
      }
    }
    return retval;
  }
  
  private int[] fracToIndex(float x, float y, float tol) {
    float gap = 2.0f/gridSize;
    float margin = gap/2*tol;
    int[] retval = new int[]{-1,-1};
    for(int iy=0; iy<gridSize; iy++) {
      for(int ix=0; ix<gridSize; ix++) {
        if(abs(x - gridFrac[ix][iy].x) < margin && abs(y - gridFrac[ix][iy].y) < margin) {
          retval[0] = ix;
          retval[1] = iy;
          return retval;
        }
      }
    }
    return retval;
  }
  
  private int[] fracToIndex(float x, float y) {
    return fracToIndex(x, y, 1.0f);
  }
  
  public void save(String fileName) {
    messageString = "Mapping saved as:" + fileName;
    messageTimer = millis() + 3000;
    mapping.saveToFile(fileName);
  }
  
  public void load(String fileName) {
    messageString = "Loading mapping from:" + fileName;
    messageTimer = millis() + 3000;
    mapping.loadFromFile(fileName);
  }
  
  //use mouse position for reference
  public void mouseUpdate() {
    float hw = screenRect.boxW/2; //half width
    float xIn = (mouseX - screenRect.x)/hw;
    float yIn = (mouseY - screenRect.y)/hw;
    
    xIn = constrain(xIn*inMax.x, -inMax.x, inMax.x);
    yIn = constrain(yIn*inMax.x, -inMax.x, inMax.x);
    update(xIn, yIn, xIn, yIn);
  }
  
  public void update(float xin, float yin, float xout, float yout) {
    if(mouseMode) {
      if(screenRect.isWithin(mouseX, mouseY)) {
        float hw = screenRect.boxW/2; //half width
        xin = (mouseX - screenRect.x)/hw;
        yin = (mouseY - screenRect.y)/hw;
      } else if(outGridRect.isWithin(mouseX, mouseY)) {
        float hw = outGridRect.boxW/2; //half width
        xin = (mouseX - outGridRect.x)/hw;
        yin = (mouseY - outGridRect.y)/hw;
      } else {
        xin = 0;
        yin = 0;
      }
    
      xin = constrain(xin*inMax.x, -inMax.x, inMax.x);
      yin = constrain(yin*inMax.x, -inMax.x, inMax.x);
      //xout = xin;
      //yout = yin;
    }
    
    
    inPos.set(xin, yin);
    outPos.set(xout, yout);
    int[] index = fracToIndex(xin, yin, tolerance);
    //println("update:x="+index[0]+" y="+index[1]);
    if(index[0] != -1 && index[1] != -1) {
      if(hoverTime == 0) {
        hoverTimerStart = millis();
        hoverTime = 1;
        prevHoverTime = 0;
      } else {
        prevHoverTime = hoverTime;
        hoverTime = millis() - hoverTimerStart;
        int logEnd = logStart + logTime;
        //println(hoverTime + " t:" + logEnd + " logstart:" + (isLogging && hoverTime <= logEnd));
        if(prevHoverTime < logStart && hoverTime >= logStart && !mapping.isMapped(index[0], index[1]) && logMode) {
          //begin logging
          logTimer = 1;
          setLogging(index[0], index[1]);
          logFrac = 0;
          isLogging = true;
          log = new ArrayList<Float2>();
          log.add(new Float2(xout, yout));
          println("START LOG:" + index[0] + "," + index[1]);
          
        } else if (isLogging && (hoverTime >= logStart) && (hoverTime <= logEnd)) {
          //currently logging;
          log.add(new Float2(xout, yout));
          logFrac = PApplet.parseFloat(hoverTime - logStart)/PApplet.parseFloat(logTime);
          println(logFrac);
        } else if (isLogging && prevHoverTime < logEnd && hoverTime > logEnd ) { 
          //done logging
          float sumX = 0, sumY = 0;
          for(int i = 0; i < log.size(); i++) {
            Float2 sample = log.get(i);
            sumX += sample.x;
            sumY += sample.y;
          }
          float aveX = sumX/log.size();
          float aveY = sumY/log.size();
          mapping.setMapping(index[0], index[1], gridFrac[index[0]][index[1]].x * inMax.x, gridFrac[index[0]][index[1]].y * inMax.y, aveX, aveY);
          isLogging = false;
        }
      }
      setHover(index[0], index[1]); 
    } else {
      setHover(-1, -1); 
      hoverTime = 0; 
      //logTime = 0;
      logIndex[0] = -1;
      logIndex[1] = -1;
      isLogging = false;
    }
    mappedOut = mapping.map(inPos.x, inPos.y);
  } 
  
  public void display() {
    float gap = screenRect.boxW/(gridSize + 1);
    colorMode(HSB, 360, 1, 1);
    fill(0, 0, 0);
    strokeWeight(3);
    stroke(0, 0, 1);
    screenRect.drawRect();
    strokeWeight(1);
    
    for(int iy=0; iy<gridSize; iy++) {
      for(int ix=0; ix<gridSize; ix++) {
        //float pointX = screenRect.x1 + (ix + 1)*gap;
        //float pointY = screenRect.y1 + (iy + 1)*gap;
        float pointX = screenRect.x + gridFrac[ix][iy].x*screenRect.boxW/2;
        float pointY = screenRect.y + gridFrac[ix][iy].y*screenRect.boxW/2;
        //stroke coloring
        if(ix == hoverIndex[0] && iy == hoverIndex[1]) {
          strokeWeight(5);
          stroke(0, 0, 0.9f);
        } else {
          stroke(0, 0, 0.5f); 
          strokeWeight(2);
        }
        
        if(mapping.isMapped(ix, iy)) {
          fill(110, 1, 1); 
        } else {
          fill(35, 1, 1);
        }        
        ellipse(pointX, pointY, 12, 12);
        strokeWeight(1);
        
        if(isLogging && logIndex[0] == ix && logIndex[1] == iy) {
          noFill();
          strokeWeight(3);
          stroke(110, 1, 1);
          arc(pointX, pointY, 25, 25, -PI/2, -PI/2 + PI*2*logFrac);
          strokeWeight(1);
        }
      }
    }
   strokeWeight(3);
   stroke(0, 1, 1);
   noFill();
   ellipse(inPos.x*screenRect.boxW/2 + screenRect.x, inPos.y*screenRect.boxW/2 + screenRect.y, 8, 8);
   
   if(messageTimer > millis()) {
     fill(130, 1, 1);
     textSize(18);
     textAlign(LEFT);
     text(messageString, screenRect.x1, screenRect.y1 - 10);
   }
   fill(130, 0, 1);
   textSize(18);
   textAlign(LEFT);
   String modeStr, inputStr;
   if(logMode) modeStr = "Calibrating";
   else modeStr = "Mapping";
   text("Mode:" + modeStr, screenRect.x1, screenRect.y2 + 20);
   
   if(mouseMode) inputStr = "Mouse";
   else inputStr = "Sensor";
   text("Input:" + inputStr, screenRect.x, screenRect.y2 + 20);
   
   //======================
   /*if(mappedOut != null) {
     stroke(180, 1, 1);
     noFill();
     rectMode(CENTER);
     Float2 mappedOutDisp = new Float2(mappedOut.x*screenRect.boxW/2*outDispScale, mappedOut.y*screenRect.boxW/2*outDispScale);
     rect(mappedOutDisp.x + screenRect.x, mappedOutDisp.y + screenRect.y, 20, 20);
   } else {
     text("NULL", screenRect.x2, screenRect.y1 - 20);
   }*/
   strokeWeight(1);
   //======================
   colorMode(RGB, 255, 255, 255, 255);
  }
  
  public void displayPoints(Rectangle rect) {
   colorMode(HSB, 360, 1, 1);
   stroke(0, 1, 1);
   strokeWeight(2);
   //sensor position
   float xPos = inPos.x*rect.boxW/2 + rect.x;
   float yPos = inPos.y*rect.boxW/2 + rect.y;
   int crossWidth = 5;
   line( xPos - crossWidth, yPos - crossWidth, xPos + crossWidth, yPos + crossWidth );
   line( xPos - crossWidth, yPos + crossWidth, xPos + crossWidth, yPos - crossWidth );
   //rect(outPos.x*screenRect.boxW/2 + screenRect.x, outPos.y*screenRect.boxW/2 + screenRect.y, 20, 20);
   
   //mappedPosition
   if(mappedOut != null) {
     stroke(120, 1, 1);
     noFill();
     float mappedXpos = mappedOut.x*rect.boxW/2*outDispScale + rect.x;
     float mappedYpos = mappedOut.y*rect.boxW/2*outDispScale + rect.y;
     crossWidth = 5;
     line( mappedXpos - crossWidth, mappedYpos - crossWidth, mappedXpos + crossWidth, mappedYpos + crossWidth );
     line( mappedXpos - crossWidth, mappedYpos + crossWidth, mappedXpos + crossWidth, mappedYpos - crossWidth );
   } 
   
   //video posistion
   strokeWeight(2);
   stroke(180, 1, 1);
   noFill();
   float outXpos = outPos.x*rect.boxW/2*outDispScale + rect.x;
   float outYpos = outPos.y*rect.boxW/2*outDispScale + rect.y;
   rectMode(CENTER);
   rect(outXpos, outYpos, 15, 15); 
   strokeWeight(1);
   
   if(videoFeed.plateDetectOn) {
     //plate pos
     strokeWeight(2);
     stroke(45, 1, 1);
     noFill();
     float plateXpos = videoFeed.plateFracPos.x*rect.boxW/2*outDispScale + rect.x;
     float plateYpos = videoFeed.plateFracPos.y*rect.boxW/2*outDispScale + rect.y;
     rectMode(CENTER);
     rect(plateXpos, plateYpos, 15, 15); 
     strokeWeight(1);
   }
   colorMode(RGB, 255, 255, 255, 255);
  }
  
  public void displayGrid(Rectangle rect) {
    colorMode(HSB, 360, 1, 1);
    //src grid
    for(int iy = 0; iy < gridSize - 1; iy++) {
      for(int ix = 0; ix < gridSize - 1; ix++) {
        float linex1, liney1, linex2, liney2;
        stroke(0, 1, 1);
        if(mapping.isMapped(ix, iy) && mapping.isMapped(ix, iy + 1) ){
           linex1 = mapping.src[ix][iy].x * rect.boxW/2 + rect.x;
           liney1 = mapping.src[ix][iy].y * rect.boxH/2 + rect.y;
           linex2 = mapping.src[ix][iy + 1].x * rect.boxW/2 + rect.x;
           liney2 = mapping.src[ix][iy + 1].y * rect.boxH/2 + rect.y;
           line(linex1, liney1, linex2, liney2);
        }
        
        if(mapping.isMapped(ix, iy) && mapping.isMapped(ix + 1, iy) ){
           linex1 = mapping.src[ix][iy].x * rect.boxW/2 + rect.x;
           liney1 = mapping.src[ix][iy].y * rect.boxH/2 + rect.y;
           linex2 = mapping.src[ix + 1][iy].x * rect.boxW/2 + rect.x;
           liney2 = mapping.src[ix + 1][iy].y * rect.boxH/2 + rect.y;
           line(linex1, liney1, linex2, liney2);
        }
      }
    }
    //dest grid
    for(int iy = 0; iy < gridSize - 1; iy++) {
      for(int ix = 0; ix < gridSize - 1; ix++) {
        float linex1, liney1, linex2, liney2;
        stroke(120, 1, 1);
        if(mapping.isMapped(ix, iy) && mapping.isMapped(ix, iy + 1) ){
           linex1 = mapping.dest[ix][iy].x * rect.boxW/2 * outDispScale + rect.x;
           liney1 = mapping.dest[ix][iy].y * rect.boxH/2 * outDispScale + rect.y;
           linex2 = mapping.dest[ix][iy + 1].x * rect.boxW/2 * outDispScale + rect.x;
           liney2 = mapping.dest[ix][iy + 1].y * rect.boxH/2 * outDispScale + rect.y;
           line(linex1, liney1, linex2, liney2);
        }
        
        if(mapping.isMapped(ix, iy) && mapping.isMapped(ix + 1, iy) ){
           linex1 = mapping.dest[ix][iy].x * rect.boxW/2 * outDispScale + rect.x;
           liney1 = mapping.dest[ix][iy].y * rect.boxH/2 * outDispScale + rect.y;
           linex2 = mapping.dest[ix + 1][iy].x * rect.boxW/2 * outDispScale + rect.x;
           liney2 = mapping.dest[ix + 1][iy].y * rect.boxH/2 * outDispScale + rect.y;
           line(linex1, liney1, linex2, liney2);
        }
      }
    }
  
    displayLegend(rect);
    colorMode(RGB, 255, 255, 255, 255);
  }
  
  public void displayLegend(Rectangle rect) {
    colorMode(HSB, 360, 1, 1, 1);
    
    textSize(15);
    textAlign(LEFT);
    fill(0, 1, 1);
    text("Sensor Position", rect.x1 + 10, rect.y1 + 15);
    fill(120, 1, 1);
    text("Predicted Position", rect.x1 + 10, rect.y1 + 35);
    fill(180, 1, 1);
    text("Actual (video) Position", rect.x1 + 10, rect.y1 + 55);
    fill(45, 1, 1);
    text("Plate Position", rect.x1 + 10, rect.y1 + 75);
    
    //========LOGGING======
    if(dataLog.isLogging) {
      textSize(20);
      fill(0, 0, 1);
      text("Logging (" + dataLog.sampleCount + ")", rect.x1 + 10, rect.y1 - 20);    
    }
    //=====================
    colorMode(RGB, 255, 255, 255, 255);
  }
}

class Mapping {
  int gridSize;
  float inMax, outMax;
  boolean[][] isMapped;
  Float2[][] src, dest, gridFrac;
  boolean isComplete = false;
  
  Mapping(int dim, float inm, float outm) {
    gridSize = dim;
    src = new Float2[dim][dim];
    dest = new Float2[dim][dim];
    isMapped = new boolean[dim][dim];
    inMax = inm;
    outMax = outm;
    generateGridFrac(dim);
    
  }
  
  private void generateGridFrac(int gs) {
    gridFrac = new Float2[gs][gs];
    for(int iy=0; iy<gridSize; iy++) {
      for(int ix=0; ix<gridSize; ix++) {
        float unitSpace = 1.0f/gridSize;
        float xFrac = ((ix + 0.5f) - PApplet.parseFloat(gridSize)/2)*2*unitSpace;
        float yFrac = ((iy + 0.5f) - PApplet.parseFloat(gridSize)/2)*2*unitSpace;
        gridFrac[ix][iy] = new Float2(xFrac, yFrac);
        //println(xFrac + " "+ yFrac);
        //print(
      }
    }
  }
  
  public Float2 map(float x, float y) {
    //x = x/inMax;
    //y = y/inMax; //normalize inputs
    Float2 Q11, Q12, Q21, Q22; //four points
    float x1, x2, y1, y2, xfrac = 0, yfrac = 0;
    //get indexes
    int ix = -1, iy = -1;
    for(int i=0; i < gridSize - 1; i++) {
      if(gridFrac[0][i].y < x && gridFrac[0][i+1].y >= x) {
        ix = i;
        x1 = gridFrac[0][i].y;
        x2 = gridFrac[0][i+1].y;
        xfrac = (x - x1)/(x2 - x1);
      }
      if(gridFrac[0][i].y < y && gridFrac[0][i+1].y >= y) {
        iy = i;
        y1 = gridFrac[0][i].y;
        y2 = gridFrac[0][i+1].y;
        yfrac = (y - y1)/(y2 - y1);
      }
    }
    
    if(ix >= 0 && iy >= 0) {
      if(isMapped(ix, iy) && isMapped(ix + 1, iy) && isMapped(ix, iy + 1) && isMapped(ix + 1, iy + 1)) {
        //get 4 dest points
        Q11 = new Float2(dest[ix][iy].x, dest[ix][iy].y);
        Q12 = new Float2(dest[ix][iy + 1].x, dest[ix][iy + 1].y);
        Q21 = new Float2(dest[ix + 1][iy].x, dest[ix + 1][iy].y);
        Q22 = new Float2(dest[ix + 1][iy + 1].x, dest[ix + 1][iy + 1].y);
        float yInt, xInt; //interpolated vars
        xInt = billinearInterpolate(xfrac, yfrac, Q11.x, Q12.x, Q21.x, Q22.x);
        yInt = billinearInterpolate(xfrac, yfrac, Q11.y, Q12.y, Q21.y, Q22.y);
        return new Float2(xInt, yInt);
      }
    }
    return null;
  }
  
  private float billinearInterpolate(float xfrac,float yfrac, float q11, float q12, float q21, float q22) {
     float f1 = (1.0f - xfrac)*q11 + (xfrac)*q21;
     float f2 = (1.0f - xfrac)*q12 + (xfrac)*q22;
     
     float f = (1.0f - yfrac)*f1 + (yfrac)*f2;
     return f;
  }
  
  public void setMapped(int x, int y, boolean val){
    isMapped[x][y] = val;
  }
  
  public void setMapping(int ix, int iy, float xin, float yin, float xout, float yout) {
     src[ix][iy] = new Float2(xin, yin);
     dest[ix][iy] = new Float2(xout, yout);
     setMapped(ix, iy, true);
  }
  
  public boolean isMapped(int x, int y) {
    return isMapped[x][y]; 
  }
  
  public void saveToFile(String fileName) {
    ArrayList<String> output = new ArrayList<String>();
    output.add("Size:" + gridSize);
    output.add("Max:" + inMax + "," + outMax);
    output.add("Points:");
    for(int iy = 0; iy < gridSize; iy++) {
      for(int ix = 0; ix < gridSize; ix++) {
        String index = ix + "," + iy + ":";
        String mapLine = "N:";
        String outLine;
        if(isMapped(ix, iy)) {
          mapLine = "Y:";
          String srcLine = src[ix][iy].x + "," + src[ix][iy].y + ":";
          String destLine = dest[ix][iy].x + "," + dest[ix][iy].y;
          outLine = mapLine + index + srcLine + destLine;
        } else {
          outLine = mapLine + index;
        }
        output.add(outLine);
      }
    }
    output.add("End Points");
    String[] saveStr = output.toArray(new String[0]);
    saveStrings(fileName, saveStr);
  }
  
  public void loadFromFile(String fileName) {
    String[] str = loadStrings(fileName);
    gridSize = Integer.parseInt(str[0].split(":")[1]);
    String[] limits = str[1].split(":")[1].split(",");
    inMax = PApplet.parseFloat(limits[0]);
    outMax = PApplet.parseFloat(limits[1]);
    
    int dim = gridSize; //lazy
    src = new Float2[dim][dim];
    dest = new Float2[dim][dim];
    isMapped = new boolean[dim][dim];
    generateGridFrac(dim);
    
    int index = 1;
    while(!str[index].equals("Points:")) {
      index++; 
    }
    index++;
    while(!str[index].equals("End Points")) {
      String[] inStrings = str[index].split(":");
      if(inStrings[0].equals("Y")) {
        String[] indexes = inStrings[1].split(",");
        String[] srcs = inStrings[2].split(",");
        String[] dests = inStrings[3].split(",");
        int ix = Integer.parseInt(indexes[0]);
        int iy = Integer.parseInt(indexes[1]);
        
        src[ix][iy] = new Float2(PApplet.parseFloat(srcs[0]), PApplet.parseFloat(srcs[1]));
        dest[ix][iy] = new Float2(PApplet.parseFloat(dests[0]), PApplet.parseFloat(dests[1]));
        isMapped[ix][iy] = true;
      }
      index++;
    }
  }
}
class Rectangle {
  float x1,y1,x2,y2;
  float x,y,boxW, boxH;  
  float defaultW, defaultH;
  //int type = CORNERS;
  
  Rectangle( float var1, float var2, float var3, float var4, int rectType ){
    set( var1, var2, var3, var4, rectType );
    defaultW = boxW;
    defaultH = boxH;
  }
  
  public void setDefualtDimensions( float w, float h ){
    defaultW = w;
    defaultH = h;
  }
  
  public void set( float var1, float var2, float var3, float var4, int rectType ){
    if( rectType == CENTER ){
      x = var1;
      y = var2;
      boxW = var3;
      boxH = var4;
      x1 = x - boxW/2;
      x2 = x + boxW/2;
      y1 = y - boxH/2;
      y2 = y + boxH/2;
    } else if( rectType == CORNERS ){
      x1 = var1;
      y1 = var2;
      x2 = var3; 
      y2 = var4;
      x = (x2 + x1)/2;
      y = (y2 + y1)/2;
      boxW = x2 - x1;
      boxH = y2 - y1;
    } else if( rectType == CORNER ){
      x1 = var1;
      y1 = var2;
      boxW = var3;
      boxH = var4;
      x2 = x1 + boxW;
      y2 = y1 + boxH;
      x = (x2 + x1)/2;
      y = (y2 + y1)/2; 
    }
  }
  
  public void translate( float translateX, float translateY ){
     x1 += translateX;
     x2 += translateX;
     x += translateX;
     y1 += translateY;
     y2 += translateY;
     y += translateY;
  }
  
  public void setDimensions( float newW, float newH ){
    this.set( x, y, newW, newH, CENTER ); 
  }
  
  public void setCenter( float xPos, float yPos ){
    set( xPos, yPos, boxW, boxH, CENTER );
  } 
  
  public void setCorner( float x1, float y1 ){
    set( x1, y1, boxW, boxH, CORNER ); 
  }
  
  //rescales the rectangle around the center
  public void reScale( float scaleFactor ){
    boxW *= scaleFactor;
    boxH *= scaleFactor;
    x1 = x - boxW/2;
    x2 = x + boxW/2;
    y1 = y - boxH/2;
    y2 = y + boxH/2;
  }
  
  public void setScale( float scaleFactor ){
    boxW = defaultW*scaleFactor;
    boxH = defaultH*scaleFactor;
    x1 = x - boxW/2;
    x2 = x + boxW/2;
    y1 = y - boxH/2;
    y2 = y + boxH/2;
  }
  
  public void rescale( float focusX, float focusY, float scaleFactor ){
    
  }
  
  public void printInfo(){
    println( "x:"+x+" y:"+y+" w:"+boxW+" h:"+boxH+" x1:"+x1+" y1:"+y1+" x2:"+x2+" y2:"+y2);
  }
  
  public void drawBorder( float xPos, float yPos, float hue ){
    stroke( hue, 1, 1 );
    noFill();
    rectMode( CORNER );
    rect( xPos, yPos, boxW, boxH );
  }
  
  public boolean isWithin( float pointX, float pointY ){
    return (pointX>=this.x1 && pointX<=this.x2 && pointY>=y1 && pointY<=y2); 
  }
  
  public boolean isWithin( int pointX, int pointY ){
    return (pointX>=this.x1 && pointX<=this.x2 && pointY>=y1 && pointY<=y2); 
  }
  
  public void drawBorder( float hue ){
    stroke( hue, 1, 1 );
    noFill();
    rectMode( CORNER );
    rect( x1, y1, boxW, boxH );
  }
  
  public void drawBorder(){ //caller must set fill, stroke, strokeWeight
    rectMode( CORNER );
    rect( x1, y1, boxW, boxH); 
  }
  
  public void drawRect(){
    rectMode( CORNERS );
    rect( x1, y1, x2, y2 );
  }
  
  public void drawRelative(){
    rectMode( CORNERS );
    rect( x1*width, y1*height, x2*width, y2*height );
  }
  
  public void drawBorder( float weight, float hue ){
    strokeWeight( weight );
    stroke( hue, 1, 1 );
    noFill();
    rectMode( CENTER );
    rect( x, y, boxW - weight, boxH - weight );
    strokeWeight( 1 );
  }
  
  public Rectangle clone(){
    return new Rectangle( this.x, this.y, this.boxW, this.boxH, CENTER ); 
  } 
}

class Float2 {
  float x, y;
  Float2( float xIn, float yIn ){
    x = xIn;
    y = yIn;
  }
  
  public void set(float xIn, float yIn ){
    x = xIn;
    y = yIn;
  }
}
/*
float[] rectMap(Rectangle src, Rectangle dest, float xIn, float yIn){
  float[] retVal = new float[2];
  retVal[0] = (xIn - src
  return retVal;
}*/
public enum BUTTON_STATE{
    DEFAULT,HOVER,PRESSED
};

public enum BUTTON_TRIGGER{
  PRESSED,RELEASED,HELD;    
};

public enum BUTTON_TYPE{
  RECT,TEXT_EXPANDING,BORDER,CUSTOM
}

public enum UI_BUTTON_COLOR_SCHEME{
  DEFAULT            ( new float[][][]{ { {0, 0, 0.8f, 1},   {0, 0, 0.9f, 1},   {0, 0, 0.5f, 1} },
                                        { {0, 0, 0.35f, 1},  {0, 0, 0.35f, 1},  {0, 0, 0.35f, 1} },
                                        { {0, 0, 0, 1},     {0, 0, 0, 1},     {0, 0, 0, 1} } } ),
                                        
  SELECTOR_PRESSED   ( new float[][][]{ { {0, 0, 0.9f, 1},   {0, 0, 0.9f, 1},   {0, 0, 0.5f, 1} },
                                        { {0, 0, 0.35f, 1},  {0, 0, 0.35f, 1},  {0, 0, 0.35f, 1} },
                                        { {0, 0, 0, 1},     {0, 0, 0, 1},     {0, 0, 0, 1} } } ),
                                        
  SELECTOR_DEFAULT   ( new float[][][]{ { {0, 0, 0.5f, 1},   {0, 0, 0.6f, 1},  {0, 0, 0.4f, 1} },
                                        { {0, 0, 0.35f, 1},  {0, 0, 0.35f, 1},  {0, 0, 0.35f, 1} },
                                        { {0, 0, 0, 1},     {0, 0, 0, 1},     {0, 0, 0, 1} } } ),
                                        
  BORDER             ( new float[][][]{ { {0, 0, 0, 0 },   {0, 0, 0, 0 },  {0, 0, 0, 0.5f } },
                                        { {0, 0, 0, 0 },  {0, 0, 0, 0.8f },  {0, 0, 0, 0.8f} },
                                        { {0, 0, 0, 0},     {0, 0, 0, 0},     {0, 0, 0, 0} } } );

   
   public float[][][] colorArray;
   private UI_BUTTON_COLOR_SCHEME( float[][][] floatArray  ){
     colorArray = floatArray;                        
   }
   
   public float[][][] getArray(){
     return colorArray; 
   }
}

class UI_Button{
  private Rectangle relativeBox, box;
  private String text;
  private int prevWidth = 0, prevHeight = 0;
  private BUTTON_STATE state;
  private BUTTON_TRIGGER trigger = BUTTON_TRIGGER.RELEASED;
  private HSIColor[] fillColors, strokeColors, textColors;
  private float textSize;
  private BUTTON_TYPE type = BUTTON_TYPE.RECT;
  
  UI_Button( String txt, float var1, float var2, float var3, float var4, int rectMode ){
    state = BUTTON_STATE.DEFAULT;
    text = txt;
    relativeBox = new Rectangle( var1, var2, var3, var4, rectMode );
    box = new Rectangle( 0, 1, 2, 3, CENTER );
    setBox();
    setColorScheme( BUTTON_TYPE.RECT );    
  }
  
  UI_Button( String txt, float var1, float var2, float var3, float var4, int rectMode, BUTTON_TRIGGER trig ){
    this( txt, var1, var2, var3, var4, rectMode );
    trigger = trig; 
  }
  /*
  UI_Button( String txt, float var1, float var2, float var3, float var4, int rectMode, int colorScheme ){
    this( txt, var1, var2, var3, var4, rectMode );
    setColorScheme( colorScheme );
  } */
  
  UI_Button( String txt, float var1, float var2, float var3, float var4, int rectMode, BUTTON_TYPE newType ){
    this( txt, var1, var2, var3, var4, rectMode );
    type = newType;
    setColorScheme( type );
  }
  
  UI_Button( String txt, float var1, float var2, float var3, float var4, int rectMode, BUTTON_TYPE newType, UI_BUTTON_COLOR_SCHEME scheme ){
    this( txt, var1, var2, var3, var4, rectMode );
    type = newType;
    setColorScheme( scheme );
  }
  
  UI_Button( String txt, float var1, float var2, float var3, float var4, int rectMode, UI_BUTTON_COLOR_SCHEME scheme ){
    this( txt, var1, var2, var3, var4, rectMode );
    //type = newType;
    setColorScheme( scheme );
  }
  
  public void setTrigger( BUTTON_TRIGGER newTrig ){
    trigger = newTrig; 
  }
  
  public void reset(){ //reset state
    state = BUTTON_STATE.DEFAULT; 
  }
  
  public void setColorScheme( UI_BUTTON_COLOR_SCHEME scheme ){
    fillColors = new HSIColor[3];
    strokeColors = new HSIColor[3];
    textColors = new HSIColor[3];
    
    float[][][] array = scheme.getArray();
    
    for( int i=0; i<3; i++ ){
      fillColors[i] = new HSIColor( array[0][i] );
      strokeColors[i] = new HSIColor( array[1][i] );
      textColors[i] = new HSIColor( array[2][i] );
    }
  }
  
  public void setColorScheme( BUTTON_TYPE schemeType ){
    fillColors = new HSIColor[3];
    strokeColors = new HSIColor[3];
    textColors = new HSIColor[3];
    //default is all off
    fillColors[0] = new HSIColor( 0, 0, 0, 0 );
    fillColors[1] = new HSIColor( 0, 0, 0, 0 );
    fillColors[2] = new HSIColor( 0, 0, 0, 0 );
      
    strokeColors[0] = new HSIColor( 0, 0, 0, 0 );
    strokeColors[1] = new HSIColor( 0, 0, 0, 0 );
    strokeColors[2] = new HSIColor( 0, 0, 0, 0 );
      
    textColors[0] = new HSIColor( 0, 0, 0, 0 );
    textColors[1] = new HSIColor( 0, 0, 0, 0 );
    textColors[2] = new HSIColor( 0, 0, 0, 0 );
    
    if( schemeType == BUTTON_TYPE.BORDER ){ //empty box type, with gray press, no text color
      fillColors[0] = new HSIColor( 0, 0, 0, 0 );
      fillColors[1] = new HSIColor( 0, 0, 0, 0 );
      fillColors[2] = new HSIColor( 0, 0, 0, 0.5f );
      
      strokeColors[0] = new HSIColor( 0, 0, 0, 0 );
      strokeColors[1] = new HSIColor( 0, 0, 0.8f );
      strokeColors[2] = new HSIColor( 0, 0, 0.8f );
    } else if( schemeType == BUTTON_TYPE.TEXT_EXPANDING ){         //text only
      textColors[0] = new HSIColor( 0, 0, 0.7f );
      textColors[1] = new HSIColor( 0, 0, 1 );
      textColors[2] = new HSIColor( 0, 0, 0.4f );
    } else { //default type
      fillColors[0] = new HSIColor( 0, 0, 0.8f );
      fillColors[1] = new HSIColor( 0, 0, 0.9f );
      fillColors[2] = new HSIColor( 0, 0, 0.5f );
      
      strokeColors[0] = new HSIColor( 0, 0, 0.35f );
      strokeColors[1] = new HSIColor( 0, 0, 0.35f );
      strokeColors[2] = new HSIColor( 0, 0, 0.35f );
      
      textColors[0] = new HSIColor( 0, 0, 0 );
      textColors[1] = new HSIColor( 0, 0, 0 );
      textColors[2] = new HSIColor( 0, 0, 0 );
    }
  }
  
  public void setPosition( float xPos, float yPos ){
    box.setCenter( xPos, yPos ); 
  }
  
  private void setBox(){
    box.set( relativeBox.x1*width, relativeBox.y1*height, relativeBox.x2*width, relativeBox.y2*height, CORNERS );
  }
  
  public boolean isWithin( float xPos, float yPos ){
    return relativeBox.isWithin( xPos, yPos ); 
  }
  
  public boolean update(){
    if( prevWidth != width || prevHeight != height ){ //check for window resizes
      setBox();
    }
    prevWidth = width;
    prevHeight = height;
    
    boolean triggerFlag = false;
    
    if( box.isWithin( mouseX, mouseY ) ){
      if( mousePressed && mouseButton == LEFT ){
        if( state != BUTTON_STATE.PRESSED && trigger == BUTTON_TRIGGER.PRESSED ) triggerFlag = true; //pressed trigger
        if( state == BUTTON_STATE.HOVER ) state = BUTTON_STATE.PRESSED; //can only transition to pressed from hover
      } else {
        if( state == BUTTON_STATE.PRESSED && trigger == BUTTON_TRIGGER.RELEASED ) triggerFlag = true; //released trigger
        if( state == BUTTON_STATE.DEFAULT && type == BUTTON_TYPE.TEXT_EXPANDING ); //UISounds.trigger( UI_SOUND.HOVER ); //trigger hover sound for text buttons
        state = BUTTON_STATE.HOVER;
        if( trigger == BUTTON_TRIGGER.HELD ) triggerFlag = true; //held trigger
      }
    } else {
      state = BUTTON_STATE.DEFAULT;
    }
    //if( triggerFlag ) UISounds.trigger( UI_SOUND.CLICK_SHORT );
    return triggerFlag;
  }
  
  public void display(){
    int stateIndex = 0;
    if( state == BUTTON_STATE.DEFAULT ) stateIndex = 0;
    else if( state == BUTTON_STATE.HOVER ) stateIndex = 1;
    else if( state == BUTTON_STATE.PRESSED ) stateIndex = 2;
    
    fillColors[stateIndex].setFill();
    strokeColors[stateIndex].setStroke();
    strokeWeight( 2 );
    box.drawRect();
    textAlign( CENTER, CENTER );
    //textFont( arial );
    textSize( box.boxH*0.7f );
    if( type == BUTTON_TYPE.TEXT_EXPANDING && state != BUTTON_STATE.DEFAULT ) textSize( box.boxH*0.75f*1.1f ); //slightly expand text if type is text expanding
    
    textColors[stateIndex].setFill();
    text( text, box.x, box.y - box.boxH*0.1f );
    strokeWeight( 1 );
  }
}
public void keyReleased(){
  if(key == ' ') {
    centerSensor();
    videoFeed.centerImage();
  }
  
  if(key == 's') {
    sensorToActual.save("sensor2actual.txt");
  }
  
  if(key == 'r') {
    sensorToActual.load("sensor2actual.txt");
  }
  
  if(key == 'l') {
    sensorToActual.toggleLogMode(); 
  }
  
  if(key == 'm') {
    sensorToActual.mouseMode = !sensorToActual.mouseMode; 
  }
  
  if(key == 'p') {
    videoFeed.plateDetectOn = !videoFeed.plateDetectOn; 
  }
  
  if(key == 'k') {
    if(!dataLog.isLogging) dataLog.start();
    else dataLog.stop();
  }
  
  if(key == 'j') {
    dataLog.stop();
    dataLog.save("log.txt");
  }
}

public void mouseWheel(MouseEvent event) {
  float e = event.getCount();
  if(e == 1){
    videoFeed.imgScale -= 1; 
  } else {
    videoFeed.imgScale += 1; 
  }
}
class VideoFeed {
  public Capture cam;
  public Detector bd, pd;
  private int vidW = 640, vidH = 480;
  public Rectangle screenRect, rawRect = null, plateRect = null, roi;
  public float threshold;
  private PImage frameRaw, framePlate, frame;
  
  private float imgXPos = 0, imgYPos = 0, imgXOffset = 0, imgYOffset = 0, plateX = 0, plateY = 0, plateXOffset = 0, plateYOffset = 0;
  private int imgScale = 30;
  private float xRatio, yRatio;
  private Float2 fracPos = new Float2(0, 0), plateFracPos = new Float2(0, 0); //normalized to screen height
  
  private boolean plateDetectOn = true;
  
  VideoFeed(PApplet pa, Rectangle screenPos, String cap) {
    bd = new Detector(pa, 255);
    pd = new Detector(pa, 255);
    screenRect = screenPos;
    xRatio = (screenRect.boxW/vidW);
    yRatio = (screenRect.boxH/vidH);
    int areaW = 300, areaH = 300;
    roi = new Rectangle(screenPos.x, screenPos.y, areaW, areaH, CENTER);
    bd.setRoi(320 - areaW/2, 240 - areaH/2, areaW, areaH); 
    pd.setRoi(320 - PApplet.parseInt(areaW/1.5f), 240 - PApplet.parseInt(areaH/1.5f), areaW, areaH); 
    cam = new Capture(pa, cap);
    cam.start();
  }
  
  public void setRawRect(Rectangle rect) {
    rawRect = rect; 
  }
  
  public void setPlateRect(Rectangle rect) {
    plateRect = rect; 
  }
  
  private void findPlatePos(Detector detector, PImage img) {
    detector.imageFindBlobs(img);
    detector.loadBlobsFeatures(); 
    strokeWeight(5);
    stroke(255, 0, 0);
    detector.findCentroids();
    if(detector.getBlobsNumber() > 0) {
      int id = getBiggestBlobIndex(detector); 
      //point(detector.getCentroidX(id), detector.getCentroidY(id));
      plateX = detector.getCentroidX(id)*0.4f + plateX*0.6f;
      plateY = detector.getCentroidY(id)*0.4f + plateY*0.6f;;
      //imageMap.setDot((imgXPos - imgXOffset)*(float)imgScale/10000.0,-(imgYPos - imgYOffset)*(float)imgScale/10000.0);
      plateFracPos.set((plateX - plateXOffset)*2/vidH, -(plateY - plateYOffset)*2/vidH);
      //println(fracPos.x + " " + fracPos.y);
      //fill(0,200,100);
      //text("x-> " + detector.getCentroidX(id) + "\n" + "y-> " + detector.getCentroidY(id), detector.getCentroidX(id), detector.getCentroidY(id)-7);
      /*println("Blob 0 has centroid's coordinates at x:" 
                  + detector.getCentroidX(0) 
                  + " and y:" 
                  + detector.getCentroidY(0)); */
      /*color boundingBoxCol = color(255, 0, 0);
      int boundingBoxThickness = 1;
      detector.drawBox(boundingBoxCol, boundingBoxThickness);*/
    }
    strokeWeight(1);
  }
  
  //cloned to save time, should merge into general purpose func which returns val
  private void findCentroids(Detector detector, PImage img) {
    detector.imageFindBlobs(img);
    detector.loadBlobsFeatures(); 
    strokeWeight(5);
    stroke(255, 0, 0);
    detector.findCentroids();
    if(detector.getBlobsNumber() > 0) {
      int id = getBiggestBlobIndex(detector); 
      //point(detector.getCentroidX(id), detector.getCentroidY(id));
      imgXPos = detector.getCentroidX(id)*0.4f + imgXPos*0.6f;
      imgYPos = detector.getCentroidY(id)*0.4f + imgYPos*0.6f;;
      imageMap.setDot((imgXPos - imgXOffset)*(float)imgScale/10000.0f,-(imgYPos - imgYOffset)*(float)imgScale/10000.0f);
      fracPos.set((imgXPos - imgXOffset)*2/vidH, -(imgYPos - imgYOffset)*2/vidH);
      //println(fracPos.x + " " + fracPos.y);
      fill(0,200,100);
      //text("x-> " + detector.getCentroidX(id) + "\n" + "y-> " + detector.getCentroidY(id), detector.getCentroidX(id), detector.getCentroidY(id)-7);
      /*println("Blob 0 has centroid's coordinates at x:" 
                  + detector.getCentroidX(0) 
                  + " and y:" 
                  + detector.getCentroidY(0)); */
      /*color boundingBoxCol = color(255, 0, 0);
      int boundingBoxThickness = 1;
      detector.drawBox(boundingBoxCol, boundingBoxThickness);*/
    }
    strokeWeight(1);
  }
  
  private void drawBoxes(Detector detector, Rectangle rect) {
    //colorMode(HSB, 360, 1, 1, 1);
    //stroke(boxColor);
    //strokeWeight(thickness);
    stroke(120, 1, 1);
    strokeWeight(1);
    noFill();
    PVector[] A = detector.getA();
    //PVector[] B = bd.getB();
    //PVector[] C = bd.getC();
    PVector[] D = detector.getD();
    
    int xOffset = (int)(rect.x1);
    int yOffset = (int)(rect.y1);
    //println(D[0].x*xRatio + xOffset + " " + D[0].y*yRatio + yOffset);
    if (A.length > 0)
      for (int i = 0; i < A.length; i++) {
        rectMode(CORNERS);
        rect(A[i].x*xRatio + xOffset, A[i].y*yRatio + yOffset, D[i].x*xRatio + xOffset, D[i].y*yRatio + yOffset);
        /*line(sroix + A[i].x, sroiy + A[i].y, sroix + B[i].x,
            sroiy + B[i].y);
        line(sroix + B[i].x, sroiy + B[i].y, sroix + D[i].x,
            sroiy + D[i].y);
        line(sroix + A[i].x, sroiy + A[i].y, sroix + C[i].x,
            sroiy + C[i].y);
        line(sroix + C[i].x, sroiy + C[i].y, sroix + D[i].x,
            sroiy + D[i].y);*/
      }
    strokeWeight(1);
    //colorMode(RGB, 255, 255, 255, 255);
  }

  public void centerImage(){
    imgXOffset = imgXPos;
    imgYOffset = imgYPos;
    plateXOffset = plateX;
    plateYOffset = plateY;
  }
  
  //void filterGreen(
  private int getBiggestBlobIndex(Detector d) {
    int nbrBlobs = d.getBlobsNumber();
    // If we have no blobs return
    if (nbrBlobs ==0)
      return -1;
    d.weightBlobs(false);
    BlobWeight[] blobs = new BlobWeight[nbrBlobs];
    for (int i = 0; i < blobs.length; i++)
      blobs[i] = new BlobWeight(i, d.getBlobWeight(i));
    Arrays.sort(blobs);
    return blobs[0].id;
  }

  private class BlobWeight implements Comparable<BlobWeight> {
    public int id;
    public Integer weight;
  
    public BlobWeight(int id, Integer weight) {
      this.id = id;
      this.weight = weight;
    }
  
    public int compareTo(BlobWeight b) {
      // Reverse so heaviest are first in list
      return new Integer(b.weight).compareTo(weight);
    }
  }
    
  public void update(){
    if (cam.available() == true) { 
      colorMode(HSB, 360, 1, 1, 1);
      cam.read();
      frameRaw = cam.copy();
      frame = cam.copy();
      
      frame.filter(INVERT);
      frame.filter(THRESHOLD, 0.90f);
      
      if(plateDetectOn) {
        framePlate = cam.copy();
        framePlate.loadPixels();
        float h,s,b;
        
        float minH = 25;
        float maxH = 50;
        float minB = 0.5f;
        float maxB = 0.99f;
        float minS = 0.5f;
        float maxS = 1;
        for(int i = 0; i < framePlate.pixels.length; i++) {
          /*if(!(i > 640*roi.y1 && i < 640*roi.boxH && i%640 > roi.x1 && i%640 < roi.x2)) {
            continue; 
          }*/
          h = hue(framePlate.pixels[i]);
          s = saturation(framePlate.pixels[i]);
          b = brightness(framePlate.pixels[i]);
          if(i == 0) {
            //println("h:" + h + " s:" + s + " b:" + b); 
          }
          if (h >= minH && h <= maxH && b >= minB && b <= maxB && s >= minS && s <= maxS) {
            framePlate.pixels[i] = MAX_INT;
          } else {
            framePlate.pixels[i] = 0;
          }
        }
        framePlate.updatePixels();
      }
      colorMode(RGB, 255, 255, 255, 255);
      findCentroids(bd, frame); 
      if(plateDetectOn)
        findPlatePos(pd, framePlate);
      }
    
  }
  
  public void display(){
    colorMode(HSB, 360, 1, 1, 1);
    if(frame != null) {
      imageMode(CORNERS);
      image(frame, screenRect.x1, screenRect.y1, screenRect.x2, screenRect.y2);
      stroke(120, 1, 1, 0.5f);
      line(screenRect.x, screenRect.y1, screenRect.x, screenRect.y2);
      line(screenRect.x1, screenRect.y, screenRect.x2, screenRect.y);
    //println(frame.width + " " + frame.height);
     if(rawRect != null) {
       image(frameRaw, rawRect.x1, rawRect.y1, rawRect.x2, rawRect.y2);
       stroke(120, 1, 1, 0.5f);
       line(rawRect.x, rawRect.y1, rawRect.x, rawRect.y2);
       line(rawRect.x1, rawRect.y, rawRect.x2, rawRect.y);
     }
     
     if(plateDetectOn && plateRect != null) {
       image(framePlate, plateRect.x1, plateRect.y1, plateRect.x2, plateRect.y2);
       stroke(120, 1, 1, 0.5f);
       line(plateRect.x, plateRect.y1, plateRect.x, plateRect.y2);
       line(plateRect.x1, plateRect.y, plateRect.x2, plateRect.y);
     }
      this.drawBoxes(bd, screenRect);
      this.drawBoxes(pd, plateRect);
    } 
    strokeWeight(3);
    stroke(0, 0, 1);
    noFill();
    screenRect.drawRect();
    if(rawRect != null) {
      rawRect.drawRect(); 
    }
    if(plateDetectOn && plateRect != null){
      plateRect.drawRect(); 
    }
    strokeWeight(1);
    colorMode(RGB, 255, 255, 255, 255);
    
  }
}
  public void settings() {  size(1250, 600); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "Motion_tracking_calibration" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
