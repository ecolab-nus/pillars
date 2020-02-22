package tetriski.pillars.testers

import chisel3.iotesters.PeekPokeTester
import tetriski.pillars.hardware.TopModuleWrapper

class ApplicationWrapperTester(c: TopModuleWrapper) extends PeekPokeTester(c) {
  def asUnsignedInt(signedInt: Int): BigInt = (BigInt(signedInt >>> 1) << 1) + (signedInt & 1)
  def enqData(numInLSU: Int, inData: Array[Int], base: Int): Unit ={
    poke(c.io.startLSU, 1)
    poke(c.io.enqEnLSU, 1)
    poke(c.io.streamInLSU.valid, 0)
    poke(c.io.baseLSU, base)
    poke(c.io.LSUnitID, numInLSU)
    step(1)

    // push
    for (x <- inData) {
      poke(c.io.streamInLSU.valid, 1)
      expect(c.io.streamInLSU.valid, 1)
      poke(c.io.streamInLSU.bits, x)
      if (peek(c.io.streamInLSU.ready) == 0) {
        while (peek(c.io.streamInLSU.ready) == 0) {
          step(1)
        }
      } else {
        step(1)
      } // exit condition: (c.io.in.ready === true.B) and step()
    }
    poke(c.io.streamInLSU.valid, 0)

    // exec
    while (peek(c.io.idleLSU) == 0) {
      step(1)
    }

    poke(c.io.enqEnLSU, 0)
  }
  def deqData(numInLSU: Int, refArray: Array[Int], base: Int): Unit ={
    // exec
    poke(c.io.startLSU, 1)
    poke(c.io.baseLSU, base)
    poke(c.io.lenLSU, refArray.length)
    poke(c.io.deqEnLSU, 1)
    poke(c.io.LSUnitID, numInLSU)
    step(1)
    poke(c.io.startLSU, 0)

    for (i <- 0 until refArray.length) {
      poke(c.io.streamOutLSU.ready, 1)
      if (peek(c.io.streamOutLSU.valid) == 0) {
        while (peek(c.io.streamOutLSU.valid) == 0) {
          poke(c.io.streamOutLSU.ready, 1)
          step(1)
        }
      }
      expect(c.io.streamOutLSU.bits,  asUnsignedInt(refArray(i)))
//      println(asUnsignedInt(refArray(i)).toString + " " + peek(c.io.streamOutLSU.bits).toString())
      step(1)
    }

    poke(c.io.deqEnLSU, 0)
  }
}

class VaddWrapperTester(c: TopModuleWrapper, appTestHelper: AppTestHelper)
  extends ApplicationWrapperTester(c) {

  poke(c.io.en, 0)

  //input data into LSU
  for(inDataItem <- appTestHelper.inDataMap){
    val numInLSU = inDataItem._1(0)
    val base = inDataItem._1(1)
    val inData = inDataItem._2
    enqData(numInLSU, inData, base)
  }


  val testII = appTestHelper.getTestII()
  val schedules = appTestHelper.getSchedulesBigInt()
  val bitStreams = appTestHelper.getBitStreams()

  poke(c.io.en, 1)
  poke(c.io.II, testII)

  val outputCycle = appTestHelper.getOutputCycle()
  step(outputCycle)

  //stream deq test
  for(outDataItem <- appTestHelper.outDataMap){
    val numInLSU = outDataItem._1(0)
    val base = outDataItem._1(1)
    val refArray = outDataItem._2
    deqData(numInLSU, refArray, base)
  }

}

class SumWrapperTester(c: TopModuleWrapper, appTestHelper: AppTestHelper)
  extends ApplicationWrapperTester(c) {

  poke(c.io.en, 0)

  //input data into LSU
  for(inDataItem <- appTestHelper.inDataMap){
    val numInLSU = inDataItem._1(0)
    val base = inDataItem._1(1)
    val inData = inDataItem._2
    enqData(numInLSU, inData, base)
  }


  val testII = appTestHelper.getTestII()
  val schedules = appTestHelper.getSchedulesBigInt()
  val bitStreams = appTestHelper.getBitStreams()

  poke(c.io.en, 1)
  poke(c.io.II, testII)

  //  for(i <- 0 until schedules.size){
  //    poke(c.io.schedules(i), schedules(i))
  //  }

  val outputCycle = appTestHelper.getOutputCycle()
  step(outputCycle)

  val refs = appTestHelper.getOutPortRefs()
  for(ref <- refs){
    for(i <- ref._2){
      expect(c.io.outs(ref._1), asUnsignedInt(i))
      //      println(asUnsignedInt(i).toString + " " + peek(c.io.outs(ref._1)).toString())
      step(testII)
    }
  }

  //stream deq test
  for(inDataItem <- appTestHelper.outDataMap){
    val numInLSU = inDataItem._1(0)
    val base = inDataItem._1(1)
    val refArray = inDataItem._2
    deqData(numInLSU, refArray, base)
  }

}