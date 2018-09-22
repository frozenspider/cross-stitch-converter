package org.fs.embroidery

import java.io.File

import scala.io.Codec

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import org.apache.commons.io.FileUtils

object EmbroideryMarkerMain extends App {
  val configFile = new File("application.conf")

  def saveConfig(config: Config): Unit = {
    val renderOptions = ConfigRenderOptions.defaults()
    val configContent = config.root().render(renderOptions)
    FileUtils.write(configFile, configContent, Codec.UTF8.charSet)
  }

  val config: Config = ConfigFactory.parseFileAnySyntax(configFile)

  val ui = new MainFrame(config, saveConfig)
  ui.visible = true
}
