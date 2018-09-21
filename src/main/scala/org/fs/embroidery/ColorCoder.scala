package org.fs.embroidery

import java.awt.Color

import scala.collection.immutable.SortedMap

object ColorCoder {

  def classifyColor(rgb: Int): String = {
    val hsb = getHsb(rgb)
    if (hsb.brightness < 0.25) "black"
    else if (hsb.saturation < 0.20) {
      hsb.brightness match {
        case b if b > 0.9  => "white"
        case b if b > 0.65 => "light grey"
        case b if b > 0.5  => "grey"
        case _             => "dark grey"
      }
    } else {
      findNearest(math.round(hsb.hue * 360))
    }
  }

  private val hueDegreesToColors = SortedMap[Int, String](
    0   -> "red",
    39  -> "orange",
    60  -> "yellow",
    72  -> "lime",
    100 -> "green",
    120 -> "green",
    195 -> "light blue",
    240 -> "blue",
    300 -> "magenta",
    360 -> "red"
  )

  private def findNearest(hueDeg: Int): String = {
    require(hueDeg >= 0 && hueDeg <= 360)
    val (low, high) = {
      val set          = hueDegreesToColors.keySet
      val (less, more) = set.partition(_ < hueDeg)
      ((less + -999).max, (more + 999).min)
    }
    val key = if (hueDeg - low < high - hueDeg) low else high
    hueDegreesToColors(key)
  }

  def getHsb(rgb: Int): Hsb = {
    // See https://stackoverflow.com/a/22179665/466646
    val hsb = new Array[Float](3)
    val r   = (rgb >> 16) & 0xFF
    val g   = (rgb >> 8) & 0xFF
    val b   = rgb & 0xFF
    Color.RGBtoHSB(r, g, b, hsb)
    Hsb(hsb(0), hsb(1), hsb(2))
  }

  case class Hsb(hue: Float, saturation: Float, brightness: Float)
}
