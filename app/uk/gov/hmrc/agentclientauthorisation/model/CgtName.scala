/*
 * Copyright 2019 HM Revenue & Customs
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


//TODO Use for APB-4370
//package uk.gov.hmrc.agentclientauthorisation.model
//import play.api.libs.json._
//
//case class CgtName(name: String)
//
//object CgtName {
//  implicit val format: Format[CgtName] = Json.format[CgtName]
//}
//
//case class InvalidCgt(code: String, reason: String)
//
//object InvalidCgt {
//  implicit val format: Format[InvalidCgt] = Json.format[InvalidCgt]
//}
//
//case class CgtResponse(response: Either[InvalidCgt, CgtName])
//
//object CgtResponse {
//  implicit val format: Format[CgtResponse] = new Format[CgtResponse] {
//    override def writes(cgtResponse: CgtResponse): JsValue = cgtResponse.response match {
//      case Right(trustName)   => Json.toJson(trustName)
//      case Left(invalidCgtName) => Json.toJson(invalidCgtName)
//    }
//
//    override def reads(json: JsValue): JsResult[CgtResponse] =
//      json.asOpt[CgtName] match {
//        case Some(name) => JsSuccess(CgtResponse(Right(name)))
//        case None       => JsSuccess(CgtResponse(Left(json.as[InvalidCgt])))
//      }
//  }}
