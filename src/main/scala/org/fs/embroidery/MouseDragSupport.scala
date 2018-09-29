package org.fs.embroidery

import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

import hu.kazocsaba.imageviewer.ImageMouseEvent
import hu.kazocsaba.imageviewer.ImageMouseMotionListener
import javax.swing.JComponent
import javax.swing.JViewport

class MouseDragSupport(viewport: JViewport, viewedComponent: JComponent) {
  var refPointOption: Option[Point] = None

  val mouseListener: MouseAdapter =
    new MouseAdapter {
      override def mousePressed(e: MouseEvent): Unit = {
        if (e.getButton == MouseEvent.BUTTON1) refPointOption = Some(e.getPoint)
      }
      override def mouseReleased(e: MouseEvent): Unit =
        refPointOption = None
    }

  val imageMouseMotionListener: ImageMouseMotionListener =
    new ImageMouseMotionListener {
      override def mouseMoved(e: ImageMouseEvent):   Unit = {}
      override def mouseEntered(e: ImageMouseEvent): Unit = {}
      override def mouseExited(e: ImageMouseEvent):  Unit = refPointOption = None
      override def mouseDragged(e: ImageMouseEvent): Unit = refPointOption foreach { refPoint =>
        val deltaX = refPoint.x - e.getOriginalEvent.getX
        val deltaY = refPoint.y - e.getOriginalEvent.getY
        val view   = viewport.getViewRect
        view.x += deltaX
        view.y += deltaY
        viewedComponent.scrollRectToVisible(view)
      }
    }
}
