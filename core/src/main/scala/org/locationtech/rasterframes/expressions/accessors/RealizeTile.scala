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

package org.locationtech.rasterframes.expressions.accessors

import geotrellis.raster.Tile
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.catalyst.expressions.{Expression, ExpressionDescription}
import org.apache.spark.sql.rf.TileUDT
import org.apache.spark.sql.types.DataType
import org.apache.spark.sql.{Column, TypedColumn}
import org.locationtech.rasterframes._
import org.locationtech.rasterframes.encoders.CatalystSerializer._
import org.locationtech.rasterframes.expressions.UnaryRasterOp
import org.locationtech.rasterframes.model.TileContext

@ExpressionDescription(
  usage = "_FUNC_(raster) - Extracts the Tile component of a RasterSource, ProjectedRasterTile (or Tile) and ensures the cells are fully fetched.",
  examples = """
    Examples:
      > SELECT _FUNC_(raster);
         ....
  """)
case class RealizeTile(child: Expression) extends UnaryRasterOp with CodegenFallback {
  override def dataType: DataType = TileType

  override def nodeName: String = "rf_realize_tile"
  implicit val tileSer = TileUDT.tileSerializer

  override protected def eval(tile: Tile, ctx: Option[TileContext]): Any =
    (tile.toArrayTile(): Tile).toInternalRow
}

object RealizeTile {
  def apply(col: Column): TypedColumn[Any, Tile] =
    new Column(new RealizeTile(col.expr)).as[Tile]
}
