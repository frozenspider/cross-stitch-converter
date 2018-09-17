package org.fs.embroidery

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

class Images(isPortrait: => Boolean) {

  private val dpi: Int                     = 96
  private val a4SizeMm: (Int, Int)         = (210, 297)
  private val a4SizeInch: (Double, Double) = (8.27d, 11.69d)

  private val (a4PortraitImage, a4LandscapeImage): (BufferedImage, BufferedImage) = {
    val w    = (a4SizeInch._1 * dpi).toInt
    val h    = (a4SizeInch._2 * dpi).toInt
    val imgP = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val imgL = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB)

    val gr = imgP.createGraphics()
    gr.setPaint(Color.WHITE)
    gr.fillRect(0, 0, imgP.getWidth, imgP.getHeight)
    gr.setStroke(new BasicStroke(3))
    gr.setPaint(Color.DARK_GRAY)
    gr.drawRect(1, 1, imgP.getWidth - 3, imgP.getHeight - 3)

    val at = new AffineTransform() {
      quadrantRotate(1)
      translate(0, -imgL.getWidth)
    }
    imgL.createGraphics().drawImage(imgP, at, null)

    (imgP, imgL)
  }

  private def a4Image: BufferedImage =
    if (isPortrait) a4PortraitImage else a4LandscapeImage

  private var canvasImage: BufferedImage =
    new BufferedImage(a4Image.getWidth, a4Image.getHeight, BufferedImage.TYPE_INT_ARGB)

  private var canvasImageGraphics: Graphics2D =
    canvasImage.createGraphics()

  private var loadedImage: BufferedImage =
    new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

  private var processedImage: BufferedImage =
    new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

  private var processedImageGraphics: Graphics2D =
    processedImage.createGraphics()

  private var pixelationStep: Int = -1

  def load(image: BufferedImage): Unit = {
    loadedImage = image
    processedImage = image
    processedImageGraphics = processedImage.createGraphics()
    pixelationStep = -1
  }

  def pixelate(step: Int): Unit = {
    if (pixelationStep != step) {
      pixelationStep = step
      processedImage = Pixelator.pixelate(loadedImage, step)
      processedImageGraphics = processedImage.createGraphics()
      paintGrid()
    }
  }

  private def paintGrid(): Unit = {
    if (pixelationStep > 1) {
      processedImageGraphics.setColor(Color.BLACK)
      for {
        x <- 0 until processedImage.getWidth by (pixelationStep)
        y <- 0 until processedImage.getHeight
      } inverseColor(x, y)
      for {
        x <- 0 until processedImage.getWidth if (x % pixelationStep != 0)
        y <- 0 until processedImage.getHeight by (pixelationStep)
      } inverseColor(x, y)
    }
  }

  private def inverseColor(x: Int, y: Int): Unit = {
    processedImage.setRGB(x, y, 0xFFFFFFFF - processedImage.getRGB(x, y) + 0xFF000000)
  }

  /** Re-render canvas and get updated image */
  def updated(): BufferedImage = {
    val a4 = a4Image
    if (a4.getWidth != canvasImage.getWidth) {
      canvasImage = new BufferedImage(a4.getWidth, a4.getHeight, BufferedImage.TYPE_INT_ARGB)
      canvasImageGraphics = canvasImage.createGraphics()
    }
    canvasImageGraphics.drawImage(a4, 0, 0, null)
    canvasImageGraphics.drawImage(processedImage, 0, 0, null)
    canvasImage
  }
}
