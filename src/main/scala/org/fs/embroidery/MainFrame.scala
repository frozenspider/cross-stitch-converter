package org.fs.embroidery

import java.awt.Color
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import scala.swing.BorderPanel
import scala.swing._
import scala.swing.event.ButtonClicked
import scala.swing.event.ValueChanged
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import hu.kazocsaba.imageviewer.DefaultStatusBar
import hu.kazocsaba.imageviewer.ImageViewer
import hu.kazocsaba.imageviewer.ResizeStrategy
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriter
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.KeyStroke
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.TransferHandler
import javax.swing.WindowConstants
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.filechooser.FileSystemView
import org.apache.commons.io.FilenameUtils
import org.slf4s.Logging

class MainFrame(
    var config: Config,
    saveConfig: Config => Unit
) extends Frame
    with Logging {

  private val initComplete = new AtomicBoolean(false)

  private val viewer                 = new ImageViewer(null, false)
  private val loadButton             = UnfocusableButton("Load")
  private val saveButton             = UnfocusableButton("Save")
  private val portraitRadioButton    = new RadioButton("Portrait") { selected = true }
  private val landscapeRadioButton   = new RadioButton("Landscape") { selected = false }
  private val dominantRadioButton    = new RadioButton("Dominant") { selected = true }
  private val averageRadioButton     = new RadioButton("Average") { selected = false }
  private val gridCheckbox           = new CheckBox("Grid overlay") { selected = true }
  private val simplifyColorsCheckbox = new CheckBox("Simplify to the given number of colors") { selected = false }
  private val simplifyColorsSpinner  = new JSpinner(new SpinnerNumberModel(3, 2, 20, 1))
  private val useDistinctCheckbox    = new CheckBox("Treat colors irrelatively of quantity") { selected = false }
  private val colorCodeCheckbox      = new CheckBox("Color-code") { selected = false }

  private val imageFileSuffixes = ImageIO.getReaderFileSuffixes

  private val pixelateSlider = new Slider {
    paintTicks = true
    paintLabels = true
    snapToTicks = true
    minorTickSpacing = 1
    min = 3
  }
  private val scaleSlider = new Slider {
    import Scaling._
    paintTicks = true
    paintLabels = true
    minorTickSpacing = LogCoeff
    min = -LogCoeff * 2
    max = LogCoeff * 2
    value = 0
    labels = Map(
      (-LogCoeff * 2)                    -> new Label("x0.01"),
      (-LogCoeff * math.log10(20)).toInt -> new Label("x0.05"),
      (-LogCoeff)                        -> new Label("x0.1"),
      (-LogCoeff * math.log10(2)).toInt  -> new Label("x0.5"),
      0                                  -> new Label("x1"),
      (LogCoeff * math.log10(2)).toInt   -> new Label("x2"),
      (LogCoeff)                         -> new Label("x10"),
      (LogCoeff * math.log10(20)).toInt  -> new Label("x20"),
      (LogCoeff * 2)                     -> new Label("x100")
    )
  }

  private val imagesService = new ImagesService(portraitRadioButton.selected)

  //
  // Initialization block
  //

  // TODO: Clipboard

  attempt {
    saveButton.enabled = false

    val zoomCoeff = 1.2
    val viewerScrollPane = viewer.getComponent
      .getComponent(0)
      .asInstanceOf[JScrollPane]
    val viewerImageComponent = viewerScrollPane.getViewport.getView
      .asInstanceOf[JLayeredPane]
      .getComponent(0)
      .asInstanceOf[JComponent]

    // Drag-scroll
    val dragSupport = new MouseDragSupport(viewerScrollPane.getViewport, viewerImageComponent)
    viewer.addMouseListener(dragSupport.mouseListener)
    viewer.addImageMouseMotionListener(dragSupport.imageMouseMotionListener)

    // Wheel zoom
    viewerImageComponent.addMouseWheelListener(e => {
      val notches = e.getWheelRotation
      if (viewer.getImage != null) {
        if (viewer.getResizeStrategy != ResizeStrategy.CUSTOM_ZOOM) {
          val transform = viewer.getImageTransform
          viewer.setResizeStrategy(ResizeStrategy.CUSTOM_ZOOM)
          viewer.setZoomFactor(transform.getScaleX)
        }
        val newZoomFactor = if (notches < 0) {
          viewer.getZoomFactor * zoomCoeff * (-notches)
        } else {
          viewer.getZoomFactor / zoomCoeff / notches
        }
        viewer.setZoomFactor(newZoomFactor)
      }
      e.consume()
    })

    viewer.setStatusBar(new DefaultStatusBar {
      override def updateLabel(image: BufferedImage, x: Int, y: Int, availableWidth: Int): Unit = {
        super.updateLabel(image, x, y, availableWidth)
        val newText = label.getText +
          ", zoom " + viewer.getZoomFactor +
          ", color " + ColorCoder.classifyColor(image.getRGB(x, y))
        label.setText(newText)
      }
    })
    viewer.setStatusBarVisible(true)

    viewer.addOverlay((g: Graphics2D, image: BufferedImage, transform: AffineTransform) => {
      g.setColor(Color.DARK_GRAY)
      val (w, h) = (image.getWidth - 1, image.getHeight - 1)
      val bounds = Array[Double](0, 0, w, 0, w, h, 0, h)
      transform.transform(bounds, 0, bounds, 0, 4)
      val coordPairs = bounds.map(_.toInt).grouped(2).toSeq
      (coordPairs :+ coordPairs.head).sliding(2) foreach {
        case Seq(Array(x0, y0), Array(x1, y1)) => g.drawLine(x0, y0, x1, y1)
      }
    })

    contents = new BorderPanel {
      def addHotkey(key: String, event: Int, mod: Int, f: => Unit): Unit = {
        peer
          .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
          .put(KeyStroke.getKeyStroke(event, mod), key)
        peer.getActionMap.put(key, (_ => f): AbstractAction)
      }

      import scala.swing.BorderPanel.Position._
      val topPanel = new BorderPanel {
        layout(
          new FlowPanel(
            loadButton,
            saveButton
          )
        ) = West
      }
      layout(topPanel) = North
      val centerPanel = new BorderPanel {
        layout(Component.wrap(viewer.getComponent)) = Center
        val configPanel = new BoxPanel(Orientation.Vertical) {
          contents += new FlowPanel(
            new BoxPanel(Orientation.Horizontal) {
              new ButtonGroup(portraitRadioButton, landscapeRadioButton)
              border = BorderFactory.createTitledBorder("Page orientation")
              contents += portraitRadioButton
              contents += landscapeRadioButton
            },
            new BoxPanel(Orientation.Horizontal) {
              new ButtonGroup(dominantRadioButton, averageRadioButton)
              border = BorderFactory.createTitledBorder("Pixelation mode")
              contents += dominantRadioButton
              contents += averageRadioButton
              contents += new Separator(Orientation.Vertical)
              contents += gridCheckbox
            },
            new BoxPanel(Orientation.Vertical) {
              border = BorderFactory.createTitledBorder("Color simplification")
              contents += new FlowPanel(
                simplifyColorsCheckbox,
                Component.wrap(simplifyColorsSpinner)
              ) { vGap = 0; hGap = 0 }
              contents += new FlowPanel(
                colorCodeCheckbox,
                useDistinctCheckbox
              ) { vGap = 0; hGap = 0 }
            }
          )
          contents += new BorderPanel {
            layout(new Label("Pixelation step:")) = West
            layout(pixelateSlider) = Center
          }
          contents += new BorderPanel {
            layout(new Label("Scale:")) = West
            layout(scaleSlider) = Center
          }
        }
        layout(configPanel) = South
      }
      layout(centerPanel) = Center
      val bottomPanel = new BorderPanel {}
      layout(bottomPanel) = South
      addHotkey("save", KeyEvent.VK_S, InputEvent.CTRL_MASK, attempt(saveClicked()))
      addHotkey("paste", KeyEvent.VK_V, InputEvent.CTRL_MASK, attempt(paste()))
    }

    listenTo(
      loadButton,
      saveButton,
      pixelateSlider,
      scaleSlider,
      portraitRadioButton,
      landscapeRadioButton,
      dominantRadioButton,
      averageRadioButton,
      gridCheckbox,
      simplifyColorsCheckbox,
      useDistinctCheckbox,
      colorCodeCheckbox
    )
    // Button reactions
    reactions += {
      case ButtonClicked(`loadButton`)             => attempt(loadClicked())
      case ButtonClicked(`saveButton`)             => attempt(saveClicked())
      case ButtonClicked(`portraitRadioButton`)    => attempt(scheduleRender())
      case ButtonClicked(`landscapeRadioButton`)   => attempt(scheduleRender())
      case ButtonClicked(`dominantRadioButton`)    => attempt(scheduleRender())
      case ButtonClicked(`averageRadioButton`)     => attempt(scheduleRender())
      case ButtonClicked(`gridCheckbox`)           => attempt(scheduleRender())
      case ButtonClicked(`simplifyColorsCheckbox`) => attempt(simplifyColorsCheckboxClicked())
      case ButtonClicked(`useDistinctCheckbox`)    => attempt(scheduleRender())
      case ButtonClicked(`colorCodeCheckbox`)      => attempt(scheduleRender())
      case ValueChanged(`pixelateSlider`)          => attempt(scheduleRender())
      case ValueChanged(`scaleSlider`)             => attempt(scheduleRender())
    }
    simplifyColorsSpinner.addChangeListener(x => attempt(scheduleRender()))

    title = BuildInfo.fullPrettyName
    size = new Dimension(1000, 700)
    peer.setLocationRelativeTo(null)
    peer.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)

    peer.setTransferHandler(DataTransferHandler)
    simplifyColorsSpinner.setEnabled(simplifyColorsCheckbox.selected)
    useDistinctCheckbox.enabled = simplifyColorsCheckbox.selected
    colorCodeCheckbox.enabled = simplifyColorsCheckbox.selected

    // load(new File("_build/img.png"))
    initComplete.set(true)
    scheduleRender()
  }

  private def createFileChooser(lastAccessedPath: String, extFilter: (String, Seq[String])): FileChooser = {
    val lastAccessedFile       = new File(lastAccessedPath)
    val fc                     = new FileChooser(if (lastAccessedFile.exists) lastAccessedFile else lastAccessedFile.getParentFile)
    val fileNameExtensionLabel = extFilter._1 + (extFilter._2 map (ext => s"*.$ext") mkString (" (", ", ", ")"))
    fc.fileFilter = new FileNameExtensionFilter(fileNameExtensionLabel, extFilter._2: _*)
    fc.peer.setPreferredSize(new Dimension(800, 600))
    fc
  }

  //
  // Actions and Callbacks
  //

  private def loadClicked(): Unit = {
    val fc = createFileChooser(
      getConfigStringOr(MainFrame.LoadFilePath, defaultPath),
      ("Images", imageFileSuffixes)
    )
    fc.showOpenDialog(this) match {
      case FileChooser.Result.Approve =>
        updateConfigString(MainFrame.LoadFilePath, fc.selectedFile.getParentFile.getAbsolutePath)
        load(fc.selectedFile)
      case _ => // NOOP
    }
  }

  private def load(file: File): Unit = {
    load(ImageIO.read(file))
  }

  private def load(image: BufferedImage): Unit = {
    saveButton.enabled = true
    pixelateSlider.value = 10
    scaleSlider.value = 0
    imagesService.load(image)
    RenderAsync.enqueue()
  }

  private def saveClicked(): Unit = {
    val fc = createFileChooser(
      getConfigStringOr(MainFrame.SaveFilePath, defaultPath),
      ("PNG image", Seq("png"))
    )
    fc.showSaveDialog(this) match {
      case FileChooser.Result.Approve =>
        updateConfigString(MainFrame.SaveFilePath, fc.selectedFile.getParentFile.getAbsolutePath)
        val file = fc.selectedFile
        val file2 =
          if (FilenameUtils.isExtension(file.getName, "png"))
            file
          else
            new File(file.getAbsolutePath + ".png")
        if (!file2.exists() || accepted(Dialog.showConfirmation(this, "File exists, overwrite?", "Confirm"))) {
          save(file2)
        }
      case _ => // NOOP
    }
  }

  private def save(file: File): Unit = {
    import scala.collection.JavaConverters._
    val image = imagesService.previousUpdatedImage
    val fmt   = "png"
    val file2 = if (FilenameUtils.isExtension(file.getName, fmt)) file else new File(file.getAbsolutePath + ".png")
    saveInner(file2, image, ImageIO.getImageWritersByFormatName(fmt).asScala.toList)
  }

  private def saveInner(
      file: File,
      image: BufferedImage,
      writers: List[ImageWriter]
  ): Boolean = writers match {
    case Nil => false
    case writer :: rest =>
      val typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(image.getType)
      val writeParam    = writer.getDefaultWriteParam
      val metadata      = writer.getDefaultImageMetadata(typeSpecifier, writeParam)
      if (metadata.isReadOnly || !metadata.isStandardMetadataFormatSupported) {
        saveInner(file, image, rest)
      } else {
        setPngDpi(metadata)
        val stream = ImageIO.createImageOutputStream(file)
        try {
          writer.setOutput(stream)
          writer.write(metadata, new IIOImage(image, null, metadata), writeParam)
          true
        } catch {
          case th: Throwable =>
            showError(th)
            false
        } finally {
          stream.close()
        }
      }
  }

  private def paste(): Unit = {
    val clipboard = Toolkit.getDefaultToolkit.getSystemClipboard
    DataTransferHandler.importDataInner(clipboard.isDataFlavorAvailable, clipboard.getData)
  }

  private def setPngDpi(metadata: IIOMetadata): Unit = {
    val cmPerInch = 2.54
    // For PMG, it's dots per millimeter
    val dotsPerMilli = imagesService.dpi.toDouble / 10 / cmPerInch

    val horiz = new IIOMetadataNode("HorizontalPixelSize")
    val vert  = new IIOMetadataNode("VerticalPixelSize")
    horiz.setAttribute("value", dotsPerMilli.toString)
    vert.setAttribute("value", dotsPerMilli.toString)
    val dim = new IIOMetadataNode("Dimension")
    dim.appendChild(horiz)
    dim.appendChild(vert)
    val root = new IIOMetadataNode("javax_imageio_1.0")
    root.appendChild(dim)
    metadata.mergeTree("javax_imageio_1.0", root)
  }

  private def simplifyColorsCheckboxClicked(): Unit = {
    simplifyColorsSpinner.setEnabled(simplifyColorsCheckbox.selected)
    useDistinctCheckbox.enabled = simplifyColorsCheckbox.selected
    colorCodeCheckbox.enabled = simplifyColorsCheckbox.selected
    scheduleRender()
  }

  private def scheduleRender(): Unit = {
    RenderAsync.enqueue()
  }

  //
  // Helpers
  //

  private def attempt(stuff: => Unit): Unit = {
    try {
      stuff
    } catch {
      case th: Throwable => showError(th)
    }
  }

  private def showError(th: Throwable): Unit = {
    log.error("Something bad happened", th)
    Dialog.showMessage(message = th, title = "Something bad happened", messageType = Dialog.Message.Error)
  }

  private def showError(s: String): Unit = {
    Dialog.showMessage(message = "Error: " + s, title = "Something bad happened", messageType = Dialog.Message.Error)
  }

  private def accepted(dialog: => Dialog.Result.Value): Boolean = {
    val res = dialog
    res match {
      case Dialog.Result.Yes | Dialog.Result.Ok => true
      case _                                    => false
    }
  }

  private def UnfocusableButton(label: String) = new Button(label) { focusable = false }

  private val defaultPath = FileSystemView.getFileSystemView.getHomeDirectory.getAbsolutePath

  private def getConfigStringOr(path: String, default: String): String = this.synchronized {
    if (config.hasPath(path)) {
      config.getString(path)
    } else {
      updateConfigString(path, default)
      default
    }
  }

  private def updateConfigString(path: String, value: String): Unit = this.synchronized {
    config = config.withValue(path, ConfigValueFactory.fromAnyRef(value))
    saveConfig(config)
  }

  object DataTransferHandler extends TransferHandler {
    import scala.collection.JavaConverters._

    private val fileListFlavor = DataFlavor.javaFileListFlavor
    private val imageFlavor    = DataFlavor.imageFlavor

    override def canImport(support: TransferHandler.TransferSupport): Boolean = {
      if (!support.isDrop) {
        false
      } else {
        // We cannot invoke support.getTransferable.getTransferData here!
        // See https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6759788
        support.isDataFlavorSupported(fileListFlavor)
      }
    }

    override def importData(support: TransferHandler.TransferSupport): Boolean = {
      importDataInner(support.isDataFlavorSupported, support.getTransferable.getTransferData)
    }

    /** Import data from flavored source, used for both drag-n-drop and clipboard paste */
    def importDataInner(
        checkDataFlavor: DataFlavor => Boolean,
        getData: DataFlavor => Object
    ): Boolean = {
      if (checkDataFlavor(fileListFlavor)) {
        val data = getData(fileListFlavor).asInstanceOf[java.util.List[File]].asScala
        val file = data.head
        if (data.tail.nonEmpty || !imageFileSuffixes.contains(FilenameUtils.getExtension(file.getName).toLowerCase)) {
          false
        } else {
          val attempt = Try(load(file))
          attempt.failed foreach showError
          attempt.isSuccess
        }
      } else if (checkDataFlavor(imageFlavor)) {
        val image   = getData(imageFlavor).asInstanceOf[BufferedImage]
        val attempt = Try(load(image))
        attempt.failed foreach showError
        attempt.isSuccess
      } else {
        false
      }
    }
  }

  object Scaling {
    val LogCoeff = 100

    def linearToLog(linear: Int): Double = {
      math.pow(10, linear.toDouble / LogCoeff)
    }

    def logToLinear(log: Double): Int = {
      (LogCoeff * math.log10(log)).toInt
    }
  }

  object RenderAsync {
    private val shouldRender = new AtomicBoolean(true)

    val thread = new Thread(() => {
      while (!Thread.currentThread().isInterrupted) {
        while (initComplete.get && shouldRender.getAndSet(false)) {
          try {
            render()
          } catch {
            case th: Throwable => showError(th)
          }
        }
        RenderAsync.synchronized {
          RenderAsync.wait()
        }
      }
    })
    thread.setDaemon(true)
    thread.start()

    def enqueue(): Unit = {
      shouldRender.set(true)
      RenderAsync.synchronized {
        RenderAsync.notifyAll()
      }
    }

    private def render(): Unit = {
      val scalingFactor  = Scaling.linearToLog(scaleSlider.value)
      val pixelationMode = if (dominantRadioButton.selected) Pixelator.Mode.Dominant else Pixelator.Mode.Average
      val simplifyColorsOption =
        if (simplifyColorsCheckbox.selected)
          Some(
            (
              simplifyColorsSpinner.getValue.asInstanceOf[Int],
              colorCodeCheckbox.selected,
              useDistinctCheckbox.selected))
        else
          None
      val canvasImage = imagesService.updatedCanvas(
        scalingFactor,
        pixelateSlider.value,
        pixelationMode,
        gridCheckbox.selected,
        simplifyColorsOption
      )
      val innerImage = imagesService.previousUpdatedImage

      SwingUtilities.invokeAndWait(() => {
        viewer.setImage(canvasImage)

        val pixelateMax = ((innerImage.getWidth min innerImage.getHeight) / 10) max 10
        pixelateSlider.max = pixelateMax
        pixelateSlider.majorTickSpacing = pixelateMax / 3
        pixelateSlider.labels = {
          val range = pixelateSlider.min +: (10 until pixelateSlider.max by 10) :+ pixelateSlider.max
          range.map(i => (i, new Label(s"$i"))).toMap
        }
      })
    }
  }
}

object MainFrame {
  val LoadFilePath = "loadFilePath"
  val SaveFilePath = "saveFilePath"
}
