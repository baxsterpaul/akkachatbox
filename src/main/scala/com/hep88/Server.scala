package com.hep88
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import com.hep88.Upnp.AddPortMapping
import scalafx.collections.ObservableBuffer


object Server {

  sealed trait Command

  //Inbound Protocol
  case class ReceiveLoginRequest(username: String, fromActor: ActorRef[Client.Command]) extends Command

  case class Logout(username: String, fromActor: ActorRef[Client.Command]) extends Command

  case class Account(username: String, fromActor: ActorRef[Client.Command])

  case class ReceiveBroadcastMessage(message: String, fromClient: ActorRef[Client.Command]) extends Command

  //State
  val ServerKey: ServiceKey[Server.Command] = ServiceKey("Server")
  var ServerMemberList: ObservableBuffer[Account] = ObservableBuffer.empty


  ServerMemberList.onChange { (ns, _) =>
    ServerMemberList.foreach { Account =>
      Account.fromActor ! Client.MemberListUpdate(ServerMemberList.toList)
    }
  }

  //Behavior on setup
  def apply(): Behavior[Server.Command] = Behaviors.setup { context =>

    //Upnp
    val upnpRef = context.spawn(Upnp(), Upnp.name)
    upnpRef ! AddPortMapping(2000)

    //Register Server with the Receptionist
    context.system.receptionist ! Receptionist.Register(ServerKey, context.self)

    //Behavior on receiving message
    Behaviors.receiveMessage { message =>
      message match {

        case ReceiveLoginRequest(username, fromActor) =>
          val newMember = Account(username, fromActor)
          val isDuplicateEntry = ServerMemberList.exists { element =>
            element.username == newMember.username || element.fromActor == newMember.fromActor
          }
          if (isDuplicateEntry) {
            fromActor ! Client.LoginResult(false, username)
          } else {
            ServerMemberList += newMember
            fromActor ! Client.LoginResult(true, username)
            val joinMessage = s"User $username has joined the chat."
            ServerMemberList.foreach { account =>
              account.fromActor ! Client.ReceiveMessage(joinMessage, None)
            }
          }
          Behaviors.same

        case ReceiveBroadcastMessage(message, fromClient) =>
          if (ServerMemberList.exists(_.fromActor == fromClient)) {
            ServerMemberList.foreach { account =>
              account.fromActor ! Client.ReceiveMessage(message, Some(fromClient))
            }
          }
          Behaviors.same

        case Logout(username, fromActor) =>
          val accountToRemove = Account(username, fromActor)
          val leaveMessage = s"User $username has left the chat."
          ServerMemberList -= accountToRemove
          ServerMemberList.foreach { account =>
            account.fromActor ! Client.ReceiveMessage(leaveMessage, None)
          }
          fromActor ! Client.MemberListUpdate(ServerMemberList.toList)
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
  }
}


object ServerApp extends App {
  val greeterMain: ActorSystem[Server.Command] = ActorSystem(Server(), "HelloSystem")

}
