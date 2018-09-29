package org.fs.embroidery

import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.image.BufferedImage

/** A wrapper class for mutable AWT image */
case class InternalImage(inner: BufferedImage, graphics: Graphics2D) {
  def w:       Int = inner.getWidth
  def h:       Int = inner.getHeight
  def typeInt: Int = inner.getType

  def drawCenteredStringInRect(s: String, x: Int, y: Int, w: Int, h: Int): Unit = {
    val fm = graphics.getFontMetrics
    // Taken from https://stackoverflow.com/a/27740330/466646
    graphics.drawString(s, fm.centerH(x, w, s), fm.centerV(y, h))
  }

  /** Create an independent copy of this image */
  def copy: InternalImage = {
    val inner2    = new BufferedImage(w, h, typeInt)
    val graphics2 = inner2.createGraphics
    graphics2.drawImage(inner, 0, 0, null)
    InternalImage(inner2, graphics2)
  }

  private implicit class RichFontMetrics(fm: FontMetrics) {
    def centerH(x: Int, w: Int, s: String): Float = x + (w - fm.stringWidth(s)).toFloat / 2
    def centerV(y: Int, h: Int):            Float = y + (h - fm.getHeight).toFloat / 2 + fm.getAscent
  }
}

object InternalImage {
  def apply(inner: BufferedImage): InternalImage =
    new InternalImage(inner, inner.createGraphics())

  def apply(inner: BufferedImage, graphics: Graphics2D): InternalImage =
    new InternalImage(inner, graphics)
}
