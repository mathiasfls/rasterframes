/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright 2017 Astraea, Inc.
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

package org.locationtech.rasterframes.extensions

import org.locationtech.rasterframes.RasterFrameLayer
import org.locationtech.rasterframes.util.{WithMergeMethods, WithPrototypeMethods}
import geotrellis.raster._
import geotrellis.raster.io.geotiff.SinglebandGeoTiff
import geotrellis.spark.{Metadata, SpaceTimeKey, SpatialKey, TileLayerMetadata}
import geotrellis.util.MethodExtensions
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql._
import org.apache.spark.sql.types.{MetadataBuilder, Metadata => SMetadata}
import org.locationtech.rasterframes.model.TileDimensions
import spray.json.JsonFormat

import scala.reflect.runtime.universe._

/**
 * Library-wide implicit class definitions.
 *
 * @since 12/21/17
 */
trait Implicits {
  implicit class WithSparkSessionMethods(val self: SparkSession) extends SparkSessionMethods

  implicit class WithSQLContextMethods(val self: SQLContext) extends SQLContextMethods

  implicit class WithBKryoMethods(val self: SparkSession.Builder) extends KryoMethods.BuilderKryoMethods

  implicit class WithSKryoMethods(val self: SparkConf) extends KryoMethods.SparkConfKryoMethods

  implicit class WithProjectedRasterMethods[T <: CellGrid: WithMergeMethods: WithPrototypeMethods: TypeTag](
    val self: ProjectedRaster[T]) extends ProjectedRasterMethods[T]

  implicit class WithSinglebandGeoTiffMethods(val self: SinglebandGeoTiff) extends SinglebandGeoTiffMethods

  implicit class WithDataFrameMethods[D <: DataFrame](val self: D) extends DataFrameMethods[D]

  implicit class WithRasterFrameLayerMethods(val self: RasterFrameLayer) extends RasterFrameLayerMethods

  implicit class WithSpatialContextRDDMethods[T <: CellGrid](
    val self: RDD[(SpatialKey, T)] with Metadata[TileLayerMetadata[SpatialKey]]
  )(implicit spark: SparkSession) extends SpatialContextRDDMethods[T]

  implicit class WithSpatioTemporalContextRDDMethods[T <: CellGrid](
    val self: RDD[(SpaceTimeKey, T)] with Metadata[TileLayerMetadata[SpaceTimeKey]]
  )(implicit spark: SparkSession) extends SpatioTemporalContextRDDMethods[T]

  private[rasterframes]
  implicit class WithMetadataMethods[R: JsonFormat](val self: R)
      extends MetadataMethods[R]

  private[rasterframes]
  implicit class WithMetadataAppendMethods(val self: SMetadata)
      extends MethodExtensions[SMetadata] {
    def append = new MetadataBuilder().withMetadata(self)
  }

  private[rasterframes]
  implicit class WithMetadataBuilderMethods(val self: MetadataBuilder)
      extends MetadataBuilderMethods

  private[rasterframes]
  implicit class TLMHasTotalCells(tlm: TileLayerMetadata[_]) {
    // TODO: With upgrade to GT 3.1, replace this with the more general `Dimensions[Long]`
    def totalDimensions: TileDimensions = {
      val gb = tlm.layout.toRasterExtent().gridBoundsFor(tlm.extent)
      TileDimensions(gb.width, gb.height)
    }
  }
}

object Implicits extends Implicits

