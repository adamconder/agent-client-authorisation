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

package uk.gov.hmrc.agentclientauthorisation.service

import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.mockito.ArgumentMatchers.{any, eq => eqs, _}
import play.api.mvc.Request
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.test.FakeRequest
import play.api.libs.json.JsObject
import play.api.libs.json.Json.JsValueWrapper
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.BSONObjectID.generate
import reactivemongo.core.errors.ReactiveMongoException
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.TransitionInvitation
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, MtdItId}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InvitationsServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TransitionInvitation {
  val invitationsRepository: InvitationsRepository = mock[InvitationsRepository]
  val relationshipsConnector: RelationshipsConnector = mock[RelationshipsConnector]
  val auditService: AuditService = mock[AuditService]
  val desConnector: DesConnector = mock[DesConnector]

  val service = new InvitationsService(invitationsRepository, relationshipsConnector, desConnector, auditService, "10 days")

  val ninoAsString: String = nino1.value

  implicit val hc = HeaderCarrier()
  implicit val request: Request[Any] = FakeRequest()


  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(invitationsRepository, relationshipsConnector)
  }

  "acceptInvitation" should {
    "create a relationship" when {
      "invitation status update succeeds" in {
        whenRelationshipIsCreated thenReturn (Future successful {})
        val acceptedTestInvitation = transitionInvitation(testInvitation, Accepted)
        whenStatusIsChangedTo(Accepted) thenReturn (Future successful acceptedTestInvitation)

        val response = await(service.acceptInvitation(testInvitation))

        response shouldBe Right(acceptedTestInvitation)
      }

    }

    "should not create a relationship" when {
      "invitation has already been accepted" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Accepted)))

        response shouldBe Left(StatusUpdateFailure(Accepted, "The invitation cannot be transitioned to Accepted because its current status is Accepted. Only Pending invitations may be transitioned to Accepted."))
      }
      "invitation has been cancelled" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Cancelled)))

        response shouldBe Left(StatusUpdateFailure(Cancelled, "The invitation cannot be transitioned to Accepted because its current status is Cancelled. Only Pending invitations may be transitioned to Accepted."))
      }
      "invitation has been rejected" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Rejected)))

        response shouldBe Left(StatusUpdateFailure(Rejected, "The invitation cannot be transitioned to Accepted because its current status is Rejected. Only Pending invitations may be transitioned to Accepted."))
      }
      "invitation has expired" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Expired)))

        response shouldBe Left(StatusUpdateFailure(Expired, "The invitation cannot be transitioned to Accepted because its current status is Expired. Only Pending invitations may be transitioned to Accepted."))
      }
    }

    "should not change the invitation status when relationship creation fails" in {
      whenRelationshipIsCreated thenReturn (Future failed ReactiveMongoException("Mongo error"))

      intercept[ReactiveMongoException] {
        await(service.acceptInvitation(testInvitation))
      }

      verify(invitationsRepository, never()).update(any[BSONObjectID], any[InvitationStatus])(any())
    }
  }


  "rejectInvitation" should {
    "update the invitation status" in {
      val rejectedTestInvitation = transitionInvitation(testInvitation, Rejected)
      whenStatusIsChangedTo(Rejected) thenReturn rejectedTestInvitation

      val response = await(service.rejectInvitation(testInvitation))

      response shouldBe Right(rejectedTestInvitation)
    }

    "not reject a cancelled invitation" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Cancelled)))

      response shouldBe Left(StatusUpdateFailure(Cancelled, "The invitation cannot be transitioned to Rejected because its current status is Cancelled. Only Pending invitations may be transitioned to Rejected."))
    }

    "not reject an accepted invitation" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Accepted)))

      response shouldBe Left(StatusUpdateFailure(Accepted, "The invitation cannot be transitioned to Rejected because its current status is Accepted. Only Pending invitations may be transitioned to Rejected."))
    }

    "not reject an already rejected invitation" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Rejected)))

      response shouldBe Left(StatusUpdateFailure(Rejected, "The invitation cannot be transitioned to Rejected because its current status is Rejected. Only Pending invitations may be transitioned to Rejected."))
    }
    "not reject an invitation that has expired" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Expired)))

      response shouldBe Left(StatusUpdateFailure(Expired, "The invitation cannot be transitioned to Rejected because its current status is Expired. Only Pending invitations may be transitioned to Rejected."))
    }
  }

  "cancelInvitation" should {
    "update the invitation status" in {
      val cancelledTestInvitation = transitionInvitation(testInvitation, Cancelled)
      whenStatusIsChangedTo(Cancelled) thenReturn cancelledTestInvitation

      val response = await(service.cancelInvitation(testInvitation))

      response shouldBe Right(cancelledTestInvitation)
    }

    "not cancel a cancelled invitation" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Cancelled)))

      response shouldBe Left(StatusUpdateFailure(Cancelled, "The invitation cannot be transitioned to Cancelled because its current status is Cancelled. Only Pending invitations may be transitioned to Cancelled."))
    }

    "not cancel an accepted invitation" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Accepted)))

      response shouldBe Left(StatusUpdateFailure(Accepted, "The invitation cannot be transitioned to Cancelled because its current status is Accepted. Only Pending invitations may be transitioned to Cancelled."))
    }

    "not cancel an already rejected invitation" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Rejected)))

      response shouldBe Left(StatusUpdateFailure(Rejected, "The invitation cannot be transitioned to Cancelled because its current status is Rejected. Only Pending invitations may be transitioned to Cancelled."))
    }

    "not cancel an invitation that has expired" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Expired)))

      response shouldBe Left(StatusUpdateFailure(Expired, "The invitation cannot be transitioned to Cancelled because its current status is Expired. Only Pending invitations may be transitioned to Cancelled."))
    }
  }

  "findInvitation" should {
    "return None when the passed invitationId is not in the repository" in {
      when(invitationsRepository.find((any[String], any[JsObject]))).thenReturn(Future.successful(List.empty))
      await(service.findInvitation(InvitationId("INVALIDINV"))) shouldBe None
    }

    "return Some(invitation) when invitation is present" in {
      val invitation = testInvitation
      val query = ("invitationId" -> invitation.invitationId)
      when(invitationsRepository.find((any[String], any[JsObject]))).thenReturn(Future successful List(invitation))

      await(service.findInvitation(invitation.invitationId)) shouldBe Some(invitation)
    }

    "return some invitation with status Expired when invitation is older than expiryDuration" in {
      def elevenDaysAgo() = now().minusDays(11)
      val invitation = testInvitationWithDate(elevenDaysAgo)
      when(invitationsRepository.find((any[String], any[JsObject]))).thenReturn(Future successful List(invitation))
      when(invitationsRepository.update(eqs(invitation.id), eqs(Expired))(any())).thenReturn(testInvitationWithStatus(Expired))

      await(service.findInvitation(invitation.invitationId)).get.status shouldBe Expired

      verify(auditService).sendInvitationExpired(eqs(invitation))(any(), any())
    }
  }

  "translateToMtdItId" should {
    "return the mtfItId if supplied" in {
      val shouldBeMtdItId: Option[MtdItId] = await(service.translateToMtdItId(mtdItId1.value, "MTDITID"))
      shouldBeMtdItId.head.value shouldBe mtdItId1.value
    }

    "return None if an invalid client id type is supplied" in {
      val shouldBeNone: Option[MtdItId] = await(service.translateToMtdItId("id", "noSuchType"))
      shouldBeNone.isEmpty shouldBe true
    }

    "return an mtdItId if a nino is supplied for which there is no persisted match and there is a matching DES business partner record with an mtdItId" in {
      whenDesBusinessPartnerRecordExistsFor(Nino(nino1.value), mtdItId1.value)

      val shouldBeMtdItId: Option[MtdItId] = await(service.translateToMtdItId(nino1.value, "ni"))
      shouldBeMtdItId.head shouldBe mtdItId1
    }

    "return None if a nino is supplied for which there is a matching DES business partner record without a mtdItId" in {
      whenDesBusinessPartnerRecordExistsWithoutMtdItIdFor(Nino(nino1.value))
      val shouldBeMtdItId: Option[MtdItId] = await(service.translateToMtdItId(nino1.value, "ni"))
      shouldBeMtdItId shouldBe None
    }

    "return None if a nino is supplied for which there is no matching DES business partner record" in {
      whenDesBusinessPartnerRecordDoesNotExist

      val shouldBeMtdItId: Option[MtdItId] = await(service.translateToMtdItId(nino1.value, "ni"))
      shouldBeMtdItId shouldBe None
    }
  }

  "create" should {
    "create an invitation" in {
      val serviceId: String = "serviceId"
      val postcode: String = "postcode"

      service.create(Arn(arn), serviceId, mtdItId1, postcode, nino1.value, "ni")
      verify(invitationsRepository, times(1)).create(Arn(arn), serviceId, mtdItId1, postcode, nino1.value, "ni")
    }
  }

  def serviceWithDurationAndCurrentDate(invitationExpiryDuration: String): InvitationsService = {
    new InvitationsService(invitationsRepository, relationshipsConnector, desConnector, auditService, invitationExpiryDuration)
  }

  "isInvitationExpired" should {
    "return expired when duration is one day and invitation created yesterday an hour before midnight" in {
      val service = new InvitationsService(invitationsRepository, relationshipsConnector, desConnector, auditService, "1 days")

      def yesterdayAnHourBeforeMidnight() = now().minusDays(1).millisOfDay().withMaximumValue().minusHours(1)
      val invitation = testInvitationWithDate(yesterdayAnHourBeforeMidnight)

      service.isInvitationExpired(invitation) shouldBe true
    }

    "return expired when duration is one day and invitation created yesterday a millisecond before midnight" in {
      val service = new InvitationsService(invitationsRepository, relationshipsConnector, desConnector, auditService, "1 days")

      def yesterdayAMilliBeforeMidnight() = now().minusDays(1).millisOfDay().withMaximumValue()
      val invitation = testInvitationWithDate(yesterdayAMilliBeforeMidnight)

      service.isInvitationExpired(invitation) shouldBe true
    }

    "return expired when duration is one day and invitation created today" in {
      val service = new InvitationsService(invitationsRepository, relationshipsConnector, desConnector, auditService, "1 days")

      def todayAtMidnight() = now().minusDays(1).millisOfDay().withMaximumValue().plusMillis(1)
      val invitation = testInvitationWithDate(todayAtMidnight)

      service.isInvitationExpired(invitation) shouldBe false
    }

    "return not expired when duration is one hour and 59 minutes pass" in {
      val service = new InvitationsService(invitationsRepository, relationshipsConnector, desConnector, auditService, "1 hour")

      def todayAtTen() = now().withTime(10, 0, 0, 0)
      def todayAt10FiftyNine() = now().withTime(10, 59, 0, 0)
      val invitation = testInvitationWithDate(todayAtTen)

      service.isInvitationExpired(invitation, todayAt10FiftyNine) shouldBe false
    }

    "return not expired when duration is one hour and 60 minutes pass" in {
      val service = new InvitationsService(invitationsRepository, relationshipsConnector, desConnector, auditService, "1 hour")

      def todayAtTen() = now().withTime(10, 0, 0, 0)
      def todayAtEleven() = now().withTime(11, 0, 0, 0)
      val invitation = testInvitationWithDate(todayAtTen)

      service.isInvitationExpired(invitation, todayAtEleven) shouldBe false
    }

    "return expired when duration is one hour and 60 and one millisecond minutes pass" in {
      val service = new InvitationsService(invitationsRepository, relationshipsConnector, desConnector, auditService, "1 hour")

      def todayAtTen() = now().withTime(10, 0, 0, 0)
      def todayAtElevenAndOneMilli() = now().withTime(11, 0, 0, 1)
      val invitation = testInvitationWithDate(todayAtTen)

      service.isInvitationExpired(invitation, todayAtElevenAndOneMilli) shouldBe true
    }
  }

  private def testInvitationWithStatus(status: InvitationStatus) = Invitation(generate,
    InvitationId.create(Arn(arn), mtdItId1, "mtd-sa", DateTime.parse("2001-01-01"))('A'),
    Arn(arn),
    "mtd-sa",
    mtdItId1.value,
    "A11 1AA",
    nino1.value,
    "ni",
    List(StatusChangeEvent(now(), Pending), StatusChangeEvent(now(), status))
  )

  private def testInvitation = testInvitationWithDate(now)

  private def testInvitationWithDate(creationDate: () => DateTime) = Invitation(generate,
    InvitationId.create(Arn(arn), mtdItId1, "mtd-sa", DateTime.parse("2001-01-01"))('A'),
    Arn(arn),
    "mtd-sa",
    mtdItId1.value,
    "A11 1AA",
    nino1.value,
    "ni",
    List(StatusChangeEvent(creationDate(), Pending))
  )

  private def whenStatusIsChangedTo(status: InvitationStatus): OngoingStubbing[Future[Invitation]] = {
    when(invitationsRepository.update(any[BSONObjectID], eqs(status))(any()))
  }

  private def whenRelationshipIsCreated: OngoingStubbing[Future[Unit]] = {
    when(relationshipsConnector.createRelationship(Arn(arn), mtdItId1))
  }

  private def whenDesBusinessPartnerRecordExistsFor(nino: Nino, mtdItId: String): OngoingStubbing[Future[Option[BusinessDetails]]] = {
    when(desConnector.getBusinessDetails(nino)).thenReturn(
      Future successful Some(BusinessDetails(Array(BusinessData(BusinessAddressDetails("postcode", None))), Some(MtdItId(mtdItId)))))
  }

  private def whenDesBusinessPartnerRecordExistsWithoutMtdItIdFor(nino: Nino): OngoingStubbing[Future[Option[BusinessDetails]]] = {
    when(desConnector.getBusinessDetails(nino)).thenReturn(
      Future successful Some(BusinessDetails(Array(BusinessData(BusinessAddressDetails("postcode", None))), None)))
  }

  private def whenDesBusinessPartnerRecordDoesNotExist: OngoingStubbing[Future[Option[BusinessDetails]]] = {
    when(desConnector.getBusinessDetails(nino1)).thenReturn(Future successful None)
  }
}
