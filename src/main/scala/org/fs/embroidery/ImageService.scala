package org.fs.embroidery

import java.awt.Color
import java.awt.Font
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage

import org.fs.embroidery.classify.ColorsSupport
import org.fs.embroidery.classify.KMeans

class ImageService(isPortrait: => Boolean) {

  val dpi: Int = 150

  val mmPerPixel = (BigDecimal("25.4") / dpi).setScale(2, BigDecimal.RoundingMode.HALF_UP)

  private val a4SizeMm:   (Int, Int)       = (210, 297)
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
      pixelationMode: Pixelator.Mode,
      shouldPaintGrid: Boolean,
      simplifyColorsOption: Option[(Int, Boolean, Boolean)]
  ): BufferedImage = this.synchronized {
    val a4 = a4Image
    canvasImage = InternalImage(new BufferedImage(a4.getWidth, a4.getHeight, canvasImage.typeInt))
    processedImage = loadedImage

    var colorRefImageOption: Option[InternalImage] = None
    var rulersOption:        Option[Rulers]        = None

    processedImage = scaleImage(processedImage, canvasImage, scalingFactor)
    val (processedImage2, colorMap) = pixelateImage(processedImage, pixelationStep, pixelationMode)
    processedImage = processedImage2
    simplifyColorsOption foreach {
      case (n, colorCode, distinctOnly) =>
        val res =
          simplifyColors(processedImage, pixelationStep, colorMap.values.toIndexedSeq, n, colorCode, distinctOnly)
        processedImage = res._1
        colorRefImageOption = res._2
    }
    if (shouldPaintGrid) {
      processedImage = paintGrid(processedImage, pixelationStep)
      rulersOption = Some(createRulers(processedImage, pixelationStep))
    }
    rulersOption foreach {
      case Rulers(lr, tb) =>
        processedImage = appendImage(processedImage, lr, Align.Right, 0)
        processedImage = appendImage(processedImage, tb, Align.Bottom, 0)
        processedImage = appendImage(processedImage, lr, Align.Left, 0)
        processedImage = appendImage(processedImage, tb, Align.Top, pixelationStep)
    }
    colorRefImageOption foreach { colorRefImage =>
      processedImage = appendImage(
        processedImage,
        colorRefImage,
        Align.Bottom,
        if (rulersOption.isDefined) pixelationStep else 0
      )
    }
    canvasImage.graphics.drawImage(a4, 0, 0, null)
    canvasImage.graphics.drawImage(processedImage.inner, 0, 0, null)
    canvasImage.inner
  }

  def previousUpdatedCanvas: BufferedImage = canvasImage.inner

  def previousUpdatedImage: BufferedImage = processedImage.inner

  private def appendImage(
      src: InternalImage,
      toAdd: InternalImage,
      align: Align,
      shift: Int
  ): InternalImage = {
    val resImage = InternalImage(
      new BufferedImage(
        src.w + (if (align.extendWidth) toAdd.w else shift),
        src.h + (if (align.extendHeight) toAdd.h else shift),
        src.typeInt
      )
    )
    resImage.graphics.drawImage(
      src.inner,
      if (align == Align.Left) toAdd.w else 0,
      if (align == Align.Top) toAdd.h else 0,
      null)
    resImage.graphics.drawImage(
      toAdd.inner,
      align match {
        case Align.Right => src.inner.getWidth
        case Align.Left  => 0
        case _           => shift
      },
      align match {
        case Align.Bottom => src.inner.getHeight
        case Align.Top    => 0
        case _            => shift
      },
      null
    )
    resImage
  }

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

  private def pixelateImage(
      image: InternalImage,
      pixelationStep: Int,
      mode: Pixelator.Mode
  ): (InternalImage, Map[(Int, Int), Color]) = {
    val (inner, colorMap) = Pixelator.pixelate(image.inner, pixelationStep, mode)
    (InternalImage(inner), colorMap)
  }

  private def paintGrid(image: InternalImage, pixelationStep: Int): InternalImage = {
    paintGridInner(image, pixelationStep, getContrastRgb)
  }

  private def paintGridInner(
      image: InternalImage,
      pixelationStep: Int,
      transformRgb: Int => Int
  ): InternalImage = {
    val resImage = image.copy
    resImage.graphics.setColor(Color.BLACK)
    for {
      x <- 0 until resImage.w by (pixelationStep)
      y <- 0 until resImage.h
    } resImage.inner.setRGB(x, y, transformRgb(image.inner.getRGB(x, y)))
    for {
      x <- 0 until resImage.w if (x % pixelationStep != 0)
      y <- 0 until resImage.h by (pixelationStep)
    } resImage.inner.setRGB(x, y, transformRgb(image.inner.getRGB(x, y)))
    resImage
  }

  private def getContrastRgb(rgb: Int): Int = {
    if (isGrey(rgb)) {
      0xFF000000
    } else {
      0xFFFFFFFF - rgb + 0xFF000000
    }
  }

  private def isGrey(rgb: Int): Boolean = {
    val hsb = ColorUtils.getHsb(rgb)
    hsb.saturation < 0.2 && (hsb.brightness > 0.3 && hsb.brightness < 0.7)
  }

  private def createRulers(image: InternalImage, pixelationStep: Int): Rulers = {
    val font    = new Font(Font.SANS_SERIF, Font.BOLD, pixelationStep - 1)
    val lrRuler = InternalImage(new BufferedImage(pixelationStep, image.h, image.typeInt))
    val tbRuler = InternalImage(new BufferedImage(image.w, pixelationStep, image.typeInt))
    fillRuler(lrRuler, pixelationStep, false)
    fillRuler(tbRuler, pixelationStep, true)
    Rulers(lrRuler, tbRuler)
  }

  private def fillRuler(
      rulerImage: InternalImage,
      pixelationStep: Int,
      isHorizontal: Boolean
  ): Unit = {
    val mainDim = if (isHorizontal) rulerImage.w else rulerImage.h
    val font    = new Font(Font.SANS_SERIF, Font.BOLD, pixelationStep - 1)
    val g       = rulerImage.graphics
    g.setFont(font)
    g.setColor(Color.WHITE)
    g.fillRect(0, 0, rulerImage.w, rulerImage.h)
    g.setColor(Color.GRAY)
    for (c <- 0 to (mainDim / pixelationStep) if c * pixelationStep < mainDim) {
      val shiftedC = (c * pixelationStep)
      val x        = if (isHorizontal) shiftedC else 0
      val y        = shiftedC - x
      g.drawRect(x, y, pixelationStep, pixelationStep)
      rulerImage.drawCenteredStringInRect((c + 1).toString, x, y, pixelationStep, pixelationStep)
    }
  }

  /** Coerce all colors in the image to the N colors, mark them and list */
  private def simplifyColors(
      image: InternalImage,
      pixelationStep: Int,
      colors: IndexedSeq[Color],
      simplifiedColorsNum: Int,
      colorCode: Boolean,
      distinctOnly: Boolean
  ): (InternalImage, Option[InternalImage]) = {
    val means    = KMeans(simplifiedColorsNum, colors, distinctOnly)(ColorsSupport)
    val resImage = image.copy
    val font     = new Font(Font.SANS_SERIF, Font.BOLD, pixelationStep)
    resImage.graphics.setFont(font)
    val fm = resImage.graphics.getFontMetrics
    for {
      x <- 0 until resImage.w by pixelationStep
      y <- 0 until resImage.h by pixelationStep
    } {
      val sample  = new Color(image.inner.getRGB(x, y))
      val meanIdx = means.classify(sample)
      val mean    = means.centroids(meanIdx)
      resImage.graphics.setColor(mean)
      resImage.graphics.fillRect(x, y, pixelationStep, pixelationStep)
      if (colorCode) {
        resImage.graphics.setColor(new Color(getContrastRgb(mean.getRGB)))
        resImage.drawCenteredStringInRect((meanIdx + 1).toString, x, y, pixelationStep, pixelationStep)
      }
    }
    val colorReferenceImageOption = if (colorCode) Some {
      InternalImage(new BufferedImage(image.w, fm.getHeight * means.k, defaultImageType))
    } else None
    colorReferenceImageOption foreach { bottomImage =>
      val graphic = bottomImage.graphics
      graphic.setColor(Color.WHITE)
      graphic.fillRect(0, 0, bottomImage.w, bottomImage.h)
      graphic.setFont(font)
      means.centroids.zipWithIndex foreach {
        case (color, idx) =>
          graphic.setColor(color)
          val y = fm.getHeight * idx
          graphic.fillRect(0, y, bottomImage.w, y + fm.getHeight)
          graphic.setColor(new Color(getContrastRgb(color.getRGB)))
          graphic.drawString("Color #" + (idx + 1), 1, y + fm.getAscent)
      }
    }
    (resImage, colorReferenceImageOption)
  }

  private case class Rulers(lr: InternalImage, tb: InternalImage)

  private sealed abstract class Align(isHorizontal: Boolean) {
    def extendWidth:  Boolean = isHorizontal
    def extendHeight: Boolean = !isHorizontal
  }
  private object Align {
    case object Top    extends Align(false)
    case object Bottom extends Align(false)
    case object Left   extends Align(true)
    case object Right  extends Align(true)
  }
}
