package org.fs.embroidery

import java.awt.Graphics2D
import java.awt.image.BufferedImage

/** A wrapper class for mutable AWT image */
case class InternalImage(inner: BufferedImage, graphics: Graphics2D) {
  def w:       Int = inner.getWidth
  def h:       Int = inner.getHeight
  def typeInt: Int = inner.getType

  /** Create an independent copy of this image */
  def copy: InternalImage = {
    val inner2    = new BufferedImage(w, h, typeInt)
    val graphics2 = inner2.createGraphics
    graphics2.drawImage(inner, 0, 0, null)
    InternalImage(inner2, graphics2)
  }
}

object InternalImage {
  def apply(inner: BufferedImage): InternalImage =
    new InternalImage(inner, inner.createGraphics())

  def apply(inner: BufferedImage, graphics: Graphics2D): InternalImage =
    new InternalImage(inner, graphics)
}
