package org.fs.embroidery

import java.awt.Color

object ColorUtils {
  type RGB         = Int
  type ColorTriple = (Int, Int, Int)

  def getHsb(rgb: RGB): Hsb = {
    // See https://stackoverflow.com/a/22179665/466646
    val hsb = new Array[Float](3)
    val r   = (rgb >> 16) & 0xFF
    val g   = (rgb >> 8) & 0xFF
    val b   = rgb & 0xFF
    Color.RGBtoHSB(r, g, b, hsb)
    Hsb(hsb(0), hsb(1), hsb(2))
  }

  def getContrastRgb(rgb: RGB): RGB = {
    if (isGrey(rgb)) {
      0xFF000000
    } else {
      0xFFFFFFFF - rgb + 0xFF000000
    }
  }

  def isGrey(rgb: RGB): Boolean = {
    val hsb = ColorUtils.getHsb(rgb)
    hsb.saturation < 0.2 && (hsb.brightness > 0.3 && hsb.brightness < 0.7)
  }

  case class Hsb(hue: Float, saturation: Float, brightness: Float)

  implicit class RichColorTriple(t: ColorTriple) {
    def toColor: Color = {
      new Color(t._1, t._2, t._3)
    }
  }

  implicit class RichRgb(rgb: RGB) {
    def toColorTriple: ColorTriple = {
      // Code taken from java.awt.Color
      ((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, (rgb >> 0) & 0xFF)
    }
  }
}
