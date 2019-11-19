/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright 2019 Astraea, Inc.
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

package org.locationtech.rasterframes.expressions.transformers

import geotrellis.raster.Tile
import org.apache.spark.sql.Column
import org.apache.spark.sql.catalyst.analysis.TypeCheckResult
import org.apache.spark.sql.catalyst.analysis.TypeCheckResult.{TypeCheckFailure, TypeCheckSuccess}
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.catalyst.expressions.{Expression, ExpressionDescription, TernaryExpression}
import org.apache.spark.sql.rf.TileUDT
import org.apache.spark.sql.types.DataType
import org.locationtech.rasterframes.encoders.CatalystSerializer._
import org.locationtech.rasterframes.expressions.DynamicExtractors._
import org.locationtech.rasterframes.expressions._

@ExpressionDescription(
  usage = "_FUNC_(tile, start_bit, num_bits) - In each cell of `tile`, extract `num_bits` from the cell value, starting at `start_bit` from the left.",
  arguments = """
  Arguments:
    * tile - tile column to extract values
    * start_bit -
    * num_bits -
  """,
  examples = """
  Examples:
    > SELECT  _FUNC_(tile, lit(4), lit(2))
       ..."""
)
case class ExtractBits(child1: Expression, child2: Expression, child3: Expression) extends TernaryExpression with CodegenFallback with Serializable {
  override val nodeName: String = "rf_local_extract_bits"

  override def children: Seq[Expression] = Seq(child1, child2, child3)

  override def dataType: DataType = child1.dataType

  override def checkInputDataTypes(): TypeCheckResult =
    if(!tileExtractor.isDefinedAt(child1.dataType)) {
      TypeCheckFailure(s"Input type '${child1.dataType}' does not conform to a raster type.")
    } else if (!intArgExtractor.isDefinedAt(child2.dataType)) {
      TypeCheckFailure(s"Input type '${child2.dataType}' isn't an integral type.")
    } else if (!intArgExtractor.isDefinedAt(child3.dataType)) {
      TypeCheckFailure(s"Input type '${child3.dataType}' isn't an integral type.")
    } else TypeCheckSuccess


  override protected def nullSafeEval(input1: Any, input2: Any, input3: Any): Any = {
    implicit val tileSer = TileUDT.tileSerializer
    val (childTile, childCtx) = tileExtractor(child1.dataType)(row(input1))

    val startBits = intArgExtractor(child2.dataType)(input2).value

    val numBits = intArgExtractor(child2.dataType)(input3).value

    childCtx match {
      case Some(ctx) => ctx.toProjectRasterTile(op(childTile, startBits, numBits)).toInternalRow
      case None => op(childTile, startBits, numBits).toInternalRow
    }
  }

  protected def op(tile: Tile, startBit: Int, numBits: Int): Tile = ExtractBits(tile, startBit, numBits)

}

object ExtractBits{
  def apply(tile: Column, startBit: Column, numBits: Column): Column =
    new Column(ExtractBits(tile.expr, startBit.expr, numBits.expr))

  def apply(tile: Tile, startBit: Int, numBits: Int): Tile = {
    assert(!tile.cellType.isFloatingPoint, "ExtractBits operation requires integral CellType")
    // this is the last `numBits` positions of "111111111111111"
    val widthMask = Int.MaxValue >> (63 - numBits)
    // map preserving the nodata structure
    tile.mapIfSet(x ⇒ x >> startBit & widthMask)
  }

}
