package org.fs.embroidery

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File

import scala.swing._
import scala.swing.event.ButtonClicked
import scala.swing.event.MouseClicked
import scala.util.Failure
import scala.util.Success

import com.typesafe.config.Config
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import hu.kazocsaba.imageviewer.ImageViewer
import javax.imageio.ImageIO
import org.slf4s.Logging
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.WindowConstants
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.filechooser.FileSystemView

class MainFrame(
    var config: Config,
    saveConfig: Config => Unit
) extends Frame
    with Logging {

  private def defaultBorder = BorderFactory.createLineBorder(Color.gray, 1)

  private val viewer = new ImageViewer()
  private val images = new Images(true)

  private var loadedFileOption: Option[File] = None
  private var isPortrait                     = true

  //
  // Initialization block
  //

  // TODO: Clipboard
  // TODO: Drag-n-drop
  attempt {
    def UnfocusableButton(label: String) = new Button(label) { focusable = false }

    val browseLoadButton = UnfocusableButton("Browse")
    val browseSaveButton = UnfocusableButton("Browse")
    val saveBtn          = UnfocusableButton("Save")

    val loadFileInput = new TextField()
    val saveFileInput = new TextField()

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
//      findNextBtn.margin = new Insets(0, 2, 0, 2)
//      findFromStartBtn.margin = new Insets(0, 2, 0, 2)
//      pasteLeftBtn.margin = new Insets(2, 2, 2, 2)
//      pasteRightBtn.margin = new Insets(2, 2, 2, 2)
//      editorScrollPane.border = defaultBorder
//      editorScrollPane.preferredSize = new Dimension(0, 100)
    }
    styleComponents()

    listenTo(
      browseLoadButton,
      browseSaveButton
//      subtitlesList.mouse.clicks
    )
    // Button reactions
    reactions += {
      case ButtonClicked(`browseLoadButton`) => attempt(browseLoad())
      case ButtonClicked(`browseSaveButton`) => attempt(browseSave())
    }
    // Mouse click reactions
//    reactions += {
//      case MouseClicked(`subtitlesList`, _, _, clicks, _) if clicks >= 2 => subtitlesList.selectedEntry map editRecord
//    }

    title = BuildInfo.fullPrettyName
    size = new Dimension(1000, 700)
    peer.setLocationRelativeTo(null)
    peer.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)

    images.load(ImageIO.read(new File("_build/img.png")))
    render()
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
      case FileChooser.Result.Approve => ???
      case _                          => // NOOP
    }
  }

  private def browseSave(): Unit = {
    ???
  }

  private def render(): Unit = {
    viewer.setImage(images.updated())
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
      config = config.withValue(path, ConfigValueFactory.fromAnyRef(default))
      saveConfig(config)
      default
    }
  }
}
