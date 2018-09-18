package org.fs.embroidery

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.Future
import scala.swing.BorderPanel
import scala.swing._
import scala.swing.event.ButtonClicked
import scala.swing.event.MouseClicked
import scala.swing.event.ValueChanged
import scala.util.Failure
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global

import com.typesafe.config.Config
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import hu.kazocsaba.imageviewer.DefaultStatusBar
import hu.kazocsaba.imageviewer.ImageViewer
import javax.imageio.ImageIO
import org.slf4s.Logging
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.filechooser.FileSystemView

class MainFrame(
    var config: Config,
    saveConfig: Config => Unit
) extends Frame
    with Logging {

  private def defaultBorder = BorderFactory.createLineBorder(Color.gray, 1)
  private val viewer        = new ImageViewer()
  private val pixelateSlider = new Slider {
    paintTicks = true
    paintLabels = true
    snapToTicks = true
    minorTickSpacing = 1
    min = 3
  }
  private val scaleLogCoeff = 100
  private val scaleSlider = new Slider {
    paintTicks = true
    paintLabels = true
    minorTickSpacing = scaleLogCoeff / 5
    min = -scaleLogCoeff * 2
    max = scaleLogCoeff * 2
    value = 0
    labels = Map(
      -scaleLogCoeff * 2                      -> new Label("x0.01"),
      (-scaleLogCoeff * math.log10(20)).toInt -> new Label("x0.05"),
      -scaleLogCoeff                          -> new Label("x0.1"),
      (-scaleLogCoeff * math.log10(2)).toInt  -> new Label("x0.5"),
      0                                       -> new Label("x1"),
      (scaleLogCoeff * math.log10(2)).toInt   -> new Label("x2"),
      scaleLogCoeff                           -> new Label("x10"),
      (scaleLogCoeff * math.log10(20)).toInt  -> new Label("x20"),
      scaleLogCoeff * 2                       -> new Label("x100")
    )
  }

  private val imagesService = new ImagesService(true)

  private var loadedFileOption: Option[File] = None
  private var isPortrait: Boolean            = true

  //
  // Initialization block
  //

  // TODO: Clipboard
  // TODO: Drag-n-drop
  // TODO: Mouse controls

  attempt {
    def UnfocusableButton(label: String) = new Button(label) { focusable = false }

    val browseLoadButton = UnfocusableButton("Browse")
    val browseSaveButton = UnfocusableButton("Browse")
    val saveBtn          = UnfocusableButton("Save")

    val loadFileInput = new TextField()
    val saveFileInput = new TextField()

    viewer.setStatusBar(new DefaultStatusBar {
      override def updateLabel(image: BufferedImage, x: Int, y: Int, availableWidth: Int): Unit = {
        super.updateLabel(image, x, y, availableWidth)
        label.setText(label.getText + ", zoom " + viewer.getZoomFactor)
      }
    })
    contents = new BorderPanel {
      def addHotkey(key: String, event: Int, mod: Int, f: => Unit): Unit = {
        peer
          .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
          .put(KeyStroke.getKeyStroke(event, mod), key)
        peer.getActionMap.put(key, new javax.swing.AbstractAction {
          def actionPerformed(arg: java.awt.event.ActionEvent): Unit = f
        })
      }

      import BorderPanel.Position._
      val topPanel = new BorderPanel {
        layout(loadFileInput) = Center
        layout(browseLoadButton) = East
      }
      layout(topPanel) = North
      val centerPanel = new BorderPanel {
        layout(Component.wrap(viewer.getComponent)) = Center
        val configPanel = new BoxPanel(Orientation.Vertical) {
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
      val bottomPanel = new BorderPanel {
        layout(saveFileInput) = Center
        layout(browseSaveButton) = East
      }
      layout(bottomPanel) = South
      addHotkey("save", KeyEvent.VK_S, InputEvent.CTRL_MASK, ???)
    }
    def styleComponents(): Unit = {
//      pixelateSlider.margin = new Insets(0, 2, 0, 2)
//      findFromStartBtn.margin = new Insets(0, 2, 0, 2)
//      pasteLeftBtn.margin = new Insets(2, 2, 2, 2)
//      pasteRightBtn.margin = new Insets(2, 2, 2, 2)
//      editorScrollPane.border = defaultBorder
//      editorScrollPane.preferredSize = new Dimension(0, 100)
    }
    styleComponents()

    listenTo(
      browseLoadButton,
      browseSaveButton,
      pixelateSlider,
      scaleSlider
//      subtitlesList.mouse.clicks
    )
    // Button reactions
    reactions += {
      case ButtonClicked(`browseLoadButton`) => attempt(browseLoad())
      case ButtonClicked(`browseSaveButton`) => attempt(browseSave())
      case ValueChanged(`pixelateSlider`)    => attempt(pixelateSliderChanged())
      case ValueChanged(`scaleSlider`)       => attempt(scaleSliderChanged())
    }

    title = BuildInfo.fullPrettyName
    size = new Dimension(1000, 700)
    peer.setLocationRelativeTo(null)
    peer.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)

    load(new File("_build/img.png"))
    RenderAsync.enqueue()
  }

  private def createFileChooser(lastAccessedFilePath: String): FileChooser = {
    val lastAccessdFile = new File(lastAccessedFilePath)
    val fc              = new FileChooser(if (lastAccessdFile.exists) lastAccessdFile else lastAccessdFile.getParentFile)
    fc.fileFilter = new FileNameExtensionFilter("Images", "jpg", "jpeg", "gif", "png")
    fc.peer.setPreferredSize(new Dimension(800, 600))
    fc
  }

  //
  // Actions and Callbacks
  //

  private def browseLoad(): Unit = {
    val fc = createFileChooser(getConfigStringOr("loadFilePath", defaultPath))
    fc.showOpenDialog(this) match {
      case FileChooser.Result.Approve =>
        updateConfigString("loadFilePath", fc.selectedFile.getAbsolutePath)
        load(fc.selectedFile)
      case _ => // NOOP
    }
  }

  private def browseSave(): Unit = {
    ???
  }

  def load(file: File): Unit = {
    val img = ImageIO.read(file)
    pixelateSlider.value = 10
    imagesService.load(img)
    RenderAsync.enqueue()
  }

  def pixelateSliderChanged(): Unit = {
    RenderAsync.enqueue()
  }

  def scaleSliderChanged(): Unit = {
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

  private def showError(ex: Throwable): Unit = {
    log.error("Something bad happened", ex)
    Dialog.showMessage(message = ex, title = "Something bad happened", messageType = Dialog.Message.Error)
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

  object RenderAsync {
    private val shouldRender = new AtomicBoolean(false)

    val thread = new Thread(() => {
      while (!Thread.currentThread().isInterrupted) {
        while (shouldRender.getAndSet(false)) {
          render()
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
      val scalingFactor = math.pow(10, scaleSlider.value.toDouble / scaleLogCoeff)
      val image         = imagesService.updated(scalingFactor, pixelateSlider.value)

      SwingUtilities.invokeAndWait(() => {
        viewer.setImage(image)

        val pixelateMax = (image.getWidth min image.getHeight) / 10
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
