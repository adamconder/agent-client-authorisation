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

package uk.gov.hmrc.agentclientauthorisation.service

import org.joda.time.DateTime.now
import org.mockito.Matchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.BSONObjectID.generate
import reactivemongo.core.errors.ReactiveMongoException
import uk.gov.hmrc.agentclientauthorisation.connectors.RelationshipsConnector
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class InvitationsServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {
  val invitationsRepository = mock[InvitationsRepository]
  val relationshipsConnector = mock[RelationshipsConnector]

  val service = new InvitationsService(invitationsRepository, relationshipsConnector)

  val arn = Arn("12345")
  val mtdClientId = MtdClientId("67890")
  val mtdClientIdAsString = mtdClientId.value

  implicit val hc = HeaderCarrier()



  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(invitationsRepository, relationshipsConnector)
  }

  "acceptInvitation" should {
    "create a relationship" when {
      "invitation status update succeeds" in {
        whenRelationshipIsCreated thenReturn(Future successful {})
        whenStatusIsChangedTo(Accepted) thenReturn(Future successful testInvitation)

        val response = await(service.acceptInvitation(testInvitation))

        response shouldBe true
      }

      "invitation status update fails" in {
        pending
      }
    }

    "should not create a relationship" when {
      "invitation has already been accepted" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Accepted)))

        response shouldBe false
      }
      "invitation has been cancelled" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Cancelled)))

        response shouldBe false
      }
      "invitation has been rejected" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Rejected)))

        response shouldBe false
      }
    }

    "should not change the invitation status when relationship creation fails" in {
      whenRelationshipIsCreated thenReturn(Future failed ReactiveMongoException("Mongo error"))

      intercept[ReactiveMongoException] {
        await(service.acceptInvitation(testInvitation))
      }

      verify(invitationsRepository, never()).update(any[BSONObjectID], any[InvitationStatus])
    }
  }


  "rejectInvitation" should {
    "update the invitation status" in {
      whenStatusIsChangedTo(Rejected) thenReturn testInvitation

      val response = await(service.rejectInvitation(testInvitation))

      response shouldBe true
    }

    "not reject a cancelled invitation" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Cancelled)))

      response shouldBe false
    }

    "not reject an accepted invitation" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Accepted)))

      response shouldBe false
    }

    "not reject an already rejected invitation" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Rejected)))

      response shouldBe false
    }
  }

  "cancelInvitation" should {
    "update the invitation status" in {
      whenStatusIsChangedTo(Cancelled) thenReturn testInvitation

      val response = await(service.cancelInvitation(testInvitation))

      response shouldBe true
    }

    "not cancel a cancelled invitation" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Cancelled)))

      response shouldBe false
    }

    "not cancel an accepted invitation" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Accepted)))

      response shouldBe false
    }

    "not cancel an already rejected invitation" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Rejected)))

      response shouldBe false
    }
  }

  "findInvitation" should {
    "return None when the passed invitationId cannot be parsed as a BSONObjectId" in {
      await(service.findInvitation("not a BSON Object Id")) shouldBe None
    }
  }

  // TODO do we need this?
//  "list" should {
//    "delegate to InvitationsRepository" in {
//      val invitation: Invitation = testInvitation
//      when(invitationsRepository.list(invitation.regime, invitation.clientId))
//        .thenReturn(Future successful List(invitation))
//
//      await(service.list(invitation.regime, invitation.clientId)) shouldBe Seq(invitation)
//
//      verify(invitationsRepository).list(invitation.regime, invitation.clientId)
//    }
//  }

  private def testInvitationWithStatus(status: InvitationStatus) = Invitation(generate,
    arn,
    "mtd-sa",
    mtdClientIdAsString,
    "A11 1AA",
    List(StatusChangeEvent(now(), Pending), StatusChangeEvent(now(), status))
  )

  private def testInvitation = Invitation(generate,
    arn,
    "mtd-sa",
    mtdClientIdAsString,
    "A11 1AA",
    List(StatusChangeEvent(now(), Pending))
  )

  private def whenStatusIsChangedTo(status: InvitationStatus): OngoingStubbing[Future[Invitation]] = {
    when(invitationsRepository.update(any[BSONObjectID], eqs(status)))
  }

  private def whenRelationshipIsCreated: OngoingStubbing[Future[Unit]] = {
    when(relationshipsConnector.createRelationship(arn, mtdClientId))
  }
}
