package tetriski.pillars.hardware

import chisel3.util._
import chisel3.{Bundle, Input, Module, Output, UInt, _}

import scala.collection.mutable.ArrayBuffer


//configBits is the last param
class PillarsModuleInfo(moduleNums: List[Int], params : List[List[Int]]) {
  def getModuleNums(): List[Int] ={
    moduleNums
  }
  def getParams(num : Int): List[Int] ={
    params(num)
  }
  def getConfigBits(typeID : Int, moduleID :Int) : Int = {
    var currentNum = 0
    for (i <- 0 until typeID){
      currentNum += moduleNums(i)
    }
    currentNum += moduleID
    params(currentNum)(params(currentNum).length-1)
  }

  def getTotalBits():Int = {
    params.toArray.map(t => t(t.length-1)).reduce(_+_)
    }
}


class TopModule(val moduleInfos: PillarsModuleInfo, val connect: Map[List[Int], List[List[Int]]],
                val configList : List[List[List[Int]]], w: Int) extends Module {
  val io = IO(new Bundle {
    //port sequnces outs: 0: out
    //port sequnces inputs: 0: input_a, 1: input_b
    val configTest = Output(Vec(2, UInt(moduleInfos.getTotalBits().W)))
    val configuration = Input(UInt(moduleInfos.getTotalBits().W))
    val inputs = Input(MixedVec(Seq(UInt(w.W), UInt(w.W))))
    val outs = Output(MixedVec(Seq(UInt(w.W))))
  })

  //print("configList", configList)

//  def getConfigBit (typeID : Int): Int ={
//    val ret = typeID match {
//      case 0 => 4
//      case 1 => 3
//      case 2 => 3
//      case 3 => 32
//    }
//    ret
//  }
  val input_0 = io.inputs(0)
  val input_1 = io.inputs(1)
  val out = io.outs(0)

  val moduleNums = moduleInfos.getModuleNums()
  val types = moduleNums.size
  //  println(io.getElements(0))
  var currentNum = 0
//  val addNum = moduleNums(0)
//  val adders = (0 until addNum).toArray.map(t => Module(new Adder(moduleInfo(1)(t + currentNum))))
//  currentNum += addNum
//
//  val mulNum = moduleNums(1)
//  val muls = (0 until mulNum).toArray.map(t => Module(new Multiplier(moduleInfo(1)(t + currentNum))))
//  currentNum += mulNum
//
//  val PENum = moduleNums(3)
//  val PEs = (0 until PENum).toArray.map(t => Module(new ADRESPE(moduleInfo(1)(t + currentNum))))
//  currentNum += PENum
//
  val aluNum = moduleNums(0)
  val alus = (0 until aluNum).toArray.map(t => Module(new Alu(moduleInfos.getParams(t + currentNum)(0))))
  currentNum += aluNum



  val RFNum = moduleNums(1)
  val RFs = (0 until RFNum).toArray
    .map(t => Module(new RegisterFiles(moduleInfos.getParams(t + currentNum)(0),
      moduleInfos.getParams(t + currentNum)(1),
      moduleInfos.getParams(t + currentNum)(2),
      moduleInfos.getParams(t + currentNum)(3))))
  currentNum += RFNum

  val MuxNum = moduleNums(2)
  val Muxs = (0 until MuxNum).toArray
    .map(t => Module(new Multiplexer(moduleInfos.getParams(t + currentNum)(0), moduleInfos.getParams(t + currentNum)(1))))
  //println("2110", moduleInfos.getParams(11 + currentNum))
  currentNum += MuxNum

  val ConstNum = moduleNums(3)
  val Consts = (0 until ConstNum).toArray
    .map(t => Module(new ConstUnit(moduleInfos.getParams(t + currentNum)(0))))
  currentNum += ConstNum

  val modules = List(alus, RFs, Muxs, Consts)


  val outPorts = new ArrayBuffer[Array[List[Any]]]
  outPorts.append(alus.map(i => i.io.outs.toList))
  outPorts.append(RFs.map(i => i.io.outs.toList))
  outPorts.append(Muxs.map(i => i.io.outs.toList))
  outPorts.append(Consts.map(i => i.io.outs.toList))

  val inPorts = new ArrayBuffer[Array[List[Any]]]
  inPorts.append(alus.map(i => i.io.inputs.toList))
  inPorts.append(RFs.map(i => i.io.inputs.toList))
  inPorts.append(Muxs.map(i => i.io.inputs.toList))
  inPorts.append(Consts.map(i => List()))


  //println(configList)
  var dispatchs = ArrayBuffer[Dispatch]()
  var regionConfigBits = List[Int]()
  for (region <- configList){
    var configBits = List[Int]()
    var configPorts = List[Data]()
    for(moduleList <- region){
      val typeID = moduleList(0)
      val moduleID = moduleList(1)
      configBits = configBits :+ moduleInfos.getConfigBits(typeID, moduleID)
      val configPort = typeID match {
        case 0 => alus(moduleID).io.configuration
        case 1 => RFs(moduleID).io.configuration
        case 2 => Muxs(moduleID).io.configuration
        case 3 => Consts(moduleID).io.configuration
      }
      configPorts = configPorts :+ configPort
    }
    val regionTotalBits = configBits.reduce(_+_)
    //println(configBits)
    regionConfigBits = regionConfigBits :+ regionTotalBits
    val dispatch = Module(new Dispatch(regionTotalBits, configBits))
    for (i <- 0 until configBits.size){
      configPorts(i) := dispatch.io.outs(i)
    }
    //io.configTest(0) := alus
    dispatchs.append(dispatch)
  }
  val totalBits = regionConfigBits.reduce(_+_)
  val topDispatch = Module(new Dispatch(totalBits, regionConfigBits))
  println(totalBits, regionConfigBits)
  topDispatch.io.configuration := io.configuration
  for (i <- 0 until dispatchs.size){
    dispatchs(i).io.configuration := topDispatch.io.outs(i)
  }

  //println(regionConfigBits)
  println(configList(1))
  io.configTest(0) := topDispatch.io.outs(0)
  io.configTest(1) := topDispatch.io.outs(1)

  //sent configuration to modules
//  val dispatch = Module(new Dispatch(13, List(3, 3, 4, 3)))
//  dispatch.io.configuration := io.configuration
//
//  io.configTest(0) := dispatch.io.outs(0)
//  io.configTest(1) := dispatch.io.outs(1)
//
//  Muxs5(0).io.configuration := dispatch.io.outs(0)
//  Muxs5(1).io.configuration := dispatch.io.outs(1)
//  alus(0).io.configuration := dispatch.io.outs(2)
//  RFs1_1_2(0).io.configuration := dispatch.io.outs(3)


//  for (i <- 0 until connect.keys.size) {
//    println(connect.keys.toList(i))
//  }


  for (i <- 0 until connect.keys.size) {
    val src = connect.keys.toList(i)
    val dsts = connect(src)
    for (j <- 0 until dsts.size) {
      val dst = dsts(j)
      //println(dst, src)
      if (dst(0) == types) {
        io.outs(dst(2)) := outPorts(src(0))(src(1))(src(2)).asInstanceOf[Data]
      } else if (src(0) == types) {
        inPorts(dst(0))(dst(1))(dst(2)).asInstanceOf[Data] := io.inputs(src(2))
      } else {
        inPorts(dst(0))(dst(1))(dst(2)).asInstanceOf[Data] := outPorts(src(0))(src(1))(src(2)).asInstanceOf[Data]
      }
    }
  }

}


