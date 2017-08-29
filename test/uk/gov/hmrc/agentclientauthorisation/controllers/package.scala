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

package uk.gov.hmrc.agentclientauthorisation

import org.joda.time.DateTime._
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.model.{Accepted, Invitation, Pending, StatusChangeEvent}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._

import scala.concurrent.Future

package object controllers {

  val arn = Arn("arn1")

  val mtdSaPendingInvitationId: BSONObjectID = BSONObjectID.generate
  val mtdSaAcceptedInvitationId: BSONObjectID = BSONObjectID.generate
  val otherRegimePendingInvitationId: BSONObjectID = BSONObjectID.generate

  val allInvitations = List(
    Invitation(mtdSaPendingInvitationId, arn, "HMRC-MTD-IT", mtdItId1.value, "postcode", nino1.value, "ni", events = List(StatusChangeEvent(now(), Pending))),
    Invitation(mtdSaAcceptedInvitationId, arn, "HMRC-MTD-IT", mtdItId1.value, "postcode", nino1.value, "ni", events = List(StatusChangeEvent(now(), Accepted))),
    Invitation(otherRegimePendingInvitationId, arn, "mtd-other", mtdItId1.value, "postcode", nino1.value, "ni", events = List(StatusChangeEvent(now(), Pending)))
  )

  val agentEnrolment = Set(
    Enrolment("HMRC-AS-AGENT", Seq(EnrolmentIdentifier("AgentReferenceNumber", arn.value)), confidenceLevel = ConfidenceLevel.L200,
      state = "", delegatedAuthRule = None)
  )

  val agentAffinityAndEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future.successful(new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Agent), Enrolments(agentEnrolment)))

  val clientEnrolment = Set(
    Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", nino1.value)), confidenceLevel = ConfidenceLevel.L200,
      state = "", delegatedAuthRule = None)
  )

//  val clientFixedEnrolment = Set(
//    Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("AgentReferenceNumber", "AA112233A")), confidenceLevel = ConfidenceLevel.L200,
//      state = "", delegatedAuthRule = None)
//  )

  val clientAffinityAndEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future.successful(new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Agent), Enrolments(clientEnrolment)))

//  val clientFixedAffinityAndEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
//    Future.successful(new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Agent), Enrolments(clientFixedEnrolment)))

  val neitherHaveAffinityOrEnrolment: Future[~[Option[AffinityGroup], Enrolments]] =
    Future.successful(new ~[Option[AffinityGroup], Enrolments](None, Enrolments(Set.empty[Enrolment])))


}
