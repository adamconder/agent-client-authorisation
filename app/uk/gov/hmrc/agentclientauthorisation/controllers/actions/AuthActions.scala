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

package uk.gov.hmrc.agentclientauthorisation.controllers.actions

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import uk.gov.hmrc.agentclientauthorisation.connectors.{Accounts, AuthConnector}
import uk.gov.hmrc.domain.{SaUtr, AgentCode}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait AuthActions {

  def authConnector: AuthConnector

  val withAccounts = new ActionBuilder[RequestWithAccounts] with ActionRefiner[Request, RequestWithAccounts] {

    protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithAccounts[A]]] = {
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
      authConnector.currentAccounts()
        .map(new RequestWithAccounts(_, request))
        .map(Right(_))
        .recover({
          case e: uk.gov.hmrc.play.http.Upstream4xxResponse if e.upstreamResponseCode == 401 =>
            Left(Results.Unauthorized)
        })
    }
  }

  val onlyForAgents = withAccounts andThen new ActionRefiner[RequestWithAccounts, AgentRequest] {
    override protected def refine[A](request: RequestWithAccounts[A]): Future[Either[Result, AgentRequest[A]]] =
      Future successful (request.accounts.agent match {
        case None => Left(Results.Unauthorized)
        case Some(agentCode) => Right(new AgentRequest(agentCode, request))
      })
  }

  // not tested yet
  val saClientsOrAgents = withAccounts andThen new ActionRefiner[RequestWithAccounts, Request] {
    override protected def refine[A](request: RequestWithAccounts[A]): Future[Either[Result, Request[A]]] = {
      Future successful (request.accounts match {
        case Accounts(Some(agentCode), _) => Right(new AgentRequest(agentCode, request))
        case Accounts(None, Some(saUtr)) => Right(new SaClientRequest(saUtr, request))
        case _ => Left(Results.Unauthorized)
      })
    }
  }

}

class RequestWithAccounts[A](val accounts: Accounts, request: Request[A]) extends WrappedRequest[A](request)
class AgentRequest[A](val agentCode: AgentCode, request: Request[A]) extends WrappedRequest[A](request)
class SaClientRequest[A](val saUtr: SaUtr, request: Request[A]) extends WrappedRequest[A](request)