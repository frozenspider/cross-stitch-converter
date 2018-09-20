package org.fs.embroidery

import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage

class ImagesService(isPortrait: => Boolean) {

  val dpi: Int = 150

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
    new BufferedImage(1, 1, loadedImage.getType)

  private var processedImageGraphics: Graphics2D =
    processedImage.createGraphics()

  def load(image: BufferedImage): Unit = this.synchronized {
    loadedImage = image
    processedImage = image
    processedImageGraphics = processedImage.createGraphics()
  }

  /** Re-render canvas and get updated image */
  def updatedCanvas(scalingFactor: Double, pixelationStep: Int, colorCode: Boolean): BufferedImage = this.synchronized {
    val a4 = a4Image
    if (a4.getWidth != canvasImage.getWidth) {
      canvasImage = new BufferedImage(a4.getWidth, a4.getHeight, canvasImage.getType)
      canvasImageGraphics = canvasImage.createGraphics()
    }
    processedImage = loadedImage
    processedImageGraphics = processedImage.createGraphics()
    scaleImage(scalingFactor)
    pixelateImage(pixelationStep)
    paintGrid(pixelationStep)
    canvasImageGraphics.drawImage(a4, 0, 0, null)
    canvasImageGraphics.drawImage(processedImage, 0, 0, null)
    canvasImage
  }

  def previousUpdatedCanvas: BufferedImage = canvasImage

  def previousUpdatedImage: BufferedImage = processedImage

  private def scaleImage(scalingFactor: Double): Unit = {
    this.processedImage = {
      val resultingImage = new BufferedImage(
        (processedImage.getWidth * scalingFactor).toInt min canvasImage.getWidth max 1,
        (processedImage.getHeight * scalingFactor).toInt min canvasImage.getHeight max 1,
        processedImage.getType
      )
      val atOp = new AffineTransformOp(new AffineTransform {
        scale(scalingFactor, scalingFactor)
      }, AffineTransformOp.TYPE_BILINEAR)
      atOp.filter(processedImage, resultingImage)
      resultingImage
    }
    this.processedImageGraphics = processedImage.createGraphics()
  }

  private def pixelateImage(pixelationStep: Int): Unit = {
    this.processedImage = Pixelator.pixelate(processedImage, pixelationStep)
    this.processedImageGraphics = processedImage.createGraphics()
  }

  private def paintGrid(pixelationStep: Int): Unit = {
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

  private def inverseColor(x: Int, y: Int): Unit = {
    processedImage.setRGB(x, y, 0xFFFFFFFF - processedImage.getRGB(x, y) + 0xFF000000)
  }
}
