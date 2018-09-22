package org.fs.embroidery

import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage

import org.fs.embroidery.classify.ColorsSupport
import org.fs.embroidery.classify.KMeans

class ImagesService(isPortrait: => Boolean) {

  val dpi: Int = 150

  private val a4SizeMm: (Int, Int)         = (210, 297)
  private val a4SizeInch: (Double, Double) = (8.27d, 11.69d)

  private val defaultImageType = BufferedImage.TYPE_4BYTE_ABGR

  private val (a4PortraitImage, a4LandscapeImage): (BufferedImage, BufferedImage) = {
    val w    = (a4SizeInch._1 * dpi).toInt
    val h    = (a4SizeInch._2 * dpi).toInt
    val imgP = new BufferedImage(w, h, defaultImageType)
    val imgL = new BufferedImage(h, w, defaultImageType)

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

  private var canvasImage: InternalImage =
    InternalImage(new BufferedImage(a4Image.getWidth, a4Image.getHeight, defaultImageType))

  private var loadedImage: InternalImage =
    InternalImage(new BufferedImage(1, 1, defaultImageType))

  private var processedImage: InternalImage =
    InternalImage(new BufferedImage(1, 1, loadedImage.typeInt))

  def load(image: BufferedImage): Unit = this.synchronized {
    loadedImage = InternalImage(image)
    processedImage = loadedImage
  }

  /** Re-render canvas and get updated image */
  def updatedCanvas(
      scalingFactor: Double,
      pixelationStep: Int,
      colorCodeColorsNumOption: Option[Int]
  ): BufferedImage = this.synchronized {
    val a4 = a4Image
    canvasImage = InternalImage(new BufferedImage(a4.getWidth, a4.getHeight, canvasImage.typeInt))
    processedImage = loadedImage
    processedImage = scaleImage(processedImage, canvasImage, scalingFactor)
    val (processedImage2, colorMap) = pixelateImage(processedImage, pixelationStep)
    processedImage = processedImage2
    colorCodeColorsNumOption foreach { colorCodeColorsNum =>
      processedImage = colorCode(processedImage, pixelationStep, colorMap, colorCodeColorsNum)
    }
    processedImage = paintGrid(processedImage, pixelationStep)
    canvasImage.graphics.drawImage(a4, 0, 0, null)
    canvasImage.graphics.drawImage(processedImage.inner, 0, 0, null)
    canvasImage.inner
  }

  def previousUpdatedCanvas: BufferedImage = canvasImage.inner

  def previousUpdatedImage: BufferedImage = processedImage.inner

  //
  // Processing methods
  //

  private def scaleImage(image: InternalImage, canvasImage: InternalImage, scalingFactor: Double): InternalImage = {
    val resultingImageVal = new BufferedImage(
      (image.w * scalingFactor).toInt min canvasImage.w max 1,
      (image.h * scalingFactor).toInt min canvasImage.h max 1,
      image.typeInt
    )
    val atOp = new AffineTransformOp(new AffineTransform {
      scale(scalingFactor, scalingFactor)
    }, AffineTransformOp.TYPE_BILINEAR)
    atOp.filter(image.inner, resultingImageVal)
    InternalImage(resultingImageVal)
  }

  private def pixelateImage(image: InternalImage, pixelationStep: Int): (InternalImage, Map[(Int, Int), Color]) = {
    val (inner, colorMap) = Pixelator.pixelate(image.inner, pixelationStep)
    (InternalImage(inner), colorMap)
  }

  private def paintGrid(image: InternalImage, pixelationStep: Int): InternalImage = {
    val resImage = image.copy
    resImage.graphics.setColor(Color.BLACK)
    for {
      x <- 0 until resImage.w by (pixelationStep)
      y <- 0 until resImage.h
    } applyContrastColor(resImage, x, y)
    for {
      x <- 0 until resImage.w if (x % pixelationStep != 0)
      y <- 0 until resImage.h by (pixelationStep)
    } applyContrastColor(resImage, x, y)
    resImage
  }

  private def applyContrastColor(image: InternalImage, x: Int, y: Int): Unit = {
    val rgb = image.inner.getRGB(x, y)
    if (!isGrey(rgb)) {
      image.inner.setRGB(x, y, 0xFFFFFFFF - rgb + 0xFF000000)
    } else {
      image.inner.setRGB(x, y, 0xFF000000)
    }
  }

  private def isGrey(rgb: Int): Boolean = {
    val hsb = ColorCoder.getHsb(rgb)
    hsb.saturation < 0.2 && (hsb.brightness > 0.3 && hsb.brightness < 0.7)
  }

  private def colorCode(
      image: InternalImage,
      pixelationStep: Int,
      colorMap: Map[(Int, Int), Color],
      colorCodeColorsNum: Int
  ): InternalImage = {
    val means    = KMeans(colorCodeColorsNum, colorMap.values.toIndexedSeq)(ColorsSupport)
    val resImage = image.copy
    for {
      x <- 0 until resImage.w by pixelationStep
      y <- 0 until resImage.h by pixelationStep
    } {
      val sample = new Color(image.inner.getRGB(x, y))
      val mean   = means.classifyAndGet(sample)
      resImage.graphics.setColor(mean)
      resImage.graphics.fillRect(x, y, pixelationStep, pixelationStep)
    }
    resImage
  }
}
