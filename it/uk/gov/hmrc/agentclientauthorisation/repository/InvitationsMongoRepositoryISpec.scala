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

package uk.gov.hmrc.agentclientauthorisation.repository

import org.joda.time.DateTime
import org.scalatest.Inside
import org.scalatest.LoneElement._
import org.scalatest.concurrent.Eventually
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.ResetMongoBeforeTest
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InvitationsMongoRepositoryISpec extends UnitSpec with MongoSpecSupport with ResetMongoBeforeTest with Eventually with Inside {

  private val now = DateTime.now()

  private def repository = new InvitationsMongoRepository() {
    override def withCurrentTime[A](f: (DateTime) => A): A = f(now)
  }


  "InvitationsMongoRepository create" should {

    "create a new StatusChangedEvent of Pending" in {

      val arn = Arn("1")
      val customerRegimeId = "1A"

      inside(addInvitation((arn, "sa", customerRegimeId, "user-details"))) {
        case event =>
          event.arn shouldBe arn
          event.customerRegimeId shouldBe customerRegimeId
          event.regime shouldBe "sa"
          event.events.loneElement shouldBe StatusChangeEvent(now, Pending)
      }
    }
  }

  "AuthorisationRequestRepository update" should {

    "create a new StatusChangedEvent" in {

      val created = addInvitation((Arn("2"), "sa", "2A", "user-details"))
      val updated = update(created.id, Accepted)

      inside(updated) {
        case Invitation(created.id, _, _, _, _, events) =>
          events shouldBe List(
            StatusChangeEvent(now, Pending),
            StatusChangeEvent(now, Accepted)
          )
      }
    }
  }

  "AuthorisationRequestRepository listing by arn" should {

    "return previously created and updated elements" in {

      val arn = Arn("3")
      val saUtr1 = "SAUTR3A"
      val vrn2 = "VRN3B"

      val requests = addInvitations((arn, "sa", saUtr1, "user-details-1"), (arn, "vat", vrn2, "user-details-2"))
      update(requests.last.id, Accepted)

      val list = listByArn(arn).sortBy(_.customerRegimeId)

      inside(list head) {
        case Invitation(_, `arn`, "sa", `saUtr1`, "user-details-1", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }

      inside(list(1)) {
        case Invitation(_, `arn`, "vat", `vrn2`, "user-details-2", List(StatusChangeEvent(date1, Pending), StatusChangeEvent(date2, Accepted))) =>
          date1 shouldBe now
          date2 shouldBe now
      }
    }

    "return elements only for the given agent code" in {

      addInvitations((Arn("123A"), "sa", "1234567890", "user-details"), (Arn("123B"), "sa", "1234567891", "user-details"))

      inside(listByArn(Arn("123B")) loneElement) {
        case Invitation(_, Arn("123B"), "sa", "1234567891", "user-details", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }

  }


  "AuthorisationRequestRepository listing by customerSaUtr" should {

    "return an empty list when there is no such regime-regimeId pair" in {
      val regime = "sa"
      val regimeId = "4A"
      val arn = Arn("4")
      val userDetailsLink = "user-details"

      addInvitation((arn, regime, regimeId, userDetailsLink))

      listByClientRegimeId(regime, "no-such-id") shouldBe Nil
      listByClientRegimeId("different-regime", regimeId) shouldBe Nil
    }

    "return a single agent request" in {

      val regime = "sa"
      val regimeId = "4A"
      val arn = Arn("4")
      val userDetailsLink = "user-details"

      addInvitation((arn, regime, regimeId, userDetailsLink))

      inside(listByClientRegimeId(regime, regimeId) loneElement) {
        case Invitation(_, `arn`, `regime`, `regimeId`, `userDetailsLink`, List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }

    "return a multiple requests for the same customer" in {

      val firstAgent = Arn("5")
      val secondAgent = Arn("6")
      val regime = "sa"
      val customerSaUtr = "5A"
      val userDetailsLink = "user-details"


      addInvitations(
        (firstAgent, regime, customerSaUtr, userDetailsLink),
        (secondAgent, regime, customerSaUtr, userDetailsLink),
        (Arn("should-not-show-up"), "sa", "another-customer", userDetailsLink)
      )

      val requests = listByClientRegimeId(regime, customerSaUtr).sortBy(_.arn.arn)

      requests.size shouldBe 2

      inside(requests head) {
        case Invitation(_, `firstAgent`, `regime`, `customerSaUtr`, `userDetailsLink`, List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }

      inside(requests(1)) {
        case Invitation(_, `secondAgent`, `regime`, `customerSaUtr`, `userDetailsLink`, List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }
  }


  private type Invitation = (Arn, String, String, String)

  private def addInvitations(invitations: Invitation*) = {
    await(Future sequence invitations.map {
      case (code: Arn, regime: String, customerRegimeId: String, userDetailsLink: String) =>
        repository.create(code, regime, customerRegimeId, userDetailsLink)
    })
  }

  private def addInvitation(invitations: Invitation) = addInvitations(invitations) head

  private def listByArn(arn: Arn) = await(repository.list(arn))

  private def listByClientRegimeId(regime: String, regimeId: String) = await(repository.list(regime, regimeId))

  private def update(id: BSONObjectID, status: InvitationStatus) = await(repository.update(id, status))

}