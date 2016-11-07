/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.hal.{Hal, HalLink, HalLinks, HalResource}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, Result}
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.{AgentInvitationValidation, AgentRequest, AuthActions}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.{InvitationsService, PostcodeService}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class AgencyInvitationsController(override val postcodeService:PostcodeService,
                                  invitationsService: InvitationsService,
                                  override val authConnector: AuthConnector,
                                  override val agenciesFakeConnector: AgenciesFakeConnector) extends BaseController with AuthActions with HalWriter with AgentInvitationValidation{


  def createInvitation(arn: Arn) = onlyForSaAgents.async(parse.json) { implicit request =>
    withJsonBody[AgentInvitation] { authRequest =>
      checkForErrors(authRequest)
        .headOption.fold(makeInvitation(arn, authRequest))(error => Future successful error)
    }
  }

  private def makeInvitation(arn: Arn, authRequest: AgentInvitation): Future[Result] = {
    invitationsService.create(arn, authRequest.regime, authRequest.clientId, authRequest.postcode)
      .map(invitation => Created.withHeaders(location(invitation)))
  }

  private def location(invitation: Invitation) = {
    LOCATION -> routes.AgencyInvitationsController.getSentInvitation(invitation.arn, invitation.id.stringify).url
  }

  def getSentInvitations(arn: Arn, regime: Option[String], clientId: Option[String], status: Option[InvitationStatus]) = onlyForSaAgents.async { implicit request =>
    forThisAgency(arn) {
      invitationsService.agencySent(arn, regime, clientId, status).map { invitations =>
        Ok(toHalResource(invitations, arn, regime, clientId, status))
      }
    }
  }

  def getSentInvitation(arn: Arn, invitation: String) = onlyForSaAgents.async { implicit request =>
    forThisAgency(arn) {
      invitationsService.findInvitation(invitation).map {
        case Some(r) => Ok(toHalResource(r, arn))
        case None => NotFound
      }
    }
  }

  private def forThisAgency(arn: Arn)( block: => Future[Result])(implicit request: AgentRequest[_]) = {
    if (arn != request.arn) {
      Future successful Forbidden
    } else {
      block
    }
  }

  def cancelInvitation(arn: Arn, invitation: String) = Action.async {
    Future successful NotImplemented
  }

  private def toHalResource(requests: List[Invitation], arn: Arn, regime: Option[String], clientId: Option[String], status: Option[InvitationStatus]): HalResource = {
    val requestResources: Vector[HalResource] = requests.map(toHalResource(_, arn)).toVector

    val links = Vector(HalLink("self", routes.AgencyInvitationsController.getSentInvitations(arn, regime, clientId, status).url))
    Hal.hal(Json.obj(), links, Vector("invitations" -> requestResources))
  }

  private def toHalResource(invitation: Invitation, arn: Arn): HalResource = {
    var links = HalLinks(Vector(HalLink("self", routes.AgencyInvitationsController.getSentInvitation(arn, invitation.id.stringify).url),
      HalLink("agency", agenciesFakeConnector.agencyUrl(invitation.arn).toString)))
    if (invitation.status == Pending) {
      links = links ++ HalLink("cancel", routes.AgencyInvitationsController.cancelInvitation(arn, invitation.id.stringify).url)
    }
    HalResource(links, toJson(invitation).as[JsObject])
  }
}
