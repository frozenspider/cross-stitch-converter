package org.fs.embroidery

import java.awt.Color

object ColorUtils {
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
