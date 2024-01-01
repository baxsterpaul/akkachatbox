package com.hep88
import Server.{Account, ServerKey}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import com.hep88.Upnp.AddPortMapping
import scalafx.application.Platform
import scalafx.collections.ObservableBuffer


object Client {

  sealed trait Command

  //Protocol
  case object start extends Command
  case object LogoutRequest extends Command

  //Inbound Protocol
  case class LoginResult (validity: Boolean, username: String) extends Command
  case class MemberListUpdate (memberList: List[Account]) extends Command
  case class ReceiveMessage(message: String, fromActor: Option[ActorRef[Client.Command]]) extends Command

  //Outbound Protocol
  case class SendLoginRequest (username: String) extends Command
  case class SendMessage (message: String, targetActor: ActorRef[Client.Command]) extends Command
  case class SendBroadcastMessage(message: String) extends Command

  //State
  var defaultBehavior: Option[Behavior[Client.Command]] = None
  var remoteOpt: Option[ActorRef[Server.Command]] = None
  var nameOpt: Option[String] = None
  var TempClientMemberList: ObservableBuffer[Account] = ObservableBuffer.empty
  var ClientMemberList: ObservableBuffer[Account] = ObservableBuffer.empty
  var isLoggedIn: Boolean = false

  ClientMemberList.onChange { (ns, _) =>
    Platform.runLater {
      ClientApp.control.updateMemberList(ClientMemberList.toList)
    }
  }

  //Receptionist
  case object FindTheServer extends Command
  case class ListingResponse(listing: Receptionist.Listing) extends Command

  //Behavior on setup
  def apply(): Behavior[Client.Command] = Behaviors.setup { context =>

    //Upnp
    val upnpRef = context.spawn(Upnp(), Upnp.name)
    upnpRef ! AddPortMapping(2000)

    //Subscribe to Receptionist
    val listingAdapter: ActorRef[Receptionist.Listing] = context.messageAdapter ( Client.ListingResponse )
    context.system.receptionist ! Receptionist.Subscribe(ServerKey, listingAdapter)

    defaultBehavior = Some(Behaviors.receiveMessage[Client.Command] { message =>
      message match {

        //Send FindTheServer message to itself
        case Client.start =>
          context.self ! FindTheServer
          Behaviors.same

        //Find Server using Receptionist
        case FindTheServer =>
          context.system.receptionist !
            Receptionist.Find(Server.ServerKey, listingAdapter)
          Behaviors.same

        //Obtain ActorRef of Server and stores it in remoteOpt
        case ListingResponse(Server.ServerKey.Listing(listings)) =>
          val xs: Set[ActorRef[Server.Command]] = listings
          for (x <- xs) {
            remoteOpt = Some(x)
          }
          Behaviors.same

        case SendLoginRequest(username) =>
          remoteOpt.foreach (_ ! Server.ReceiveLoginRequest(username, context.self))
          Behaviors.same

        case LoginResult(validity, username) =>
          Platform.runLater{
            var status: String = ""
            if (validity) {
              status = "Successful Login"
              nameOpt = Option(username)
              isLoggedIn = true
              ClientApp.control.updateStatusLabel(status)
              ClientApp.control.updateCurrentUsername(username)
              ClientApp.control.hideLoginShowMessage()
            } else {
              status = "One User Per Client and Username must be unique"
              ClientApp.control.updateStatusLabel(status)
            }

          }
          Behaviors.same

        case MemberListUpdate(memberList) =>
          TempClientMemberList.clear()
          TempClientMemberList ++= memberList
          ClientMemberList.setAll(TempClientMemberList)
          Behaviors.same

        case SendMessage(message, targetActor) =>
          if (isLoggedIn) {
            targetActor ! ReceiveMessage(message, Some(context.self))
          }
          Behaviors.same

        case ReceiveMessage(message, fromActorOpt) =>
          val displayMessage = fromActorOpt match {
            case Some(ref) => s"$message"
            case None => s"[Broadcast] System: $message"
          }
          Platform.runLater {
            ClientApp.control.updateMessageList(displayMessage)
          }
          Behaviors.same

        case SendBroadcastMessage(message) =>
          if (isLoggedIn) {
            remoteOpt.foreach { serverActor =>
              serverActor ! Server.ReceiveBroadcastMessage(message, context.self)
            }
          }
          Behaviors.same

        case LogoutRequest =>
          for (name <- nameOpt; server <- remoteOpt) {
            server ! Server.Logout(name, context.self)
          }
          isLoggedIn = false
          Platform.runLater {
            ClientApp.control.updateStatusLabel("Successful Logout")
            ClientApp.control.showLoginHideMessage()
          }
          Behaviors.same

        case _=>
          Behaviors.unhandled
      }
    }.receiveSignal {
      case (context, PostStop) =>
        for (name <- nameOpt;
             remote <- remoteOpt){
          remote ! Server.Logout(name, context.self)
        }
        Behaviors.same
    })
    defaultBehavior.get
  }
}
