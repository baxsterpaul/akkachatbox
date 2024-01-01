package com.hep88
import akka.actor.typed.ActorSystem
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafxml.core.{FXMLLoader, NoDependencyResolver}
import scalafx.Includes._

object ClientApp extends JFXApp {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  val greeterMain: ActorSystem[Client.Command] = ActorSystem(Client(), "HelloSystem")

  greeterMain ! Client.start

  val loader = new FXMLLoader(null, NoDependencyResolver)
  loader.load(getClass.getResourceAsStream("view/MainView.fxml"))
  val border: scalafx.scene.layout.AnchorPane = loader.getRoot[javafx.scene.layout.AnchorPane]()
  val control = loader.getController[com.hep88.view.MainViewController#Controller]()
  control.chatClientRef = Option(greeterMain)
  stage = new PrimaryStage() {
    title = "Chat Application"
    scene = new Scene(){
      root = border
    }
  }

  stage.onCloseRequest = handle( {
    greeterMain.terminate
  })
}