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

package org.locationtech.rasterframes.datasource
import java.net.URI

import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import _root_.geotrellis.spark.LayerId
import org.locationtech.rasterframes._
import shapeless.tag.@@
import shapeless.tag

package object geotrellis extends DataSourceOptions {
  implicit val layerEncoder = Layer.layerEncoder

  /** Convenience column selector for a GeoTrellis layer. */
  def geotrellis_layer = col("layer").as[Layer]

  /** Tagged type construction for enabling type-safe extension methods for loading
   * a RasterFrameLayer from a GeoTrellis layer. */
  type GeoTrellisRasterFrameReader = DataFrameReader @@ GeoTrellisRasterFrameReaderTag
  trait GeoTrellisRasterFrameReaderTag
  /** Tagged type construction for enabling type-safe extension methods for writing
   * a RasterFrameLayer to a GeoTrellis layer. */
  type GeoTrellisRasterFrameWriter[T] = DataFrameWriter[T] @@ GeoTrellisRasterFrameWriterTag
  trait GeoTrellisRasterFrameWriterTag

  /** Set of convenience extension methods on [[org.apache.spark.sql.DataFrameReader]]
   * for querying the GeoTrellis catalog and loading layers from it. */
  implicit class DataFrameReaderHasGeotrellisFormat(val reader: DataFrameReader) {
    /** Read the GeoTrellis Catalog of layers from a base path. */
    def geotrellisCatalog(base: URI): DataFrame =
      reader.format("geotrellis-catalog").load(base.toASCIIString)

    def geotrellis: GeoTrellisRasterFrameReader =
      tag[GeoTrellisRasterFrameReaderTag][DataFrameReader](reader.format(GeoTrellisLayerDataSource.SHORT_NAME))
  }

  implicit class DataFrameWriterHasGeotrellisFormat[T](val writer: DataFrameWriter[T]) {
    def geotrellis: GeoTrellisRasterFrameWriter[T] =
      tag[GeoTrellisRasterFrameWriterTag][DataFrameWriter[T]](writer.format(GeoTrellisLayerDataSource.SHORT_NAME))
  }

  implicit class GeoTrellisWriterAddLayer[T](val writer: GeoTrellisRasterFrameWriter[T]) {
    def asLayer(id: LayerId): DataFrameWriter[T] =
      writer
        .option(LAYER_PARAM, id.name)
        .option(ZOOM_PARAM, id.zoom.toString)

    def asLayer(layer: Layer): DataFrameWriter[T] =
      asLayer(layer.id)
        .option("path", layer.base.toASCIIString)
  }

  /** Extension methods for loading a RasterFrameLayer from a tagged `DataFrameReader`. */
  implicit class GeoTrellisReaderWithRF(val reader: GeoTrellisRasterFrameReader) {
    def withTileSubdivisions(divs: Int): GeoTrellisRasterFrameReader =
      tag[GeoTrellisRasterFrameReaderTag][DataFrameReader](
        reader.option(TILE_SUBDIVISIONS_PARAM, divs.toLong)
      )

    def withNumPartitions(partitions: Int): GeoTrellisRasterFrameReader =
      tag[GeoTrellisRasterFrameReaderTag][DataFrameReader](
        reader.option(NUM_PARTITIONS_PARAM, partitions.toLong)
      )

    def loadLayer(uri: URI, id: LayerId): RasterFrameLayer =
      reader
        .option(LAYER_PARAM, id.name)
        .option(ZOOM_PARAM, id.zoom.toString)
        .load(uri.toASCIIString)
        .asLayer

    def loadLayer(layer: Layer): RasterFrameLayer = loadLayer(layer.base, layer.id)
  }
}
