package com.hep88.view
import com.hep88.{Client, ClientApp}
import akka.actor.typed.ActorRef
import com.hep88.Server.Account
import scalafxml.core.macros.sfxml
import scalafx.event.ActionEvent
import scalafx.scene.control.{Button, Label, ListCell, ListView, TextField}
import scalafx.collections.ObservableBuffer
import scalafx.Includes._

@sfxml
class MainViewController (

                           //Ui Elements
                           val messageStatus: Label,
                           val currentUser: Label,
                           val status: Label,
                           val sendStatus: Label,
                           val usernameList: ListView[Account]= new ListView[Account](),
                           val usernameTextField: TextField,
                           val messageList: ListView[String],
                           val messageTextField: TextField,
                           val JoinButtonUI: Button,
                           val MessageButtonAllUI: Button,
                           val MessageButtonUI: Button,
                           val LeaveButtonUI: Button)
{

  //State
  var username: String = usernameTextField.text.value
  var sending: Option[String] = None
  var chatClientRef: Option[ActorRef[Client.Command]] = None
  val receivedText: ObservableBuffer[String] =  new ObservableBuffer[String]()
  messageTextField.editable = false
  messageList.items = receivedText

  usernameList.cellFactory = { _ =>
    new ListCell[Account] {
      item.onChange { (_, _, account) =>
        text = if (account != null) account.username else ""
      }
    }
  }

  def updateMemberList(memberList: List[Account]): Unit = {
    val observableList = ObservableBuffer(memberList)
    usernameList.items = observableList
  }

  //Button function for sending LoginRequest
  def JoinButton(action: ActionEvent): Unit = {
    if (usernameTextField != null && usernameTextField.getText != null && usernameTextField.getText.trim.nonEmpty) {
      val name = usernameTextField.text()
      sending = Some(name)
      chatClientRef.foreach(_ ! Client.SendLoginRequest(name))
      usernameTextField.text = ""
    } else {
      messageStatus.text = "Empty input not allowed."
    }
  }

  //Button for sending message to individual
  def MessageButton(action: ActionEvent): Unit = {
    if (messageTextField != null && messageTextField.getText != null && messageTextField.getText.trim.nonEmpty) {
      if (usernameList.selectionModel().selectedIndex.value >= 0) {
        val selectedUser = usernameList.selectionModel().selectedItem.value
        val recipientName = selectedUser.username

        // Send the message to the selected user
        sending.foreach { name =>
          if (recipientName == name) {
            // Display a warning message if trying to send to themselves
            sendStatus.text = "Cannot send a message to yourself."
          } else {
            val messageToSelf = s"[Yourself] $name > $recipientName: ${messageTextField.text.value}"
            val messageToSend = s"[Private] $name: ${messageTextField.text.value}"

            // Update the message list with the message to be sent
            updateMessageList(messageToSelf)
            ClientApp.greeterMain ! Client.SendMessage(
              messageToSend, selectedUser.fromActor
            )
          }
        }
        messageTextField.text = ""
      } else {
        sendStatus.text = "Please select user from member list."
      }
    } else {
      sendStatus.text = "Empty input not allowed."
    }
  }

  //Button for sending message to all
  def MessageButtonAll(action: ActionEvent): Unit = {
    if (messageTextField != null && messageTextField.getText != null && messageTextField.getText.trim.nonEmpty) {
      sending.foreach { name =>
        ClientApp.greeterMain ! Client.SendBroadcastMessage(
          s"[Broadcast] $name: ${messageTextField.text()}"
        )
      }
      messageTextField.text = ""
    } else {
      sendStatus.text = "Empty input not allowed."
    }
  }
  def LogOutButton(action: ActionEvent): Unit = {
    if (currentUser != null && currentUser.getText != null && currentUser.getText.trim.nonEmpty) {
      chatClientRef.foreach(_ ! Client.LogoutRequest)
      clearCurrentUsername()
    }
  }

  def updateMessageList(text: String): Unit = {
    receivedText += text
  }

  def updateStatusLabel(text: String): Unit = {
    messageStatus.text = text
  }

  def updateCurrentUsername(username: String): Unit = {
    currentUser.text = username
    status.text = "Joined"
  }

  def clearCurrentUsername(): Unit = {
    currentUser.text = ""
    status.text = "Logged out"
  }

  def hideLoginShowMessage(): Unit = {
    usernameTextField.editable = false
    usernameTextField.promptText = "Logout first"
    JoinButtonUI.disable = true
    messageTextField.editable = true
    messageTextField.promptText = "Enter your message here"
    MessageButtonAllUI.disable = false
    MessageButtonUI.disable = false
    LeaveButtonUI.disable = false
  }

  def showLoginHideMessage(): Unit = {
    usernameTextField.editable = true
    usernameTextField.promptText = "Enter your username here"
    JoinButtonUI.disable = false
    messageTextField.editable = false
    messageTextField.promptText = "Login first"
    MessageButtonAllUI.disable = true
    MessageButtonUI.disable = true
    LeaveButtonUI.disable = true

  }

  }
