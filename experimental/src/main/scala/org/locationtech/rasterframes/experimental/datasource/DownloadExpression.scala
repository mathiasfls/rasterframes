/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright 2018 Astraea, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     [http://www.apache.org/licenses/LICENSE-2.0]
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.locationtech.rasterframes.experimental.datasource

import java.net.URI

import org.locationtech.rasterframes.util.GeoTiffInfoSupport
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.{Column, TypedColumn}
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.analysis.TypeCheckResult
import org.apache.spark.sql.catalyst.analysis.TypeCheckResult.{TypeCheckFailure, TypeCheckSuccess}
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.catalyst.expressions.{Expression, Generator, GenericInternalRow, UnaryExpression}
import org.apache.spark.sql.types._
import org.apache.spark.unsafe.types.UTF8String
import org.locationtech.rasterframes.util._

/**
 * Downloads data from URL and stores it in a column.
 *
 * @since 5/4/18
 */
case class DownloadExpression(override val child: Expression, colPrefix: String) extends UnaryExpression
  with Generator with CodegenFallback with GeoTiffInfoSupport with DownloadSupport with LazyLogging {

  override def nodeName: String = "download"

  override def checkInputDataTypes(): TypeCheckResult = {
    if(child.dataType == StringType) TypeCheckSuccess
    else TypeCheckFailure(
      s"Expected '${StringType.typeName}' but received '${child.dataType.simpleString}'"
    )
  }

  override def elementSchema: StructType = StructType(Seq(
    StructField(colPrefix + "_data", BinaryType, true)
  ))

  override def eval(input: InternalRow): TraversableOnce[InternalRow] = {
    val urlString = child.eval(input).asInstanceOf[UTF8String]
    val bytes = getBytes(URI.create(urlString.toString))
    Traversable(new GenericInternalRow(Array[Any](bytes)))
  }
}

object DownloadExpression {
  import org.locationtech.rasterframes.encoders.StandardEncoders.PrimitiveEncoders.arrayEnc

  def apply(urlColumn: Column): TypedColumn[Any, Array[Byte]] =
    new Column(
      new DownloadExpression(urlColumn.expr, urlColumn.columnName)
    ).as[Array[Byte]]
}
