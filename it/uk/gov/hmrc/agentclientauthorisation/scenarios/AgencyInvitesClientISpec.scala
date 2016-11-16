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

package uk.gov.hmrc.agentclientauthorisation.scenarios

import org.scalatest._
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.agentclientauthorisation.model.MtdClientId
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.AgentCode

class AgencyInvitesClientISpec extends FeatureSpec with ScenarioHelpers with GivenWhenThen with Matchers with MongoAppAndStubs with Inspectors with Inside with Eventually {

  implicit val arn = RandomArn()
  private implicit val agentCode = AgentCode("LMNOP123456")
  val mtdClientId: MtdClientId = FakeMtdClientId.random()

  feature("Agencies can filter")  {

    scenario("on the status of clients invitations") {
      val agency = new AgencyApi(arn, port)
      val client = new ClientApi(mtdClientId, port)
      Given("An agent and a client are logged in")
      given().agentAdmin(arn, agentCode).isLoggedInWithSessionId().andHasMtdBusinessPartnerRecord()
      given().client(clientId = mtdClientId).isLoggedInWithSessionId().aRelationshipIsCreatedWith(arn)

      When("the Agency sends several invitations to the Client")
      agencySendsSeveralInvitations(agency)(
        (mtdClientId, MtdSaRegime),
        (mtdClientId, MtdSaRegime)
      )

      Then(s"the Client should see 2 pending invitations from the Agency $arn")
      clientsViewOfPendingInvitations(client)

      When(s"the Client accepts the first Agency invitation")
      clientAcceptsFirstInvitation(client)

      Then(s"the Agency filters their sent invitations by status")
      agencyFiltersByStatus(agency)
    }
  }


  private def agencyFiltersByStatus(agency: AgencyApi): Unit = {
    val pendingFiltered = agency.sentInvitations(filteredBy = Seq("status" -> "pending"))
    pendingFiltered.numberOfInvitations shouldBe 1
    pendingFiltered.firstInvitation.status shouldBe "Pending"

    val acceptedFiltered = agency.sentInvitations(filteredBy = Seq("status" -> "accepted"))
    acceptedFiltered.numberOfInvitations shouldBe 1
    acceptedFiltered.firstInvitation.status shouldBe "Accepted"
  }
}
