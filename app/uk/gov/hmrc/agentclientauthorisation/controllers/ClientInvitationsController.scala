/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentclientauthorisation.controllers

import javax.inject._

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationStatus}
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class ClientInvitationsController @Inject() (invitationsService: InvitationsService,
                                  override val authConnector: AuthConnector,
                                  override val agenciesFakeConnector: AgenciesFakeConnector) extends BaseController with AuthActions with HalWriter with ClientInvitationsHal  {

  // TODO clientId in path needs to be checked against nino from auth/userdetails
   def getDetailsForAuthenticatedClient() = onlyForSaClients.async { implicit request =>
    Future successful Ok(toHalResource("not-implemented", request.path))
  }

  // TODO clientId in path needs to be checked against nino from auth/userdetails
  def getDetailsForClient(clientId: String) = onlyForSaClients.async { implicit request =>
    if (clientId == clientId) {
      Future successful Ok(toHalResource(clientId, request.path))
    } else {
      Future successful NoPermissionOnClient
    }
  }

  // TODO clientId in path needs to be checked against nino from auth/userdetails
  def acceptInvitation(clientId: String, invitationId: String) = onlyForSaClients.async { implicit request =>
    actionInvitation(clientId, invitationId, invitationsService.acceptInvitation)
  }

  // TODO clientId in path needs to be checked against nino from auth/userdetails
  def rejectInvitation(clientId: String, invitationId: String) = onlyForSaClients.async { implicit request =>
    actionInvitation(clientId, invitationId, invitationsService.rejectInvitation)
  }

  // TODO clientId in path needs to be checked against nino from auth/userdetails
  private def actionInvitation(clientId: String, invitationId: String, action: Invitation => Future[Either[String, Invitation]]) = {
    invitationsService.findInvitation(invitationId) flatMap {
      case Some(invitation) if invitation.clientId == clientId => action(invitation) map {
        case Right(_) => NoContent
        case Left(message) => invalidInvitationStatus(message)
      }
      case None => Future successful InvitationNotFound
      case _ => Future successful NoPermissionOnClient
    }
  }

  // TODO clientId in path needs to be checked against nino from auth/userdetails
  def getInvitation(clientId: String, invitationId: String) = onlyForSaClients.async { implicit request =>
    invitationsService.findInvitation(invitationId).map {
      case Some(x) if x.clientId == clientId => Ok(toHalResource(x))
      case None => InvitationNotFound
      case _ => NoPermissionOnClient
    }
  }
  // TODO clientId in path needs to be checked against nino from auth/userdetails
  def getInvitations(clientId: String, status: Option[InvitationStatus]) = onlyForSaClients.async { implicit request =>
    if (clientId == clientId) {
      invitationsService.clientsReceived(SUPPORTED_REGIME, clientId, status) map (results => Ok(toHalResource(results, clientId, status)))
    } else {
      Future successful NoPermissionOnClient
    }
  }

  override protected def agencyLink(invitation: Invitation): Option[String] =
    Some(agenciesFakeConnector.agencyUrl(invitation.arn).toString)
}
