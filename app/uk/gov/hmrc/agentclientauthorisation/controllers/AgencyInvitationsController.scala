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

import play.api.mvc.Result
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.{InvitationNotFound, NoPermissionOnAgency, invalidInvitationStatus}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.{AgentInvitationValidation, AgentRequest, AuthActions}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.{InvitationsService, PostcodeService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class AgencyInvitationsController @Inject()(override val postcodeService:PostcodeService,
                                            invitationsService: InvitationsService,
                                            override val authConnector: AuthConnector) extends BaseController with AuthActions with HalWriter with AgentInvitationValidation with AgencyInvitationsHal {


  def createInvitation(arn: Arn) = onlyForAgents.async(parse.json) { implicit request =>
    withJsonBody[AgentInvitation] { agentInvitation =>
      checkForErrors(agentInvitation).flatMap(
        _.fold(makeInvitation(arn, agentInvitation))(error => Future successful error))
    }
  }

  private def makeInvitation(arn: Arn, authRequest: AgentInvitation)(implicit hc: HeaderCarrier): Future[Result] = {
    invitationsService.translateToMtdItId(authRequest.clientId, authRequest.clientIdType).flatMap {
      case None => Future successful BadRequest(s"invalid combination of client id ${authRequest.clientId} anf client id type ${authRequest.clientIdType}")
      case Some(clientId) =>
        invitationsService.create(
          arn, authRequest.service, clientId, authRequest.clientPostcode, authRequest.clientId, authRequest.clientIdType).map(
            invitation => Created.withHeaders(location(invitation)))
    }
  }

  private def location(invitation: Invitation) = {
    LOCATION -> routes.AgencyInvitationsController.getSentInvitation(invitation.arn, invitation.id.stringify).url
  }

  def getDetailsForAuthenticatedAgency() = onlyForAgents.async { implicit request =>
    Future successful Ok(toHalResource(request.arn,request.path))
  }

  def getDetailsForAgency(arn: Arn) = onlyForAgents.async { implicit request =>
    forThisAgency(arn) {
      Future successful Ok(toHalResource(arn, request.path))
    }
  }

  def getDetailsForAgencyInvitations(arn: Arn) = getDetailsForAgency(arn)

  def getSentInvitations(arn: Arn, service: Option[String], clientIdType: Option[String], clientId: Option[String], status: Option[InvitationStatus]) = onlyForAgents.async { implicit request =>
    forThisAgency(arn) {
      invitationsService.agencySent(arn, service, clientIdType, clientId, status).map { invitations =>
       Ok(toHalResource(invitations, arn, service, clientIdType, clientId, status))
      }
    }
  }

  def getSentInvitation(arn: Arn, invitationId: String) = onlyForAgents.async { implicit request =>
    forThisAgency(arn) {
      invitationsService.findInvitation(invitationId).map {
        _.map(invitation => Ok(toHalResource(invitation))) getOrElse InvitationNotFound
      }
    }
  }

  private def forThisAgency(arn: Arn)( block: => Future[Result])(implicit request: AgentRequest[_]) = {
    if (arn != request.arn) {
      Future successful NoPermissionOnAgency
    } else {
      block
    }
  }

  def cancelInvitation(arn: Arn, invitationId: String) = onlyForAgents.async { implicit request =>
    forThisAgency(arn) {
      invitationsService.findInvitation(invitationId) flatMap {
        case Some(i) if i.arn == arn => invitationsService.cancelInvitation(i) map {
          case Right(_) => NoContent
          case Left(message) => invalidInvitationStatus(message)
        }
        case None => Future successful InvitationNotFound
        case _ => Future successful NoPermissionOnAgency
      }
    }
  }

  override protected def agencyLink(invitation: Invitation) = None
}
